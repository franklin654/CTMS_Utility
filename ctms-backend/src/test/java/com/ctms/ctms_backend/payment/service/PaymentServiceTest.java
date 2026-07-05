package com.ctms.ctms_backend.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.budget.entity.CostCategory;
import com.ctms.ctms_backend.esignature.ESignature;
import com.ctms.ctms_backend.esignature.ESignatureService;
import com.ctms.ctms_backend.payment.dto.HoldPaymentRequest;
import com.ctms.ctms_backend.payment.dto.PaymentResponse;
import com.ctms.ctms_backend.payment.dto.ReleasePaymentRequest;
import com.ctms.ctms_backend.payment.entity.Payment;
import com.ctms.ctms_backend.payment.entity.PaymentStatus;
import com.ctms.ctms_backend.payment.exception.InvalidPaymentTransitionException;
import com.ctms.ctms_backend.payment.repository.PaymentRepository;
import com.ctms.ctms_backend.payment.rules.PaymentRuleOutcome;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentRuleService paymentRuleService;
    @Mock private ESignatureService eSignatureService;
    @Mock private AuditService auditService;

    @InjectMocks
    private PaymentService paymentService;

    private Study study;
    private User actor;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");

        actor = new User();
        actor.setId(1L);
        actor.setUsername("finance.mgr");

        lenient().when(userRepository.findByUsername("finance.mgr")).thenReturn(Optional.of(actor));
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(500L);
            }
            return p;
        });

        pendingPayment = new Payment();
        pendingPayment.setId(500L);
        pendingPayment.setStudy(study);
        pendingPayment.setCostCategory(CostCategory.MONITORING);
        pendingPayment.setAmount(new BigDecimal("500.00"));
        pendingPayment.setCurrency("USD");
        pendingPayment.setStatus(PaymentStatus.PENDING);
        pendingPayment.setCreatedBy(actor);
        lenient().when(paymentRepository.findById(500L)).thenReturn(Optional.of(pendingPayment));
    }

    @Test
    void generatePayment_noRuleOutcome_returnsEmptyAndDoesNotSave() {
        when(paymentRuleService.evaluate("MILESTONE_REACHED_LPO")).thenReturn(Optional.empty());

        Optional<PaymentResponse> result = paymentService.generatePayment(
                "MILESTONE_REACHED_LPO", study, null, "Milestone", 1L, "finance.mgr");

        assertTrue(result.isEmpty());
    }

    @Test
    void generatePayment_uncapped_computesBaseTimesMultiplier() {
        PaymentRuleOutcome outcome = new PaymentRuleOutcome("MONITORING", new BigDecimal("500.00"), new BigDecimal("2.0"), null, "USD");
        when(paymentRuleService.evaluate("VISIT_COMPLETED")).thenReturn(Optional.of(outcome));

        Optional<PaymentResponse> result = paymentService.generatePayment(
                "VISIT_COMPLETED", study, null, "Visit", 1L, "finance.mgr");

        assertTrue(result.isPresent());
        assertEquals(0, new BigDecimal("1000.00").compareTo(result.get().amount()));
    }

    @Test
    void generatePayment_exceedsCap_clampsToCapAmount() {
        PaymentRuleOutcome outcome = new PaymentRuleOutcome(
                "INVESTIGATOR_FEES", new BigDecimal("5000.00"), new BigDecimal("2.0"), new BigDecimal("6000.00"), "USD");
        when(paymentRuleService.evaluate("MILESTONE_REACHED_FPI")).thenReturn(Optional.of(outcome));

        Optional<PaymentResponse> result = paymentService.generatePayment(
                "MILESTONE_REACHED_FPI", study, null, "Milestone", 1L, "finance.mgr");

        assertTrue(result.isPresent());
        assertEquals(0, new BigDecimal("6000.00").compareTo(result.get().amount()));
    }

    @Test
    void hold_fromPending_succeeds() {
        PaymentResponse response = paymentService.hold(500L, new HoldPaymentRequest("Budget review pending"), "finance.mgr");
        assertEquals("ON_HOLD", response.status());
    }

    @Test
    void hold_alreadyOnHold_throws() {
        pendingPayment.setStatus(PaymentStatus.ON_HOLD);
        assertThrows(InvalidPaymentTransitionException.class,
                () -> paymentService.hold(500L, new HoldPaymentRequest("dup"), "finance.mgr"));
    }

    @Test
    void release_fromOnHold_wrongPassword_throwsAndLeavesPaymentOnHold() {
        pendingPayment.setStatus(PaymentStatus.ON_HOLD);
        when(eSignatureService.sign("finance.mgr", "wrong", "Payment", "500", "release reason"))
                .thenThrow(new InvalidCredentialsException());

        assertThrows(InvalidCredentialsException.class, () -> paymentService.release(
                500L, new ReleasePaymentRequest("release reason", "wrong"), "finance.mgr"));
        assertEquals(PaymentStatus.ON_HOLD, pendingPayment.getStatus());
    }

    @Test
    void release_fromOnHold_correctPassword_succeeds() {
        pendingPayment.setStatus(PaymentStatus.ON_HOLD);
        ESignature signature = new ESignature(actor, "Payment", "500", "release reason");
        when(eSignatureService.sign("finance.mgr", "correct", "Payment", "500", "release reason")).thenReturn(signature);

        PaymentResponse response = paymentService.release(500L, new ReleasePaymentRequest("release reason", "correct"), "finance.mgr");

        assertEquals("RELEASED", response.status());
    }

    @Test
    void release_fromPending_throws() {
        assertThrows(InvalidPaymentTransitionException.class,
                () -> paymentService.release(500L, new ReleasePaymentRequest("too early", "any"), "finance.mgr"));
    }
}
