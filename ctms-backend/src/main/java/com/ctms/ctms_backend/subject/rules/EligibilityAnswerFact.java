package com.ctms.ctms_backend.subject.rules;

/** Plain Drools fact (not a JPA entity) -- one per submitted eligibility answer, inserted into
 * the "ELIGIBILITY_DEFAULT" rule set's session by SubjectService.enrollSubject. Getters must
 * match the field names referenced in the seeded DRL (V6 migration). */
public class EligibilityAnswerFact {

    private final String criterionType;
    private final String label;
    private final boolean met;

    public EligibilityAnswerFact(String criterionType, String label, boolean met) {
        this.criterionType = criterionType;
        this.label = label;
        this.met = met;
    }

    public String getCriterionType() {
        return criterionType;
    }

    public String getLabel() {
        return label;
    }

    public boolean getMet() {
        return met;
    }
}
