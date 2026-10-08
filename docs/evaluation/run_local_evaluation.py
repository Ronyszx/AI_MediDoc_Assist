"""Evaluate running local services using newly created, fictional records only.

This is an integration smoke test and evidence-collection tool, not a clinical
factuality evaluator. It deliberately retains the labelled synthetic cohort.
"""

import argparse
import hashlib
import json
import math
import re
import subprocess
import time
import uuid
from datetime import datetime
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).parent
FIXTURE = json.loads((HERE / "synthetic_records.json").read_text())
RESULTS_PATH = HERE / "rag_evaluation_results.json"
BASE = "http://localhost:8080"
RUN_ID = datetime.now().strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:6]
ACTOR = "rag-evaluation-" + RUN_ID
RESULTS = {
    "run_id": RUN_ID,
    "actor": ACTOR,
    "started_at": datetime.now().isoformat(),
    "checks": [],
    "rag_runs": [],
    "configuration": {
        "embedding_model": "BAAI/bge-m3", "dimensions": 1024,
        "llm_model": "qwen3.5:4b", "top_k_values": [5, 10],
        "note": "Configured defaults; returned model names are also captured.",
    },
}


def save():
    RESULTS_PATH.write_text(json.dumps(RESULTS, indent=2) + "\n")


def check(name, passed, detail=""):
    RESULTS["checks"].append({"name": name, "passed": bool(passed), "detail": str(detail)})
    save()
    print(f"{'PASS' if passed else 'FAIL'} {name}: {detail}", flush=True)


def http(method, path, payload=None, *, raw=None, content_type=None, actor=True, timeout=180):
    url = path if path.startswith("http") else BASE + path
    headers = {"Accept": "*/*"}
    if actor:
        headers["X-Actor"] = ACTOR
    data = raw
    if payload is not None:
        data = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    elif content_type:
        headers["Content-Type"] = content_type
    request = Request(url, data=data, headers=headers, method=method)
    start = time.monotonic()
    try:
        response = urlopen(request, timeout=timeout)
    except HTTPError as error:
        response = error
    except (URLError, TimeoutError) as error:
        return {"status": 0, "body": {"transport_error": str(error)}, "headers": {},
                "seconds": round(time.monotonic() - start, 3), "bytes": b""}
    with response:
        body = response.read()
        try:
            parsed = json.loads(body)
        except (ValueError, UnicodeDecodeError):
            parsed = body.decode("utf-8", errors="replace")[:2000]
        return {"status": response.status, "body": parsed, "headers": dict(response.headers),
                "seconds": round(time.monotonic() - start, 3), "bytes": body}


def expect(name, response, status, predicate=None, required=False):
    passed = response["status"] == status
    if passed and predicate is not None:
        try:
            passed = bool(predicate(response["body"]))
        except (KeyError, TypeError, ValueError):
            passed = False
    detail = f"HTTP {response['status']} (expected {status}); {response['seconds']:.1f}s"
    if not passed:
        detail += "; " + str(response["body"])[:500]
        RESULTS.setdefault("failed_responses", []).append({"check": name, "status": response["status"],
                                                          "body": response["body"]})
    check(name, passed, detail)
    if required and not passed:
        raise RuntimeError(f"Cannot continue safely: {name}")
    return response["body"]


def multipart(file_bytes, filename, mime="application/pdf"):
    boundary = "MediAssistEvaluation" + uuid.uuid4().hex
    body = (
        f'--{boundary}\r\nContent-Disposition: form-data; name="documentType"\r\n\r\nOTHER\r\n'
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f'Content-Type: {mime}\r\n\r\n'
    ).encode() + file_bytes + f"\r\n--{boundary}--\r\n".encode()
    return body, "multipart/form-data; boundary=" + boundary


def upload(patient_id, file_bytes, filename, mime="application/pdf"):
    body, content_type = multipart(file_bytes, filename, mime)
    if len(body) > 20 * 1024 * 1024:
        # curl can receive the early 413 before sending a large request body.
        start = time.monotonic()
        response = subprocess.run([
            "curl", "-sS", "--max-time", "60", "--header", "Accept: */*",
            "--header", "Expect: 100-continue", "--header", "X-Actor: " + ACTOR,
            "--header", "Content-Type: " + content_type, "--data-binary", "@-",
            "--write-out", "\n%{http_code}",
            BASE + f"/api/v1/patients/{patient_id}/documents/upload",
        ], input=body, capture_output=True)
        text, _, code = response.stdout.decode(errors="replace").rpartition("\n")
        try:
            parsed = json.loads(text)
        except ValueError:
            parsed = text
        return {"status": int(code or 0), "body": parsed, "headers": {}, "bytes": text.encode(),
                "seconds": round(time.monotonic() - start, 3)}
    return http("POST", f"/api/v1/patients/{patient_id}/documents/upload", raw=body, content_type=content_type)


def process_document(document_id, label, expected_pages, check_guards=False):
    route = f"/api/v1/documents/{document_id}"
    if check_guards:
        expect("Embedding blocked before chunks", http("POST", route + "/embeddings"), 409)
        expect("Chunking blocked before extraction", http("POST", route + "/chunk"), 404)
        expect("Questions blocked before chunks", http("POST", route + "/questions",
               {"question": "What is recorded?", "topK": 5}), 409)
    extraction = expect(label + " extraction", http("POST", route + "/extract"), 200,
                        lambda b: b["extractionStatus"] == "COMPLETED" and b["pageCount"] == expected_pages,
                        required=True)
    expect(label + " extraction metadata", http("GET", route + "/extraction"), 200,
           lambda b: b["extractionId"] == extraction["extractionId"])
    if check_guards:
        expect("Extraction idempotency", http("POST", route + "/extract"), 200,
               lambda b: b["extractionId"] == extraction["extractionId"])
    text = expect(label + " extracted text", http("GET", route + "/text"), 200,
                  lambda b: bool(b["extractedText"].strip()), required=True)
    RESULTS[label.lower() + "_extraction"] = text
    chunks = expect(label + " chunking", http("POST", route + "/chunk"), 200,
                    lambda b: len(b) > 0 and [c["chunkIndex"] for c in b] == list(range(len(b)))
                    and all(c["chunkText"].strip() and c["chunkStatus"] == "CREATED" for c in b), required=True)
    RESULTS[label.lower() + "_chunks"] = chunks
    chunk_ids = {c["chunkId"] for c in chunks}
    expect(label + " chunk list", http("GET", route + "/chunks"), 200,
           lambda b: {c["chunkId"] for c in b} == chunk_ids)
    if check_guards:
        expect("Chunking idempotency", http("POST", route + "/chunk"), 200,
               lambda b: {c["chunkId"] for c in b} == chunk_ids and len(b) == len(chunks))
        expect("Questions blocked before embeddings", http("POST", route + "/questions",
               {"question": "What is recorded?", "topK": 5}), 404)
    summary = expect(label + " embedding generation", http("POST", route + "/embeddings"), 200,
                     lambda b: b["embeddingDimension"] == 1024 and b["modelName"] == "BAAI/bge-m3"
                     and b["totalChunks"] == len(chunks) and b["embeddedChunks"] == len(chunks), required=True)
    RESULTS[label.lower() + "_embedding_summary"] = summary
    if check_guards:
        expect("Embedding idempotency", http("POST", route + "/embeddings"), 200,
               lambda b: b["embeddedChunks"] == 0 and b["skippedChunks"] == len(chunks))
    expect(label + " embedding metadata hides vectors", http("GET", route + "/embeddings"), 200,
           lambda b: len(b) == len(chunks) and all(e["embeddingDimension"] == 1024 and "embedding" not in e
                                                    and "embeddings" not in e for e in b))
    save()
    print(f"{label}: {len(chunks)} chunks stored and embedded", flush=True)
    return chunks


def run_question(document_id, chunks, case, top_k):
    print(f"Asking {case['id']} with topK={top_k} ...", flush=True)
    response = http("POST", f"/api/v1/documents/{document_id}/questions",
                    {"question": case["question"], "topK": top_k})
    body = response["body"]
    chunk_map = {c["chunkId"]: c for c in chunks}
    sources = body.get("sources", []) if isinstance(body, dict) else []
    context = "\n".join(chunk_map.get(s.get("chunkId"), {}).get("chunkText", "") for s in sources)
    normalized = " ".join(context.casefold().split())
    found = [marker for marker in case["evidence"] if " ".join(marker.casefold().split()) in normalized]
    answer = body.get("answer", "") if isinstance(body, dict) else ""
    cited = [int(n) for n in re.findall(r"\[Chunk\s+(\d+)\]", answer)]
    valid = (response["status"] == 200 and bool(answer.strip()) and bool(sources)
             and len(sources) <= top_k and all(s.get("chunkId") in chunk_map for s in sources)
             and all(1 <= n <= len(sources) for n in cited)
             and body.get("documentId") == document_id and body.get("modelName") == "qwen3.5:4b")
    RESULTS["rag_runs"].append({
        "case_id": case["id"], "top_k": top_k, "question": case["question"], "expected": case["expected"],
        "status": response["status"], "seconds": response["seconds"], "response": body,
        "evidence_coverage": {"found": len(found), "total": len(case["evidence"]),
                              "found_markers": found,
                              "missing_markers": [m for m in case["evidence"] if m not in found]},
        "cited_source_numbers": cited,
    })
    check(f"{case['id']} topK {top_k}: answer transport/source scope", valid,
          f"HTTP {response['status']}; {response['seconds']:.1f}s; {len(sources)} sources; factual review separate")


def main():
    print("Running Maven clean test ...", flush=True)
    build = subprocess.run(["mvn", "clean", "test"], cwd=ROOT / "backend", capture_output=True, text=True)
    reports = list((ROOT / "backend/target/surefire-reports").glob("TEST-*.xml"))
    if build.returncode:
        check("Maven clean test", False, build.stdout[-2000:] + build.stderr[-1000:])
        raise RuntimeError("Maven verification failed")
    from xml.etree import ElementTree
    suites = [ElementTree.parse(path).getroot() for path in reports]
    test_count = sum(int(s.attrib.get("tests", 0)) for s in suites)
    failures = sum(int(s.attrib.get("failures", 0)) + int(s.attrib.get("errors", 0)) for s in suites)
    check("Maven clean test", bool(suites) and failures == 0,
          f"{test_count} tests; {failures} failures/errors; executed before HTTP evaluation")
    RESULTS["build"] = {"test_count": test_count, "failures_and_errors": failures, "exit_code": build.returncode}
    expect("Spring Boot health", http("GET", "/actuator/health", actor=False), 200,
           lambda b: b["status"] == "UP", required=True)
    expect("FastAPI health", http("GET", "http://localhost:8001/health", actor=False), 200,
           lambda b: b["status"] == "UP" and b["dimensions"] == 1024, required=True)
    expect("Ollama model available", http("GET", "http://localhost:11434/api/tags", actor=False), 200,
           lambda b: any(m["name"] == "qwen3.5:4b" for m in b["models"]), required=True)
    expect("OpenAPI includes pipeline", http("GET", "/api-docs", actor=False), 200,
           lambda b: all(f"/api/v1/documents/{{documentId}}/{path}" in b["paths"]
                         for path in ["extract", "chunk", "embeddings", "semantic-search", "questions"]))

    patient_request = {"mrn": "EVAL-RAG-" + RUN_ID, "firstName": "Synthetic", "lastName": "Evaluation",
                       "dateOfBirth": "1990-01-01", "gender": "UNKNOWN", "phone": None, "email": None}
    patient = expect("Create synthetic patient", http("POST", "/api/v1/patients", patient_request), 201, required=True)
    patient_id = patient["id"]
    RESULTS["patient_id"] = patient_id
    RESULTS["patient_mrn"] = patient_request["mrn"]
    save()
    expect("Read patient", http("GET", f"/api/v1/patients/{patient_id}"), 200, lambda b: b["id"] == patient_id)
    expect("Patient list includes fixture", http("GET", "/api/v1/patients"), 200,
           lambda b: any(p["id"] == patient_id for p in b))
    update = {k: v for k, v in patient_request.items() if k != "mrn"}
    update.update(lastName="EvaluationUpdated", status="ACTIVE")
    expect("Update patient", http("PUT", f"/api/v1/patients/{patient_id}", update), 200,
           lambda b: b["lastName"] == "EvaluationUpdated")
    for status in ["INACTIVE", "ACTIVE"]:
        expect("Patient status " + status, http("PATCH", f"/api/v1/patients/{patient_id}/status", {"status": status}),
               200, lambda b: b["status"] == status)
    expect("Duplicate MRN rejected", http("POST", "/api/v1/patients", patient_request), 409)
    bad_patient = dict(patient_request, mrn="", firstName="", email="not-an-email")
    error = http("POST", "/api/v1/patients", bad_patient)
    expect("Invalid patient DTO rejected", error, 400)
    check("DTO errors use consistent error model", isinstance(error["body"], dict)
          and all(k in error["body"] for k in ["timestamp", "status", "error", "message", "path"]))
    expect("Unknown patient", http("GET", f"/api/v1/patients/{uuid.uuid4()}"), 404)

    source_path = ROOT / "output/pdf/MediAssist_Synthetic_RAG_Test_Document.pdf"
    pdf_bytes = source_path.read_bytes()
    main_doc = expect("PDF upload", upload(patient_id, pdf_bytes, source_path.name), 201, required=True)
    document_id = main_doc["id"]
    RESULTS["main_document_id"] = document_id
    RESULTS["main_pdf_sha256"] = hashlib.sha256(pdf_bytes).hexdigest()
    save()
    route = f"/api/v1/documents/{document_id}"
    expect("Document metadata preserves filename", http("GET", route), 200,
           lambda b: b["originalFileName"] == source_path.name and b["fileSizeBytes"] == len(pdf_bytes))
    expect("Patient document list", http("GET", f"/api/v1/patients/{patient_id}/documents"), 200,
           lambda b: any(d["id"] == document_id for d in b))
    download = http("GET", route + "/download")
    check("Download bytes and PDF content type", download["status"] == 200 and download["bytes"] == pdf_bytes
          and download["headers"].get("Content-Type", "").startswith("application/pdf"),
          "Byte-for-byte comparison with generated source PDF")
    for status in ["ARCHIVED", "UPLOADED"]:
        expect("Document status " + status, http("PATCH", route + "/status", {"status": status}), 200,
               lambda b: b["status"] == status)
    for name, data, filename, mime in [
        ("Non-PDF extension rejected", pdf_bytes, "invalid.txt", "application/pdf"),
        ("Wrong MIME rejected", pdf_bytes, "invalid.pdf", "text/plain"),
        ("Invalid PDF signature rejected", b"not a PDF", "invalid.pdf", "application/pdf"),
        ("Empty upload rejected", b"", "empty.pdf", "application/pdf"),
    ]:
        expect(name, upload(patient_id, data, filename, mime), 400)
    expect("Oversized upload rejected", upload(patient_id, b"%PDF-" + b"x" * (26 * 1024 * 1024), "large.pdf"), 413)
    sanitized = expect("Submitted filename directories stripped", upload(patient_id, pdf_bytes, "../../evaluation.pdf"),
                       201, lambda b: b["originalFileName"] == "evaluation.pdf")
    if isinstance(sanitized, dict) and sanitized.get("id"):
        RESULTS["filename_sanitization_document_id"] = sanitized["id"]

    metadata = {"documentType": "OTHER", "documentDate": None, "description": "Synthetic missing-file test",
                "originalFileName": "missing-evaluation.pdf", "storedFileName": uuid.uuid4().hex + ".pdf",
                "mimeType": "application/pdf", "fileSizeBytes": 10, "checksumSha256": "0" * 64,
                "storagePath": "evaluation-missing/" + uuid.uuid4().hex + ".pdf"}
    missing = expect("Metadata-only creation", http("POST", f"/api/v1/patients/{patient_id}/documents", metadata),
                     201, required=True)
    RESULTS["missing_file_document_id"] = missing["id"]
    expect("Duplicate storage path rejected", http("POST", f"/api/v1/patients/{patient_id}/documents", metadata), 409)
    expect("Missing stored file", http("GET", f"/api/v1/documents/{missing['id']}/download"), 404)
    traversal = dict(metadata, storagePath="../outside-evaluation-" + RUN_ID + ".pdf", storedFileName=uuid.uuid4().hex + ".pdf")
    unsafe = expect("Traversal fixture metadata", http("POST", f"/api/v1/patients/{patient_id}/documents", traversal), 201)
    if isinstance(unsafe, dict) and unsafe.get("id"):
        RESULTS["path_guard_document_id"] = unsafe["id"]
        expect("Storage path traversal blocked on read", http("GET", f"/api/v1/documents/{unsafe['id']}/download"), 400)

    main_chunks = process_document(document_id, "Main", 5, check_guards=True)
    check("Fixture text survived extraction", all(marker in RESULTS["main_extraction"]["extractedText"]
          for marker in ["lumbar strain", "carpal tunnel syndrome", "AMBER-FOX-317", "BANANA"]))
    check("Main fixture exceeds topK 10", len(main_chunks) > 10, f"{len(main_chunks)} total chunks")
    isolation_path = ROOT / "output/pdf/MediAssist_Synthetic_Isolation_Record.pdf"
    isolation_doc = expect("Isolation PDF upload", upload(patient_id, isolation_path.read_bytes(), isolation_path.name),
                           201, required=True)
    RESULTS["isolation_document_id"] = isolation_doc["id"]
    isolation_chunks = process_document(isolation_doc["id"], "Isolation", 1)
    main_ids = {c["chunkId"] for c in main_chunks}
    search = expect("Semantic search", http("POST", route + "/semantic-search",
                    {"query": "Which diabetes types are recorded?", "topK": 5}), 200, required=True)
    RESULTS["semantic_search"] = search
    matches = search["matches"]
    scores = [m["similarityScore"] for m in matches]
    check("Search ordering and document scope", len(matches) == 5 and scores == sorted(scores, reverse=True)
          and all(m["chunkId"] in main_ids and math.isfinite(m["similarityScore"]) for m in matches),
          "Five cosine similarity results, descending, selected document only")

    for case in FIXTURE["questions"]:
        for top_k in [5, 10]:
            run_question(document_id, main_chunks, case, top_k)
    run_question(isolation_doc["id"], isolation_chunks,
                 {"id": "ISO", "question": "What administrative access code belongs to SYNTHETIC-002?",
                  "expected": "SILVER-PINE-908 from the separate document.", "evidence": ["SILVER-PINE-908"]}, 5)

    for name, body in [
        ("Blank question rejected", {"question": "   ", "topK": 5}),
        ("topK zero rejected", {"question": "What is recorded?", "topK": 0}),
        ("topK above 10 rejected", {"question": "What is recorded?", "topK": 11}),
        ("Long question rejected", {"question": "x" * 1001, "topK": 5}),
    ]:
        expect(name, http("POST", route + "/questions", body), 400)
    missing_actor = http("POST", route + "/questions", {"question": "What is recorded?", "topK": 5}, actor=False)
    expect("Missing actor rejected", missing_actor, 400)
    check("Missing actor uses consistent error model", isinstance(missing_actor["body"], dict)
          and all(k in missing_actor["body"] for k in ["timestamp", "status", "error", "message", "path"]),
          str(missing_actor["body"])[:300])
    expect("Malformed document ID rejected", http("POST", "/api/v1/documents/not-a-uuid/questions",
           {"question": "What is recorded?", "topK": 5}), 400)
    expect("Unknown document rejected", http("POST", f"/api/v1/documents/{uuid.uuid4()}/questions",
           {"question": "What is recorded?", "topK": 5}), 404)

    embedding_url = "http://localhost:8001/api/v1/embeddings"
    embedding = expect("Direct embedding inference", http("POST", embedding_url,
                       {"texts": ["Fictional text one", "Fictional text two"], "model": "BAAI/bge-m3"}, actor=False),
                       200, required=True)
    vectors = embedding["embeddings"]
    norms = [math.sqrt(sum(v * v for v in vector)) for vector in vectors]
    check("Embedding dimensions and normalization", len(vectors) == 2
          and all(len(v) == 1024 and all(math.isfinite(x) for x in v) for v in vectors)
          and all(abs(n - 1) < 0.001 for n in norms), f"L2 norms: {norms}")
    for name, payload, status in [
        ("Unsupported embedding model", {"texts": ["text"], "model": "unsupported-test-model"}, 400),
        ("Empty embedding batch", {"texts": [], "model": "BAAI/bge-m3"}, 422),
        ("Blank embedding text", {"texts": ["   "], "model": "BAAI/bge-m3"}, 422),
        ("Oversized embedding batch", {"texts": ["text"] * 33, "model": "BAAI/bge-m3"}, 413),
    ]:
        expect(name, http("POST", embedding_url, payload, actor=False), status)
    RESULTS["finished_at"] = datetime.now().isoformat()
    save()
    print(f"Results: {RESULTS_PATH}", flush=True)


def recheck_remaining():
    """Correct harness negotiation and check failure persistence on test rows."""
    global RESULTS, ACTOR
    RESULTS = json.loads(RESULTS_PATH.read_text())
    ACTOR = RESULTS["actor"]
    names = ["Download bytes and PDF content type", "Oversized upload rejected", "Missing stored file",
             "Storage path traversal blocked on read"]
    RESULTS["harness_initial_attempts"] = [c for c in RESULTS["checks"] if c["name"] in names]
    RESULTS["checks"] = [c for c in RESULTS["checks"] if c["name"] not in names]
    RESULTS["harness_recheck_reason"] = (
        "Initial download requests incorrectly advertised only application/json and were rejected with 406. "
        "Corrected to Accept: */*. Initial oversized urllib request failed locally with a broken pipe; "
        "retested with curl and Expect: 100-continue. These were harness issues, not application failures."
    )
    route = f"/api/v1/documents/{RESULTS['main_document_id']}"
    download = http("GET", route + "/download")
    source = (ROOT / "output/pdf/MediAssist_Synthetic_RAG_Test_Document.pdf").read_bytes()
    check(names[0], download["status"] == 200 and download["bytes"] == source
          and download["headers"].get("Content-Type", "").startswith("application/pdf"),
          "Correct Accept header; byte-for-byte PDF and SHA-256 match")
    expect(names[1], upload(RESULTS["patient_id"], b"%PDF-" + b"x" * (26 * 1024 * 1024), "large.pdf"), 413)
    missing_route = f"/api/v1/documents/{RESULTS['missing_file_document_id']}"
    expect(names[2], http("GET", missing_route + "/download"), 404)
    expect(names[3], http("GET", f"/api/v1/documents/{RESULTS['path_guard_document_id']}/download"), 400)
    expect("Failed extraction returns processing error", http("POST", missing_route + "/extract"), 500)
    failure_state = http("GET", missing_route + "/extraction")
    expect("Failed extraction state is retained", failure_state, 200,
           lambda b: b["extractionStatus"] == "FAILED")
    RESULTS["failed_extraction_state"] = {"status": failure_state["status"], "body": failure_state["body"]}
    RESULTS["rechecked_at"] = datetime.now().isoformat()
    save()


def verify_database_and_coverage():
    """Inspect this cohort with read-only SQL and independently check retrieval."""
    global RESULTS, ACTOR
    RESULTS = json.loads(RESULTS_PATH.read_text())
    ACTOR = RESULTS["actor"]
    document_id = str(uuid.UUID(RESULTS["main_document_id"]))
    isolation_id = str(uuid.UUID(RESULTS["isolation_document_id"]))
    missing_id = str(uuid.UUID(RESULTS["missing_file_document_id"]))
    case = next(q for q in FIXTURE["questions"] if q["id"] == "Q08")
    RESULTS["independent_retrieval"] = []
    for top_k in [5, 10]:
        search = expect(f"Q08 independent retrieval topK {top_k}", http("POST",
                        f"/api/v1/documents/{document_id}/semantic-search",
                        {"query": case["question"], "topK": top_k}), 200, required=True)
        context = " ".join(" ".join(m["chunkText"] for m in search["matches"]).casefold().split())
        found = [m for m in case["evidence"] if " ".join(m.casefold().split()) in context]
        RESULTS["independent_retrieval"].append({"case_id": "Q08", "top_k": top_k, "response": search,
                                               "found": len(found), "total": len(case["evidence"]),
                                               "missing_markers": [m for m in case["evidence"] if m not in found]})
    query = f"""
        SELECT json_build_object(
            'migrations', (SELECT json_agg(row_to_json(m)) FROM
                (SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank) m),
            'pgvector_version', (SELECT extversion FROM pg_extension WHERE extname='vector'),
            'embedding_stats', (SELECT json_agg(row_to_json(s)) FROM
                (SELECT e.document_id, count(*) AS vector_count, min(ce.embedding_dimension) AS min_dimension,
                        max(vector_dims(ce.embedding)) AS max_vector_dimension,
                        min(vector_norm(ce.embedding)) AS min_norm, max(vector_norm(ce.embedding)) AS max_norm
                 FROM document_chunk_embeddings ce JOIN document_chunks c ON c.id=ce.chunk_id
                 JOIN document_extractions e ON e.id=c.document_extraction_id
                 WHERE e.document_id IN ('{document_id}','{isolation_id}') GROUP BY e.document_id) s),
            'storage', (SELECT row_to_json(d) FROM
                (SELECT checksum_sha256, stored_file_name, storage_path FROM medical_documents WHERE id='{document_id}') d),
            'duplicate_embeddings', (SELECT count(*) FROM
                (SELECT ce.chunk_id, ce.model_name FROM document_chunk_embeddings ce
                 JOIN document_chunks c ON c.id=ce.chunk_id
                 JOIN document_extractions e ON e.id=c.document_extraction_id
                 WHERE e.document_id IN ('{document_id}','{isolation_id}')
                 GROUP BY ce.chunk_id, ce.model_name HAVING count(*) > 1) duplicates),
            'audit_counts', (SELECT json_agg(row_to_json(a)) FROM
                (SELECT action, count(*) AS event_count FROM audit_events WHERE actor='{ACTOR}' GROUP BY action ORDER BY action) a),
            'failed_extraction_rows', (SELECT count(*) FROM document_extractions WHERE document_id='{missing_id}'),
            'failed_extraction_audits', (SELECT count(*) FROM audit_events
                WHERE actor='{ACTOR}' AND entity_id='{missing_id}' AND action='EXTRACTION_FAILED')
        );
    """
    response = subprocess.run(["docker", "exec", "mediassist-postgres", "psql", "-U", "mediassist", "-d",
                               "mediassist", "--tuples-only", "--no-align", "--command", query],
                              capture_output=True, text=True, check=True)
    database = json.loads(response.stdout)
    RESULTS["database_verification"] = database
    check("Flyway V1-V9 applied", [m["version"] for m in database["migrations"]] == [str(i) for i in range(1, 10)]
          and all(m["success"] for m in database["migrations"]), "All nine migration history entries successful")
    check("pgvector enabled", bool(database["pgvector_version"]), "Version " + str(database["pgvector_version"]))
    stats = database["embedding_stats"]
    check("Persisted vector count/dimensions/normalization", len(stats) == 2
          and sum(s["vector_count"] for s in stats) == len(RESULTS["main_chunks"]) + len(RESULTS["isolation_chunks"])
          and all(s["min_dimension"] == 1024 and s["max_vector_dimension"] == 1024
                  and abs(s["min_norm"] - 1) < 0.001 and abs(s["max_norm"] - 1) < 0.001 for s in stats),
          f"{sum(s['vector_count'] for s in stats)} vectors for this cohort; dimensions 1024; L2 norms within 0.001 of 1")
    check("No duplicate chunk/model embeddings", database["duplicate_embeddings"] == 0, "Synthetic cohort only")
    storage = database["storage"]
    check("Persisted SHA-256 matches source", storage["checksum_sha256"] == RESULTS["main_pdf_sha256"],
          storage["checksum_sha256"])
    check("Stored filename is UUID-based and relative", bool(re.fullmatch(r"[0-9a-f-]{36}\.pdf", storage["stored_file_name"]))
          and not Path(storage["storage_path"]).is_absolute() and ".." not in Path(storage["storage_path"]).parts,
          storage["storage_path"])
    actions = {a["action"] for a in database["audit_counts"]}
    required = {"CREATED", "UPDATED", "STATUS_CHANGED", "UPLOADED", "EXTRACTION_STARTED", "EXTRACTION_COMPLETED",
                "CHUNKING_STARTED", "CHUNKING_COMPLETED", "EMBEDDING_STARTED", "EMBEDDING_COMPLETED",
                "SEMANTIC_SEARCH_PERFORMED", "DOCUMENT_QUESTION_ASKED"}
    check("Successful pipeline audit actions persisted", required.issubset(actions),
          ", ".join(sorted(actions)))
    check("Failed extraction audit persisted", database["failed_extraction_audits"] > 0,
          f"{database['failed_extraction_audits']} failure events; {database['failed_extraction_rows']} extraction rows")
    answered_at = [q["response"]["answeredAt"] for q in RESULTS["rag_runs"] if q["status"] == 200]
    check("Answer timestamps use LocalDateTime format", all(datetime.fromisoformat(t).tzinfo is None for t in answered_at),
          "Successful answer responses contain unzoned ISO date-times")
    expect("Final Spring Boot health", http("GET", "/actuator/health", actor=False), 200, lambda b: b["status"] == "UP")
    expect("Final FastAPI health", http("GET", "http://localhost:8001/health", actor=False), 200, lambda b: b["status"] == "UP")
    RESULTS["verified_at"] = datetime.now().isoformat()
    save()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--recheck", action="store_true", help="Reuse recorded IDs; do not create another cohort")
    parser.add_argument("--database", action="store_true", help="Read-only database checks and independent retrieval")
    args = parser.parse_args()
    try:
        if args.recheck:
            recheck_remaining()
        elif args.database:
            verify_database_and_coverage()
        else:
            main()
    except Exception as error:
        RESULTS["runner_error"] = str(error)
        save()
        raise
