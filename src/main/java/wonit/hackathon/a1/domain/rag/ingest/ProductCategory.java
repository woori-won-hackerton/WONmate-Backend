package wonit.hackathon.a1.domain.rag.ingest;

/** 상품명으로 대분류 추정 (프론트 챗봇의 상품 대분류와 같은 값) */
public final class ProductCategory {

    public static final String WHOLE_LIFE = "종신보험";
    public static final String CANCER = "암보험";
    public static final String CHILD = "어린이보험";
    public static final String OTHER = "기타";

    private ProductCategory() {
    }

    public static String infer(String productName) {
        if (productName == null) {
            return OTHER;
        }
        String name = productName.replaceAll("\\s+", "");
        if (name.contains("종신")) {
            return WHOLE_LIFE;
        }
        if (name.contains("암")) {
            return CANCER;
        }
        if (name.contains("어린이") || name.contains("아이")) {
            return CHILD;
        }
        return OTHER;
    }

    /** DB의 insurance_type이 대분류 값이면 그대로, 아니면 상품명으로 추정 */
    public static String of(String insuranceType, String productName) {
        if (WHOLE_LIFE.equals(insuranceType) || CANCER.equals(insuranceType) || CHILD.equals(insuranceType)) {
            return insuranceType;
        }
        return infer(productName);
    }
}