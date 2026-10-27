package wonit.hackathon.a1.rag;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import wonit.hackathon.a1.controller.rag.AskController;
import wonit.hackathon.a1.dto.rag.*;
import wonit.hackathon.a1.service.rag.AskService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AskControllerTests {
    @Test void acceptsAllTypesAndSingleProductOnlyForExplanation() throws Exception {
        var service = mock(AskService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AskController(service)).build();
        when(service.ask(any())).thenReturn(new AskResponse("답변", List.of()));
        for (var type : QuestionType.values()) {
            String selection = type == QuestionType.PRODUCT_EXPLANATION ? ",\"productId\":6" : "";
            mvc.perform(post("/api/rag/ask").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"설명해줘\",\"questionType\":\"" + type + "\"" + selection + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("답변"));
            verify(service).ask(new AskRequest("설명해줘", type, type == QuestionType.PRODUCT_EXPLANATION ? 6L : null, 5));
        }
    }
    @Test void rejectsInvalidTypesQuestionsAndSelections() throws Exception {
        var service = mock(AskService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AskController(service)).build();
        for (String json : List.of(
                "{\"question\":\"질문\"}",
                "{\"question\":\"질문\",\"questionType\":null}",
                "{\"question\":\"질문\",\"questionType\":\"UNKNOWN\"}",
                "{\"question\":\"질문\",\"questionType\":\"PRODUCT_COMPARISON\"}",
                "{\"question\":\"질문\",\"questionType\":\"PRODUCT_RECOMMENDATION\"}",
                "{\"question\":\" \",\"questionType\":\"TERM_EXPLANATION\"}",
                "{\"question\":\"질문\",\"questionType\":\"PRODUCT_EXPLANATION\"}",
                "{\"question\":\"질문\",\"questionType\":\"PRODUCT_EXPLANATION\",\"productId\":0}",
                "{\"question\":\"질문\",\"questionType\":\"PRODUCT_EXPLANATION\",\"productId\":[6,7]}",
                "{\"question\":\"질문\",\"questionType\":\"TERM_EXPLANATION\",\"topK\":0}"))
            mvc.perform(post("/api/rag/ask").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        for (var type : QuestionType.values()) {
            if (type == QuestionType.PRODUCT_EXPLANATION) continue;
            mvc.perform(post("/api/rag/ask").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"질문\",\"questionType\":\"" + type + "\",\"productId\":6}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
