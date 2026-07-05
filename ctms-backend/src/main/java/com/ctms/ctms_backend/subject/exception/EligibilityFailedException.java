package com.ctms.ctms_backend.subject.exception;

import java.util.List;

/** Thrown by SubjectService.enrollSubject when the Drools ELIGIBILITY_DEFAULT rule set reports
 * one or more violations -- the Subject row is never created (BRD Story 01 AC4: "Enrollment
 * blocked if eligibility fails"). Carries the violation messages so the frontend can render a
 * clear blocking list, same structured-response pattern as Phase 3's SiteActivationBlockedException. */
public class EligibilityFailedException extends RuntimeException {

    private final List<String> violations;

    public EligibilityFailedException(List<String> violations) {
        super("Enrollment blocked -- eligibility criteria not met: " + String.join(", ", violations));
        this.violations = violations;
    }

    public List<String> getViolations() {
        return violations;
    }
}
