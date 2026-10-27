package wonit.hackathon.a1.rag;

import java.util.*;
import org.junit.jupiter.api.Test;
import wonit.hackathon.a1.client.rag.ChatClient;
import wonit.hackathon.a1.dto.rag.*;
import wonit.hackathon.a1.service.rag.*;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AskTests {
    final ChunkSearchService search = mock(ChunkSearchService.class);
    final ChatClient chat = mock(ChatClient.class);
    final AskService service = new AskService(search, chat);
    final AskRequest request = new AskRequest("암 진단비 지급 조건", List.of(6L), 5);
    SearchResult chunk(long id, long product) {
        return new SearchResult(id, 2L, product, "약관.pdf", DocumentType.TERMS, "암 진단비", "암 특약", 12, 13, "원문", .8);
    }
    @Test void supplementsRestrictionsAndReturnsOnlyVerifiedSources() {
        when(search.search(any())).thenReturn(List.of(chunk(1, 6)), List.of(chunk(2, 6)));
        when(chat.generate(eq(request), anyList())).thenReturn(new ChatClient.Generated("지급 조건 및 감액 조건", List.of(2L, 1L, 2L)));
        var response = service.ask(request);
        assertEquals(List.of(2L, 1L), response.sources().stream().map(AskResponse.Source::chunkId).toList());
        verify(search).search(argThat(r -> r.question().contains("면책 감액") && r.question().contains("암 진단비")
                && r.productIds().equals(List.of(6L)) && r.documentTypes().equals(List.of(DocumentType.TERMS))));
    }
    @Test void unrestrictedSearchSupplementsEachProductWithinTotalBudget() {
        var unrestricted = new AskRequest("암 보장", null, 5);
        when(search.search(any())).thenReturn(List.of(chunk(1, 6), chunk(2, 7)),
                List.of(chunk(3, 6)), List.of(chunk(4, 7)));
        when(chat.generate(any(), anyList())).thenReturn(new ChatClient.Generated("상품별 답변", List.of(3L, 4L)));
        var response = service.ask(unrestricted);
        assertEquals(List.of(6L, 7L), response.sources().stream().map(AskResponse.Source::productId).toList());
        verify(search).search(argThat(r -> r.productIds() == null && r.documentTypes().size() == 2));
        verify(search).search(argThat(r -> List.of(6L).equals(r.productIds()) && r.topK() == 3));
        verify(search).search(argThat(r -> List.of(7L).equals(r.productIds()) && r.topK() == 2));
    }
    @Test void rejectsInventedSourceAndForeignProduct() {
        when(search.search(any())).thenReturn(List.of(chunk(1, 6)));
        when(chat.generate(any(), anyList())).thenReturn(new ChatClient.Generated("답변", List.of(999L)));
        assertThrows(IllegalStateException.class, () -> service.ask(request));
        reset(chat);
        when(search.search(any())).thenReturn(List.of(chunk(1, 7)));
        assertThrows(IllegalStateException.class, () -> service.ask(request));
        verifyNoInteractions(chat);
    }
    @Test void noEvidenceNeverCallsGptAndUncitedClaimsAreNotReturned() {
        when(search.search(any())).thenReturn(List.of());
        assertTrue(service.ask(request).sources().isEmpty());
        verifyNoInteractions(chat);
        when(search.search(any())).thenReturn(List.of(chunk(1, 6)));
        when(chat.generate(any(), anyList())).thenReturn(new ChatClient.Generated("근거 없는 주장", List.of()));
        assertTrue(service.ask(request).answer().contains("확인할 수 없습니다"));
    }
}
