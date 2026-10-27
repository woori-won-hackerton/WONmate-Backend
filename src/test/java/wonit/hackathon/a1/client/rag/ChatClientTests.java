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
        var content = new java.util.concurrent.atomic.AtomicReference<>("{\"answer\":\"확인 불가\",\"evidenceIndexes\":[]}");
        server.createContext("/chat", exchange -> {
            observed.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = mapper.writeValueAsString(Map.of("choices", List.of(Map.of(
                    "finish_reason", finish.get(), "message", Map.of("content", content.get())))))
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client = new ChatClient("test", "configured-model", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"));
            var request = new AskRequest("암 보장", QuestionType.PRODUCT_EXPLANATION, 6L, 5);
            var chunk = new SearchResult(8027L, 22L, 6L, "internal.pdf",
                    wonit.hackathon.a1.domain.insurance.enums.DocumentType.SUMMARY,
                    null, null, 4, 5, "암 진단비 원문", .8);
            var chunks = List.of(chunk);
            var names = Map.of(6L, "무배당우리WON하는암보험");
            assertEquals("확인 불가", client.generate(request, chunks, names).answer());
            var sent = mapper.readTree(observed.get());
            assertEquals("configured-model", sent.path("model").asText());
            assertFalse(sent.path("store").asBoolean());
            assertTrue(sent.path("response_format").path("json_schema").path("strict").asBoolean());
            assertEquals(2, sent.path("messages").size());
            var user = mapper.readTree(sent.path("messages").path(1).path("content").asText());
            assertEquals("PRODUCT_EXPLANATION", user.path("questionType").asText());
            assertFalse(user.has("productId"));
            var document = user.path("documents").path(0);
            assertEquals(1, document.path("evidenceIndex").asInt());
            assertEquals("무배당우리WON하는암보험", document.path("productName").asText());
            assertEquals("암 진단비 원문", document.path("content").asText());
            for (String field : List.of("productId", "documentId", "chunkId", "documentName", "startPage", "endPage"))
                assertFalse(document.has(field), field);
            assertFalse(user.toString().contains("internal.pdf"));
            var schema = sent.path("response_format").path("json_schema").path("schema").path("properties");
            assertTrue(schema.has("evidenceIndexes"));
            assertFalse(schema.has("chunkIds"));
            content.set("{\"answer\":\"보장 설명\",\"evidenceIndexes\":[1]}");
            assertEquals(List.of(1), client.generate(request, chunks, names).evidenceIndexes());
            for (String indexes : List.of("[0]", "[2]", "[1.5]", "[null]", "[2147483648]")) {
                content.set("{\"answer\":\"보장 설명\",\"evidenceIndexes\":" + indexes + "}");
                assertThrows(IllegalStateException.class, () -> client.generate(request, chunks, names));
            }
            finish.set("length");
            assertThrows(IllegalStateException.class, () -> client.generate(request, chunks, names));
        } finally { server.stop(0); }
    }
}
