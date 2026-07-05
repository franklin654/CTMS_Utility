package com.ctms.ctms_backend.budget.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.budget.dto.BudgetLineItemRequest;
import com.ctms.ctms_backend.budget.dto.BudgetLineItemResponse;
import com.ctms.ctms_backend.budget.dto.BudgetVersionResponse;
import com.ctms.ctms_backend.budget.dto.CreateBudgetRequest;
import com.ctms.ctms_backend.budget.dto.CreateBudgetVersionRequest;
import com.ctms.ctms_backend.budget.entity.Budget;
import com.ctms.ctms_backend.budget.entity.BudgetLineItem;
import com.ctms.ctms_backend.budget.entity.BudgetVersion;
import com.ctms.ctms_backend.budget.entity.BudgetVersionStatus;
import com.ctms.ctms_backend.budget.entity.CostCategory;
import com.ctms.ctms_backend.budget.exception.BudgetNotFoundException;
import com.ctms.ctms_backend.budget.exception.BudgetVersionNotFoundException;
import com.ctms.ctms_backend.budget.exception.DuplicateBudgetException;
import com.ctms.ctms_backend.budget.exception.MissingBudgetVersionReasonException;
import com.ctms.ctms_backend.budget.repository.BudgetLineItemRepository;
import com.ctms.ctms_backend.budget.repository.BudgetRepository;
import com.ctms.ctms_backend.budget.repository.BudgetVersionRepository;
import com.ctms.ctms_backend.payment.repository.PaymentRepository;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.exception.StudyNotFoundException;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL Epic 8 Story 02/03. Budget versioning mirrors DocumentVersion's version-and-archive
 * pattern (CLAUDE.md S2.4, verbatim instruction for budgets specifically): increment version,
 * mark previous CURRENT -> SUPERSEDED, keep it queryable and read-only. */
@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final BudgetVersionRepository budgetVersionRepository;
    private final BudgetLineItemRepository budgetLineItemRepository;
    private final PaymentRepository paymentRepository;
    private final StudyRepository studyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public BudgetService(
            BudgetRepository budgetRepository,
            BudgetVersionRepository budgetVersionRepository,
            BudgetLineItemRepository budgetLineItemRepository,
            PaymentRepository paymentRepository,
            StudyRepository studyRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.budgetRepository = budgetRepository;
        this.budgetVersionRepository = budgetVersionRepository;
        this.budgetLineItemRepository = budgetLineItemRepository;
        this.paymentRepository = paymentRepository;
        this.studyRepository = studyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public BudgetVersionResponse create(CreateBudgetRequest req, String actorUsername) {
        if (budgetRepository.existsByStudyId(req.studyId())) {
            throw new DuplicateBudgetException(req.studyId());
        }
        Study study = studyRepository.findById(req.studyId()).orElseThrow(() -> new StudyNotFoundException(req.studyId()));
        User actor = currentUser(actorUsername);

        Budget budget = new Budget();
        budget.setStudy(study);
        budget.setCreatedBy(actor);
        budget.setModifiedBy(actor);
        budget = budgetRepository.save(budget);

        BudgetVersion version = createVersion(budget, 1, null, req.lineItems(), actor);

        auditService.record(
                "Budget", String.valueOf(budget.getId()), AuditAction.CREATE,
                null, "budget created for study " + study.getStudyCode(), null);

        return toResponse(study, version, true);
    }

    @Transactional
    public BudgetVersionResponse createNewVersion(Long studyId, CreateBudgetVersionRequest req, String actorUsername) {
        if (req.reason() == null || req.reason().isBlank()) {
            throw new MissingBudgetVersionReasonException();
        }
        Study study = studyRepository.findById(studyId).orElseThrow(() -> new StudyNotFoundException(studyId));
        Budget budget = budgetRepository.findByStudyId(studyId).orElseThrow(() -> new BudgetNotFoundException(studyId));
        User actor = currentUser(actorUsername);

        BudgetVersion current = budgetVersionRepository
                .findByBudgetIdAndStatus(budget.getId(), BudgetVersionStatus.CURRENT)
                .orElseThrow(() -> new BudgetNotFoundException(studyId));
        current.setStatus(BudgetVersionStatus.SUPERSEDED);
        budgetVersionRepository.save(current);

        BudgetVersion newVersion = createVersion(budget, current.getVersionNumber() + 1, req.reason(), req.lineItems(), actor);

        auditService.record(
                "Budget", String.valueOf(budget.getId()), AuditAction.UPDATE,
                "version " + current.getVersionNumber(), "version " + newVersion.getVersionNumber(), req.reason());

        return toResponse(study, newVersion, true);
    }

    @Transactional(readOnly = true)
    public BudgetVersionResponse getCurrentVersion(Long studyId) {
        Study study = studyRepository.findById(studyId).orElseThrow(() -> new StudyNotFoundException(studyId));
        Budget budget = budgetRepository.findByStudyId(studyId).orElseThrow(() -> new BudgetNotFoundException(studyId));
        BudgetVersion current = budgetVersionRepository
                .findByBudgetIdAndStatus(budget.getId(), BudgetVersionStatus.CURRENT)
                .orElseThrow(() -> new BudgetNotFoundException(studyId));
        return toResponse(study, current, true);
    }

    @Transactional(readOnly = true)
    public BudgetVersionResponse getVersion(Long studyId, int versionNumber) {
        Study study = studyRepository.findById(studyId).orElseThrow(() -> new StudyNotFoundException(studyId));
        Budget budget = budgetRepository.findByStudyId(studyId).orElseThrow(() -> new BudgetNotFoundException(studyId));
        BudgetVersion version = budgetVersionRepository
                .findByBudgetIdAndVersionNumber(budget.getId(), versionNumber)
                .orElseThrow(() -> new BudgetVersionNotFoundException(studyId, versionNumber));
        return toResponse(study, version, false);
    }

    @Transactional(readOnly = true)
    public List<BudgetVersionResponse> listVersions(Long studyId) {
        Study study = studyRepository.findById(studyId).orElseThrow(() -> new StudyNotFoundException(studyId));
        Budget budget = budgetRepository.findByStudyId(studyId).orElseThrow(() -> new BudgetNotFoundException(studyId));
        return budgetVersionRepository.findByBudgetIdOrderByVersionNumberDesc(budget.getId()).stream()
                .map(v -> toResponse(study, v, false))
                .toList();
    }

    private BudgetVersion createVersion(Budget budget, int versionNumber, String reason, List<BudgetLineItemRequest> lineItemRequests, User actor) {
        BudgetVersion version = new BudgetVersion();
        version.setBudget(budget);
        version.setVersionNumber(versionNumber);
        version.setStatus(BudgetVersionStatus.CURRENT);
        version.setReason(reason);
        version.setCreatedBy(actor);
        version = budgetVersionRepository.save(version);

        for (BudgetLineItemRequest itemReq : lineItemRequests) {
            BudgetLineItem item = new BudgetLineItem();
            item.setBudgetVersion(version);
            item.setCostCategory(CostCategory.valueOf(itemReq.costCategory()));
            item.setPlannedAmount(itemReq.plannedAmount());
            item.setCurrency(itemReq.currency());
            budgetLineItemRepository.save(item);
        }
        return version;
    }

    private BudgetVersionResponse toResponse(Study study, BudgetVersion version, boolean includeActuals) {
        List<BudgetLineItem> lineItems = budgetLineItemRepository.findByBudgetVersionId(version.getId());

        Map<CostCategory, BigDecimal> actualByCategory = new HashMap<>();
        if (includeActuals) {
            for (Object[] row : paymentRepository.sumActualByCategory(study.getId())) {
                actualByCategory.put((CostCategory) row[0], (BigDecimal) row[1]);
            }
        }

        List<BudgetLineItemResponse> lineItemResponses = lineItems.stream()
                .map(item -> {
                    BigDecimal actual = includeActuals ? actualByCategory.getOrDefault(item.getCostCategory(), BigDecimal.ZERO) : null;
                    BigDecimal variance = includeActuals ? item.getPlannedAmount().subtract(actual) : null;
                    return new BudgetLineItemResponse(item.getCostCategory().name(), item.getPlannedAmount(), actual, variance, item.getCurrency());
                })
                .toList();

        return new BudgetVersionResponse(
                version.getId(),
                study.getId(),
                study.getStudyCode(),
                version.getVersionNumber(),
                version.getStatus().name(),
                version.getReason(),
                lineItemResponses,
                version.getCreatedBy().getUsername(),
                version.getCreatedAt());
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
