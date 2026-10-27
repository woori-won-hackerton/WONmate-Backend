package wonit.hackathon.a1.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import wonit.hackathon.a1.domain.rag.ingest.ArticleChunker;
import wonit.hackathon.a1.domain.rag.ingest.PdfExtractor;

/** RAG 문서 처리 빈 (PDF 추출 · 청크 나누기) */
@Configuration(proxyBeanMethods = false)
public class RagConfiguration {

    @Bean
    public PdfExtractor pdfExtractor() {
        return new PdfExtractor();
    }

    @Bean
    public ArticleChunker articleChunker(
            @Value("${rag.chunking.target-tokens:750}") int targetTokens,
            @Value("${rag.chunking.max-tokens:1000}") int maxTokens,
            @Value("${rag.chunking.overlap-tokens:80}") int overlapTokens,
            @Value("${rag.chunking.chars-per-token:1.5}") double charsPerToken) {
        return new ArticleChunker(new ArticleChunker.Settings(targetTokens, maxTokens, overlapTokens, charsPerToken));
    }
}