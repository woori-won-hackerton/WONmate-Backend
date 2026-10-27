package wonit.hackathon.a1.domain.rag.service;

/** 질문을 AI에 보내기 전에 이메일 · 연락처 · 주민번호 · 고객 이름을 가림 */
public final class QuestionPrivacy {

    private QuestionPrivacy() {
    }

    public static String sanitize(String question) {
        return question
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[이메일 삭제]")
                .replaceAll("(?<![0-9])0?1[016789][- .]?[0-9]{3,4}[- .]?[0-9]{4}(?![0-9])", "[연락처 삭제]")
                .replaceAll("(?<![0-9])[0-9]{6}[- ]?[1-4][0-9]{6}(?![0-9])", "[주민번호 삭제]")
                .replaceAll("(이름|성명|고객명)\\s*[:：=]?\\s*[가-힣A-Za-z]{2,20}", "$1 [이름 삭제]")
                // '설계사님', '피보험자님' 같은 일반 호칭은 남기고 '홍길동 고객(님)', '홍길동 씨'만 가림
                .replaceAll("[가-힣]{2,4}\\s*(고객님|고객|씨)(?![가-힣])", "[이름 삭제] $1");
    }
}