package com.ctms.ctms_backend.payment.controller;

import com.ctms.ctms_backend.budget.entity.CostCategory;
import com.ctms.ctms_backend.payment.dto.HoldPaymentRequest;
import com.ctms.ctms_backend.payment.dto.PaymentResponse;
import com.ctms.ctms_backend.payment.dto.ReleasePaymentRequest;
import com.ctms.ctms_backend.payment.entity.PaymentStatus;
import com.ctms.ctms_backend.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BL Epic 8 Story 01/04. Hold/release are CLAUDE.md's own literal example of a genuine-action
 * endpoint without a clean resource shape (SS6): /api/payments/{id}/hold. Finance Manager/Admin
 * only, same restricted-RBAC decision as BudgetController. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final String ROLES = "hasAnyRole('FINANCE_MANAGER','ADMIN')";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public Page<PaymentResponse> list(
            @RequestParam(required = false) Long studyId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) CostCategory costCategory,
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return paymentService.list(studyId, siteId, costCategory, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    public PaymentResponse get(@PathVariable Long id) {
        return paymentService.get(id);
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize(ROLES)
    public PaymentResponse hold(Principal principal, @PathVariable Long id, @Valid @RequestBody HoldPaymentRequest req) {
        return paymentService.hold(id, req, principal.getName());
    }

    @PostMapping("/{id}/release")
    @PreAuthorize(ROLES)
    public PaymentResponse release(Principal principal, @PathVariable Long id, @Valid @RequestBody ReleasePaymentRequest req) {
        return paymentService.release(id, req, principal.getName());
    }
}
