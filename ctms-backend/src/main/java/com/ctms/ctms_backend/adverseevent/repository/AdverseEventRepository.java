package com.ctms.ctms_backend.adverseevent.repository;

import com.ctms.ctms_backend.adverseevent.entity.AdverseEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdverseEventRepository extends JpaRepository<AdverseEvent, Long> {

    List<AdverseEvent> findBySubjectIdOrderByCreatedAtDesc(Long subjectId);
}
