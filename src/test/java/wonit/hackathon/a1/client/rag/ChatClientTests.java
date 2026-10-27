package wonit.hackathon.a1.client.rag;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import wonit.hackathon.a1.dto.rag.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatClientTests {
    @Test void sendsStatelessStructuredRequestAndRejectsIncompleteResponses() throws Exception {
        var mapper = JsonMapper.builder().build();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var finish = new java.util.concurrent.atomic.AtomicReference<>("stop");
        var observed = new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/chat", exchange -> {
            observed.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = mapper.writeValueAsString(Map.of("choices", List.of(Map.of(
                    "finish_reason", finish.get(), "message", Map.of("content", "{\"answer\":\"확인 불가\",\"chunkIds\":[]}")))))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client = new ChatClient("test", "configured-model", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"));
            var request = new AskRequest("암 보장", List.of(6L), 5);
            assertEquals("확인 불가", client.generate(request, List.of()).answer());
            var sent = mapper.readTree(observed.get());
            assertEquals("configured-model", sent.path("model").asText());
            assertFalse(sent.path("store").asBoolean());
            assertTrue(sent.path("response_format").path("json_schema").path("strict").asBoolean());
            assertEquals(2, sent.path("messages").size());
            finish.set("length");
            assertThrows(IllegalStateException.class, () -> client.generate(request, List.of()));
        } finally { server.stop(0); }
    }
}
