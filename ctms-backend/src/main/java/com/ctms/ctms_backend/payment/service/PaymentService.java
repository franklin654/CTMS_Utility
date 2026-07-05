package com.ctms.ctms_backend.payment.service;

import com.ctms.ctms_backend.audit.AuditAction;
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
import com.ctms.ctms_backend.payment.exception.PaymentNotFoundException;
import com.ctms.ctms_backend.payment.repository.PaymentRepository;
import com.ctms.ctms_backend.payment.rules.PaymentRuleOutcome;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL Epic 8 Story 01/04. generatePayment is called from VisitService.markCompleted,
 * SiteActivationService.promote, and MilestoneService.recordActual (FPI/LPI only) -- see those
 * call sites. No payment is generated if PaymentRuleService returns no outcome for the event
 * code, which is the normal case for events with no configured rule (e.g. LPO/DBL milestones). */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PaymentRuleService paymentRuleService;
    private final ESignatureService eSignatureService;
    private final AuditService auditService;

    public PaymentService(
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            PaymentRuleService paymentRuleService,
            ESignatureService eSignatureService,
            AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.paymentRuleService = paymentRuleService;
        this.eSignatureService = eSignatureService;
        this.auditService = auditService;
    }

    @Transactional
    public Optional<PaymentResponse> generatePayment(
            String eventCode, Study study, Site siteOrNull, String triggerEntityName, Long triggerEntityId, String actorUsername) {
        Optional<PaymentRuleOutcome> outcome = paymentRuleService.evaluate(eventCode);
        if (outcome.isEmpty()) {
            return Optional.empty();
        }
        PaymentRuleOutcome o = outcome.get();
        User actor = currentUser(actorUsername);

        BigDecimal amount = o.getBaseAmount().multiply(o.getMultiplier());
        if (o.getCapAmount() != null && amount.compareTo(o.getCapAmount()) > 0) {
            amount = o.getCapAmount();
        }

        Payment payment = new Payment();
        payment.setStudy(study);
        payment.setSite(siteOrNull);
        payment.setCostCategory(CostCategory.valueOf(o.getCostCategory()));
        payment.setEventCode(eventCode);
        payment.setTriggerEntityName(triggerEntityName);
        payment.setTriggerEntityId(triggerEntityId);
        payment.setBaseAmount(o.getBaseAmount());
        payment.setMultiplier(o.getMultiplier());
        payment.setCapAmount(o.getCapAmount());
        payment.setAmount(amount);
        payment.setCurrency(o.getCurrency());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedBy(actor);
        payment.setModifiedBy(actor);
        payment = paymentRepository.save(payment);

        auditService.record(
                "Payment", String.valueOf(payment.getId()), AuditAction.CREATE,
                null, eventCode + ": " + amount + " " + o.getCurrency() + " (" + o.getCostCategory() + ")", null);

        return Optional.of(PaymentResponse.from(payment));
    }

    @Transactional
    public PaymentResponse hold(Long id, HoldPaymentRequest req, String actorUsername) {
        Payment payment = findPayment(id);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentTransitionException("Cannot hold a payment from status " + payment.getStatus());
        }
        User actor = currentUser(actorUsername);

        payment.setStatus(PaymentStatus.ON_HOLD);
        payment.setHoldReason(req.reason());
        payment.setHeldAt(Instant.now());
        payment.setHeldBy(actor);
        payment.setModifiedBy(actor);
        payment = paymentRepository.save(payment);

        auditService.record(
                "Payment", String.valueOf(id), AuditAction.STATE_CHANGE,
                PaymentStatus.PENDING.name(), PaymentStatus.ON_HOLD.name(), req.reason());

        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse release(Long id, ReleasePaymentRequest req, String actorUsername) {
        Payment payment = findPayment(id);
        if (payment.getStatus() != PaymentStatus.ON_HOLD) {
            throw new InvalidPaymentTransitionException("Cannot release a payment from status " + payment.getStatus());
        }
        ESignature signature = eSignatureService.sign(actorUsername, req.password(), "Payment", String.valueOf(id), req.reason());
        User actor = currentUser(actorUsername);

        payment.setStatus(PaymentStatus.RELEASED);
        payment.setReleaseReason(req.reason());
        payment.setReleasedAt(Instant.now());
        payment.setReleasedBy(actor);
        payment.setESignature(signature);
        payment.setModifiedBy(actor);
        payment = paymentRepository.save(payment);

        auditService.record(
                "Payment", String.valueOf(id), AuditAction.STATE_CHANGE,
                PaymentStatus.ON_HOLD.name(), PaymentStatus.RELEASED.name(), req.reason());

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(Long studyId, Long siteId, CostCategory costCategory, PaymentStatus status, Pageable pageable) {
        return paymentRepository.search(studyId, siteId, costCategory, status, pageable).map(PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        return PaymentResponse.from(findPayment(id));
    }

    private Payment findPayment(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
