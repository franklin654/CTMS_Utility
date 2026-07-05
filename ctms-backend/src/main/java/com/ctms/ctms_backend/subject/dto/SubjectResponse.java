package com.ctms.ctms_backend.subject.dto;

import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.user.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public record SubjectResponse(
        Long id,
        String subjectCode,
        Long studyId,
        String studyCode,
        Long siteId,
        String siteCode,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String gender,
        String contactPhone,
        String contactEmail,
        String address,
        String emergencyContact,
        String notes,
        String medicalHistory,
        LocalDate screeningDate,
        String status,
        String createdByUsername,
        String modifiedByUsername,
        Instant createdAt,
        Instant modifiedAt) {

    /** Fields the BRD names as sensitive (Story 04 AC2: "Role-based access controls hide
     * sensitive fields (e.g., medical notes)") are visible only to these roles; nulled out for
     * everyone else, per your confirmed decision. */
    private static final Set<String> MEDICAL_HISTORY_VISIBLE_ROLES =
            Set.of(Role.STUDY_MANAGER, Role.SITE_COORDINATOR, Role.INVESTIGATOR, Role.ADMIN);

    public static SubjectResponse from(Subject s, Set<String> callerRoles) {
        boolean canSeeMedicalHistory = callerRoles.stream().anyMatch(MEDICAL_HISTORY_VISIBLE_ROLES::contains);
        return new SubjectResponse(
                s.getId(),
                s.getSubjectCode(),
                s.getStudy().getId(),
                s.getStudy().getStudyCode(),
                s.getSite().getId(),
                s.getSite().getSiteCode(),
                s.getFirstName(),
                s.getLastName(),
                s.getDateOfBirth(),
                s.getGender(),
                s.getContactPhone(),
                s.getContactEmail(),
                s.getAddress(),
                s.getEmergencyContact(),
                s.getNotes(),
                canSeeMedicalHistory ? s.getMedicalHistory() : null,
                s.getScreeningDate(),
                s.getStatus().name(),
                s.getCreatedBy().getUsername(),
                s.getModifiedBy().getUsername(),
                s.getCreatedAt(),
                s.getModifiedAt());
    }
}
