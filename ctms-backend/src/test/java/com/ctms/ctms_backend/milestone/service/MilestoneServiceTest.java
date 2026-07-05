package com.ctms.ctms_backend.milestone.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.milestone.dto.CreateMilestoneRequest;
import com.ctms.ctms_backend.milestone.dto.MilestoneResponse;
import com.ctms.ctms_backend.milestone.dto.RecordMilestoneActualRequest;
import com.ctms.ctms_backend.milestone.dto.UpdateMilestonePlannedDateRequest;
import com.ctms.ctms_backend.milestone.entity.Milestone;
import com.ctms.ctms_backend.milestone.entity.MilestoneType;
import com.ctms.ctms_backend.milestone.exception.DuplicateMilestoneTypeException;
import com.ctms.ctms_backend.milestone.exception.InvalidMilestoneActualDateException;
import com.ctms.ctms_backend.milestone.repository.MilestoneRepository;
import com.ctms.ctms_backend.payment.service.PaymentService;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

    @Mock private MilestoneRepository milestoneRepository;
    @Mock private StudyRepository studyRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private PaymentService paymentService;

    @InjectMocks
    private MilestoneService milestoneService;

    private Study study;
    private User actor;

    @BeforeEach
    void setUp() {
        study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");

        actor = new User();
        actor.setId(1L);
        actor.setUsername("study.manager");

        lenient().when(studyRepository.findById(10L)).thenReturn(Optional.of(study));
        lenient().when(userRepository.findByUsername("study.manager")).thenReturn(Optional.of(actor));
        lenient().when(milestoneRepository.save(any(Milestone.class))).thenAnswer(inv -> {
            Milestone m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(400L);
            }
            return m;
        });
    }

    @Test
    void create_happyPath_savesAndAudits() {
        CreateMilestoneRequest req = new CreateMilestoneRequest(10L, "FPI", LocalDate.of(2026, 3, 1));
        MilestoneResponse response = milestoneService.create(req, "study.manager");

        assertEquals("FPI", response.milestoneType());
        assertEquals(LocalDate.of(2026, 3, 1), response.plannedDate());
    }

    @Test
    void create_duplicateType_throws() {
        lenient().when(milestoneRepository.existsByStudyIdAndMilestoneType(10L, MilestoneType.FPI)).thenReturn(true);
        CreateMilestoneRequest req = new CreateMilestoneRequest(10L, "FPI", LocalDate.of(2026, 3, 1));
        assertThrows(DuplicateMilestoneTypeException.class, () -> milestoneService.create(req, "study.manager"));
    }

    @Test
    void recordActual_alreadyRecorded_throws() {
        Milestone milestone = new Milestone();
        milestone.setId(400L);
        milestone.setStudy(study);
        milestone.setMilestoneType(MilestoneType.FPI);
        milestone.setPlannedDate(LocalDate.of(2026, 3, 1));
        milestone.setActualDate(LocalDate.of(2026, 3, 2));
        milestone.setCreatedBy(actor);
        lenient().when(milestoneRepository.findById(400L)).thenReturn(Optional.of(milestone));

        RecordMilestoneActualRequest req = new RecordMilestoneActualRequest(LocalDate.of(2026, 3, 5));
        assertThrows(InvalidMilestoneActualDateException.class, () -> milestoneService.recordActual(400L, req, "study.manager"));
    }

    @Test
    void recordActual_notYetRecorded_succeeds() {
        Milestone milestone = new Milestone();
        milestone.setId(400L);
        milestone.setStudy(study);
        milestone.setMilestoneType(MilestoneType.FPI);
        milestone.setPlannedDate(LocalDate.of(2026, 3, 1));
        milestone.setCreatedBy(actor);
        lenient().when(milestoneRepository.findById(400L)).thenReturn(Optional.of(milestone));

        RecordMilestoneActualRequest req = new RecordMilestoneActualRequest(LocalDate.of(2026, 3, 5));
        MilestoneResponse response = milestoneService.recordActual(400L, req, "study.manager");

        assertEquals(LocalDate.of(2026, 3, 5), response.actualDate());
        assertEquals(true, response.delayed());
    }

    @Test
    void updatePlannedDate_afterActualRecorded_throws() {
        Milestone milestone = new Milestone();
        milestone.setId(400L);
        milestone.setStudy(study);
        milestone.setMilestoneType(MilestoneType.FPI);
        milestone.setPlannedDate(LocalDate.of(2026, 3, 1));
        milestone.setActualDate(LocalDate.of(2026, 3, 2));
        milestone.setCreatedBy(actor);
        lenient().when(milestoneRepository.findById(400L)).thenReturn(Optional.of(milestone));

        UpdateMilestonePlannedDateRequest req = new UpdateMilestonePlannedDateRequest(LocalDate.of(2026, 4, 1));
        assertThrows(InvalidMilestoneActualDateException.class, () -> milestoneService.updatePlannedDate(400L, req, "study.manager"));
    }
}
