package com.ctms.ctms_backend.study.repository;

import com.ctms.ctms_backend.study.entity.Study;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyRepository extends JpaRepository<Study, Long> {

    Optional<Study> findByProtocolId(String protocolId);

    boolean existsByProtocolId(String protocolId);

    Page<Study> findByNameContainingIgnoreCaseOrProtocolIdContainingIgnoreCase(
            String name, String protocolId, Pageable pageable);
}
