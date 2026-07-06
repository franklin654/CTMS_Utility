package com.ctms.ctms_backend.patientportal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.patientportal.exception.NoLinkedSubjectException;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientContextServiceTest {

    @Mock private SubjectRepository subjectRepository;

    @InjectMocks
    private PatientContextService service;

    @Test
    void resolveCurrentSubject_linked_returnsSubject() {
        Subject subject = new Subject();
        subject.setId(1L);
        when(subjectRepository.findByLinkedUser_Username("patient1")).thenReturn(Optional.of(subject));

        Subject result = service.resolveCurrentSubject("patient1");
        assertEquals(1L, result.getId());
    }

    @Test
    void resolveCurrentSubject_unlinked_throws() {
        when(subjectRepository.findByLinkedUser_Username("nobody")).thenReturn(Optional.empty());
        assertThrows(NoLinkedSubjectException.class, () -> service.resolveCurrentSubject("nobody"));
    }
}
