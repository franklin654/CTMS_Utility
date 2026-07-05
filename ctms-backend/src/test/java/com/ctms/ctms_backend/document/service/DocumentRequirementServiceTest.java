package com.ctms.ctms_backend.document.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.DocumentRepository;
import com.ctms.ctms_backend.document.dto.CreateDocumentRequirementRequest;
import com.ctms.ctms_backend.document.dto.DocumentRequirementResponse;
import com.ctms.ctms_backend.document.entity.DocumentRequirement;
import com.ctms.ctms_backend.document.entity.DocumentVersionStatus;
import com.ctms.ctms_backend.document.repository.DocumentRequirementRepository;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.entity.StudyStatus;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentRequirementServiceTest {

    @Mock private DocumentRequirementRepository documentRequirementRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private StudyRepository studyRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private DocumentRequirementService service;

    private Study study;

    @BeforeEach
    void setUp() {
        study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");
        lenient().when(studyRepository.findById(10L)).thenReturn(Optional.of(study));

        User actor = new User();
        actor.setId(1L);
        actor.setUsername("admin1");
        lenient().when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(actor));

        lenient().when(documentRequirementRepository.save(any(DocumentRequirement.class))).thenAnswer(inv -> {
            DocumentRequirement r = inv.getArgument(0);
            r.setId(500L);
            return r;
        });
    }

    @Test
    void create_savesAndAudits() {
        CreateDocumentRequirementRequest req = new CreateDocumentRequirementRequest(10L, "ACTIVE", "REGULATORY_APPROVAL", true);
        DocumentRequirementResponse response = service.create(req, "admin1");

        assertEquals("ACTIVE", response.studyPhase());
        assertEquals("REGULATORY_APPROVAL", response.documentCategory());
    }

    @Test
    void checkRequirementsMet_allSatisfied_returnsEmpty() {
        DocumentRequirement req1 = requirement("REGULATORY_APPROVAL", true);
        when(documentRequirementRepository.findByStudyIdAndStudyPhase(10L, StudyStatus.ACTIVE)).thenReturn(List.of(req1));
        when(documentRepository.existsByStudyIdAndCategoryAndCurrentVersionStatus(10L, "REGULATORY_APPROVAL", DocumentVersionStatus.CURRENT))
                .thenReturn(true);

        List<String> missing = service.checkRequirementsMet(study, StudyStatus.ACTIVE);
        assertTrue(missing.isEmpty());
    }

    @Test
    void checkRequirementsMet_missingMandatoryCategory_returnsIt() {
        DocumentRequirement req1 = requirement("REGULATORY_APPROVAL", true);
        when(documentRequirementRepository.findByStudyIdAndStudyPhase(10L, StudyStatus.ACTIVE)).thenReturn(List.of(req1));
        when(documentRepository.existsByStudyIdAndCategoryAndCurrentVersionStatus(10L, "REGULATORY_APPROVAL", DocumentVersionStatus.CURRENT))
                .thenReturn(false);

        List<String> missing = service.checkRequirementsMet(study, StudyStatus.ACTIVE);
        assertEquals(List.of("REGULATORY_APPROVAL"), missing);
    }

    @Test
    void checkRequirementsMet_nonMandatoryCategoryIgnoredEvenIfMissing() {
        DocumentRequirement req1 = requirement("OPTIONAL_ADDENDUM", false);
        when(documentRequirementRepository.findByStudyIdAndStudyPhase(10L, StudyStatus.ACTIVE)).thenReturn(List.of(req1));

        List<String> missing = service.checkRequirementsMet(study, StudyStatus.ACTIVE);
        assertTrue(missing.isEmpty());
    }

    @Test
    void checkRequirementsMet_mixedSatisfiedAndMissing() {
        DocumentRequirement satisfied = requirement("REGULATORY_APPROVAL", true);
        DocumentRequirement missingReq = requirement("SITE_AGREEMENT", true);
        when(documentRequirementRepository.findByStudyIdAndStudyPhase(10L, StudyStatus.ACTIVE))
                .thenReturn(List.of(satisfied, missingReq));
        when(documentRepository.existsByStudyIdAndCategoryAndCurrentVersionStatus(10L, "REGULATORY_APPROVAL", DocumentVersionStatus.CURRENT))
                .thenReturn(true);
        when(documentRepository.existsByStudyIdAndCategoryAndCurrentVersionStatus(10L, "SITE_AGREEMENT", DocumentVersionStatus.CURRENT))
                .thenReturn(false);

        List<String> missing = service.checkRequirementsMet(study, StudyStatus.ACTIVE);
        assertEquals(List.of("SITE_AGREEMENT"), missing);
    }

    private DocumentRequirement requirement(String category, boolean mandatory) {
        DocumentRequirement r = new DocumentRequirement();
        r.setStudy(study);
        r.setStudyPhase(StudyStatus.ACTIVE);
        r.setDocumentCategory(category);
        r.setMandatory(mandatory);
        return r;
    }
}
