package wonit.hackathon.a1.global.config.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import wonit.hackathon.a1.domain.rag.ArticleChunker;
import wonit.hackathon.a1.domain.rag.PdfExtractor;

/** 순수 PDF 처리 클래스는 Spring 의존 없이 유지하고 여기에서 애플리케이션 빈으로 조립한다. */
@Configuration(proxyBeanMethods = false)
public class RagConfiguration {
    @Bean
    PdfExtractor pdfExtractor() { return new PdfExtractor(); }

    @Bean
    // 토큰 수는 문자 비율로 추정한다. 원문 페이지 좌표와 조항 경계를 청커가 보존한다.
    ArticleChunker articleChunker(@Value("${rag.chunking.target-tokens:750}") int target,
                                  @Value("${rag.chunking.max-tokens:1000}") int max,
                                  @Value("${rag.chunking.overlap-tokens:80}") int overlap,
                                  @Value("${rag.chunking.chars-per-token:1.5}") double ratio) {
        return new ArticleChunker(new ArticleChunker.Settings(target, max, overlap, ratio));
    }
}
