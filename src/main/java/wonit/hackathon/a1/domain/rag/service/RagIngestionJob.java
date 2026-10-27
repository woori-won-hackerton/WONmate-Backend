package wonit.hackathon.a1.domain.rag.service;

import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import wonit.hackathon.a1.domain.rag.dto.IngestionReport;

/** Shared product bootstrap and reporting for startup and CLI ingestion. */
@Service
public class RagIngestionJob {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final DocumentIngestionService ingestion;
    private final String documents;

    public RagIngestionJob(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            DocumentIngestionService ingestion, @Value("${rag.debug.documents:documents}") String documents) {
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
        this.ingestion = ingestion;
        this.documents = documents;
    }

    public synchronized IngestionReport run() throws Exception {

        var tx = new TransactionTemplate(transactionManager);
        var mapper = JsonMapper.builder().build();
        Path root = Path.of(documents);
        var mappings = new ArrayList<Object>();
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(p -> p.getFileName().toString().equals("metadata.json"))
                    .filter(p -> !p.getParent().getFileName().toString().equals("unmatched")).sorted().toList()) {
                var meta = mapper.readTree(Files.readString(path));
                String name = meta.path("productName").asString("");
                if (name.isBlank()) throw new IllegalStateException("Missing product name: " + path);
                Long id = tx.execute(status -> {
                    jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", Object.class, "rag-product:" + name);
                    if (!meta.path("productId").isNull() && !meta.path("productId").isMissingNode()) {
                        long declared = meta.path("productId").asLong(-1);
                        var names = jdbc.queryForList("SELECT product_name FROM insurance_products WHERE product_id=?", String.class, declared);
                        if (names.size() != 1 || !name.equals(names.getFirst()))
                            throw new IllegalStateException("Declared product mapping does not match: " + path);
                        return declared;
                    }
                    var ids = jdbc.queryForList("SELECT product_id FROM insurance_products WHERE product_name=?", Long.class, name);
                    if (ids.size() > 1) throw new IllegalStateException("Ambiguous product name: " + name);
                    if (!ids.isEmpty()) return ids.getFirst();
                    // Metadata does not establish premium, age, payment terms or insurance type.
                    return jdbc.queryForObject("INSERT INTO insurance_products(product_name, insurance_type) VALUES (?, 'UNKNOWN') RETURNING product_id", Long.class, name);
                });
                mappings.add(Map.of("metadata", path.toString(), "productId", id, "productName", name));
            }
        }
        Path output = Path.of("build/rag-ingest"); Files.createDirectories(output);
        Files.writeString(output.resolve("product-mappings.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappings));
        System.out.println("Product mappings prepared: " + mappings.size());
        var report = ingestion.ingestAll();
        Files.writeString(output.resolve("ingestion-report.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.printf("Ingestion: success=%d skipped=%d failed=%d chunks=%d%n", report.success(), report.skipped(), report.failed(), report.totalChunks());
        return report;
    }
}
