package wonit.hackathon.a1.client.rag;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmbeddingClientTests {
    private String vector(int index, int size) {
        return "{\"index\":" + index + ",\"embedding\":[" + String.join(",", Collections.nCopies(size, "0.1")) + "]}";
    }

    @Test void batchesRetriesAndRestoresResponseIndexOrder() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int call = calls.incrementAndGet();
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(request.contains("text-embedding-3-small")); assertTrue(request.contains("1536"));
            String body = call == 1 ? "secret-body" : "{\"data\":[" + (call == 2 ? vector(1,1536) + "," : "") + vector(0,1536) + "]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(call == 1 ? 429 : 200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var client = new EmbeddingClient("test-key", URI.create("http://127.0.0.1:" + server.getAddress().getPort()), 2, 2, 1);
            var result = client.embed(List.of("one", "two", "three"));
            assertEquals(3, result.size()); assertEquals(1536, result.getFirst().length); assertEquals(3, calls.get());
        } finally { server.stop(0); }
    }

    @Test void rejectsWrongDimensionsAndDoesNotRetryUnauthorized() throws Exception {
        for (int status : List.of(200,401,500)) {
            var calls = new AtomicInteger();
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0),0);
            server.createContext("/", exchange -> {
                calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
                byte[] body = (status == 200 ? "{\"data\":[" + vector(0,3) + "]}" : "private-key-body").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close();
            });
            server.start();
            try {
                var client = new EmbeddingClient("test-key", URI.create("http://127.0.0.1:"+server.getAddress().getPort()),2,2,1);
                var error = assertThrows(IllegalStateException.class, () -> client.embed(List.of("one")));
                assertFalse(error.getMessage().contains("private-key-body"));
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
            var client = new EmbeddingClient("secret-key", URI.create("http://127.0.0.1:"+server.getAddress().getPort()),1,2,0);
            var error = assertThrows(IllegalStateException.class, () -> client.embed(List.of("one")));
            assertEquals("Embedding transport failure", error.getMessage());
        } finally { server.stop(0); }
    }
}
