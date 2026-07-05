package com.ctms.ctms_backend.esignature;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ESignatureRepository extends JpaRepository<ESignature, Long> {

    List<ESignature> findByEntityNameAndEntityIdOrderBySignedAtDesc(String entityName, String entityId);
}
