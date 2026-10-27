package wonit.hackathon.a1.domain.rag.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 약관 텍스트 → 청크
 * - 조(제n조(…)) · 특약/주계약 · 별표 제목에서 끊고, 너무 길면 목표 길이로 나눔 (앞 청크와 일부 겹침)
 * - 추출된 텍스트 순서를 그대로 유지하며 표의 셀 관계를 추측하지 않음
 */
public class ArticleChunker {

    private static final Pattern ARTICLE = Pattern.compile(
            "^\\s*제\\s*\\d+\\s*조(?:의\\s*\\d+)?\\s*[（(【\\[][^）)】\\]]+[）)】\\]]\\s*$");
    private static final Pattern CONTRACT = Pattern.compile(
            "^\\s*(?:(?:\\d+[.)]\\s*)?(?:무배당\\s*)?.{0,100}(?:특약|주계약)(?:\\s*약관)?|보통보험약관)\\s*$");
    // 별표/별첨은 앞 조항과 다른 구간
    private static final Pattern APPENDIX = Pattern.compile(
            "^[（(【\\[]\\s*별\\s*[표첨]\\s*\\d+\\s*[）)】\\]](?:[ \\t]+[^.·…\\d].{0,80})?$");

    /** 토큰 수는 '글자 수 ÷ chars-per-token'으로 추정 */
    public record Settings(int targetTokens, int maxTokens, int overlapTokens, double charsPerToken) {
        public Settings {
            if (targetTokens < 1 || maxTokens < targetTokens || overlapTokens < 0
                    || overlapTokens >= targetTokens || !Double.isFinite(charsPerToken) || charsPerToken < 1) {
                throw new IllegalArgumentException("Invalid chunk settings");
            }
        }
    }

    public record Chunk(int chunkIndex, String sectionTitle, String sectionPath,
            int startPage, int endPage, String content) {
    }

    private final Settings settings;

    public ArticleChunker(Settings settings) {
        this.settings = settings;
    }

    public List<Chunk> chunk(List<PdfExtractor.Page> pages) {
        List<Chunk> chunks = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        List<Integer> sourcePages = new ArrayList<>();
        String title = null;
        String contract = null;
        boolean appendixTitlePending = false;

        for (int p = 0; p < pages.size(); p++) {
            PdfExtractor.Page page = pages.get(p);
            String[] lines = page.text().split("(?<=\n)");
            for (int l = 0; l < lines.length; l++) {
                String line = lines[l];
                String heading = line.strip();
                boolean article = ARTICLE.matcher(heading).matches();
                boolean parent = CONTRACT.matcher(heading).matches();
                boolean appendix = APPENDIX.matcher(heading).matches();

                if (article || parent || appendix) {
                    emit(chunks, text, sourcePages, title, contract);
                    if (appendix) {
                        // 페이지 머리글로 이전 특약 경로가 남을 수 있어 부모를 이어받지 않음
                        contract = null;
                        title = heading;
                    } else if (parent) {
                        contract = heading;
                        title = null;
                    } else {
                        title = heading;
                    }
                    appendixTitlePending = appendix && heading.matches(".*[）)】\\]]$");
                } else if (appendixTitlePending && !heading.isBlank()) {
                    if (heading.length() <= 80) {
                        title += " " + heading;
                    }
                    appendixTitlePending = false;
                }

                text.append(line);
                for (int i = 0; i < line.length(); i++) {
                    sourcePages.add(page.number());
                }
            }
            // 줄바꿈 없이 끝나는 페이지도 구분
            text.append('\n');
            sourcePages.add(page.number());
        }
        emit(chunks, text, sourcePages, title, contract);
        return List.copyOf(chunks);
    }

    private void emit(List<Chunk> chunks, StringBuilder text, List<Integer> pages, String title, String parent) {
        int max = (int) (settings.maxTokens() * settings.charsPerToken());
        int target = (int) (settings.targetTokens() * settings.charsPerToken());
        int overlap = (int) (settings.overlapTokens() * settings.charsPerToken());
        int start = 0;

        while (start < text.length()) {
            int end = text.length();
            if (end - start > max) {
                end = start + target;
                int newline = text.indexOf("\n", end);
                if (newline >= 0 && newline + 1 <= start + max) {
                    end = newline + 1;
                }
                if (end < text.length() && Character.isLowSurrogate(text.charAt(end))) {
                    end--;
                }
            }

            int first = start;
            int last = end;
            while (first < last && Character.isWhitespace(text.charAt(first))) {
                first++;
            }
            while (last > first && Character.isWhitespace(text.charAt(last - 1))) {
                last--;
            }
            if (first < last) {
                String path = parent == null ? title : title == null ? parent : parent + " > " + title;
                chunks.add(new Chunk(chunks.size(), title, path,
                        pages.get(first), pages.get(last - 1), text.substring(first, last)));
            }

            if (end == text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
            if (Character.isLowSurrogate(text.charAt(start))) {
                start++;
            }
        }
        text.setLength(0);
        pages.clear();
    }
}