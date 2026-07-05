package com.ctms.ctms_backend.document.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.Document;
import com.ctms.ctms_backend.document.entity.DocumentCategoryAccessRule;
import com.ctms.ctms_backend.document.exception.DocumentAccessDeniedException;
import com.ctms.ctms_backend.document.repository.DocumentCategoryAccessRuleRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class DocumentAccessControlServiceTest {

    @Mock private DocumentCategoryAccessRuleRepository ruleRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private DocumentAccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "cra1", null, List.of(new SimpleGrantedAuthority("ROLE_CRA_MONITOR"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Document documentWithCategory(String category) {
        Document document = new Document();
        document.setId(5L);
        document.setCategory(category);
        return document;
    }

    @Test
    void assertReadable_noCategory_allowed() {
        assertDoesNotThrow(() -> accessControlService.assertReadable(documentWithCategory(null)));
    }

    @Test
    void assertReadable_categoryNotDenied_allowed() {
        lenient().when(ruleRepository.findByCategoryAndRoleCodeIn(anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        assertDoesNotThrow(() -> accessControlService.assertReadable(documentWithCategory("PROTOCOL")));
    }

    @Test
    void assertReadable_categoryDenied_throwsAndAudits() {
        DocumentCategoryAccessRule denyRule = new DocumentCategoryAccessRule();
        denyRule.setCategory("FINANCIAL");
        denyRule.setRoleCode("CRA_MONITOR");
        denyRule.setAccess(DocumentCategoryAccessRule.ACCESS_DENY);
        when(ruleRepository.findByCategoryAndRoleCodeIn(org.mockito.ArgumentMatchers.eq("FINANCIAL"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(denyRule));

        Document document = documentWithCategory("FINANCIAL");
        assertThrows(DocumentAccessDeniedException.class, () -> accessControlService.assertReadable(document));
        verify(auditService).record(
                org.mockito.ArgumentMatchers.eq("Document"), org.mockito.ArgumentMatchers.eq("5"),
                org.mockito.ArgumentMatchers.eq("ACCESS_DENIED"), org.mockito.ArgumentMatchers.isNull(),
                anyString(), anyString());
    }

    @Test
    void assertReadable_categoryDeniedForDifferentRole_notThisRole_allowed() {
        // rule exists for FINANCE_MANAGER but caller is CRA_MONITOR -- repository call itself
        // filters by caller roles, so an empty result here simulates "no rule matches my roles."
        when(ruleRepository.findByCategoryAndRoleCodeIn(org.mockito.ArgumentMatchers.eq("FINANCIAL"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        assertDoesNotThrow(() -> accessControlService.assertReadable(documentWithCategory("FINANCIAL")));
        verify(auditService, never()).record(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }
}
