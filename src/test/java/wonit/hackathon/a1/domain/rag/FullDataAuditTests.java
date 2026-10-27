package wonit.hackathon.a1.domain.rag;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only audit of every local PDF against the actual database. No external API calls. */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="RAG_DATA_AUDIT", matches="true")
class FullDataAuditTests {
    @Autowired JdbcTemplate jdbc;

    @Test void allDocumentsMappingsHashesVectorsAndPhysicalPages() throws Exception {
        var mapper = JsonMapper.builder().build();
        var report = new ArrayList<Map<String,Object>>();
        var issues = new ArrayList<String>();
        var registered = new HashSet<Path>();
        try (var paths = Files.walk(Path.of("documents"))) {
            for (var metadata : paths.filter(p -> p.getFileName().toString().equals("metadata.json")).sorted().toList()) {
                var meta = mapper.readTree(Files.readString(metadata));
                for (var doc : meta.path("documents")) {
                    Path pdf = metadata.getParent().resolve(doc.path("fileName").asString()).normalize();
                    registered.add(pdf);
                    var rows = jdbc.queryForList("""
                            SELECT d.*, p.product_name FROM product_documents d
                            JOIN insurance_products p USING(product_id) WHERE d.storage_url=?
                            """, pdf.toString().replace('\\','/'));
                    if (rows.size()!=1) { issues.add(pdf+": document count="+rows.size()); continue; }
                    var row = rows.getFirst();
                    var pages = new PdfExtractor().extract(pdf);
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(pdf)));
                    if (!hash.equals(row.get("file_hash"))) issues.add(pdf+": hash mismatch");
                    if (!meta.path("productName").asString().equals(row.get("product_name"))) issues.add(pdf+": product name mismatch");
                    if (!meta.path("productId").isNull() && !meta.path("productId").isMissingNode()
                            && meta.path("productId").asLong()!=((Number)row.get("product_id")).longValue()) issues.add(pdf+": product ID mismatch");
                    if (!doc.path("documentType").asString().equals(row.get("document_type"))) issues.add(pdf+": type mismatch");
                    if (!"COMPLETED".equals(row.get("ingestion_status"))) issues.add(pdf+": incomplete");
                    var chunks = jdbc.queryForList("""
                            SELECT chunk_index,start_page,end_page,content,
                            embedding IS NOT NULL AND vector_dims(embedding)=1536 AND vector_norm(embedding)>0 AS valid_vector
                            FROM product_chunks WHERE document_id=? ORDER BY chunk_index
                            """, row.get("document_id"));
                    int pageErrors=0;
                    for (int i=0; i<chunks.size(); i++) {
                        var c=chunks.get(i);
                        int start=((Number)c.get("start_page")).intValue(), end=((Number)c.get("end_page")).intValue();
                        if (((Number)c.get("chunk_index")).intValue()!=i || !Boolean.TRUE.equals(c.get("valid_vector")))
                            issues.add(pdf+": invalid index/vector "+i);
                        if (start<1 || end<start || end>pages.size()) { pageErrors++; continue; }
                        String source=pages.subList(start-1,end).stream().map(PdfExtractor.Page::text).reduce("",(a,b)->a+"\n"+b).replaceAll("\\s+","");
                        if (!source.contains(c.get("content").toString().replaceAll("\\s+",""))) pageErrors++;
                    }
                    if (chunks.isEmpty() || pageErrors>0) issues.add(pdf+": empty chunks or page errors="+pageErrors);
                    Long duplicates=jdbc.queryForObject("SELECT count(*) FROM (SELECT content FROM product_chunks WHERE document_id=? GROUP BY content HAVING count(*)>1) t",Long.class,row.get("document_id"));
                    report.add(Map.of("pdf",pdf.toString(),"productId",row.get("product_id"),"pages",pages.size(),"chunks",chunks.size(),"pageErrors",pageErrors,"duplicateTextGroups",duplicates,
                            "ocrPages",pages.stream().filter(PdfExtractor.Page::needsOcr).map(PdfExtractor.Page::number).toList()));
                    Files.createDirectories(Path.of("build/rag-audit"));
                    Files.writeString(Path.of("build/rag-audit/progress.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("documents",report,"issues",issues)));
                }
            }
        }
        try (var paths=Files.walk(Path.of("documents"))) {
            paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".pdf")).filter(p->!registered.contains(p)).forEach(p->issues.add("Unregistered PDF: "+p));
        }
        long stored=jdbc.queryForObject("SELECT count(*) FROM product_documents",Long.class);
        if (stored!=registered.size()) issues.add("Stored/local document count mismatch: "+stored+"/"+registered.size());
        Files.createDirectories(Path.of("build/rag-audit"));
        Files.writeString(Path.of("build/rag-audit/data.json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("documents",report,"issues",issues)));
        assertTrue(issues.isEmpty(),issues.toString());
    }
}
