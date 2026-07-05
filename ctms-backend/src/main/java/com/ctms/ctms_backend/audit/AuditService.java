package com.ctms.ctms_backend.audit;

import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central write path for {@link AuditLog}. Every state-changing feature (study lifecycle, document
 * approvals, subject/visit updates, etc. in later phases) should call this directly for precise
 * before/after diffs; {@link AuditAspect} covers the simpler "log this method call" case via
 * {@link Audited}.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    /** Runs in its own transaction so an audit write always survives even if the caller's
     * transaction later rolls back for an unrelated reason. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String entityName, String entityId, String action, String beforeValue, String afterValue, String reason) {
        AuditLog log = new AuditLog();
        log.setEntityName(entityName);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setBeforeValue(beforeValue);
        log.setAfterValue(afterValue);
        log.setReason(reason);
        currentUsername().flatMap(userRepository::findByUsername).ifPresent(log::setPerformedBy);
        auditLogRepository.save(log);
    }

    public void record(String entityName, String entityId, String action, String afterValue) {
        record(entityName, entityId, action, null, afterValue, null);
    }

    private Optional<String> currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof String s ? Optional.of(s) : Optional.empty();
    }
}
