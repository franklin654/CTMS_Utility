package com.ctms.ctms_backend.document.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.document.DocumentRepository;
import com.ctms.ctms_backend.document.entity.DocumentVersionStatus;
import com.ctms.ctms_backend.document.exception.MissingConsentException;
import com.ctms.ctms_backend.subject.entity.Subject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsentGateServiceTest {

    @Mock private DocumentRepository documentRepository;

    @InjectMocks
    private ConsentGateService consentGateService;

    @Test
    void assertConsentPresent_consented_doesNotThrow() {
        Subject subject = new Subject();
        subject.setId(1000L);
        when(documentRepository.existsBySubjectIdAndCategoryAndCurrentVersionStatus(
                        1000L, ConsentGateService.INFORMED_CONSENT_CATEGORY, DocumentVersionStatus.CURRENT))
                .thenReturn(true);

        assertDoesNotThrow(() -> consentGateService.assertConsentPresent(subject));
    }

    @Test
    void assertConsentPresent_notConsented_throws() {
        Subject subject = new Subject();
        subject.setId(1000L);
        when(documentRepository.existsBySubjectIdAndCategoryAndCurrentVersionStatus(
                        1000L, ConsentGateService.INFORMED_CONSENT_CATEGORY, DocumentVersionStatus.CURRENT))
                .thenReturn(false);

        assertThrows(MissingConsentException.class, () -> consentGateService.assertConsentPresent(subject));
    }

    @Test
    void assertConsentPresent_isPerSubject_secondSubjectStillBlockedEvenIfFirstConsented() {
        Subject subjectA = new Subject();
        subjectA.setId(1L);
        Subject subjectB = new Subject();
        subjectB.setId(2L);

        when(documentRepository.existsBySubjectIdAndCategoryAndCurrentVersionStatus(
                        1L, ConsentGateService.INFORMED_CONSENT_CATEGORY, DocumentVersionStatus.CURRENT))
                .thenReturn(true);
        when(documentRepository.existsBySubjectIdAndCategoryAndCurrentVersionStatus(
                        2L, ConsentGateService.INFORMED_CONSENT_CATEGORY, DocumentVersionStatus.CURRENT))
                .thenReturn(false);

        assertDoesNotThrow(() -> consentGateService.assertConsentPresent(subjectA));
        assertThrows(MissingConsentException.class, () -> consentGateService.assertConsentPresent(subjectB));
    }
}
