package wonit.hackathon.a1.domain.rag.service;

import java.util.LinkedHashSet;
import java.util.regex.Pattern;

/** 고객 나이·예산은 답변 입력에 유지하고, 명시된 진단 보장은 검색의 중심으로 삼는다. */
public record RetrievalQuery(String summary, String terms, String restrictions) {
    private static final Pattern DIAGNOSIS = Pattern.compile("([가-힣A-Za-z]{1,15})\\s*(진단비|진단금)");

    public static RetrievalQuery from(String question) {
        var matcher = DIAGNOSIS.matcher(question);
        var coverages = new LinkedHashSet<String>();
        while (matcher.find()) coverages.add(matcher.group(1) + " " + matcher.group(2));
        if (coverages.isEmpty()) {
            return new RetrievalQuery(question, question, question.strip()
                    + " 관련 보장 보험금을 지급하지 않는 사유 면책 감액 보장개시일 지급 제한");
        }
        // 복합 질문의 입원 보장 등 다른 요구사항도 잃지 않도록 원래 질문을 보존한다.
        String focus = String.join(" ", coverages) + " " + question.strip();
        return new RetrievalQuery(focus + " 주요 보장 보험금 지급기준",
                focus + " 보험금 지급기준표 지급사유 보험가입금액",
                focus + " 보험금을 지급하지 않는 사유 면책 감액 보장개시일 지급 횟수 제한");
    }
}
