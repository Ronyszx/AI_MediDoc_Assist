"""Repeat saved synthetic questions without replacing the original baseline.

This sends question requests only. It reuses stored documents and embeddings,
and each successful request writes its normal question audit event.
"""

import argparse
import hashlib
import json
import re
import time
from datetime import datetime
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

HERE = Path(__file__).parent


def request(base_url, method, path, actor, payload=None):
    headers = {"Accept": "application/json", "X-Actor": actor}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    started = time.monotonic()
    try:
        response = urlopen(Request(base_url + path, data=data, headers=headers, method=method), timeout=180)
    except HTTPError as error:
        response = error
    with response:
        body = json.loads(response.read())
        return response.status, body, round(time.monotonic() - started, 3)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--baseline", type=Path, default=HERE / "rag_evaluation_results.json")
    parser.add_argument("--output", type=Path, default=HERE / "rag_prompt_evaluation_results.json")
    args = parser.parse_args()
    if args.output.resolve() == args.baseline.resolve() or args.output.exists():
        parser.error("Choose a new output file; existing evidence must not be overwritten.")

    baseline = json.loads(args.baseline.read_text())
    fixture = json.loads((HERE / "synthetic_records.json").read_text())
    cases = {q["id"]: q for q in fixture["questions"]}
    results = {
        "baseline_run_id": baseline["run_id"],
        "started_at": datetime.now().isoformat(),
        "base_url": args.base_url,
        "actor": "prompt-evaluation-" + datetime.now().strftime("%Y%m%d-%H%M%S"),
        "scope": "Prompt-only change; same questions, document IDs, embeddings and topK values.",
        "prompt_builder_sha256": hashlib.sha256((HERE.parents[1] /
            "backend/src/main/java/com/mediassist/platform/documentqa/application/DocumentQaPromptBuilder.java").read_bytes()).hexdigest(),
        "rag_runs": [],
        "documents": {},
    }
    for label in ["main", "isolation"]:
        document_id = baseline[label + "_document_id"]
        status, chunks, _ = request(args.base_url, "GET", f"/api/v1/documents/{document_id}/chunks", results["actor"])
        if status != 200:
            raise RuntimeError(f"Cannot retrieve the saved {label} fixture: HTTP {status}")
        results["documents"][label] = {"document_id": document_id, "chunks": chunks}
    args.output.write_text(json.dumps(results, indent=2) + "\n")

    for previous in baseline["rag_runs"]:
        label = "isolation" if previous["case_id"] == "ISO" else "main"
        document = results["documents"][label]
        question = previous["question"]
        print(f"Asking {previous['case_id']} topK={previous['top_k']} ...", flush=True)
        status, response, seconds = request(
            args.base_url, "POST", f"/api/v1/documents/{document['document_id']}/questions", results["actor"],
            {"question": question, "topK": previous["top_k"]},
        )
        chunk_map = {c["chunkId"]: c for c in document["chunks"]}
        sources = response.get("sources", [])
        context = " ".join("\n".join(chunk_map.get(s["chunkId"], {}).get("chunkText", "") for s in sources).casefold().split())
        expected = cases.get(previous["case_id"], {"evidence": ["SILVER-PINE-908"]})
        markers = expected["evidence"]
        answer = response.get("answer", "")
        references = re.findall(r"\[Chunk\s+[^\]]*\]", answer)
        citations = [int(n) for reference in references for n in re.findall(r"\b(?:Chunk\s+)?(\d+)\b", reference)]
        source_ids = [s["chunkId"] for s in sources]
        previous_ids = [s["chunkId"] for s in previous["response"].get("sources", [])]
        results["rag_runs"].append({
            "case_id": previous["case_id"], "question": question, "top_k": previous["top_k"],
            "status": status, "seconds": seconds, "response": response,
            "baseline_status": previous["status"],
            "same_source_ids_and_order": source_ids == previous_ids if previous["status"] == 200 and status == 200 else None,
            "sources_in_document": all(chunk_id in chunk_map for chunk_id in source_ids),
            "citation_numbers_in_range": all(1 <= n <= len(sources) for n in citations),
            "cited_source_numbers": citations,
            "evidence_coverage": {
                "found": sum(" ".join(m.casefold().split()) in context for m in markers),
                "total": len(markers),
                "missing_markers": [m for m in markers if " ".join(m.casefold().split()) not in context],
            },
            "manual_claim_review_required": True,
        })
        args.output.write_text(json.dumps(results, indent=2) + "\n")
        print(f"HTTP {status}; {seconds:.1f}s; {len(sources)} sources", flush=True)
    results["finished_at"] = datetime.now().isoformat()
    args.output.write_text(json.dumps(results, indent=2) + "\n")
    print(f"Saved comparison: {args.output}", flush=True)


if __name__ == "__main__":
    main()
