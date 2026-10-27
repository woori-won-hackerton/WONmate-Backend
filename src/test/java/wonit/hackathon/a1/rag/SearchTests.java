package wonit.hackathon.a1.rag;

import jakarta.validation.Validation;
import java.util.*;
import org.junit.jupiter.api.Test;
import wonit.hackathon.a1.client.rag.EmbeddingClient;
import wonit.hackathon.a1.dto.rag.*;
import wonit.hackathon.a1.repository.rag.ChunkSearchRepository;
import wonit.hackathon.a1.service.rag.ChunkSearchService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchTests {
    @Test void validatesBoundsAndDefaults() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertEquals(5, new SearchRequest("면책기간", null, null, null).topK());
            assertTrue(validator.validate(new SearchRequest("면책기간", null, null, null)).isEmpty());
            for (var request : List.of(new SearchRequest(" ", null, null, 5),
                    new SearchRequest("질문", List.of(-1L), null, 5),
                    new SearchRequest("질문", null, null, 0),
                    new SearchRequest("질문", null, null, 51),
                    new SearchRequest("가".repeat(2001), null, null, 5))) {
                assertFalse(validator.validate(request).isEmpty());
            }
        }
    }

    @Test void embedsQuestionAndPassesFiltersAndPropagatesFailure() {
        var embedding = mock(EmbeddingClient.class);
        var repository = mock(ChunkSearchRepository.class);
        var service = new ChunkSearchService(embedding, repository);
        var vector = new float[1536]; vector[0] = 1;
        var request = new SearchRequest(" 면책기간 ", List.of(1L), null, 5);
        when(embedding.embed(List.of("면책기간"))).thenReturn(Collections.singletonList(vector));
        when(repository.search(vector, request)).thenReturn(List.of());
        assertEquals(List.of(), service.search(request));
        var order = inOrder(embedding, repository);
        order.verify(embedding).embed(List.of("면책기간"));
        order.verify(repository).search(vector, request);
        reset(repository);
        when(embedding.embed(anyList())).thenThrow(new IllegalStateException("Embedding HTTP 429"));
        assertThrows(IllegalStateException.class, () -> service.search(request));
        verifyNoInteractions(repository);
    }
}
