package wonit.hackathon.a1.domain.ai.config;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient briefingChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}