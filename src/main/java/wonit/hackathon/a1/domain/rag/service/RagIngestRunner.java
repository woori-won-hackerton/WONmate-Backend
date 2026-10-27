package wonit.hackathon.a1.domain.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import wonit.hackathon.a1.domain.rag.dto.IngestionReport;
import wonit.hackathon.a1.domain.rag.dto.IngestionResult;

/**
 * 서버 시작 시 문서 자동 적재 — RAG_INGEST_ON_STARTUP=true 일 때만
 * 서버는 바로 뜨고, 적재는 백그라운드에서 진행 (진행 상황은 backend 로그 · GET /api/documents)
 * 이미 적재한 파일은 건너뛰므로 켜 둔 채로 재시작해도 OpenAI 비용이 다시 들지 않음
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rag.ingest-on-startup", havingValue = "true")
public class RagIngestRunner implements ApplicationRunner {

    private final DocumentIngestionService ingestionService;

    public RagIngestRunner(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread worker = new Thread(() -> {
            try {
                log.info("RAG ingest: started");
                IngestionReport report = ingestionService.ingestAll();
                for (int i = 0; i < report.documents().size(); i++) {
                    IngestionResult r = report.documents().get(i);
                    if (r.status().equals("FAILED")) {
                        log.warn("RAG ingest failed document: {} ({})", r.path(), r.reason());
                    }
                }
                log.info("RAG ingest: finished completed={} skipped={} failed={} totalChunks={}",
                        report.completed(), report.skipped(), report.failed(), report.totalChunks());
            } catch (Exception e) {
                log.error("RAG ingest: stopped ({})", e.getClass().getSimpleName());
            }
        }, "rag-ingest");
        worker.setDaemon(true);
        worker.start();
    }
}