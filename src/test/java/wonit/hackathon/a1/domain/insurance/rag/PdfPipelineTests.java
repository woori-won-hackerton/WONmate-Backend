package wonit.hackathon.a1.domain.insurance.rag;

import java.nio.file.*;
import java.util.List;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PDF 추출 → 페이지 정보 보존 → 조항 청킹을 DB와 API 없이 검증한다.
 * 앞의 6개 테스트는 임시 PDF 또는 직접 만든 페이지 문자열을 사용한다.
 * 마지막 테스트는 로컬 product_01 PDF를 읽고 육안 대조용 PNG도 생성한다.
 * 테스트별 입력·예상 결과와 실행 방법은 docs/RAG_STAGE2_IMPLEMENTATION.md를 참고한다.
 */
class PdfPipelineTests {
    // 테스트가 생성한 PDF는 JUnit 임시 폴더에 저장되며 원본 보험 PDF를 수정하지 않는다.
    @TempDir Path temp;
    private ArticleChunker chunker() {
        // 테스트는 분할을 쉽게 발생시키기 위해 목표를 500으로 사용한다(실행 기본값은 750).
        // 실제 토크나이저가 아닌 문자 수 추정: 목표 750자, 상한 1,500자, 중복 120자.
        return new ArticleChunker(new ArticleChunker.Settings(500, 1000, 80, 1.5));
    }

    /**
     * 한글·공백이 포함된 경로에 2쪽짜리 PDF를 직접 생성한다.
     * 1쪽은 비우고 2쪽에는 "Printed page 99"와 지급 조건·금액 문자열을 넣는다.
     * 추출에서 빈 1쪽이 사라지지 않고, 청크 출처가 인쇄 문구 99가 아닌 실제 2쪽인지 확인한다.
     * PDF 내부 글자는 영문이므로, 한글 글꼴 추출 자체를 검증하는 테스트는 아니다.
     */
    @Test void extractsUnicodePathAndKeepsPhysicalPagesIncludingBlankPages() throws Exception {
        Path pdf = Files.createDirectories(temp.resolve("한글 상품")).resolve("통합 약관.pdf");
        try (var doc = new PDDocument()) {
            doc.addPage(new PDPage());
            var page = new PDPage(); doc.addPage(page);
            try (var stream = new PDPageContentStream(doc, page)) {
                stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700); stream.showText("Printed page 99: condition A 1000 KRW"); stream.endText();
            }
            doc.save(pdf.toFile());
        }
        var pages = new PdfExtractor().extract(pdf);
        assertEquals(2, pages.size()); assertTrue(pages.getFirst().text().isBlank());
        assertTrue(pages.get(1).text().contains("condition A 1000 KRW"));
        var chunks = chunker().chunk(pages);
        assertEquals(2, chunks.getFirst().startPage()); assertEquals(2, chunks.getFirst().endPage());
    }

    /**
     * PDF 추출 이후를 가정한 5·6쪽 문자열을 입력한다(PDF 표 인식 테스트가 아니다).
     * 제1조가 두 페이지에 이어지면 출처는 5~6쪽이고, 주계약과 암진단특약의 경로는 구분해야 한다.
     * "암 진단 확정    1,000만원"은 전달받은 그대로 보존하며 표의 셀 관계를 추론하지 않는다.
     */
    @Test void preservesArticlesParentsAndTableTextWithoutInferringCells() {
        String table = "지급 조건    지급 금액\n암 진단 확정    1,000만원\n";
        var chunks = chunker().chunk(List.of(
                new PdfExtractor.Page(5, "주계약\n제 1 조 (보험금 지급)\n" + table),
                new PdfExtractor.Page(6, "계속되는 내용\n제2조(면책)\n면책 내용\n암진단특약\n제1조(특약 지급)\n특약 내용\n")));
        var first = chunks.stream().filter(c -> "제 1 조 (보험금 지급)".equals(c.sectionTitle())).findFirst().orElseThrow();
        assertEquals(5, first.startPage()); assertEquals(6, first.endPage());
        assertEquals("주계약 > 제 1 조 (보험금 지급)", first.sectionPath());
        assertTrue(first.content().contains(table));
        assertTrue(chunks.stream().anyMatch(c -> "암진단특약 > 제1조(특약 지급)".equals(c.sectionPath())));
    }

    /**
     * 실제 3쪽에 있는 긴 제1조를 가정해 반복 문자열 600개로 여러 청크를 만든다.
     * 각 청크의 길이(1,500자 이하), 출처(3쪽), 제목 유지 및 0부터 연속인 인덱스를 검사한다.
     * 인접 청크의 중복을 제거해 다시 이었을 때 원문과 같아야 하므로 분할 중 누락도 탐지한다.
     */
    @Test void boundsLongArticlesAndOverlapsWithoutLosingTextOrPages() {
        String content = "제1조(긴 조항)\n" + "가나다라마바사 ".repeat(600);
        var chunks = chunker().chunk(List.of(new PdfExtractor.Page(3, content)));
        assertTrue(chunks.size() > 2);
        StringBuilder recovered = new StringBuilder(chunks.getFirst().content());
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            assertTrue(chunk.content().length() <= 1500);
            assertEquals(3, chunk.startPage()); assertEquals(3, chunk.endPage());
            assertEquals("제1조(긴 조항)", chunk.sectionTitle()); assertEquals(i, chunk.chunkIndex());
            if (i > 0) {
                String previous = chunks.get(i - 1).content();
                // strip()으로 가장자리 공백이 빠질 수 있어 정확히 120자 대신 110자 이상을 확인한다.
                // 실제 겹치는 접두사·접미사를 찾아 중복 부분만 제외하고 원문을 복원한다.
                int overlap = Math.min(120, Math.min(previous.length(), chunk.content().length()));
                while (overlap > 0 && !previous.endsWith(chunk.content().substring(0, overlap))) overlap--;
                assertTrue(overlap >= 110); recovered.append(chunk.content().substring(overlap));
            }
        }
        assertEquals(content.strip(), recovered.toString());
    }

    /** 목표와 중복이 모두 500이면 분할이 전진하지 못할 수 있으므로 설정 생성 단계에서 거부한다. */
    @Test void rejectsSettingsThatPreventProgress() {
        assertThrows(IllegalArgumentException.class, () -> new ArticleChunker.Settings(500, 1000, 500, 1.5));
    }

    /**
     * 글자 없이 이미지만 넣은 PDF를 만들어 needsOcr()가 true인지 검사한다.
     * 이미지도 없는 빈 페이지는 false여야 한다. OCR로 글자를 읽는 기능은 실행하지 않는다.
     */
    @Test void identifiesImageOnlyScannedPagesAsRequiringOcr() throws Exception {
        Path pdf = temp.resolve("스캔.pdf");
        try (var doc = new PDDocument()) {
            var page = new PDPage(); doc.addPage(page);
            var image = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc,
                    new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB));
            try (var stream = new PDPageContentStream(doc, page)) { stream.drawImage(image, 0, 0); }
            doc.save(pdf.toFile());
        }
        assertTrue(new PdfExtractor().extract(pdf).getFirst().needsOcr());
        assertFalse(new PdfExtractor.Page(1, "").needsOcr());
    }

    /**
     * "제1조 【목적】"과 "제2조 【용어】"는 새 조항으로 인식한다.
     * "제 3 조(의료기관)의 규정에 따른 병원"은 본문 인용이고,
     * "제2조 【정의】 .... 30"은 목차이므로 기존 조항 내용에 남겨야 한다.
     * 예상 청크 3개는 주계약 제목 구간 1개와 실제 조항 2개다.
     */
    @Test void recognizesBracketedHeadingsButNotCrossReferencesOrTableOfContents() {
        var chunks = chunker().chunk(List.of(new PdfExtractor.Page(1,
                "주계약\n제1조 【목적】\n본문\n제 3 조(의료기관)의 규정에 따른 병원\n제2조 【정의】 .... 30\n제2조 【용어】\n정의 본문\n")));
        assertEquals(3, chunks.size());
        assertEquals("제1조 【목적】", chunks.get(1).sectionTitle());
        assertTrue(chunks.get(1).content().contains("의료기관"));
        assertTrue(chunks.get(1).content().contains(".... 30"));
        assertEquals("제2조 【용어】", chunks.get(2).sectionTitle());
    }

    /**
     * 로컬 상품 1의 실제 PDF를 다시 추출·청킹한다(기존 출력 JSON을 읽는 테스트는 아니다).
     * 약관 320쪽·요약서 14쪽, 모든 청크의 페이지 범위·길이와 아래 대표 내용을 검사한다.
     * - 실제 27쪽: 주계약 > 제1조 【목적】
     * - 실제 104쪽: 무배당동양연금전환특약 > 제1조 【목적】
     * - 요약서 실제 2쪽: 환급률 50%·95% 문자열
     * review/ PNG는 사람이 원본 표와 추출 텍스트를 대조하기 위한 자료다.
     * 이미지 생성만 자동화하며 표 조건·금액의 의미 관계나 이미지 내용은 자동 판정하지 않는다.
     */
    @Test void validatesRealSampleArtifactsAndRendersPagesForTableReview() throws Exception {
        Path dir = Path.of("documents/product_01");
        // 원본 PDF는 Git 배포 대상이 아니다. 폴더가 없는 환경에서는 이 테스트만 건너뛴다.
        assumeTrue(Files.isDirectory(dir), "Local insurance PDFs are not distributed with source");
        Path rendered = Files.createDirectories(Path.of("build/rag-debug/review"));
        try (var files = Files.list(dir)) {
            for (Path pdf : files.filter(p -> p.toString().endsWith(".pdf")).toList()) {
                var pages = new PdfExtractor().extract(pdf);
                var chunks = chunker().chunk(pages);
                assertFalse(chunks.isEmpty());
                for (var chunk : chunks) {
                    assertTrue(chunk.startPage() >= 1 && chunk.endPage() <= pages.size());
                    assertTrue(chunk.startPage() <= chunk.endPage());
                    assertTrue(chunk.content().length() <= 1500);
                }
                boolean terms = pdf.getFileName().toString().startsWith("통합");
                if (terms) {
                    assertEquals(320, pages.size());
                    assertTrue(chunks.stream().anyMatch(c -> c.startPage() == 27 && "주계약 > 제1조 【목적】".equals(c.sectionPath())));
                    assertTrue(chunks.stream().anyMatch(c -> c.startPage() == 104 && "무배당동양연금전환특약 > 제1조 【목적】".equals(c.sectionPath())));
                } else {
                    assertEquals(14, pages.size());
                    assertTrue(pages.get(1).text().contains("표준형 해약환급금의 50%"));
                    assertTrue(pages.get(1).text().contains("표준형 해약환급금의 95%"));
                }
                try (var document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
                    var renderer = new org.apache.pdfbox.rendering.PDFRenderer(document);
                    // 약관의 주계약·특약 시작 및 이미지 설명도, 요약서의 환급률 표를 렌더링한다.
                    for (int page : terms ? new int[]{27, 104, 319} : new int[]{2}) {
                        javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page - 1, 90), "png",
                                rendered.resolve((terms ? "terms-" : "summary-") + page + ".png").toFile());
                    }
                }
            }
        }
    }
}
