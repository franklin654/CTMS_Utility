package com.ctms.ctms_backend.deviation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.deviation.dto.ProtocolDeviationResponse;
import com.ctms.ctms_backend.deviation.dto.ReportProtocolDeviationRequest;
import com.ctms.ctms_backend.deviation.entity.ProtocolDeviation;
import com.ctms.ctms_backend.deviation.exception.InvalidProtocolDeviationException;
import com.ctms.ctms_backend.deviation.repository.ProtocolDeviationRepository;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.exception.SubjectNotFoundException;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProtocolDeviationServiceTest {

    @Mock private ProtocolDeviationRepository protocolDeviationRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private ProtocolDeviationService service;

    private Subject subject;

    @BeforeEach
    void setUp() {
        subject = new Subject();
        subject.setId(1000L);
        subject.setSubjectCode("SUBJ-001000");
        lenient().when(subjectRepository.findById(1000L)).thenReturn(Optional.of(subject));

        User actor = new User();
        actor.setId(1L);
        actor.setUsername("coordinator1");
        lenient().when(userRepository.findByUsername("coordinator1")).thenReturn(Optional.of(actor));

        lenient().when(protocolDeviationRepository.save(ArgumentMatchers.any(ProtocolDeviation.class))).thenAnswer(inv -> {
            ProtocolDeviation d = inv.getArgument(0);
            d.setId(500L);
            return d;
        });
    }

    @Test
    void report_savesAndAudits() {
        ReportProtocolDeviationRequest req =
                new ReportProtocolDeviationRequest(1000L, "Missed visit window", "MINOR", LocalDate.now());
        ProtocolDeviationResponse response = service.report(req, "coordinator1");

        assertEquals("MINOR", response.severity());
        assertEquals("Missed visit window", response.description());
        assertEquals("SUBJ-001000", response.subjectCode());
    }

    @Test
    void report_unknownSubject_throws() {
        when(subjectRepository.findById(999L)).thenReturn(Optional.empty());
        ReportProtocolDeviationRequest req = new ReportProtocolDeviationRequest(999L, "desc", "MINOR", LocalDate.now());
        assertThrows(SubjectNotFoundException.class, () -> service.report(req, "coordinator1"));
    }

    @Test
    void report_invalidSeverity_throws() {
        ReportProtocolDeviationRequest req = new ReportProtocolDeviationRequest(1000L, "desc", "BOGUS", LocalDate.now());
        assertThrows(InvalidProtocolDeviationException.class, () -> service.report(req, "coordinator1"));
    }

    @Test
    void list_returnsBySubject() {
        ProtocolDeviation d = new ProtocolDeviation();
        d.setId(1L);
        d.setSubject(subject);
        d.setDescription("desc");
        d.setSeverity(com.ctms.ctms_backend.deviation.entity.DeviationSeverity.MAJOR);
        d.setDeviationDate(LocalDate.now());
        d.setCreatedBy(subject.getCreatedBy());

        User actor = new User();
        actor.setId(1L);
        actor.setUsername("coordinator1");
        d.setCreatedBy(actor);

        when(protocolDeviationRepository.findBySubjectIdOrderByCreatedAtDesc(1000L)).thenReturn(List.of(d));

        List<ProtocolDeviationResponse> result = service.list(1000L);
        assertEquals(1, result.size());
        assertEquals("MAJOR", result.get(0).severity());
    }
}
