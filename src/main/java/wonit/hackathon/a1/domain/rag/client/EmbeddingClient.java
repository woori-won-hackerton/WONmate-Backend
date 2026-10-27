package wonit.hackathon.a1.domain.rag.client;

import java.util.*;
import com.openai.errors.OpenAIServiceException;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Spring AI handles transport; this adapter enforces batching and stored-vector invariants. */
@Component
public class EmbeddingClient {
    public static final String MODEL = "text-embedding-3-small";
    private final EmbeddingModel model;
    private final int batchSize;
    private final int retries;

    public EmbeddingClient(EmbeddingModel model,
            @Value("${rag.embedding.batch-size:16}") int batchSize,
            @Value("${rag.embedding.retries:3}") int retries) {
        if (batchSize < 1 || batchSize > 128 || retries < 0 || retries > 5)
            throw new IllegalArgumentException("Invalid embedding settings");
        this.model = model;
        this.batchSize = batchSize;
        this.retries = retries;
    }

    /** One failed batch aborts the document before existing data is changed. */
    public List<float[]> embed(List<String> texts) {
        var result = new ArrayList<float[]>();
        for (int offset = 0; offset < texts.size(); offset += batchSize) {
            var batch = texts.subList(offset, Math.min(offset + batchSize, texts.size()));
            if (batch.stream().anyMatch(s -> s == null || s.isBlank()
                    || s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8192))
                throw new IllegalArgumentException("Empty or oversized embedding input");
            result.addAll(request(batch));
        }
        return result;
    }

    private List<float[]> request(List<String> batch) {
        for (int attempt = 0; ; attempt++) {
            try {
                var response = model.call(new EmbeddingRequest(batch, null));
                if (response == null || response.getResults().size() != batch.size())
                    throw new EmbeddingFailure("Embedding count mismatch");
                float[][] vectors = new float[batch.size()][];
                for (var item : response.getResults()) {
                    int index = item.getIndex();
                    float[] vector = item.getOutput();
                    if (index < 0 || index >= vectors.length || vectors[index] != null
                            || vector == null || vector.length != 1536)
                        throw new EmbeddingFailure("Invalid embedding index or dimension");
                    double norm = 0;
                    for (float value : vector) {
                        if (!Float.isFinite(value)) throw new EmbeddingFailure("Nonfinite embedding value");
                        norm += (double) value * value;
                    }
                    if (norm == 0) throw new EmbeddingFailure("Zero embedding vector");
                    vectors[index] = vector;
                }
                return Arrays.asList(vectors);
            } catch (OpenAIServiceException e) {
                int status = e.statusCode();
                if ((status == 429 || status >= 500 && status <= 599) && attempt < retries) {
                    LoggerFactory.getLogger(getClass()).warn("Embedding HTTP {} attempt {}", status, attempt + 1);
                    try { Thread.sleep(Math.min(1000L << attempt, 8000)); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new EmbeddingFailure("Embedding interrupted");
                    }
                    continue;
                }
                // SDK exceptions may contain the response body: never propagate their cause/message.
                throw new EmbeddingFailure("Embedding HTTP " + status);
            } catch (EmbeddingFailure e) {
                throw e;
            } catch (RuntimeException e) {
                throw new EmbeddingFailure("Embedding transport or response failure");
            }
        }
    }
}
