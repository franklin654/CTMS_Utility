package com.ctms.ctms_backend.site.repository;

import com.ctms.ctms_backend.site.entity.ChecklistItemType;
import com.ctms.ctms_backend.site.entity.SiteActivationChecklistItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteActivationChecklistItemRepository extends JpaRepository<SiteActivationChecklistItem, Long> {

    List<SiteActivationChecklistItem> findBySiteIdOrderByItemType(Long siteId);

    Optional<SiteActivationChecklistItem> findBySiteIdAndItemType(Long siteId, ChecklistItemType itemType);
}
