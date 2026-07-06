package com.ctms.ctms_backend.deviation.repository;

import com.ctms.ctms_backend.deviation.entity.ProtocolDeviation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtocolDeviationRepository extends JpaRepository<ProtocolDeviation, Long> {

    List<ProtocolDeviation> findBySubjectIdOrderByCreatedAtDesc(Long subjectId);
}
