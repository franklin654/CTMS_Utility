package com.ctms.ctms_backend.site.dto;

import com.ctms.ctms_backend.site.entity.Site;
import java.time.Instant;
import java.time.LocalDate;

public record SiteResponse(
        Long id,
        Long studyId,
        String studyCode,
        String siteCode,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String stateProvince,
        String postalCode,
        String country,
        String principalInvestigatorName,
        String principalInvestigatorContact,
        String contactName,
        String contactEmail,
        String contactPhone,
        String feasibilityStatus,
        String regulatoryInformation,
        String status,
        LocalDate activationDate,
        String assignedCraUsername,
        String createdByUsername,
        String modifiedByUsername,
        Instant createdAt,
        Instant modifiedAt) {

    public static SiteResponse from(Site s) {
        return new SiteResponse(
                s.getId(),
                s.getStudy().getId(),
                s.getStudy().getStudyCode(),
                s.getSiteCode(),
                s.getName(),
                s.getAddressLine1(),
                s.getAddressLine2(),
                s.getCity(),
                s.getStateProvince(),
                s.getPostalCode(),
                s.getCountry(),
                s.getPrincipalInvestigatorName(),
                s.getPrincipalInvestigatorContact(),
                s.getContactName(),
                s.getContactEmail(),
                s.getContactPhone(),
                s.getFeasibilityStatus(),
                s.getRegulatoryInformation(),
                s.getStatus().name(),
                s.getActivationDate(),
                s.getAssignedCra() == null ? null : s.getAssignedCra().getUsername(),
                s.getCreatedBy().getUsername(),
                s.getModifiedBy().getUsername(),
                s.getCreatedAt(),
                s.getModifiedAt());
    }
}
