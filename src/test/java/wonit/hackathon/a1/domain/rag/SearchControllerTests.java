package wonit.hackathon.a1.domain.rag;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import wonit.hackathon.a1.domain.rag.controller.SearchController;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.rag.dto.SearchResult;
import wonit.hackathon.a1.domain.rag.service.ChunkSearchService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SearchControllerTests {
    @Test void exposesSourceFieldsAndRejectsInvalidRequestsBeforeEmbedding() throws Exception {
        var service = mock(ChunkSearchService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SearchController(service)).build();
        when(service.search(any())).thenReturn(List.of(new SearchResult(1L, 2L, 3L, "약관.pdf",
                DocumentType.TERMS, "제2조", "특약", 12, 13, "원문", 0.8)));
        mvc.perform(post("/api/rag/search").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"암의 정의\",\"productIds\":[3],\"documentTypes\":[\"TERMS\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].chunkId").value(1))
                .andExpect(jsonPath("$[0].documentId").value(2)).andExpect(jsonPath("$[0].productId").value(3))
                .andExpect(jsonPath("$[0].documentName").value("약관.pdf"))
                .andExpect(jsonPath("$[0].documentType").value("TERMS"))
                .andExpect(jsonPath("$[0].sectionTitle").value("제2조"))
                .andExpect(jsonPath("$[0].sectionPath").value("특약"))
                .andExpect(jsonPath("$[0].startPage").value(12)).andExpect(jsonPath("$[0].endPage").value(13))
                .andExpect(jsonPath("$[0].content").value("원문"))
                .andExpect(jsonPath("$[0].similarityScore").value(0.8));
        reset(service);
        for (String json : List.of("{\"question\":\" \"}", "{\"question\":\"면책기간\",\"topK\":0}",
                "{\"question\":\"질문\",\"productIds\":[null]}",
                "{\"question\":\"질문\",\"documentTypes\":[\"INVALID\"]}")) {
            mvc.perform(post("/api/rag/search").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
