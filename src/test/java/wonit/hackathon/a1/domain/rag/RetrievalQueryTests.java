package wonit.hackathon.a1.domain.rag;

import org.junit.jupiter.api.Test;
import wonit.hackathon.a1.domain.rag.service.RetrievalQuery;
import static org.junit.jupiter.api.Assertions.*;

class RetrievalQueryTests {
    @Test void prioritizesDiagnosisPaymentAndRestrictionsWhilePreservingOtherRequirements() {
        String question = "이 상품의 암 진단비와 입원 보장의 지급 조건을 설명해줘";
        var query = RetrievalQuery.from(question);
        assertTrue(query.summary().startsWith("암 진단비"));
        assertTrue(query.terms().contains("지급기준표"));
        assertTrue(query.terms().contains("보험가입금액"));
        assertTrue(query.restrictions().contains("면책 감액 보장개시일"));
        assertTrue(query.restrictions().contains("지급 횟수 제한"));
        assertTrue(query.summary().contains("입원 보장"));
        assertTrue(query.terms().contains(question));
    }

    @Test void leavesGeneralTermsSearchIntact() {
        var query = RetrievalQuery.from("암보장개시일은 언제야?");
        assertEquals("암보장개시일은 언제야?", query.summary());
        assertEquals("암보장개시일은 언제야?", query.terms());
        assertTrue(query.restrictions().startsWith(query.terms()));
        // 예금보험도 질문 대상이 될 수 있으며 제목에 따른 금지 목록은 없다.
        assertEquals("예금보험 지급보장이란?", RetrievalQuery.from("예금보험 지급보장이란?").terms());
    }
}
