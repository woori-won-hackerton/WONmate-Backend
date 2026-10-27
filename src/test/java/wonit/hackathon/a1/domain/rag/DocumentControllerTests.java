package wonit.hackathon.a1.domain.rag;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import wonit.hackathon.a1.domain.rag.controller.DocumentController;
import wonit.hackathon.a1.domain.rag.service.DocumentIngestionService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentControllerTests {
    @Test void removedIngestionEndpointDoesNotInvokeEmbedding() throws Exception {
        var service = mock(DocumentIngestionService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new DocumentController(service)).build();
        mvc.perform(post("/api/admin/documents/ingest")).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test void documentListingRemainsReadOnly() throws Exception {
        var service = mock(DocumentIngestionService.class);
        when(service.list()).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new DocumentController(service)).build();
        mvc.perform(get("/api/documents")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).list();
        verifyNoMoreInteractions(service);
    }
}
