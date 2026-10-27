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
    @Test void acceptsOptionalProductWithoutQuestionClassification() throws Exception {
        var service = mock(AskService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AskController(service)).build();
        when(service.ask(any())).thenReturn(new AskResponse("답변", List.of()));
        for (String suffix : List.of("", ",\"productIds\":null", ",\"productIds\":[]", ",\"productIds\":[6]")) {
            mvc.perform(post("/api/rag/ask").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"question\":\"암 관련 보장 내용 찾아줘\"" + suffix + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("답변"));
        }
        verify(service).ask(new AskRequest("암 관련 보장 내용 찾아줘", List.of(6L), 5));
    }
    @Test void rejectsInvalidQuestionsAndExplicitComparisonScope() throws Exception {
        var service = mock(AskService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AskController(service)).build();
        for (String json : List.of("{\"question\":\" \"}",
                "{\"question\":\"질문\",\"productIds\":[6,7]}",
                "{\"question\":\"질문\",\"productIds\":[null]}",
                "{\"question\":\"질문\",\"productIds\":[0]}",
                "{\"question\":\"질문\",\"topK\":0}"))
            mvc.perform(post("/api/rag/ask").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
