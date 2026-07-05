package com.ctms.ctms_backend.document.repository;

import com.ctms.ctms_backend.document.entity.DocumentCategoryAccessRule;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentCategoryAccessRuleRepository extends JpaRepository<DocumentCategoryAccessRule, Long> {

    List<DocumentCategoryAccessRule> findByCategoryAndRoleCodeIn(String category, Collection<String> roleCodes);
}
