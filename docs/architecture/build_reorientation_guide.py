"""Render the editable MediAssist guide to a paginated PDF using ReportLab."""

from html import escape
from pathlib import Path
import re

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.platypus import (
    Flowable,
    KeepTogether,
    PageBreak,
    Paragraph,
    Preformatted,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "MediAssist_Returning_Developer_Guide.md"
OUTPUT = ROOT / "MediAssist_Returning_Developer_Guide.pdf"
NAVY = colors.HexColor("#132D3B")
TEAL = colors.HexColor("#176D70")
INK = colors.HexColor("#293B45")
MUTED = colors.HexColor("#647780")
PALE = colors.HexColor("#F0F5F5")
RULE = colors.HexColor("#DCE5E7")
PAGE_W, PAGE_H = A4
WIDTH = PAGE_W - 84


def inline(text: str) -> str:
    text = escape(text)
    text = re.sub(r"`([^`]+)`", r'<font name="Courier" size="8.25">\1</font>', text)
    return re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)


styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    "GuideBody", fontName="Helvetica", fontSize=9.35, leading=13.1,
    textColor=INK, spaceAfter=9, splitLongWords=True,
))
styles.add(ParagraphStyle(
    "GuideTitle", fontName="Helvetica-Bold", fontSize=23, leading=28,
    textColor=NAVY, spaceAfter=15, keepWithNext=True,
))
styles.add(ParagraphStyle(
    "CoverTitle", fontName="Helvetica-Bold", fontSize=46, leading=50,
    textColor=NAVY, spaceAfter=8, keepWithNext=True,
))
styles.add(ParagraphStyle(
    "GuideSubtitle", fontName="Helvetica", fontSize=23, leading=29,
    textColor=TEAL, spaceAfter=17, keepWithNext=True,
))
styles.add(ParagraphStyle(
    "GuideSection", fontName="Helvetica-Bold", fontSize=12, leading=16,
    textColor=TEAL, spaceBefore=5, spaceAfter=7, keepWithNext=True,
))
styles.add(ParagraphStyle(
    "GuideCell", fontName="Helvetica", fontSize=8.6, leading=11.5,
    textColor=INK, spaceAfter=0,
))
styles.add(ParagraphStyle(
    "GuideHeaderCell", parent=styles["GuideCell"], fontName="Helvetica-Bold",
    textColor=colors.white,
))
styles.add(ParagraphStyle(
    "GuideCode", fontName="Courier", fontSize=7.65, leading=10.35,
    textColor=NAVY, spaceAfter=0,
))
styles.add(ParagraphStyle(
    "GuideList", parent=styles["GuideBody"], leftIndent=16, firstLineIndent=-13,
    spaceAfter=5,
))


class FlowDiagram(Flowable):
    def __init__(self, kind: str):
        super().__init__()
        self.kind = kind
        self.width = WIDTH
        self.height = 130 if kind == "pipeline" else 91

    def draw(self):
        canvas = self.canv
        if self.kind == "pipeline":
            gap = 12
            box_w = (WIDTH - 3 * gap) / 4
            nodes = [
                (0, 79, "PDF", "Upload / storage"),
                (1, 79, "TEXT", "PDFBox extraction"),
                (2, 79, "CHUNKS", "Ordered + overlap"),
                (3, 79, "EMBEDDINGS", "BGE-M3 / 1024"),
                (3, 22, "PGVECTOR", "Stored vectors"),
                (2, 22, "SEARCH", "Ranked context"),
                (1, 22, "RAG ANSWER", "External LLM needed"),
            ]
            for col, y, title, subtitle in nodes:
                self.box(col * (box_w + gap), y, box_w, 41, title, subtitle)
            for col in range(3):
                self.arrow((col + 1) * box_w + col * gap + 2, 99,
                           (col + 1) * (box_w + gap) - 2, 99)
            self.arrow(3 * (box_w + gap) + box_w / 2, 77,
                       3 * (box_w + gap) + box_w / 2, 65)
            for col in (3, 2):
                self.arrow(col * (box_w + gap) - 2, 42,
                           col * (box_w + gap) - gap + 2, 42)
            canvas.setFillColor(MUTED)
            canvas.setFont("Helvetica", 7.5)
            canvas.drawString(0, 5, "Ingestion builds the stored context; a question triggers retrieval and answer generation.")
        else:
            gap = 10
            box_w = (WIDTH - gap * 3) / 4
            nodes = [
                ("QUESTION", "Validated DTO"),
                ("RETRIEVAL", "Java + pgvector"),
                ("LLM CALL", "Prompt + chunks"),
                ("RESPONSE", "Answer + sources"),
            ]
            for col, (title, subtitle) in enumerate(nodes):
                self.box(col * (box_w + gap), 29, box_w, 42, title, subtitle)
                if col < 3:
                    self.arrow((col + 1) * box_w + col * gap + 1, 50,
                               (col + 1) * (box_w + gap) - 1, 50)
            canvas.setFont("Helvetica", 7.5)
            canvas.setFillColor(MUTED)
            canvas.drawString(0, 10, "The QA service reuses searchSimilarChunks() directly inside Spring Boot.")

    def box(self, x, y, w, h, title, subtitle):
        canvas = self.canv
        canvas.setFillColor(PALE)
        canvas.setStrokeColor(RULE)
        canvas.roundRect(x, y, w, h, 5, fill=1, stroke=1)
        canvas.setFillColor(TEAL)
        canvas.setFont("Helvetica-Bold", 8.3)
        canvas.drawCentredString(x + w / 2, y + 24, title)
        canvas.setFillColor(INK)
        canvas.setFont("Helvetica", 7.0)
        canvas.drawCentredString(x + w / 2, y + 11, subtitle)

    def arrow(self, x1, y1, x2, y2):
        canvas = self.canv
        canvas.setStrokeColor(TEAL)
        canvas.setLineWidth(1)
        canvas.line(x1, y1, x2, y2)
        if y1 == y2:
            d = 1 if x2 > x1 else -1
            canvas.line(x2, y2, x2 - d * 3, y2 + 2)
            canvas.line(x2, y2, x2 - d * 3, y2 - 2)
        else:
            canvas.line(x2, y2, x2 - 2, y2 + 3)
            canvas.line(x2, y2, x2 + 2, y2 + 3)


def code_block(lines):
    longest = max((len(line) for line in lines), default=0)
    if longest * 4.59 > WIDTH - 20:
        raise ValueError(f"Code line exceeds PDF width ({longest} characters)")
    pre = Preformatted("\n".join(lines), styles["GuideCode"])
    table = Table([[pre]], colWidths=[WIDTH], hAlign="LEFT")
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), PALE),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 9),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 9),
        ("LINEBEFORE", (0, 0), (0, -1), 2, TEAL),
    ]))
    return KeepTogether([table, Spacer(1, 10)])


def table_block(lines):
    rows = [[cell.strip() for cell in line.strip().strip("|").split("|")] for line in lines]
    rows = [row for row in rows if not all(re.fullmatch(r"[:\- ]+", cell) for cell in row)]
    cols = len(rows[0])
    if cols == 2:
        if "Environment Variable" in rows[0][0]:
            weights = [0.44, 0.56]
        elif "File In" in rows[0][0]:
            weights = [0.40, 0.60]
        elif rows[0][0] in ("Pages", "Version"):
            weights = [0.21, 0.79]
        else:
            weights = [0.34, 0.66]
    elif rows[0][0] == "Method":
        weights = [0.10, 0.58, 0.32]
    else:
        weights = [0.24, 0.46, 0.30]
    cells = [[Paragraph(inline(cell), styles["GuideHeaderCell" if i == 0 else "GuideCell"])
              for cell in row] for i, row in enumerate(rows)]
    table = Table(cells, colWidths=[WIDTH * w for w in weights], repeatRows=1, hAlign="LEFT")
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), NAVY),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, PALE]),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ("LINEBELOW", (0, -1), (-1, -1), 0.5, RULE),
    ]))
    return KeepTogether([table, Spacer(1, 11)])


def parse_section(section):
    story = []
    lines = section.strip().splitlines()
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if not line:
            i += 1
            continue
        if line.startswith("```"):
            block = []
            i += 1
            while i < len(lines) and not lines[i].strip().startswith("```"):
                block.append(lines[i])
                i += 1
            story.append(code_block(block))
        elif line.startswith("|"):
            block = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                block.append(lines[i])
                i += 1
            story.append(table_block(block))
            continue
        elif line.startswith("[["):
            story.append(FlowDiagram(line.strip("[]")))
            story.append(Spacer(1, 7))
        elif line.startswith("# "):
            content = line[2:]
            style = "CoverTitle" if content == "MediAssist" else "GuideTitle"
            story.append(Paragraph(inline(content), styles[style]))
        elif line.startswith("## "):
            story.append(Paragraph(inline(line[3:]), styles["GuideSubtitle"]))
        elif line.startswith("### "):
            story.append(Paragraph(inline(line[4:]), styles["GuideSection"]))
        elif re.match(r"\d+\. ", line):
            story.append(Paragraph(inline(line), styles["GuideList"]))
        else:
            paragraph = [line]
            i += 1
            while i < len(lines) and lines[i].strip():
                paragraph.append(lines[i].strip())
                i += 1
            story.append(Paragraph(inline(" ".join(paragraph)), styles["GuideBody"]))
            continue
        i += 1
    return story


def decorate(canvas, doc):
    canvas.saveState()
    canvas.setTitle("MediAssist - Returning Developer Guide")
    canvas.setAuthor("MediAssist project documentation")
    canvas.setSubject("Repository architecture, code walkthrough, startup runbook, and RAG checkpoint")
    canvas.setFillColor(TEAL)
    canvas.rect(0, PAGE_H - 7, PAGE_W, 7, stroke=0, fill=1)
    canvas.setFont("Helvetica-Bold", 8)
    canvas.drawString(42, PAGE_H - 29, "MEDIASSIST / DEVELOPER FIELD GUIDE")
    canvas.setFillColor(MUTED)
    canvas.setFont("Helvetica", 8)
    canvas.drawRightString(PAGE_W - 42, PAGE_H - 29, "01 OCTOBER 2026")
    canvas.setStrokeColor(RULE)
    canvas.setLineWidth(0.6)
    canvas.line(42, 39, PAGE_W - 42, 39)
    canvas.setFont("Helvetica", 7.5)
    canvas.drawString(42, 25, "Code snapshot / startup guide / current checkpoint")
    canvas.drawRightString(PAGE_W - 42, 25, f"{doc.page:02d}")
    canvas.restoreState()


class GuideDocument(SimpleDocTemplate):
    def afterFlowable(self, flowable):
        if isinstance(flowable, Paragraph) and flowable.style.name in ("GuideTitle", "CoverTitle"):
            key = f"page-{self.page}"
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(flowable.getPlainText(), key, 0, False)


def main():
    sections = SOURCE.read_text(encoding="utf-8").split("<!-- page -->")
    story = []
    for i, section in enumerate(sections):
        if i:
            story.append(PageBreak())
        story.extend(parse_section(section))
    doc = GuideDocument(
        str(OUTPUT), pagesize=A4, rightMargin=42, leftMargin=42,
        topMargin=51, bottomMargin=51,
    )
    doc.build(story, onFirstPage=decorate, onLaterPages=decorate)
    print(f"Created {OUTPUT}")


if __name__ == "__main__":
    main()
