package wonit.hackathon.a1.domain.rag.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import wonit.hackathon.a1.domain.insurance.entity.ProductChunk;

/**
 * OpenAI 임베딩 (Spring AI EmbeddingModel — 모델은 application.yaml 의
 * spring.ai.openai.embedding)
 * 여러 문장을 batch-size씩 나눠 보내고, 실패하면 retries번까지 다시 시도
 */
@Slf4j
@Component
public class EmbeddingClient {

    /** OpenAI 임베딩 입력 한 건 최대 크기 (넉넉히 제한) */
    private static final int MAX_INPUT_BYTES = 30_000;

    private final EmbeddingModel model;
    private final int batchSize;
    private final int retries;

    public EmbeddingClient(
            EmbeddingModel model,
            @Value("${rag.embedding.batch-size:32}") int batchSize,
            @Value("${rag.embedding.retries:3}") int retries) {
        if (batchSize < 1 || batchSize > 256 || retries < 0 || retries > 5) {
            throw new IllegalArgumentException("Invalid embedding settings");
        }
        this.model = model;
        this.batchSize = batchSize;
        this.retries = retries;
    }

    /** 질문 한 건 임베딩 */
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    /** 여러 건 임베딩 — 입력 순서대로 반환. 하나라도 실패하면 예외 */
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        for (int offset = 0; offset < texts.size(); offset += batchSize) {
            List<String> batch = texts.subList(offset, Math.min(offset + batchSize, texts.size()));
            for (int i = 0; i < batch.size(); i++) {
                String s = batch.get(i);
                if (s == null || s.isBlank() || s.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
                    throw new IllegalArgumentException("Empty or oversized embedding input");
                }
            }
            result.addAll(request(batch));
        }
        return result;
    }

    private List<float[]> request(List<String> batch) {
        for (int attempt = 0;; attempt++) {
            try {
                var response = model.call(new EmbeddingRequest(batch, null));
                if (response == null || response.getResults().size() != batch.size()) {
                    throw new IllegalStateException("Embedding count mismatch");
                }
                float[][] vectors = new float[batch.size()][];
                var results = response.getResults();
                for (int i = 0; i < results.size(); i++) {
                    var item = results.get(i);
                    int index = item.getIndex();
                    float[] vector = item.getOutput();
                    if (index < 0 || index >= vectors.length || vectors[index] != null
                            || vector == null || vector.length != ProductChunk.DIMENSIONS) {
                        throw new IllegalStateException("Invalid embedding index or dimension");
                    }
                    vectors[index] = vector;
                }
                return Arrays.asList(vectors);
            } catch (RuntimeException e) {
                if (attempt >= retries) {
                    // 외부 응답 본문 · 키가 로그에 남지 않도록 예외 종류만 남김
                    throw new IllegalStateException("Embedding failed: " + e.getClass().getSimpleName());
                }
                log.warn("Embedding failed ({}), retry {}/{}", e.getClass().getSimpleName(), attempt + 1, retries);
                try {
                    Thread.sleep(Math.min(1000L << attempt, 8000L));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Embedding interrupted");
                }
            }
        }
    }
}