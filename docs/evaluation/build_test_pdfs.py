"""Create fictional source PDFs and an evaluation guide without changing backend code."""

import argparse
import json
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "output" / "pdf"
FIXTURE = json.loads((Path(__file__).parent / "synthetic_records.json").read_text())
INK = colors.HexColor("#173844")
TEAL = colors.HexColor("#137E82")
MUTED = colors.HexColor("#52656D")
STYLES = getSampleStyleSheet()
STYLES.add(ParagraphStyle("TitleCustom", fontName="Helvetica-Bold", fontSize=25, leading=29, textColor=INK, spaceAfter=12))
STYLES.add(ParagraphStyle("SectionCustom", fontName="Helvetica-Bold", fontSize=14, leading=18, textColor=TEAL, spaceAfter=10))
STYLES.add(ParagraphStyle("BodyCustom", fontName="Helvetica", fontSize=10.3, leading=15, textColor=INK, spaceAfter=10))
STYLES.add(ParagraphStyle("SmallCustom", fontName="Helvetica", fontSize=8.3, leading=11, textColor=MUTED, spaceAfter=6))
STYLES.add(ParagraphStyle("CellCustom", fontName="Helvetica", fontSize=8.1, leading=11, textColor=INK, alignment=TA_LEFT))


def p(text, style="BodyCustom"):
    return Paragraph(escape(str(text)).replace("\n", "<br/>"), STYLES[style])


def footer(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(TEAL)
    canvas.line(20 * mm, 18 * mm, 190 * mm, 18 * mm)
    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(MUTED)
    canvas.drawString(20 * mm, 12 * mm, "MediAssist | Fictional evaluation data | 3 October 2026")
    canvas.drawRightString(190 * mm, 12 * mm, str(doc.page))
    canvas.restoreState()


def write_pdf(filename, story, title):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(str(OUTPUT / filename), pagesize=A4, rightMargin=20 * mm,
                            leftMargin=20 * mm, topMargin=18 * mm, bottomMargin=25 * mm,
                            title=title, author="MediAssist development evaluation")
    doc.build(story, onFirstPage=footer, onLaterPages=footer)
    print(f"Created {OUTPUT / filename}", flush=True)


def source_pdfs():
    story = []
    for index, page in enumerate(FIXTURE["pages"]):
        if index:
            story.append(PageBreak())
        story.extend([p(FIXTURE["main_title"], "TitleCustom"), p(FIXTURE["main_subtitle"], "SmallCustom"),
                      Spacer(1, 5 * mm), p(page["title"], "SectionCustom")])
        story.extend(p(text) for text in page["paragraphs"])
    write_pdf("MediAssist_Synthetic_RAG_Test_Document.pdf", story, FIXTURE["main_title"])
    story = [p(FIXTURE["isolation_title"], "TitleCustom"),
             p("Fictional patient SYNTHETIC-002 | Document isolation fixture", "SmallCustom"), Spacer(1, 8 * mm)]
    story.extend(p(text) for text in FIXTURE["isolation_paragraphs"])
    write_pdf("MediAssist_Synthetic_Isolation_Record.pdf", story, FIXTURE["isolation_title"])


def results_table(rows):
    data = [[p(cell, "CellCustom") for cell in row] for row in rows]
    table = Table(data, colWidths=[17 * mm, 24 * mm, 129 * mm], repeatRows=1, hAlign="LEFT")
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#DCEFF0")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6), ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 6), ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("LINEBELOW", (0, 0), (-1, -1), 0.3, colors.HexColor("#D5E0E3"))
    ]))
    return table


def guide(results_path=None):
    results = json.loads(Path(results_path).read_text()) if results_path else {}
    review_path = Path(__file__).parent / "quality_review.json"
    if results and review_path.exists():
        results["quality_review"] = json.loads(review_path.read_text())
    story = [p("RAG Reliability\nTest Guide & Results", "TitleCustom"),
             p("A practical baseline for the current MediAssist implementation", "SectionCustom"),
             p("Use only the two synthetic record PDFs as upload fixtures. Do not upload this guide: it contains the answer key and would contaminate retrieval."),
             p("Scope: patient APIs, document upload/download, PDF extraction, ordered chunks, normalized 1024-dimensional embeddings, pgvector retrieval, RAG answers, validation, idempotency and audit persistence."),
             p("No backend code, prompts, model settings, database schema or existing patient documents were changed during this baseline run. Test records are deliberately retained with a synthetic actor label."),
             p("This is development testing, not proof of clinical safety, production readiness or comprehensive security. Source references identify supplied context; they do not automatically validate each claim."),
             p("Current architecture", "SectionCustom"),
             p("Spring Boot orchestrates the workflow. FastAPI/BGE-M3 produces embeddings. PostgreSQL/pgvector stores and searches vectors. Local Ollama/Qwen generates answer text. There is no automatic processing pipeline or conversation history.")]
    if results:
        passed = sum(item["passed"] for item in results.get("checks", []))
        success_answers = sum(item["status"] == 200 for item in results.get("rag_runs", []))
        story.extend([p("Baseline verdict", "SectionCustom"),
                      p(f"Core pipeline works, but the baseline is not fully green: {passed}/{len(results.get('checks', []))} technical assertions passed; {success_answers}/{len(results.get('rag_runs', []))} question requests returned an answer. All 16 existing Maven tests passed. These counts are not answer-accuracy scores."),
                      p("Fix failure-state persistence, blank-text validation and broad-answer grounding before expanding the feature set. The detailed findings below explain the evidence.")])
    story.extend([PageBreak(), p("01 / Run the baseline yourself", "TitleCustom")])
    steps = [
        "1. Keep PostgreSQL on 5432, Spring Boot on 8080, FastAPI on 8001 and Ollama on 11434 running. Verify health, then make a model request; health alone does not prove inference works.",
        "2. Create a fictional patient using Swagger. Use an MRN beginning EVAL-RAG and X-Actor: rag-evaluation. Never use a real patient for this fixture.",
        "3. Upload MediAssist_Synthetic_RAG_Test_Document.pdf using the multipart /patients/{patientId}/documents/upload route and documentType OTHER. Save the document ID.",
        "4. Call POST /documents/{documentId}/extract, then /chunk, then /embeddings. These routes are under /api/v1 and require X-Actor. Check GET extraction, text, chunks and embedding metadata.",
        "5. Upload and process the isolation record under the same fictional patient. It has a different document ID. Queries must remain restricted to the selected document.",
        "6. Call POST /api/v1/documents/{documentId}/questions with question and topK. Run each question with topK 5, then 10. Inspect full returned-source text via GET /chunks.",
        "7. Capture answers, sources, modelName, latency and the exact configuration. Repeat selected cases. Do not change the model or prompt during a baseline comparison.",
        "8. Check missing information, invalid input and duplicate processing. Review every claim against its cited text before marking an answer correct."
    ]
    story.extend(p(step) for step in steps)
    story.extend([p('Example body: {"question":"Was carpal tunnel syndrome confirmed or only investigated?","topK":5}', "SmallCustom"),
                  p("Swagger: http://localhost:8080/swagger-ui/index.html", "SmallCustom"),
                  PageBreak(), p("Local startup reference", "TitleCustom"),
                  p("Run these in separate terminals only when the corresponding service is stopped. The services were already running for this evaluation. Do not start another process on an occupied port."),
                  p("PostgreSQL / pgvector", "SectionCustom"),
                  p('From the project root:\ndocker compose -f infra/compose/docker-compose.postgres.yml up -d', "SmallCustom"),
                  p("Embedding service / port 8001", "SectionCustom"),
                  p('cd services/embedding-service\nsource .venv/bin/activate\nuvicorn app.main:app --host 127.0.0.1 --port 8001', "SmallCustom"),
                  p("Ollama / port 11434", "SectionCustom"),
                  p('Open the installed Ollama application, or use the CLI:\nOLLAMA_NO_CLOUD=1 ollama serve\nIn another terminal: ollama list\nIf the model is absent: ollama pull qwen3.5:4b', "SmallCustom"),
                  p("Spring Boot / port 8080", "SectionCustom"),
                  p('cd backend\nmvn spring-boot:run\nBuild/test separately: mvn clean test', "SmallCustom"),
                  p("Health and shutdown", "SectionCustom"),
                  p('curl http://localhost:8080/actuator/health\ncurl http://localhost:8001/health\ncurl http://localhost:11434/api/tags\nStop a foreground server with Ctrl+C. Stop PostgreSQL with:\ndocker compose -f infra/compose/docker-compose.postgres.yml stop', "SmallCustom"),
                  p("Do not use docker compose down -v: it deletes the database volume. The older returning-developer PDF predates native Ollama support; port 8002 is not needed with the current provider.", "SmallCustom"),
                  PageBreak(),
                  p("02 / Questions and answer key", "TitleCustom")])
    for item in FIXTURE["questions"][:5]:
        story.extend([p(f'{item["id"]} / {item["question"]}', "SectionCustom"), p(item["expected"])])
    story.extend([PageBreak(), p("03 / Coverage and adversarial checks", "TitleCustom")])
    for item in FIXTURE["questions"][5:]:
        story.extend([p(f'{item["id"]} / {item["question"]}', "SectionCustom"), p(item["expected"])])
    story.extend([p("Citation checks", "SectionCustom"),
                  p("[Chunk 1] refers to the first source in that response, not PDF page 1 or original chunkIndex 1. Verify citation numbers are in range and read the full cited chunk. A similarity score is not an answer-confidence percentage."),
                  PageBreak(), p("04 / Observed technical results", "TitleCustom")])
    if results:
        checks = results.get("checks", [])
        passed = sum(item["passed"] for item in checks)
        story.extend([p(f"Run: {results.get('run_id')} | Technical checks: {passed}/{len(checks)} passed."),
                      p(f"Test actor: {results.get('actor')}", "SmallCustom"),
                      p(f"Synthetic patient ID: {results.get('patient_id', 'not created')}", "SmallCustom"),
                      p(f"Main document ID: {results.get('main_document_id', 'not uploaded')}", "SmallCustom"),
                      p(f"Isolation document ID: {results.get('isolation_document_id', 'not uploaded')}", "SmallCustom")])
        rows = [["ID", "Outcome", "Check"]]
        rows.extend([str(index + 1), "PASS" if item["passed"] else "FAIL", item["name"] + ": " + item.get("detail", "")]
                    for index, item in enumerate(checks))
        story.append(results_table(rows))
        story.append(p(results.get("harness_recheck_reason", ""), "SmallCustom"))
    else:
        story.append(p("Awaiting local baseline execution. Results will be populated after testing."))
    story.extend([PageBreak(), p("05 / Findings & Priorities", "TitleCustom")])
    for index, item in enumerate(results.get("quality_review", [])):
        if index in [3, 6]:
            story.extend([PageBreak(), p("Findings / Continued", "TitleCustom")])
        story.extend([p(item["title"], "SectionCustom"), p(item["finding"])])
    if not results.get("quality_review"):
        story.append(p("Manual claim-level review is pending. An HTTP 200 and valid JSON do not establish factual accuracy."))
    rag = results.get("rag_runs", [])
    if rag:
        story.extend([PageBreak(), p("Question-run comparison", "TitleCustom")])
        rows = [["Case", "Top-K", "Retrieval evidence coverage / answer transport"]]
        for item in rag:
            coverage = item.get("evidence_coverage", {})
            if item["status"] != 200:
                counts = "No returned context; answer-generation failure"
            else:
                counts = f"{coverage.get('found', 0)}/{coverage.get('total', 0)} expected text markers" if coverage.get("total") else "Abstention/isolation case"
            rows.append([item["case_id"], str(item["top_k"]), f"{counts}; HTTP {item['status']}; {item['seconds']:.1f}s"])
        story.append(results_table(rows))
        for item in results.get("independent_retrieval", []):
            story.append(p(f"Independent Q08 semantic search, topK {item['top_k']}: {item['found']}/{item['total']} evidence markers. Missing: {', '.join(item['missing_markers']) or 'none'}.", "SmallCustom"))
        story.append(p("Marker coverage only checks whether selected passages contain known phrases. It is not a factuality score or an independent clinical evaluation.", "SmallCustom"))
    story.extend([PageBreak(), p("06 / What to improve next", "TitleCustom")])
    followups = [
        "Stabilize the proven failure paths first: retain FAILED extraction/audits, return JSON-safe FastAPI validation errors, and normalize Spring framework error responses. Add targeted regression tests for each.",
        "Extract prompt construction into a small DocumentQaPromptBuilder in the application package. Require concise answers with separate diagnoses, investigations, symptoms and attributed opinions; preserve conflicting dated statements and uncertainty. Document text is evidence, not instructions.",
        "Measure broad-question retrieval coverage and output budgets. TopK 10 recovered missing evidence here but produced a longer answer that exceeded the output limit. Do not assume more chunks always give a better answer or introduce an arbitrary similarity cutoff.",
        "Add prompt-builder and application-service regression tests, then repeat the unchanged synthetic questions against the actual model. Unit tests prove software behavior, not clinical factuality. No frontend, OCR or knowledge graph is needed to fix this baseline.",
        "Before real patient use, add authentication, document-level authorization, trusted audit identities, privacy/retention controls and appropriate operational safeguards. Local execution is not automatically healthcare-compliant."
    ]
    story.extend(p(f"{index + 1}. {text}") for index, text in enumerate(followups))
    story.extend([p("Reproduction files", "SectionCustom"),
                  p("docs/evaluation/synthetic_records.json contains fixture text and expected evidence. run_local_evaluation.py runs local HTTP checks and saves responses to rag_evaluation_results.json. quality_review.json records manual findings. build_test_pdfs.py recreates the PDFs.", "SmallCustom"),
                  p("From the project root: python3 docs/evaluation/run_local_evaluation.py. Then run it with --recheck and --database. A normal run creates a new fictional cohort; these two flags reuse the saved IDs. Preserve an earlier results JSON under another name before a new run overwrites it. --database also performs two read-only semantic searches, which write their normal audit events.", "SmallCustom"),
                  p("Build the guide with: python3 docs/evaluation/build_test_pdfs.py --guide-only --results docs/evaluation/rag_evaluation_results.json. The PDF builder requires ReportLab; the API runner uses the Python standard library, curl and Docker CLI.", "SmallCustom"),
                  p("Do not stop shared services, alter existing documents, delete volumes or change the model merely to make these tests pass. The synthetic test cohort remains in PostgreSQL/local storage and can be identified by the recorded IDs and actor.", "SmallCustom")])
    write_pdf("MediAssist_RAG_Test_Checklist_and_Results.pdf", story, "MediAssist RAG baseline evaluation")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--results")
    parser.add_argument("--guide-only", action="store_true")
    args = parser.parse_args()
    if not args.guide_only:
        source_pdfs()
    guide(args.results)
