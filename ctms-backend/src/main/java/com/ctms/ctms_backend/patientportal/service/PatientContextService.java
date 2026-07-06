package com.ctms.ctms_backend.patientportal.service;

import com.ctms.ctms_backend.patientportal.exception.NoLinkedSubjectException;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The ONLY place a patient's identity is resolved -- always from the authenticated principal's
 * username, never from a client-supplied subject ID. Every controller in this package calls this
 * first; this is what makes row-scoping structurally impossible to bypass, rather than something
 * each endpoint has to remember to check. */
@Service
public class PatientContextService {

    private final SubjectRepository subjectRepository;

    public PatientContextService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
    }

    @Transactional(readOnly = true)
    public Subject resolveCurrentSubject(String username) {
        return subjectRepository.findByLinkedUser_Username(username).orElseThrow(() -> new NoLinkedSubjectException(username));
    }
}
