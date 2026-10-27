package wonit.hackathon.a1.global.config.rag;

import java.time.Duration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import wonit.hackathon.a1.client.rag.EmbeddingClient;
import wonit.hackathon.a1.client.rag.ChatClient;

/** Model calls use Spring AI; persistence and retrieval continue to use our SQL schema. */
@Configuration(proxyBeanMethods = false)
public class AiModelConfiguration {
    @Bean
    OpenAiChatModel chatModel(@Value("${spring.ai.openai.api-key}") String key,
            @Value("${spring.ai.openai.chat.options.model}") String model) {
        return OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
                .apiKey(key).model(model).timeout(Duration.ofSeconds(120)).maxRetries(0)
                .store(false).responseFormat(OpenAiChatModel.ResponseFormat.builder()
                        .jsonSchema(ChatClient.OUTPUT_SCHEMA).strict(true).build()).build()).build();
    }

    @Bean
    OpenAiEmbeddingModel embeddingModel(@Value("${spring.ai.openai.api-key}") String key,
            @Value("${rag.embedding.timeout-seconds:60}") int seconds) {
        if (seconds < 1) throw new IllegalArgumentException("Invalid embedding timeout");
        // Keep the model/dimension aligned with existing stored embeddings. Only our wrapper retries.
        return OpenAiEmbeddingModel.builder().options(OpenAiEmbeddingOptions.builder()
                .apiKey(key).model(EmbeddingClient.MODEL).dimensions(1536)
                .encodingFormat(OpenAiEmbeddingOptions.EncodingFormat.FLOAT)
                .timeout(Duration.ofSeconds(seconds)).maxRetries(0).build()).build();
    }
}
