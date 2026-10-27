package wonit.hackathon.a1.service.rag;

import java.util.*;
import org.springframework.stereotype.Service;
import wonit.hackathon.a1.client.rag.ChatClient;
import wonit.hackathon.a1.dto.rag.*;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;

/** 요청별 검색·답변 생성만 수행한다. 대화 상태나 질문·답변을 DB에 저장하지 않는다. */
@Service
public class AskService {
    private final ChunkSearchService search;
    private final ChatClient chat;
    public AskService(ChunkSearchService search, ChatClient chat) { this.search = search; this.chat = chat; }

    public AskResponse ask(AskRequest request) {
        // 분류 입력 없이 두 문서 유형을 검색하고, 질문에 필요한 설명은 GPT가 판단한다.
        var types = List.of(DocumentType.TERMS, DocumentType.SUMMARY);
        var chunks = new LinkedHashMap<Long, SearchResult>();
        add(chunks, search.search(new SearchRequest(request.question(), request.productIds(), types, request.topK())), request);
        if (chunks.isEmpty()) return insufficient();
        // 원래 질문을 유지해 핵심 보장과 관련된 제한 조항을 찾는다.
        // 추가 검색에 면책 조항이 없어도 면책조건이 없음을 의미하지는 않는다.
        String restrictions = request.question().strip() + " 관련 보장 보험금을 지급하지 않는 사유 면책 감액 보장개시일 지급 제한";
        var products = chunks.values().stream().map(SearchResult::productId).distinct().toList();
        // 상품 필터가 없어도 다른 상품의 면책조건을 섞지 않도록 상품별로 약관을 검색한다.
        for (int i = 0; i < products.size(); i++) {
            var product = products.get(i);
            // 추가 검색 합계를 topK로 제한한다. 나머지는 기본 검색에서 먼저 나온 상품에 배분한다.
            int limit = request.topK() / products.size() + (i < request.topK() % products.size() ? 1 : 0);
            add(chunks, search.search(new SearchRequest(restrictions, List.of(product),
                    List.of(DocumentType.TERMS), limit)),
                    new AskRequest(request.question(), List.of(product), request.topK()));
        }
        var generated = chat.generate(request, List.copyOf(chunks.values()));
        // GPT가 만든 메타데이터를 신뢰하지 않고 실제 전달한 청크 ID만 출처로 허용한다.
        if (generated.chunkIds() == null || generated.chunkIds().stream().anyMatch(id -> !chunks.containsKey(id)))
            throw new IllegalStateException("Unverified RAG citation");
        // 출처 없는 생성 답변은 노출하지 않고 근거 부족 안내로 대체한다.
        if (generated.chunkIds().isEmpty()) return insufficient();
        return new AskResponse(generated.answer(), generated.chunkIds().stream().distinct()
                .map(id -> AskResponse.Source.from(chunks.get(id))).toList());
    }
    private void add(Map<Long, SearchResult> target, List<SearchResult> results, AskRequest request) {
        for (var c : results) {
            if (request.productIds() != null && !request.productIds().isEmpty()
                    && !request.productIds().contains(c.productId())) throw new IllegalStateException("RAG product scope mismatch");
            target.putIfAbsent(c.chunkId(), c);
        }
    }
    private AskResponse insufficient() {
        return new AskResponse("제공된 검색 근거만으로 질문의 내용을 확인할 수 없습니다. 해당 상품의 보장 및 면책·감액 조건을 원문에서 추가 확인해 주세요.", List.of());
    }
}
