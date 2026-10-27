package wonit.hackathon.a1.domain.rag.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.rag.client.RagAnswerClient;
import wonit.hackathon.a1.domain.rag.dto.AskRequest;
import wonit.hackathon.a1.domain.rag.dto.AskResponse;
import wonit.hackathon.a1.domain.rag.dto.ProductResponse;
import wonit.hackathon.a1.domain.rag.dto.QuestionType;
import wonit.hackathon.a1.domain.rag.dto.SearchResult;

/**
 * AI 챗봇 (RAG)
 * 1. 질문에서 개인정보를 가림
 * 2. 상품요약서 · 약관(지급 기준) · 약관(제한 조건)을 각각 벡터 검색
 * - 상품 설명: 고른 상품만
 * - 용어 · 개념 설명: 질문과 가장 관련 높은 상품 최대 3개
 * 3. 검색된 원문으로 AI 답변 + 근거 번호 → 서버가 출처(문서 · 페이지)를 붙임
 * 질문과 답변은 DB에 저장하지 않음
 */
@Slf4j
@Service
public class AskService {

    /** 용어 · 개념 질문에서 근거를 찾을 상품 수 */
    private static final int MAX_EVIDENCE_PRODUCTS = 3;

    /** 용어 · 개념 질문의 검색 1회당 문단 수 (상품이 여러 개라 줄임) */
    private static final int GENERAL_TOP_K = 3;

    private static final String INSUFFICIENT = "검색된 약관 · 상품요약서만으로는 질문 내용을 확인할 수 없어요. "
            + "질문을 조금 더 구체적으로 입력하거나, 해당 상품의 약관 원문을 직접 확인해 주세요.";

    private static final String NO_DOCUMENTS = "아직 챗봇이 참고할 보험 문서가 준비되지 않았어요. 관리자에게 문서 적재를 요청해 주세요.";

    private final ChunkSearchService searchService;
    private final RagAnswerClient answerClient;
    private final RagCatalogService catalogService;

    public AskService(
            ChunkSearchService searchService,
            RagAnswerClient answerClient,
            RagCatalogService catalogService) {
        this.searchService = searchService;
        this.answerClient = answerClient;
        this.catalogService = catalogService;
    }

    public AskResponse ask(AskRequest request) {
        List<ProductResponse> products = catalogService.getProducts();
        if (products.isEmpty()) {
            return new AskResponse(NO_DOCUMENTS, List.of());
        }

        boolean explicit = request.questionType() == QuestionType.PRODUCT_EXPLANATION;
        if (explicit && products.stream().noneMatch(p -> p.productId().equals(request.productId()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "선택한 상품의 문서가 아직 준비되지 않았어요.");
        }

        String question = QuestionPrivacy.sanitize(request.question());

        List<SearchResult> evidence;
        try {
            evidence = retrieve(question, request, products, explicit);
        } catch (RuntimeException e) {
            log.error("RAG retrieval failed: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "문서를 검색하지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
        if (evidence.isEmpty()) {
            return new AskResponse(INSUFFICIENT, List.of());
        }

        RagAnswerClient.Generated generated;
        try {
            generated = answerClient.generate(question, request.questionType(), evidence);
        } catch (RuntimeException e) {
            log.error("RAG answer failed: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 답변을 만들지 못했어요. 잠시 후 다시 시도해 주세요.");
        }

        // 근거를 하나도 고르지 않은 답변은 보여주지 않음 (근거 없는 답변 방지)
        if (generated.evidenceIndexes().isEmpty()) {
            return new AskResponse(INSUFFICIENT, List.of());
        }

        List<AskResponse.Source> sources = new ArrayList<>();
        List<Integer> indexes = generated.evidenceIndexes().stream().distinct().toList();
        for (int i = 0; i < indexes.size(); i++) {
            SearchResult c = evidence.get(indexes.get(i) - 1);
            sources.add(new AskResponse.Source(
                    c.productId(),
                    c.productName(),
                    c.documentId(),
                    DocumentTitle.of(c.productName(), c.documentType()),
                    c.documentType(),
                    c.chunkId(),
                    c.sectionTitle(),
                    c.startPage(),
                    c.endPage()));
        }
        return new AskResponse(generated.answer(), sources);
    }

    private List<SearchResult> retrieve(
            String question,
            AskRequest request,
            List<ProductResponse> products,
            boolean explicit) {
        RetrievalQuery query = RetrievalQuery.from(question);
        ChunkSearchService.Searcher searcher = searchService.newSearcher();
        int topK = explicit ? request.topK() : Math.min(request.topK(), GENERAL_TOP_K);

        List<Long> candidates = explicit
                ? List.of(request.productId())
                : products.stream().map(ProductResponse::productId).toList();

        // 상품별 요약서 · 약관(지급 기준) 검색
        Map<Long, List<SearchResult>> summaries = new HashMap<>();
        Map<Long, List<SearchResult>> terms = new HashMap<>();
        Map<Long, Double> scores = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            Long id = candidates.get(i);
            List<SearchResult> s = searcher.search(query.summary(), id, DocumentType.SUMMARY, topK);
            List<SearchResult> t = searcher.search(query.terms(), id, DocumentType.TERMS, topK);
            summaries.put(id, s);
            terms.put(id, t);
            scores.put(id, bestScore(s, t));
        }

        // 용어 · 개념 질문은 관련도 높은 상품만 근거로 사용
        List<Long> evidenceProducts = explicit
                ? candidates
                : candidates.stream()
                        .filter(id -> scores.get(id) > Double.NEGATIVE_INFINITY)
                        .sorted(Comparator.comparingDouble((Long id) -> scores.get(id)).reversed())
                        .limit(MAX_EVIDENCE_PRODUCTS)
                        .toList();

        Map<Long, SearchResult> chunks = new LinkedHashMap<>();
        for (int i = 0; i < evidenceProducts.size(); i++) {
            Long id = evidenceProducts.get(i);
            addAll(chunks, summaries.get(id));
            addAll(chunks, terms.get(id));
            addAll(chunks, searcher.search(query.restrictions(), id, DocumentType.TERMS, topK));
        }
        return List.copyOf(chunks.values());
    }

    private static double bestScore(List<SearchResult> a, List<SearchResult> b) {
        double best = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < a.size(); i++) {
            best = Math.max(best, a.get(i).similarityScore());
        }
        for (int i = 0; i < b.size(); i++) {
            best = Math.max(best, b.get(i).similarityScore());
        }
        return best;
    }

    private static void addAll(Map<Long, SearchResult> target, List<SearchResult> results) {
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            target.putIfAbsent(r.chunkId(), r);
        }
    }
}