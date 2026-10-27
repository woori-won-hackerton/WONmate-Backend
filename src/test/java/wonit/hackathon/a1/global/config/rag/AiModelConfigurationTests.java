package wonit.hackathon.a1.global.config.rag;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import wonit.hackathon.a1.client.rag.EmbeddingClient;
import static org.junit.jupiter.api.Assertions.*;

class AiModelConfigurationTests {
    @Test void configuresModelsWithoutNetworkAndWithoutHiddenSdkRetries() {
        new ApplicationContextRunner().withUserConfiguration(AiModelConfiguration.class)
                .withPropertyValues("spring.ai.openai.api-key=test-key",
                        "spring.ai.openai.chat.options.model=configured-model", "rag.embedding.timeout-seconds=7")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var chat = context.getBean(OpenAiChatModel.class).getOptions();
                    assertEquals("configured-model", chat.getModel());
                    assertEquals(Duration.ofSeconds(120), chat.getTimeout());
                    assertEquals(0, chat.getMaxRetries());
                    assertEquals(Boolean.FALSE, chat.getStore());
                    assertEquals(Boolean.TRUE, chat.getResponseFormat().getStrict());
                    var embedding = context.getBean(OpenAiEmbeddingModel.class).getOptions();
                    assertEquals(EmbeddingClient.MODEL, embedding.getModel());
                    assertEquals(1536, embedding.getDimensions());
                    assertEquals(Duration.ofSeconds(7), embedding.getTimeout());
                    assertEquals(0, embedding.getMaxRetries());
                });
    }
}
