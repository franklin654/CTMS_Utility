package com.ctms.ctms_backend.budget.repository;

import com.ctms.ctms_backend.budget.entity.BudgetVersion;
import com.ctms.ctms_backend.budget.entity.BudgetVersionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetVersionRepository extends JpaRepository<BudgetVersion, Long> {

    List<BudgetVersion> findByBudgetIdOrderByVersionNumberDesc(Long budgetId);

    Optional<BudgetVersion> findByBudgetIdAndVersionNumber(Long budgetId, int versionNumber);

    Optional<BudgetVersion> findByBudgetIdAndStatus(Long budgetId, BudgetVersionStatus status);
}
