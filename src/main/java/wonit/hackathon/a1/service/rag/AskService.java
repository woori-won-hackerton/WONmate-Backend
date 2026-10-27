package wonit.hackathon.a1.service.rag;

import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import wonit.hackathon.a1.client.rag.ChatClient;
import wonit.hackathon.a1.dto.rag.*;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;

/** 요청별 검색·답변 생성만 수행한다. 대화 상태나 질문·답변을 DB에 저장하지 않는다. */
@Service
public class AskService {
    private static final Logger log = LoggerFactory.getLogger(AskService.class);
    private static final Pattern INTERNAL_ID = Pattern.compile(
            "(?i)\\b(?:product|document|chunk)[_\\s-]*id\\b");
    private final ChunkSearchService search;
    private final ChatClient chat;
    private final wonit.hackathon.a1.repository.rag.InsuranceProductRepository products;
    public AskService(ChunkSearchService search, ChatClient chat,
            wonit.hackathon.a1.repository.rag.InsuranceProductRepository products) {
        this.search = search; this.chat = chat; this.products = products;
    }

    public AskResponse ask(AskRequest input) {
        if (input.questionType() == null || !input.isProductSelectionValid())
            throw new IllegalArgumentException("Invalid question type or product selection");
        var request = new AskRequest(QuestionPrivacy.sanitize(input.question()), input.questionType(), input.productId(), input.topK());
        var chunks = new LinkedHashMap<Long, SearchResult>();
        boolean explicit = request.questionType() == QuestionType.PRODUCT_EXPLANATION;
        var catalog = explicit ? products.findAllById(List.of(request.productId())) : products.findAll();
        var productNames = new HashMap<Long, String>();
        catalog.forEach(p -> {
            if (p.getProductName() != null && !p.getProductName().isBlank())
                productNames.put(p.getId(), p.getProductName());
        });
        var ids = explicit ? List.of(request.productId())
                : catalog.stream().map(p -> p.getId()).sorted().toList();
        // 비교 대상 상품명이 명시되면 요약서 유사도 순위로 대상이 탈락하지 않게 한다.
        var named = request.questionType() == QuestionType.PRODUCT_COMPARISON
                ? ids.stream().filter(id -> mentionsProduct(request.question(), productNames.get(id))).toList()
                : List.<Long>of();
        if (!named.isEmpty()) ids = named;
        var query = RetrievalQuery.from(request.question());
        var summaries = new LinkedHashMap<Long, List<SearchResult>>();
        for (var id : ids) {
            var found = search.search(new SearchRequest(query.summary(), List.of(id),
                    List.of(DocumentType.SUMMARY), request.topK()));
            add(new LinkedHashMap<>(), found, id);
            summaries.put(id, found);
        }
        // ID 생략 시 요약서 관련도로 최대 5개 후보를 선정한다. 지정 상품은 모두 확인한다.
        var candidates = explicit || !named.isEmpty() ? ids : ids.stream().filter(id -> !summaries.get(id).isEmpty())
                .sorted(Comparator.<Long>comparingDouble(id -> summaries.get(id).stream()
                        .mapToDouble(SearchResult::similarityScore).max().orElse(-1)).reversed())
                .limit(5).toList();
        for (var id : candidates) {
            add(chunks, summaries.get(id), id);
            add(chunks, search.search(new SearchRequest(query.terms(), List.of(id),
                    List.of(DocumentType.TERMS), request.topK())), id);
            add(chunks, search.search(new SearchRequest(query.restrictions(), List.of(id),
                    List.of(DocumentType.TERMS), request.topK())), id);
        }
        if (chunks.isEmpty()) return insufficient();
        var evidence = List.copyOf(chunks.values());
        var generated = chat.generate(request, evidence, Map.copyOf(productNames));
        // 원문이나 고객정보를 로그에 남기지 않는다. 답변은 정규식으로 삭제·변형하지 않는다.
        if (INTERNAL_ID.matcher(generated.answer()).find())
            log.warn("RAG answer contains internal identifier notation; questionType={}", request.questionType());
        // LLM의 요청 내 순번만 검증한다. DB 메타데이터를 LLM으로부터 받지 않는다.
        if (generated.evidenceIndexes() == null || generated.evidenceIndexes().stream()
                .anyMatch(index -> index == null || index < 1 || index > evidence.size()))
            throw new IllegalStateException("Unverified RAG evidence index");
        // 출처 없는 생성 답변은 노출하지 않고 근거 부족 안내로 대체한다.
        if (generated.evidenceIndexes().isEmpty()) return insufficient();
        // 답변 관련 근거로 선택된 검색 결과다. 문장별 실제 인용 관계를 보장하지 않는다.
        return new AskResponse(generated.answer(), generated.evidenceIndexes().stream().distinct()
                .map(index -> AskResponse.Source.from(evidence.get(index - 1))).toList());
    }
    private boolean mentionsProduct(String question, String productName) {
        if (productName == null) return false;
        String normalized = productName.toLowerCase(Locale.ROOT).replaceAll("[^가-힣a-z0-9]", "");
        String alias = normalized.replaceFirst("^(무배당)?우리won하는", "").replaceFirst("^무배당", "");
        String text = question.toLowerCase(Locale.ROOT).replaceAll("[^가-힣a-z0-9]", "");
        return !normalized.isBlank() && (text.contains(normalized) || alias.length() >= 6 && text.contains(alias));
    }
    private void add(Map<Long, SearchResult> target, List<SearchResult> results, Long productId) {
        for (var c : results) {
            if (!productId.equals(c.productId())) throw new IllegalStateException("RAG product scope mismatch");
            target.putIfAbsent(c.chunkId(), c);
        }
    }
    private AskResponse insufficient() {
        return new AskResponse("제공된 검색 근거만으로 질문의 내용을 확인할 수 없습니다. 해당 상품의 보장 및 면책·감액 조건을 원문에서 추가 확인해 주세요.", List.of());
    }
}
