package wonit.hackathon.a1.rag;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/** Standalone developer entry point: no Spring context, repositories or API clients. */
public class RagDebug {
    public static void main(String[] args) throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties p = Objects.requireNonNull(yaml.getObject());
        var settings = new ArticleChunker.Settings(
                Integer.parseInt(p.getProperty("rag.chunking.target-tokens", "750")),
                Integer.parseInt(p.getProperty("rag.chunking.max-tokens", "1000")),
                Integer.parseInt(p.getProperty("rag.chunking.overlap-tokens", "80")),
                Double.parseDouble(p.getProperty("rag.chunking.chars-per-token", "1.5")));
        Path root = Path.of(p.getProperty("rag.debug.documents", "documents")).toRealPath();
        Path output = Path.of(p.getProperty("rag.debug.output", "build/rag-debug"));
        boolean all = Arrays.asList(args).contains("--all");
        String product = "product_01";
        for (String arg : args) {
            if (arg.startsWith("--product=")) product = arg.substring("--product=".length());
            else if (!arg.equals("--all")) throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        var mapper = JsonMapper.builder().build();
        var report = new ArrayList<Map<String, Object>>();
        int failures = 0, processed = 0;
        try (var dirs = Files.list(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                if (!all && !dir.getFileName().toString().equals(product)) continue;
                // Stage 1 reserves this directory for files not assigned to a product.
                if (dir.getFileName().toString().equals("unmatched")) {
                    report.add(Map.of("directory", "unmatched", "status", "SKIPPED", "reason", "Not a product directory"));
                    continue;
                }
                try {
                    var metadata = mapper.readTree(Files.readString(dir.resolve("metadata.json"), StandardCharsets.UTF_8));
                    if (!metadata.path("documents").isArray()) throw new IllegalArgumentException("Missing documents array");
                    for (var doc : metadata.path("documents")) {
                        String name = doc.path("fileName").asString();
                        var item = new LinkedHashMap<String, Object>();
                        item.put("product", dir.getFileName().toString()); item.put("fileName", name);
                        item.put("documentType", doc.path("documentType").asString());
                        try {
                            Path pdf = dir.resolve(name).toRealPath();
                            if (!pdf.getParent().equals(dir.toRealPath()) || !name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                                throw new IllegalArgumentException("PDF must be inside its product directory");
                            }
                            var pages = new PdfExtractor().extract(pdf);
                            var chunks = new ArticleChunker(settings).chunk(pages);
                            var empty = pages.stream().filter(page -> page.text().isBlank()).map(PdfExtractor.Page::number).toList();
                            var ocr = pages.stream().filter(PdfExtractor.Page::needsOcr).map(PdfExtractor.Page::number).toList();
                            String status = empty.size() == pages.size() ? "OCR_REQUIRED" : !ocr.isEmpty() ? "PARTIAL_OCR_REQUIRED" : empty.isEmpty() ? "EXTRACTED" : "REVIEW_REQUIRED";
                            item.put("status", status); item.put("pageCount", pages.size());
                            item.put("emptyPages", empty); item.put("chunkCount", chunks.size());
                            item.put("ocrRequiredPages", ocr);
                            item.put("tableExtractionStatus", "UNVERIFIED_TEXT_ONLY");
                            item.put("warning", "Tables retain PDFBox text order only; manually compare payment conditions and amounts with PDF. Empty pages may be blank or require OCR. Physical PDF page numbers are used.");
                            item.put("chunking", settings);
                            String base = name.substring(0, name.length() - 4);
                            Path txtDir = Files.createDirectories(output.resolve("extracted").resolve(dir.getFileName()));
                            Path jsonDir = Files.createDirectories(output.resolve("chunks").resolve(dir.getFileName()));
                            var extracted = new StringBuilder();
                            for (var page : pages) extracted.append("\n===== PDF PAGE ").append(page.number()).append(" =====\n").append(page.text());
                            Files.writeString(txtDir.resolve(base + ".txt"), extracted, StandardCharsets.UTF_8);
                            Files.writeString(jsonDir.resolve(base + ".json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(chunks), StandardCharsets.UTF_8);
                            processed++;
                            System.out.printf("%s / %s: %s, %d pages, %d chunks%n", dir.getFileName(), doc.path("documentType").asString(), status, pages.size(), chunks.size());
                        } catch (Exception e) { item.put("status", "FAILED"); item.put("error", e.toString()); failures++; }
                        report.add(item);
                    }
                } catch (Exception e) {
                    report.add(Map.of("product", dir.getFileName().toString(), "status", "FAILED", "error", e.toString())); failures++;
                }
            }
        }
        Files.createDirectories(output);
        Files.writeString(output.resolve(all ? "report-all.json" : "report-" + product + ".json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report), StandardCharsets.UTF_8);
        if (failures > 0 || processed == 0) throw new IllegalStateException("See report: " + failures + " failures, " + processed + " processed PDFs");
    }
}
