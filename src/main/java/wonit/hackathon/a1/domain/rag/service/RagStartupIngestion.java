package wonit.hackathon.a1.domain.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/** Runs before application readiness, independently of HTTP authentication. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "rag.ingestion.on-startup", havingValue = "true", matchIfMissing = true)
public class RagStartupIngestion implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(RagStartupIngestion.class);
    private final RagIngestionJob job;

    public RagStartupIngestion(RagIngestionJob job) {
        this.job = job;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking local documents for startup ingestion");
        try {
            var report = job.run();
            if (report.failed() > 0) {
                log.warn("Startup ingestion has {} failures; see build/rag-ingest/ingestion-report.json. Failed documents will be retried on the next startup", report.failed());
            }
        } catch (Exception e) {
            // Do not log upstream response bodies or credentials.
            log.error("Startup ingestion could not finish category={}. Server will continue; check documents, metadata and database configuration", e.getClass().getSimpleName());
        }
    }
}
