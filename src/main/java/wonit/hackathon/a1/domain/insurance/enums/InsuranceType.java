package wonit.hackathon.a1.domain.insurance.enums;

public enum InsuranceType {
    CANCER("암보험"), WHOLE_LIFE("종신보험"), CHILD("어린이보험"), UNKNOWN("미분류");

    private final String label;

    InsuranceType(String label) { this.label = label; }

    public String getLabel() { return label; }
}
