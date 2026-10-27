package wonit.hackathon.a1.domain.rag;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import wonit.hackathon.a1.A1Application;
import wonit.hackathon.a1.domain.rag.service.RagIngestionJob;

/** Explicit local bootstrap: creates missing products from metadata, then ingests real PDFs. */
public class RagIngest {
    public static void main(String[] args) throws Exception {
        System.setProperty("spring.devtools.restart.enabled", "false");
        try (var context = new SpringApplicationBuilder(A1Application.class)
                .web(WebApplicationType.NONE).profiles("local")
                .run("--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.show-sql=false")) {
            var report = context.getBean(RagIngestionJob.class).run();
            if (report.failed() > 0) throw new IllegalStateException("Some documents failed; see build/rag-ingest/ingestion-report.json");
        }
    }
}
