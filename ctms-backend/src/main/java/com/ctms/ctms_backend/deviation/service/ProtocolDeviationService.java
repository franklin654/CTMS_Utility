package com.ctms.ctms_backend.deviation.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.deviation.dto.ProtocolDeviationResponse;
import com.ctms.ctms_backend.deviation.dto.ReportProtocolDeviationRequest;
import com.ctms.ctms_backend.deviation.entity.DeviationSeverity;
import com.ctms.ctms_backend.deviation.entity.ProtocolDeviation;
import com.ctms.ctms_backend.deviation.exception.InvalidProtocolDeviationException;
import com.ctms.ctms_backend.deviation.repository.ProtocolDeviationRepository;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.subject.exception.SubjectNotFoundException;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL Epic 11 Story 01. Mirrors AdverseEventService.report/list exactly, minus the workflow --
 * this is a permanent, log-only record with no state machine. */
@Service
public class ProtocolDeviationService {

    private final ProtocolDeviationRepository protocolDeviationRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ProtocolDeviationService(
            ProtocolDeviationRepository protocolDeviationRepository,
            SubjectRepository subjectRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.protocolDeviationRepository = protocolDeviationRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ProtocolDeviationResponse report(ReportProtocolDeviationRequest req, String actorUsername) {
        Subject subject = subjectRepository.findById(req.subjectId())
                .orElseThrow(() -> new SubjectNotFoundException(req.subjectId()));
        DeviationSeverity severity = parseSeverity(req.severity());
        User actor = currentUser(actorUsername);

        ProtocolDeviation deviation = new ProtocolDeviation();
        deviation.setSubject(subject);
        deviation.setDescription(req.description());
        deviation.setSeverity(severity);
        deviation.setDeviationDate(req.deviationDate());
        deviation.setCreatedBy(actor);
        deviation.setModifiedBy(actor);
        deviation = protocolDeviationRepository.save(deviation);

        auditService.record(
                "ProtocolDeviation", String.valueOf(deviation.getId()), AuditAction.CREATE,
                null, severity + ": " + req.description() + " (subject " + subject.getSubjectCode() + ")", null);

        return ProtocolDeviationResponse.from(deviation);
    }

    @Transactional(readOnly = true)
    public List<ProtocolDeviationResponse> list(Long subjectId) {
        return protocolDeviationRepository.findBySubjectIdOrderByCreatedAtDesc(subjectId).stream()
                .map(ProtocolDeviationResponse::from)
                .toList();
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }

    private DeviationSeverity parseSeverity(String value) {
        try {
            return DeviationSeverity.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidProtocolDeviationException("Unknown severity: " + value);
        }
    }
}
