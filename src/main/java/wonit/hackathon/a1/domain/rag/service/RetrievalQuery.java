package wonit.hackathon.a1.domain.rag.service;

import java.util.LinkedHashSet;
import java.util.regex.Pattern;

/**
 * 질문 → 검색어 3종
 * - summary: 상품요약서에서 찾을 내용
 * - terms: 약관의 지급 기준
 * - restrictions: 약관의 면책 · 감액 · 제한 조건
 * 질문에 'OO 진단비'가 있으면 그 보장을 검색의 중심으로 삼음
 */
public record RetrievalQuery(String summary, String terms, String restrictions) {

    private static final Pattern DIAGNOSIS = Pattern.compile("([가-힣A-Za-z]{1,15})\\s*(진단비|진단금)");

    public static RetrievalQuery from(String question) {
        var matcher = DIAGNOSIS.matcher(question);
        var coverages = new LinkedHashSet<String>();
        while (matcher.find()) {
            coverages.add(matcher.group(1) + " " + matcher.group(2));
        }
        String q = question.strip();
        if (coverages.isEmpty()) {
            return new RetrievalQuery(q, q, q + " 관련 보장 보험금을 지급하지 않는 사유 면책 감액 보장개시일 지급 제한");
        }
        // 복합 질문의 다른 요구사항도 잃지 않도록 원래 질문을 함께 넣음
        String focus = String.join(" ", coverages) + " " + q;
        return new RetrievalQuery(
                focus + " 주요 보장 보험금 지급기준",
                focus + " 보험금 지급기준표 지급사유 보험가입금액",
                focus + " 보험금을 지급하지 않는 사유 면책 감액 보장개시일 지급 횟수 제한");
    }
}