package com.ctms.ctms_backend.budget.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.budget.dto.BudgetLineItemRequest;
import com.ctms.ctms_backend.budget.dto.BudgetVersionResponse;
import com.ctms.ctms_backend.budget.dto.CreateBudgetRequest;
import com.ctms.ctms_backend.budget.dto.CreateBudgetVersionRequest;
import com.ctms.ctms_backend.budget.entity.Budget;
import com.ctms.ctms_backend.budget.entity.BudgetLineItem;
import com.ctms.ctms_backend.budget.entity.BudgetVersion;
import com.ctms.ctms_backend.budget.entity.BudgetVersionStatus;
import com.ctms.ctms_backend.budget.entity.CostCategory;
import com.ctms.ctms_backend.budget.exception.DuplicateBudgetException;
import com.ctms.ctms_backend.budget.exception.MissingBudgetVersionReasonException;
import com.ctms.ctms_backend.budget.repository.BudgetLineItemRepository;
import com.ctms.ctms_backend.budget.repository.BudgetRepository;
import com.ctms.ctms_backend.budget.repository.BudgetVersionRepository;
import com.ctms.ctms_backend.payment.repository.PaymentRepository;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetVersionRepository budgetVersionRepository;
    @Mock private BudgetLineItemRepository budgetLineItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private StudyRepository studyRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private BudgetService budgetService;

    private Study study;
    private User actor;

    @BeforeEach
    void setUp() {
        study = new Study();
        study.setId(10L);
        study.setStudyCode("ST-000010");

        actor = new User();
        actor.setId(1L);
        actor.setUsername("finance.mgr");

        lenient().when(studyRepository.findById(10L)).thenReturn(Optional.of(study));
        lenient().when(userRepository.findByUsername("finance.mgr")).thenReturn(Optional.of(actor));

        lenient().when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> {
            Budget b = inv.getArgument(0);
            if (b.getId() == null) {
                b.setId(100L);
            }
            return b;
        });
        lenient().when(budgetVersionRepository.save(any(BudgetVersion.class))).thenAnswer(inv -> {
            BudgetVersion v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(200L);
            }
            return v;
        });
        lenient().when(budgetLineItemRepository.save(any(BudgetLineItem.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(budgetLineItemRepository.findByBudgetVersionId(200L)).thenReturn(List.of());
        lenient().when(paymentRepository.sumActualByCategory(10L)).thenReturn(List.of());
    }

    private List<BudgetLineItemRequest> lineItems() {
        return List.of(new BudgetLineItemRequest("MONITORING", new BigDecimal("10000.00"), "USD"));
    }

    @Test
    void create_happyPath_savesVersion1WithNoReason() {
        when(budgetRepository.existsByStudyId(10L)).thenReturn(false);

        BudgetVersionResponse response = budgetService.create(new CreateBudgetRequest(10L, lineItems()), "finance.mgr");

        assertEquals(1, response.versionNumber());
        assertEquals("CURRENT", response.status());
    }

    @Test
    void create_duplicateBudget_throws() {
        when(budgetRepository.existsByStudyId(10L)).thenReturn(true);
        assertThrows(DuplicateBudgetException.class,
                () -> budgetService.create(new CreateBudgetRequest(10L, lineItems()), "finance.mgr"));
    }

    @Test
    void createNewVersion_withoutReason_throws() {
        assertThrows(MissingBudgetVersionReasonException.class, () -> budgetService.createNewVersion(
                10L, new CreateBudgetVersionRequest(lineItems(), null), "finance.mgr"));
    }

    @Test
    void createNewVersion_withReason_supersedesCurrentAndIncrementsVersion() {
        Budget budget = new Budget();
        budget.setId(100L);
        budget.setStudy(study);
        when(budgetRepository.findByStudyId(10L)).thenReturn(Optional.of(budget));

        BudgetVersion currentVersion = new BudgetVersion();
        currentVersion.setId(199L);
        currentVersion.setBudget(budget);
        currentVersion.setVersionNumber(1);
        currentVersion.setStatus(BudgetVersionStatus.CURRENT);
        currentVersion.setCreatedBy(actor);
        when(budgetVersionRepository.findByBudgetIdAndStatus(100L, BudgetVersionStatus.CURRENT)).thenReturn(Optional.of(currentVersion));

        BudgetVersionResponse response = budgetService.createNewVersion(
                10L, new CreateBudgetVersionRequest(lineItems(), "Increased monitoring budget"), "finance.mgr");

        assertEquals(2, response.versionNumber());
        assertEquals(BudgetVersionStatus.SUPERSEDED, currentVersion.getStatus());
    }

    @Test
    void getCurrentVersion_computesActualAndVariance() {
        Budget budget = new Budget();
        budget.setId(100L);
        budget.setStudy(study);
        when(budgetRepository.findByStudyId(10L)).thenReturn(Optional.of(budget));

        BudgetVersion currentVersion = new BudgetVersion();
        currentVersion.setId(200L);
        currentVersion.setBudget(budget);
        currentVersion.setVersionNumber(1);
        currentVersion.setStatus(BudgetVersionStatus.CURRENT);
        currentVersion.setCreatedBy(actor);
        when(budgetVersionRepository.findByBudgetIdAndStatus(100L, BudgetVersionStatus.CURRENT)).thenReturn(Optional.of(currentVersion));

        BudgetLineItem lineItem = new BudgetLineItem();
        lineItem.setBudgetVersion(currentVersion);
        lineItem.setCostCategory(CostCategory.MONITORING);
        lineItem.setPlannedAmount(new BigDecimal("10000.00"));
        lineItem.setCurrency("USD");
        when(budgetLineItemRepository.findByBudgetVersionId(200L)).thenReturn(List.of(lineItem));

        when(paymentRepository.sumActualByCategory(10L)).thenReturn(
                List.<Object[]>of(new Object[] {CostCategory.MONITORING, new BigDecimal("2500.00")}));

        BudgetVersionResponse response = budgetService.getCurrentVersion(10L);

        assertEquals(1, response.lineItems().size());
        assertEquals(0, new BigDecimal("2500.00").compareTo(response.lineItems().get(0).actualAmount()));
        assertEquals(0, new BigDecimal("7500.00").compareTo(response.lineItems().get(0).variance()));
    }
}
