package com.ctms.ctms_backend.document.service;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.Document;
import com.ctms.ctms_backend.document.entity.DocumentCategoryAccessRule;
import com.ctms.ctms_backend.document.exception.DocumentAccessDeniedException;
import com.ctms.ctms_backend.document.repository.DocumentCategoryAccessRuleRepository;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Story 05 (role-based document access) enforcement -- data-driven via
 * {@link DocumentCategoryAccessRuleRepository} (default-allow, explicit deny-list), not hardcoded
 * per-category conditionals, per CLAUDE.md 2.7. Denials are audit-logged, per CLAUDE.md 2.6 /
 * Story 05's "unauthorized access attempts blocked and logged."
 */
@Service
public class DocumentAccessControlService {

    private final DocumentCategoryAccessRuleRepository ruleRepository;
    private final AuditService auditService;

    public DocumentAccessControlService(DocumentCategoryAccessRuleRepository ruleRepository, AuditService auditService) {
        this.ruleRepository = ruleRepository;
        this.auditService = auditService;
    }

    /** Throws {@link DocumentAccessDeniedException} (and audit-logs the denial) if the current
     * caller's roles are DENY-listed for the document's category. */
    public void assertReadable(Document document) {
        if (document.getCategory() == null) {
            return;
        }
        Set<String> roles = currentRoleCodes();
        boolean denied = ruleRepository.findByCategoryAndRoleCodeIn(document.getCategory(), roles).stream()
                .anyMatch(r -> DocumentCategoryAccessRule.ACCESS_DENY.equals(r.getAccess()));
        if (denied) {
            auditService.record(
                    "Document", String.valueOf(document.getId()), "ACCESS_DENIED", null,
                    "category=" + document.getCategory(), "unauthorized category access attempt");
            throw new DocumentAccessDeniedException(document.getId());
        }
    }

    public Set<String> currentRoleCodes() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toSet());
    }
}
