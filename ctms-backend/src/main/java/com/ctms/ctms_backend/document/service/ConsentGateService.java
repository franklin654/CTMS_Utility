package com.ctms.ctms_backend.document.service;

import com.ctms.ctms_backend.document.DocumentRepository;
import com.ctms.ctms_backend.document.entity.DocumentVersionStatus;
import com.ctms.ctms_backend.document.exception.MissingConsentException;
import com.ctms.ctms_backend.subject.entity.Subject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL Epic 11 Story 01 (GCP Compliance -- consent before subject activity). Reuses the existing
 * Document.category="INFORMED_CONSENT" convention (no new entity) plus a blocking check, mirroring
 * Phase 10's DocumentRequirementService pattern but scoped per-Subject rather than per-Study --
 * consent is inherently individual, not shared across a study's subjects.
 *
 * <p>Gated at Visit completion, not Subject enrollment: a Subject must exist before a
 * subject-linked Document can reference it, so the gate can't block enrollment itself without a
 * chicken-and-egg problem. A visit can be SCHEDULED (pure calendar bookkeeping) without consent on
 * file; actually COMPLETING it (the subject physically attending, procedures happening) is the
 * genuine "subject activity" this story means to gate. */
@Service
public class ConsentGateService {

    public static final String INFORMED_CONSENT_CATEGORY = "INFORMED_CONSENT";

    private final DocumentRepository documentRepository;

    public ConsentGateService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    public void assertConsentPresent(Subject subject) {
        boolean consented = documentRepository.existsBySubjectIdAndCategoryAndCurrentVersionStatus(
                subject.getId(), INFORMED_CONSENT_CATEGORY, DocumentVersionStatus.CURRENT);
        if (!consented) {
            throw new MissingConsentException(subject.getId());
        }
    }
}
