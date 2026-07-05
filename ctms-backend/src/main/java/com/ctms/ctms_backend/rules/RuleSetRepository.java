package com.ctms.ctms_backend.rules;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleSetRepository extends JpaRepository<RuleSet, Long> {

    Optional<RuleSet> findByName(String name);
}
