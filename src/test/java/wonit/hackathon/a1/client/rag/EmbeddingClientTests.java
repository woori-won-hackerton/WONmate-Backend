package wonit.hackathon.a1.client.rag;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingClientTests {
    private EmbeddingClient client(int port, int seconds, int batchSize, int retries) {
        var model = org.springframework.ai.openai.OpenAiEmbeddingModel.builder()
                .options(org.springframework.ai.openai.OpenAiEmbeddingOptions.builder()
                        .apiKey("test-key").baseUrl("http://127.0.0.1:" + port)
                        .model(EmbeddingClient.MODEL).dimensions(1536)
                        .encodingFormat(org.springframework.ai.openai.OpenAiEmbeddingOptions.EncodingFormat.FLOAT)
                        .timeout(java.time.Duration.ofSeconds(seconds)).maxRetries(0).build()).build();
        return new EmbeddingClient(model, batchSize, retries);
    }
    private String vector(int index, int size) {
        return "{\"index\":" + index + ",\"embedding\":[" + String.join(",", Collections.nCopies(size, index == 0 ? "0.1" : "0.2")) + "]}";
    }

    @Test void batchesRetriesAndRestoresResponseIndexOrder() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int call = calls.incrementAndGet();
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(request.contains("text-embedding-3-small")); assertTrue(request.contains("1536"));
            String body = call == 1 ? "secret-body" : "{\"model\":\"text-embedding-3-small\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1},\"data\":[" + (call == 2 ? vector(1,1536) + "," : "") + vector(0,1536) + "]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(call == 1 ? 429 : 200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var client = client(server.getAddress().getPort(), 2, 2, 1);
            var result = client.embed(List.of("one", "two", "three"));
            assertEquals(3, result.size()); assertEquals(1536, result.getFirst().length); assertEquals(3, calls.get());
            assertEquals(0.1f, result.get(0)[0]);
            assertEquals(0.2f, result.get(1)[0]);
            assertEquals(0.1f, result.get(2)[0]);
        } finally { server.stop(0); }
    }

    @Test void rejectsWrongDimensionsAndDoesNotRetryUnauthorized() throws Exception {
        for (int status : List.of(200,401,500)) {
            var calls = new AtomicInteger();
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0),0);
            server.createContext("/", exchange -> {
                calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
                byte[] body = (status == 200 ? "{\"model\":\"text-embedding-3-small\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1},\"data\":[" + vector(0,3) + "]}" : "private-key-body").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.start();
            try {
                var client = client(server.getAddress().getPort(), 2, 2, 1);
                var error = assertThrows(IllegalStateException.class, () -> client.embed(List.of("one")));
                assertFalse(error.getMessage().contains("private-key-body"));
                assertNull(error.getCause());
                assertEquals(status == 500 ? 2 : 1, calls.get());
            } finally { server.stop(0); }
        }
    }

    @Test void timesOutWithoutLeakingCredentials() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var client = client(server.getAddress().getPort(), 1, 2, 0);
            var error = assertThrows(IllegalStateException.class, () -> client.embed(List.of("one")));
            assertEquals("Embedding transport or response failure", error.getMessage());
        } finally { server.stop(0); }
    }

    @Test void rejectsDuplicateOutOfRangeAndUnusableVectors() {
        var model = org.mockito.Mockito.mock(org.springframework.ai.embedding.EmbeddingModel.class);
        var client = new EmbeddingClient(model, 2, 0);
        var valid = new float[1536]; valid[0] = 1;
        var nonfinite = valid.clone(); nonfinite[0] = Float.NaN;
        for (var items : List.of(
                List.of(new org.springframework.ai.embedding.Embedding(valid, 0)),
                List.of(new org.springframework.ai.embedding.Embedding(valid, 0), new org.springframework.ai.embedding.Embedding(valid, 0)),
                List.of(new org.springframework.ai.embedding.Embedding(valid, 0), new org.springframework.ai.embedding.Embedding(valid, 2)),
                List.of(new org.springframework.ai.embedding.Embedding(valid, 0), new org.springframework.ai.embedding.Embedding(new float[1536], 1)),
                List.of(new org.springframework.ai.embedding.Embedding(valid, 0), new org.springframework.ai.embedding.Embedding(nonfinite, 1)))) {
            org.mockito.Mockito.when(model.call(org.mockito.ArgumentMatchers.any()))
                    .thenReturn(new org.springframework.ai.embedding.EmbeddingResponse(items));
            assertThrows(IllegalStateException.class, () -> client.embed(List.of("one", "two")));
        }
    }
}
