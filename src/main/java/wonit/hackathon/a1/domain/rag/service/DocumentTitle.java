package wonit.hackathon.a1.domain.rag.service;

import wonit.hackathon.a1.domain.insurance.enums.DocumentType;

/** 화면에 보여줄 문서 이름 (파일명 대신) */
public final class DocumentTitle {

    private DocumentTitle() {
    }

    public static String of(String productName, DocumentType type) {
        String name = productName == null ? "보험상품" : productName;
        return name + (type == DocumentType.TERMS ? " 약관" : " 상품요약서");
    }
}