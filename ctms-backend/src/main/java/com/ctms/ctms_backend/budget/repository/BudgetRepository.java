package com.ctms.ctms_backend.budget.repository;

import com.ctms.ctms_backend.budget.entity.Budget;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByStudyId(Long studyId);

    boolean existsByStudyId(Long studyId);
}
