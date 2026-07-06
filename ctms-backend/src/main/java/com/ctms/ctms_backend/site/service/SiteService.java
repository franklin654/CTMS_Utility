package com.ctms.ctms_backend.site.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.dto.AssignCraRequest;
import com.ctms.ctms_backend.site.dto.CreateSiteRequest;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.dto.UpdateSiteRequest;
import com.ctms.ctms_backend.site.entity.ChecklistItemStatus;
import com.ctms.ctms_backend.site.entity.ChecklistItemType;
import com.ctms.ctms_backend.site.entity.SiteStatus;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.entity.SiteActivationChecklistItem;
import com.ctms.ctms_backend.site.exception.DuplicateSiteCodeException;
import com.ctms.ctms_backend.site.exception.InvalidCraAssignmentException;
import com.ctms.ctms_backend.site.exception.SiteNotFoundException;
import com.ctms.ctms_backend.site.repository.SiteActivationChecklistItemRepository;
import com.ctms.ctms_backend.site.repository.SiteRepository;
import com.ctms.ctms_backend.study.entity.Study;
import com.ctms.ctms_backend.study.exception.StudyNotFoundException;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteService {

    private final SiteRepository siteRepository;
    private final SiteActivationChecklistItemRepository checklistRepository;
    private final StudyRepository studyRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public SiteService(
            SiteRepository siteRepository,
            SiteActivationChecklistItemRepository checklistRepository,
            StudyRepository studyRepository,
            UserRepository userRepository,
            AuditService auditService,
            NotificationService notificationService) {
        this.siteRepository = siteRepository;
        this.checklistRepository = checklistRepository;
        this.studyRepository = studyRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public SiteResponse registerSite(CreateSiteRequest req, String creatorUsername) {
        if (siteRepository.existsBySiteCode(req.siteCode())) {
            throw new DuplicateSiteCodeException(req.siteCode());
        }
        Study study = studyRepository.findById(req.studyId()).orElseThrow(() -> new StudyNotFoundException(req.studyId()));
        User creator = currentUser(creatorUsername);

        Site site = new Site();
        site.setStudy(study);
        site.setSiteCode(req.siteCode());
        site.setName(req.name());
        site.setAddressLine1(req.addressLine1());
        site.setAddressLine2(req.addressLine2());
        site.setCity(req.city());
        site.setStateProvince(req.stateProvince());
        site.setPostalCode(req.postalCode());
        site.setCountry(req.country());
        site.setPrincipalInvestigatorName(req.principalInvestigatorName());
        site.setPrincipalInvestigatorContact(req.principalInvestigatorContact());
        site.setContactName(req.contactName());
        site.setContactEmail(req.contactEmail());
        site.setContactPhone(req.contactPhone());
        site.setFeasibilityStatus(req.feasibilityStatus());
        site.setRegulatoryInformation(req.regulatoryInformation());
        site.setCreatedBy(creator);
        site.setModifiedBy(creator);
        site = siteRepository.save(site);

        seedChecklist(site);

        auditService.record(
                "Site",
                String.valueOf(site.getId()),
                AuditAction.CREATE,
                null,
                "registered site " + site.getSiteCode() + " under study " + study.getStudyCode(),
                null);

        return SiteResponse.from(site);
    }

    private void seedChecklist(Site site) {
        for (ChecklistItemType type : ChecklistItemType.values()) {
            SiteActivationChecklistItem item = new SiteActivationChecklistItem();
            item.setSite(site);
            item.setItemType(type);
            item.setStatus(ChecklistItemStatus.PENDING);
            checklistRepository.save(item);
        }
    }

    @Transactional
    public SiteResponse updateSite(Long id, UpdateSiteRequest req, String updaterUsername) {
        Site site = findSite(id);
        User updater = currentUser(updaterUsername);

        site.setName(req.name());
        site.setAddressLine1(req.addressLine1());
        site.setAddressLine2(req.addressLine2());
        site.setCity(req.city());
        site.setStateProvince(req.stateProvince());
        site.setPostalCode(req.postalCode());
        site.setCountry(req.country());
        site.setPrincipalInvestigatorName(req.principalInvestigatorName());
        site.setPrincipalInvestigatorContact(req.principalInvestigatorContact());
        site.setContactName(req.contactName());
        site.setContactEmail(req.contactEmail());
        site.setContactPhone(req.contactPhone());
        site.setFeasibilityStatus(req.feasibilityStatus());
        site.setRegulatoryInformation(req.regulatoryInformation());
        site.setModifiedBy(updater);
        site = siteRepository.save(site);

        auditService.record("Site", String.valueOf(id), AuditAction.UPDATE, null, "site details updated", null);
        return SiteResponse.from(site);
    }

    @Transactional
    public SiteResponse assignCra(Long id, AssignCraRequest req, String actorUsername) {
        Site site = findSite(id);
        User cra = resolveCra(req.craUsername());
        User backupCra = req.backupCraUsername() == null || req.backupCraUsername().isBlank()
                ? null
                : resolveCra(req.backupCraUsername());

        String beforePrimary = site.getAssignedCra() == null ? null : site.getAssignedCra().getUsername();
        String beforeBackup = site.getBackupCra() == null ? null : site.getBackupCra().getUsername();
        site.setAssignedCra(cra);
        site.setBackupCra(backupCra);
        site.setModifiedBy(currentUser(actorUsername));
        site = siteRepository.save(site);

        auditService.record("Site", String.valueOf(id), AuditAction.UPDATE, beforePrimary, cra.getUsername(), "primary CRA assignment");
        if (backupCra != null || beforeBackup != null) {
            auditService.record(
                    "Site", String.valueOf(id), AuditAction.UPDATE, beforeBackup,
                    backupCra == null ? null : backupCra.getUsername(), "backup CRA assignment");
        }

        String link = "/sites/" + site.getId();
        notificationService.notify(
                cra.getId(), "CRA_ASSIGNED", "You've been assigned to site " + site.getSiteCode(),
                "You are now the primary CRA for site " + site.getSiteCode() + " (" + site.getName() + ").", link);
        if (backupCra != null) {
            notificationService.notify(
                    backupCra.getId(), "CRA_ASSIGNED", "You've been assigned as backup CRA for site " + site.getSiteCode(),
                    "You are now the backup CRA for site " + site.getSiteCode() + " (" + site.getName() + ").", link);
        }

        return SiteResponse.from(site);
    }

    private User resolveCra(String username) {
        return userRepository
                .findByUsername(username)
                .filter(u -> u.hasRole(Role.CRA_MONITOR))
                .orElseThrow(() -> new InvalidCraAssignmentException(username));
    }

    @Transactional(readOnly = true)
    public SiteResponse get(Long id) {
        return SiteResponse.from(findSite(id));
    }

    @Transactional(readOnly = true)
    public Page<SiteResponse> list(Long studyId, String status, String search, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? "" : search;
        SiteStatus parsedStatus = (status == null || status.isBlank()) ? null : SiteStatus.valueOf(status);
        return siteRepository.search(studyId, parsedStatus, normalizedSearch, pageable).map(SiteResponse::from);
    }

    Site findSite(Long id) {
        return siteRepository.findById(id).orElseThrow(() -> new SiteNotFoundException(id));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
