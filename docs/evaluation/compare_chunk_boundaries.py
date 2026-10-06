"""Process a fresh fictional PDF copy without replacing the saved baseline.

Creates a labelled synthetic patient/document, then checks the existing pipeline
and repeats the saved questions. Successful requests write normal audit events.
Saved answers and evidence coverage still require manual claim review.
"""

import argparse
import hashlib
import json
import re
import uuid
from datetime import datetime
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from compare_prompt_baseline import request

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def upload_pdf(base_url, patient_id, actor, path):
    boundary = "MediAssistBoundaryEvaluation" + uuid.uuid4().hex
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="documentType"\r\n\r\nOTHER\r\n'
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="Synthetic_Boundary_Evaluation.pdf"\r\n'
        f'Content-Type: application/pdf\r\n\r\n'
    ).encode() + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
    headers = {"Content-Type": "multipart/form-data; boundary=" + boundary, "X-Actor": actor}
    try:
        response = urlopen(Request(
            base_url + f"/api/v1/patients/{patient_id}/documents/upload", data=body, headers=headers, method="POST"
        ), timeout=180)
    except HTTPError as error:
        response = error
    with response:
        return response.status, json.loads(response.read())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--output", type=Path, default=HERE / "rag_chunk_boundary_comparison.json")
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Choose a new output filename; existing evidence must not be overwritten.")

    previous = json.loads((HERE / "rag_prompt_evaluation_results_v3.json").read_text())
    fixture = json.loads((HERE / "synthetic_records.json").read_text())
    cases = {case["id"]: case for case in fixture["questions"]}
    pdf_path = ROOT / "output/pdf/MediAssist_Synthetic_RAG_Test_Document.pdf"
    run_id = datetime.now().strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:6]
    results = {
        "run_id": run_id,
        "actor": "boundary-evaluation-" + run_id,
        "started_at": datetime.now().isoformat(),
        "base_url": args.base_url,
        "scope": "Fresh fictional PDF copy; changed extraction separators/chunk boundaries; unchanged prompt/model/topK.",
        "baseline_main_document_id": previous["documents"]["main"]["document_id"],
        "pdf_sha256": hashlib.sha256(pdf_path.read_bytes()).hexdigest(),
        "source_sha256": {},
        "checks": [],
        "rag_runs": [],
    }
    for name, relative in {
        "prompt_builder": "documentqa/application/DocumentQaPromptBuilder.java",
        "chunker": "documentchunk/infrastructure/chunking/DefaultTextChunkingService.java",
        "boundaries": "documentchunk/infrastructure/chunking/TextChunkBoundaries.java",
        "pdf_extractor": "documentextraction/infrastructure/extraction/PdfBoxTextExtractionService.java",
        "pdf_extraction_properties": "documentextraction/infrastructure/extraction/PdfTextExtractionProperties.java",
    }.items():
        source = ROOT / "backend/src/main/java/com/mediassist/platform" / relative
        results["source_sha256"][name] = hashlib.sha256(source.read_bytes()).hexdigest()
    if results["source_sha256"]["prompt_builder"] != previous["prompt_builder_sha256"]:
        raise RuntimeError("The prompt changed since the comparison baseline; evaluate it separately first.")

    def save():
        args.output.write_text(json.dumps(results, indent=2) + "\n")

    def require(name, condition):
        results["checks"].append({"name": name, "passed": bool(condition)})
        save()
        print(f"{'PASS' if condition else 'FAIL'} {name}", flush=True)
        if not condition:
            raise RuntimeError(name)

    def call(method, suffix, payload=None, expected=200):
        status, response, _ = request(args.base_url, method, suffix, results["actor"], payload)
        require(f"{method} {suffix}: HTTP {expected}", status == expected)
        return response

    call("GET", "/actuator/health")
    patient = call("POST", "/api/v1/patients", {
        "mrn": "EVAL-BOUNDARIES-" + run_id,
        "firstName": "Synthetic", "lastName": "BoundaryEvaluation",
        "dateOfBirth": "1990-01-01", "gender": "UNKNOWN",
    }, expected=201)
    results["patient_id"] = patient["id"]
    save()
    status, document = upload_pdf(args.base_url, patient["id"], results["actor"], pdf_path)
    require("Upload labelled fictional PDF: HTTP 201", status == 201)
    results["main_document_id"] = document["id"]
    results["document"] = document
    route = f"/api/v1/documents/{document['id']}"
    extraction = call("POST", route + "/extract")
    require("Completed five-page extraction", extraction["extractionStatus"] == "COMPLETED" and extraction["pageCount"] == 5)
    results["extraction"] = call("GET", route + "/text")
    extracted_text = results["extraction"]["extractedText"]
    require("New extraction retains paragraph breaks", "\n\n" in extracted_text)
    extracted_paragraphs = [" ".join(paragraph.split()) for paragraph in re.split(r"(?:\r?\n[\t ]*){2,}", extracted_text)]
    expected_paragraphs = [paragraph for page in fixture["pages"] for paragraph in page["paragraphs"]]
    intact = sum(any(" ".join(paragraph.split()) in extracted for extracted in extracted_paragraphs)
                 for paragraph in expected_paragraphs)
    results["paragraph_preservation"] = {"intact": intact, "total": len(expected_paragraphs)}
    require("Known fixture paragraphs are not broken at ordinary wrapped lines", intact == len(expected_paragraphs))
    chunks = call("POST", route + "/chunk")
    results["main_chunks"] = chunks
    require("Ordered, nonempty chunks within the existing 1000-character limit",
            bool(chunks) and [chunk["chunkIndex"] for chunk in chunks] == list(range(len(chunks)))
            and all(0 < len(chunk["chunkText"]) <= 1000 for chunk in chunks))
    repeat = call("POST", route + "/chunk")
    require("Chunking remains idempotent", repeat == chunks)
    results["embedding_summary"] = call("POST", route + "/embeddings")
    require("New chunks receive fresh embeddings",
            results["embedding_summary"]["embeddedChunks"] == len(chunks)
            and results["embedding_summary"]["embeddingDimension"] == 1024)
    repeat_embeddings = call("POST", route + "/embeddings")
    require("Repeat embedding call does not regenerate vectors",
            repeat_embeddings["embeddedChunks"] == 0 and repeat_embeddings["skippedChunks"] == len(chunks))

    isolation = previous["documents"]["isolation"]
    for old in previous["rag_runs"]:
        is_isolation = old["case_id"] == "ISO"
        document_id = isolation["document_id"] if is_isolation else document["id"]
        source_chunks = isolation["chunks"] if is_isolation else chunks
        chunk_map = {chunk["chunkId"]: chunk for chunk in source_chunks}
        print(f"Asking {old['case_id']} topK={old['top_k']} ...", flush=True)
        status, response, seconds = request(
            args.base_url, "POST", f"/api/v1/documents/{document_id}/questions", results["actor"],
            {"question": old["question"], "topK": old["top_k"]},
        )
        sources = response.get("sources", [])
        context = " ".join("\n".join(chunk_map.get(source["chunkId"], {}).get("chunkText", "") for source in sources)
                           .casefold().split())
        markers = cases.get(old["case_id"], {"evidence": ["SILVER-PINE-908"]})["evidence"]
        citations = [int(number) for citation in re.findall(r"\[Chunk\s+[^\]]*\]", response.get("answer", ""))
                     for number in re.findall(r"\b(?:Chunk\s+)?(\d+)\b", citation)]
        results["rag_runs"].append({
            "case_id": old["case_id"], "question": old["question"], "top_k": old["top_k"],
            "status": status, "seconds": seconds, "response": response,
            "previous_status": old["status"], "previous_evidence_coverage": old["evidence_coverage"],
            "sources_in_document": all(source["chunkId"] in chunk_map for source in sources),
            "citation_numbers_in_range": all(1 <= number <= len(sources) for number in citations),
            "evidence_coverage": {
                "found": sum(" ".join(marker.casefold().split()) in context for marker in markers),
                "total": len(markers),
                "missing_markers": [marker for marker in markers if " ".join(marker.casefold().split()) not in context],
            },
            "manual_claim_review_required": True,
        })
        save()
        print(f"HTTP {status}; {seconds:.1f}s; {len(sources)} sources", flush=True)

    original_chunks = call("GET", f"/api/v1/documents/{results['baseline_main_document_id']}/chunks")
    require("Original baseline chunks were not changed", original_chunks == previous["documents"]["main"]["chunks"])
    results["finished_at"] = datetime.now().isoformat()
    save()
    print(f"Saved boundary comparison: {args.output}", flush=True)


if __name__ == "__main__":
    main()
