package com.ctms.ctms_backend.budget.repository;

import com.ctms.ctms_backend.budget.entity.BudgetLineItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetLineItemRepository extends JpaRepository<BudgetLineItem, Long> {

    List<BudgetLineItem> findByBudgetVersionId(Long budgetVersionId);
}
