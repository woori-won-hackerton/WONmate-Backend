package wonit.hackathon.a1.domain.insurance.rag;

import java.util.*;
import java.util.regex.Pattern;

/** Keeps extracted text order. Never infers table cells or payment relationships. */
public class ArticleChunker {
    private static final Pattern ARTICLE = Pattern.compile("^\\s*제\\s*\\d+\\s*조(?:의\\s*\\d+)?\\s*[（(【\\[][^）)】\\]]+[）)】\\]]\\s*$");
    private static final Pattern CONTRACT = Pattern.compile("^\\s*(?:(?:\\d+[.)]\\s*)?(?:무배당\\s*)?.{0,100}(?:특약|주계약)(?:\\s*약관)?|보통보험약관)\\s*$");
    public record Settings(int targetTokens, int maxTokens, int overlapTokens, double charsPerToken) {
        public Settings {
            if (targetTokens < 1 || maxTokens < targetTokens || overlapTokens < 0
                    || overlapTokens >= targetTokens || !Double.isFinite(charsPerToken) || charsPerToken < 1) {
                throw new IllegalArgumentException("Invalid chunk settings");
            }
        }
    }
    public record Chunk(int chunkIndex, String sectionTitle, String sectionPath,
                        int startPage, int endPage, String content) {}
    private final Settings settings;
    public ArticleChunker(Settings settings) { this.settings = settings; }

    public List<Chunk> chunk(List<PdfExtractor.Page> pages) {
        var chunks = new ArrayList<Chunk>();
        var text = new StringBuilder();
        var sourcePages = new ArrayList<Integer>();
        String title = null, contract = null;
        for (var page : pages) {
            for (String line : page.text().split("(?<=\n)")) {
                String heading = line.strip();
                boolean article = ARTICLE.matcher(heading).matches();
                boolean parent = CONTRACT.matcher(heading).matches();
                if (article || parent) {
                    emit(chunks, text, sourcePages, title, contract);
                    if (parent) { contract = heading; title = null; }
                    else { title = heading; }
                }
                text.append(line);
                for (int i = 0; i < line.length(); i++) sourcePages.add(page.number());
            }
            // Separate pages even when a PDF ends a page without a newline.
            text.append('\n'); sourcePages.add(page.number());
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
                if (newline >= 0 && newline + 1 <= start + max) end = newline + 1;
                if (end < text.length() && Character.isLowSurrogate(text.charAt(end))) end--;
            }
            int first = start, last = end;
            while (first < last && Character.isWhitespace(text.charAt(first))) first++;
            while (last > first && Character.isWhitespace(text.charAt(last - 1))) last--;
            if (first < last) {
                String path = parent == null ? title : title == null ? parent : parent + " > " + title;
                chunks.add(new Chunk(chunks.size(), title, path, pages.get(first), pages.get(last - 1), text.substring(first, last)));
            }
            if (end == text.length()) break;
            start = Math.max(start + 1, end - overlap);
            if (Character.isLowSurrogate(text.charAt(start))) start++;
        }
        text.setLength(0); pages.clear();
    }
}
