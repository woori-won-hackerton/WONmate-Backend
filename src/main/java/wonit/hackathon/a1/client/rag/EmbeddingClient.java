package wonit.hackathon.a1.client.rag;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 청크를 고정 모델의 1536차원 벡터로 변환하는 HTTP 클라이언트. DB에는 접근하지 않는다.
 * 원문·응답 본문·인증 헤더·예외 메시지는 API 키나 민감한 내용이 섞일 수 있어 로깅하지 않는다.
 */
@Component
public class EmbeddingClient {
    public static final String MODEL = "text-embedding-3-small";
    private final String key;
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;
    private final int batchSize;
    private final int retries;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @org.springframework.beans.factory.annotation.Autowired
    public EmbeddingClient(@Value("${OPENAI_API_KEY:}") String key,
                           @Value("${rag.embedding.timeout-seconds:60}") int seconds,
                           @Value("${rag.embedding.batch-size:16}") int batchSize,
                           @Value("${rag.embedding.retries:3}") int retries) {
        this(key, URI.create("https://api.openai.com/v1/embeddings"), seconds, batchSize, retries);
    }

    /** 테스트에서는 로컬 HTTP 서버 주소를 주입해 유료 API 없이 오류/재시도를 재현한다. */
    EmbeddingClient(String key, URI endpoint, int seconds, int batchSize, int retries) {
        if (seconds < 1 || batchSize < 1 || batchSize > 128 || retries < 0 || retries > 5)
            throw new IllegalArgumentException("Invalid embedding settings");
        this.key = key; this.endpoint = endpoint; this.timeout = Duration.ofSeconds(seconds);
        this.batchSize = batchSize; this.retries = retries;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** 입력 순서와 반환 순서가 같다. 한 배치라도 실패하면 부분 결과를 서비스에 반환하지 않는다. */
    public List<float[]> embed(List<String> texts) {
        if (key == null || key.isBlank()) throw new IllegalStateException("OPENAI_API_KEY is missing");
        var result = new ArrayList<float[]>();
        for (int offset = 0; offset < texts.size(); offset += batchSize) {
            var batch = texts.subList(offset, Math.min(offset + batchSize, texts.size()));
            // 실제 토크나이저 대신 UTF-8 바이트 상한으로 입력을 보수적으로 제한한다.
            if (batch.stream().anyMatch(s -> s == null || s.isBlank() || s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8192))
                throw new IllegalArgumentException("Empty or oversized embedding input");
            result.addAll(request(batch));
        }
        return result;
    }

    private List<float[]> request(List<String> batch) {
        for (int attempt = 0; ; attempt++) {
            try {
                var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                        .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                                "model", MODEL, "dimensions", 1536, "encoding_format", "float", "input", batch))))
                        .build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                // 최초 요청 + retries회까지만 시도한다. 인증/입력 오류(401/400 등)는 즉시 실패한다.
                if (status == 429 || status >= 500 && status <= 599) {
                    LoggerFactory.getLogger(getClass()).warn("Embedding HTTP {} attempt {}", status, attempt + 1);
                    if (attempt < retries) { Thread.sleep(Math.min(1000L << attempt, 8000)); continue; }
                }
                if (status != 200) throw new IllegalStateException("Embedding HTTP " + status);
                var data = mapper.readTree(response.body()).path("data");
                if (!data.isArray() || data.size() != batch.size()) throw new IllegalStateException("Embedding count mismatch");
                // 서버 응답 배열 순서에 의존하지 않고 index로 요청 청크와 벡터를 연결한다.
                float[][] vectors = new float[batch.size()][];
                for (var item : data) {
                    var indexNode = item.path("index");
                    int index = indexNode.asInt(-1);
                    var values = item.path("embedding");
                    if (!indexNode.isIntegralNumber() || index < 0 || index >= vectors.length || vectors[index] != null
                            || !values.isArray() || values.size() != 1536)
                        throw new IllegalStateException("Invalid embedding index or dimension");
                    var vector = new float[1536];
                    for (int i = 0; i < vector.length; i++) {
                        if (!values.get(i).isNumber()) throw new IllegalStateException("Invalid embedding value");
                        vector[i] = (float) values.get(i).asDouble();
                        if (!Float.isFinite(vector[i])) throw new IllegalStateException("Nonfinite embedding value");
                    }
                    vectors[index] = vector;
                }
                return Arrays.asList(vectors);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("Embedding interrupted");
            } catch (java.io.IOException e) {
                LoggerFactory.getLogger(getClass()).warn("Embedding transport failure: {}", e.getClass().getSimpleName());
                throw new IllegalStateException("Embedding transport failure");
            }
        }
    }
}
