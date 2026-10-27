package wonit.hackathon.a1.rag;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;
import wonit.hackathon.a1.client.rag.EmbeddingClient;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: sends two real PDF chunks to OpenAI, never persists credentials or vectors. */
@EnabledIfEnvironmentVariable(named="RAG_OPENAI_SMOKE", matches="true")
class RealEmbeddingSmokeTests {
    @Test void embedsRealPdfChunksWith1536Dimensions() throws Exception {
        Path dir = Path.of("documents/product_01");
        var mapper = JsonMapper.builder().build();
        var meta = mapper.readTree(Files.readString(dir.resolve("metadata.json")));
        var doc = meta.path("documents").get(1);
        var pages = new PdfExtractor().extract(dir.resolve(doc.path("fileName").asString()));
        var chunks = new ArticleChunker(new ArticleChunker.Settings(750,1000,80,1.5)).chunk(pages);
        var model = org.springframework.ai.openai.OpenAiEmbeddingModel.builder()
                .options(org.springframework.ai.openai.OpenAiEmbeddingOptions.builder()
                        .apiKey(System.getenv("OPENAI_API_KEY")).model(EmbeddingClient.MODEL).dimensions(1536)
                        .encodingFormat(org.springframework.ai.openai.OpenAiEmbeddingOptions.EncodingFormat.FLOAT)
                        .timeout(java.time.Duration.ofSeconds(60)).maxRetries(0).build()).build();
        var vectors = new EmbeddingClient(model,16,3)
                .embed(chunks.stream().limit(2).map(ArticleChunker.Chunk::content).toList());
        assertEquals(2,vectors.size());
        for (var vector : vectors) assertEquals(1536,vector.length);
        Files.createDirectories(Path.of("build/rag-ingest"));
        Files.writeString(Path.of("build/rag-ingest/openai-smoke.json"), mapper.writeValueAsString(
                java.util.Map.of("model",EmbeddingClient.MODEL,"dimensions",1536,"embeddedChunks",vectors.size(),"source",doc.path("fileName").asString())));
    }
}
