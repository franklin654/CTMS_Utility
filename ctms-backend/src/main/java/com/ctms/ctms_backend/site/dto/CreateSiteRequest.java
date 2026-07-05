package com.ctms.ctms_backend.site.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSiteRequest(
        @NotNull Long studyId,
        @NotBlank @Size(max = 30) String siteCode,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String stateProvince,
        @Size(max = 20) String postalCode,
        @NotBlank @Size(max = 100) String country,
        @NotBlank @Size(max = 255) String principalInvestigatorName,
        @NotBlank @Size(max = 255) String principalInvestigatorContact,
        @NotBlank @Size(max = 255) String contactName,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @NotBlank @Size(max = 50) String contactPhone,
        @NotBlank @Size(max = 50) String feasibilityStatus,
        @Size(max = 2000) String regulatoryInformation) {}
