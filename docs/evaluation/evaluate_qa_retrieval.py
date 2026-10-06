"""Evaluate retrieval on existing fictional documents without replacing data.

The probe reads only the saved synthetic document vectors and embeds questions.
The replay calls the actual QA API; successful answers create normal audit events.
Coverage markers are not a measure of answer correctness. Review claims manually.
"""

import argparse
import hashlib
import json
import math
import re
import subprocess
import uuid
from datetime import datetime
from pathlib import Path
from urllib.request import Request, urlopen

from compare_prompt_baseline import request

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def coverage(matches, markers):
    context = " ".join("\n".join(match["chunkText"] for match in matches).casefold().split())
    missing = [marker for marker in markers if " ".join(marker.casefold().split()) not in context]
    return {"found": len(markers) - len(missing), "total": len(markers), "missing_markers": missing}


def cosine(left, right):
    return sum(a * b for a, b in zip(left, right)) / math.sqrt(
        sum(value * value for value in left) * sum(value * value for value in right)
    )


def select(candidates, count, weight):
    remaining = list(candidates)
    selected = []
    while remaining and len(selected) < count:
        def score(candidate):
            if not selected:
                return candidate["similarityScore"]
            redundancy = max(cosine(candidate["vector"], previous["vector"]) for previous in selected)
            return weight * candidate["similarityScore"] - (1 - weight) * redundancy

        next_candidate = max(remaining, key=score)
        selected.append(next_candidate)
        remaining.remove(next_candidate)
    return selected


def load_vectors(document_ids, model):
    ids = ",".join("'" + str(uuid.UUID(document_id)) + "'" for document_id in document_ids)
    if model != "BAAI/bge-m3":
        raise ValueError("This fixture probe is restricted to the existing BGE-M3 test vectors.")
    sql = f"""
        select coalesce(json_agg(row_to_json(candidate)), '[]'::json)
        from (
            select extraction.document_id as "documentId", chunk.id as "chunkId",
                chunk.chunk_index as "chunkIndex", chunk.chunk_text as "chunkText",
                embedding.embedding::text as vector
            from document_chunk_embeddings embedding
            join document_chunks chunk on chunk.id = embedding.chunk_id
            join document_extractions extraction on extraction.id = chunk.document_extraction_id
            where extraction.document_id in ({ids}) and embedding.model_name = 'BAAI/bge-m3'
            order by extraction.document_id, chunk.chunk_index
        ) candidate
    """
    result = subprocess.run([
        "docker", "exec", "mediassist-postgres", "psql", "-U", "mediassist", "-d", "mediassist",
        "-v", "ON_ERROR_STOP=1", "-At", "-c", sql,
    ], check=True, capture_output=True, text=True)
    candidates = json.loads(result.stdout)
    for candidate in candidates:
        candidate["vector"] = json.loads(candidate["vector"])
    return candidates


def probe(args, baseline, cases):
    isolation = json.loads((HERE / "rag_prompt_evaluation_results_v3.json").read_text())["documents"]["isolation"]
    main_id = baseline["main_document_id"]
    vectors = load_vectors([main_id, isolation["document_id"]], "BAAI/bge-m3")
    questions = list(dict.fromkeys(run["question"] for run in baseline["rag_runs"]))
    body = json.dumps({"texts": questions, "model": "BAAI/bge-m3"}).encode()
    with urlopen(Request(args.embedding_url, data=body, headers={"Content-Type": "application/json"}), timeout=180) as response:
        embeddings = json.loads(response.read())
    if embeddings["model"] != "BAAI/bge-m3" or embeddings["dimensions"] != 1024:
        raise RuntimeError("Unexpected query embedding model or dimensions.")
    if len(embeddings["embeddings"]) != len(questions):
        raise RuntimeError("Unexpected query embedding count.")
    if any(len(vector) != 1024 or not all(math.isfinite(value) for value in vector)
           for vector in embeddings["embeddings"]):
        raise RuntimeError("Invalid query embedding values or dimensions.")
    query_vectors = dict(zip(questions, embeddings["embeddings"]))
    result = {"mode": "probe", "started_at": datetime.now().isoformat(), "candidate_count": args.candidate_count,
              "scope": "Read-only synthetic vectors; query embeddings only; Python MMR reference, not a Java integration test.",
              "main_document_id": main_id, "runs": []}
    for run in baseline["rag_runs"]:
        document_id = isolation["document_id"] if run["case_id"] == "ISO" else main_id
        query = query_vectors[run["question"]]
        candidates = [dict(candidate, similarityScore=cosine(query, candidate["vector"]))
                      for candidate in vectors if candidate["documentId"] == document_id]
        candidates.sort(key=lambda candidate: (-candidate["similarityScore"], candidate["chunkIndex"], candidate["chunkId"]))
        candidates = candidates[:args.candidate_count]
        markers = cases.get(run["case_id"], {"evidence": ["SILVER-PINE-908"]})["evidence"]
        variants = []
        for weight in [1.0, 0.9, 0.7, 0.5, 0.3]:
            selected = select(candidates, run["top_k"], weight)
            variants.append({"relevance_weight": weight,
                             "chunk_ids": [candidate["chunkId"] for candidate in selected],
                             "chunk_indices": [candidate["chunkIndex"] for candidate in selected],
                             "evidence_coverage": coverage(selected, markers)})
        result["runs"].append({"case_id": run["case_id"], "question": run["question"], "top_k": run["top_k"],
                               "baseline_evidence_coverage": run["evidence_coverage"], "variants": variants})
    return result


def replay(args, baseline, cases):
    isolation = json.loads((HERE / "rag_prompt_evaluation_results_v3.json").read_text())["documents"]["isolation"]
    documents = {"main": {"document_id": baseline["main_document_id"], "chunks": baseline["main_chunks"]},
                 "isolation": isolation}
    result = {"mode": "replay", "started_at": datetime.now().isoformat(), "base_url": args.base_url,
              "candidate_count": args.candidate_count, "relevance_weight": args.relevance_weight,
              "actor": "retrieval-evaluation-" + datetime.now().strftime("%Y%m%d-%H%M%S"),
              "scope": "Actual QA API; existing synthetic documents/vectors; unchanged prompt/model/topK.",
              "main_document_id": baseline["main_document_id"], "source_sha256": {}, "runs": []}
    for name, relative in {
        "prompt": "documentqa/application/DocumentQaPromptBuilder.java",
        "retrieval": "documentqa/application/DocumentQaContextRetrievalService.java",
        "selector": "documentqa/application/DocumentQaContextSelector.java",
    }.items():
        path = ROOT / "backend/src/main/java/com/mediassist/platform" / relative
        result["source_sha256"][name] = hashlib.sha256(path.read_bytes()).hexdigest()
    if result["source_sha256"]["prompt"] != baseline["source_sha256"]["prompt_builder"]:
        raise RuntimeError("The prompt differs from the comparison baseline.")
    for previous in baseline["rag_runs"]:
        if args.case and previous["case_id"] not in args.case:
            continue
        document = documents["isolation" if previous["case_id"] == "ISO" else "main"]
        print(f"Asking {previous['case_id']} topK={previous['top_k']} ...", flush=True)
        status, response, seconds = request(args.base_url, "POST",
            f"/api/v1/documents/{document['document_id']}/questions", result["actor"],
            {"question": previous["question"], "topK": previous["top_k"]})
        source_map = {chunk["chunkId"]: chunk for chunk in document["chunks"]}
        sources = response.get("sources", [])
        selected = [source_map[source["chunkId"]] for source in sources if source["chunkId"] in source_map]
        markers = cases.get(previous["case_id"], {"evidence": ["SILVER-PINE-908"]})["evidence"]
        citations = [int(number) for reference in re.findall(r"\[Chunk\s+[^\]]*\]", response.get("answer", ""))
                     for number in re.findall(r"\b(?:Chunk\s+)?(\d+)\b", reference)]
        result["runs"].append({"case_id": previous["case_id"], "question": previous["question"],
            "top_k": previous["top_k"], "status": status, "seconds": seconds, "response": response,
            "baseline_evidence_coverage": previous["evidence_coverage"], "evidence_coverage": coverage(selected, markers),
            "sources_in_document": all(source["chunkId"] in source_map for source in sources),
            "citation_numbers_in_range": all(1 <= number <= len(sources) for number in citations),
            "manual_claim_review_required": True})
        args.output.write_text(json.dumps(result, indent=2) + "\n")
        print(f"HTTP {status}; {seconds:.1f}s; evidence {result['runs'][-1]['evidence_coverage']}", flush=True)
    for document in documents.values():
        status, chunks, _ = request(args.base_url, "GET", f"/api/v1/documents/{document['document_id']}/chunks", result["actor"])
        if status != 200 or chunks != document["chunks"]:
            raise RuntimeError("Existing synthetic chunks changed unexpectedly.")
    result["stored_chunks_unchanged"] = True
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["probe", "replay"])
    parser.add_argument("--baseline", type=Path, default=HERE / "rag_chunk_boundary_comparison_v2.json")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--embedding-url", default="http://localhost:8001/api/v1/embeddings")
    parser.add_argument("--candidate-count", type=int, default=20)
    parser.add_argument("--relevance-weight", type=float, default=0.7)
    parser.add_argument("--case", action="append", help="Replay selected saved cases; omit to replay all.")
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Choose a new output filename; existing evidence must not be overwritten.")
    if not 10 <= args.candidate_count <= 100 or not math.isfinite(args.relevance_weight) or not 0 <= args.relevance_weight <= 1:
        parser.error("Use candidate count 10-100 and a finite relevance weight between 0 and 1.")
    baseline = json.loads(args.baseline.read_text())
    fixture = json.loads((HERE / "synthetic_records.json").read_text())
    cases = {case["id"]: case for case in fixture["questions"]}
    result = probe(args, baseline, cases) if args.mode == "probe" else replay(args, baseline, cases)
    result["finished_at"] = datetime.now().isoformat()
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(f"Saved {args.output}", flush=True)


if __name__ == "__main__":
    main()
