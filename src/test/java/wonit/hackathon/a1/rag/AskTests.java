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
    final wonit.hackathon.a1.repository.rag.InsuranceProductRepository products = mock(wonit.hackathon.a1.repository.rag.InsuranceProductRepository.class);
    final AskService service = new AskService(search, chat, products);
    final AskRequest request = new AskRequest("암 진단비 지급 조건", QuestionType.PRODUCT_EXPLANATION, 6L, 5);
    SearchResult chunk(long id, long product) {
        return new SearchResult(id, 2L, product, "약관.pdf", DocumentType.TERMS, "암 진단비", "암 특약", 12, 13, "원문", .8);
    }
    @Test void supplementsRestrictionsAndReturnsOnlyVerifiedSources() {
        when(search.search(any())).thenReturn(List.of(chunk(1, 6)), List.of(chunk(2, 6)));
        when(chat.generate(eq(request), anyList(), anyMap())).thenReturn(new ChatClient.Generated("지급 조건 및 감액 조건", List.of(2, 1, 2)));
        var response = service.ask(request);
        assertEquals(List.of(2L, 1L), response.sources().stream().map(AskResponse.Source::chunkId).toList());
        verify(search).search(argThat(r -> r.question().contains("면책 감액") && r.question().contains("암 진단비")
                && r.productIds().equals(List.of(6L)) && r.documentTypes().equals(List.of(DocumentType.TERMS))));
    }
    @Test void selectedEvidenceUsesServerMetadataAndDoesNotReturnEveryRetrievedChunk() {
        var product = mock(wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct.class);
        when(product.getId()).thenReturn(6L);
        when(product.getProductName()).thenReturn("무배당우리WON하는암보험");
        when(products.findAllById(List.of(6L))).thenReturn(List.of(product));
        var selected = chunk(8027, 6);
        when(search.search(any())).thenReturn(List.of(chunk(101, 6), selected, chunk(303, 6)));
        when(chat.generate(any(), anyList(), anyMap()))
                .thenReturn(new ChatClient.Generated("암 보장 설명", List.of(2, 2)));
        var response = service.ask(request);
        assertEquals(List.of(AskResponse.Source.from(selected)), response.sources());
        verify(chat).generate(eq(request), argThat(chunks -> chunks.size() == 3),
                eq(Map.of(6L, "무배당우리WON하는암보험")));
    }
    @Test void logsIdentifierViolationsWithoutLoggingOrAlteringAnswer() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AskService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(search.search(any())).thenReturn(List.of(chunk(8027, 6)));
            for (String answer : List.of("productId 14 설명", "documentId: 22 설명", "chunkId=8027 설명")) {
                when(chat.generate(any(), anyList(), anyMap()))
                        .thenReturn(new ChatClient.Generated(answer, List.of(1)));
                assertEquals(answer, service.ask(request).answer());
            }
            assertEquals(3, appender.list.size());
            for (var event : appender.list) {
                assertEquals(ch.qos.logback.classic.Level.WARN, event.getLevel());
                assertFalse(event.getFormattedMessage().contains("8027"));
                assertFalse(event.getFormattedMessage().contains("암 진단비 지급 조건"));
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
    @Test void doesNotBlacklistMisleadingHeadingWhenBodyHasDiagnosisPaymentEvidence() {
        var mixed = new SearchResult(7258L, 21L, 6L, "약관.pdf", DocumentType.TERMS,
                "제45조 【예금보험에 의한 지급보장】", null, 51, 52,
                "예금보험 지급보장\n(별표1) 보험금 지급기준표\n암진단비 [기준: 보험가입금액 1,000만원]", .8);
        when(search.search(any())).thenReturn(List.of(mixed));
        when(chat.generate(any(), anyList(), anyMap())).thenReturn(new ChatClient.Generated("지급 예시 조건 확인", List.of(1)));
        assertEquals(7258L, service.ask(request).sources().getFirst().chunkId());
    }
    @Test void conceptExploresCatalogAndRedactsIdentifiersBeforeExternalCalls() {
        var product = mock(wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct.class);
        when(product.getId()).thenReturn(6L);
        when(products.findAll()).thenReturn(List.of(product));
        when(search.search(any())).thenReturn(List.of(chunk(1, 6)));
        when(chat.generate(any(), anyList(), anyMap())).thenReturn(new ChatClient.Generated("보험 개념 설명", List.of(1)));
        service.ask(new AskRequest("이름: 홍길동, 010-1234-5678, 52세 월 50000원 암 진단비와 입원 보장 개념 설명", QuestionType.INSURANCE_CONCEPT, null, 5));
        verify(products).findAll();
        verify(chat).generate(argThat(r -> !r.question().contains("홍길동") && !r.question().contains("010-1234")
                && r.question().contains("50000") && r.question().contains("52세")), anyList(), anyMap());
        verify(search, times(3)).search(argThat(r -> !r.question().contains("홍길동") && !r.question().contains("010-1234")));
    }
    @Test void rejectsInventedSourceAndForeignProduct() {
        when(search.search(any())).thenReturn(List.of(chunk(1, 6)));
        when(chat.generate(any(), anyList(), anyMap())).thenReturn(new ChatClient.Generated("답변", List.of(999)));
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
        when(chat.generate(any(), anyList(), anyMap())).thenReturn(new ChatClient.Generated("근거 없는 주장", List.of()));
        assertTrue(service.ask(request).answer().contains("확인할 수 없습니다"));
    }
}
