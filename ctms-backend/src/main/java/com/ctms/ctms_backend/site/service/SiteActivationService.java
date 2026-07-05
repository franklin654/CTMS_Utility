package com.ctms.ctms_backend.site.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.dto.ActivationAttemptResponse;
import com.ctms.ctms_backend.site.dto.ChecklistItemResponse;
import com.ctms.ctms_backend.site.dto.SiteResponse;
import com.ctms.ctms_backend.site.dto.UpdateChecklistItemRequest;
import com.ctms.ctms_backend.site.entity.ChecklistItemStatus;
import com.ctms.ctms_backend.site.entity.ChecklistItemType;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.entity.SiteActivationChecklistItem;
import com.ctms.ctms_backend.site.entity.SiteStatus;
import com.ctms.ctms_backend.site.exception.ChecklistItemNotFoundException;
import com.ctms.ctms_backend.site.exception.InvalidSiteTransitionException;
import com.ctms.ctms_backend.site.exception.SiteActivationBlockedException;
import com.ctms.ctms_backend.site.exception.SiteNotFoundException;
import com.ctms.ctms_backend.site.repository.SiteActivationChecklistItemRepository;
import com.ctms.ctms_backend.site.repository.SiteRepository;
import com.ctms.ctms_backend.task.service.TaskService;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the site activation state machine (Epic 2 Stories 02/03): checklist item updates,
 * silent auto-promotion when all prerequisites are met, and the explicit "attempt activation"
 * action that surfaces a clear list of what's missing. Both promotion paths funnel through a
 * single {@link #promote} method so there is exactly one way a site becomes ACTIVE. */
@Service
public class SiteActivationService {

    private static final Map<ChecklistItemType, String> DISPLAY_LABEL = Map.of(
            ChecklistItemType.FEASIBILITY_COMPLETION, "Feasibility Completion",
            ChecklistItemType.IRB_EC_APPROVAL, "IRB/EC Approval",
            ChecklistItemType.CONTRACT_COMPLETION, "Contract Completion",
            ChecklistItemType.ESSENTIAL_DOCUMENTS_SUBMISSION, "Essential Documents Submission",
            ChecklistItemType.SITE_INITIATION_VISIT, "Site Initiation Visit");

    private final SiteRepository siteRepository;
    private final SiteActivationChecklistItemRepository checklistRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final TaskService taskService;

    public SiteActivationService(
            SiteRepository siteRepository,
            SiteActivationChecklistItemRepository checklistRepository,
            UserRepository userRepository,
            AuditService auditService,
            NotificationService notificationService,
            TaskService taskService) {
        this.siteRepository = siteRepository;
        this.checklistRepository = checklistRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.taskService = taskService;
    }

    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> checklist(Long siteId) {
        getSite(siteId);
        return checklistRepository.findBySiteIdOrderByItemType(siteId).stream().map(ChecklistItemResponse::from).toList();
    }

    /** Story 02: update one checklist item, then silently re-check for auto-promotion --
     * no exception if still incomplete, since this call is "make progress," not "activate." */
    @Transactional
    public ChecklistItemResponse updateChecklistItem(
            Long siteId, String itemTypeRaw, UpdateChecklistItemRequest req, String actorUsername) {
        Site site = getSite(siteId);
        ChecklistItemType itemType = parseItemType(itemTypeRaw);
        SiteActivationChecklistItem item = checklistRepository
                .findBySiteIdAndItemType(siteId, itemType)
                .orElseThrow(() -> new ChecklistItemNotFoundException(siteId, itemTypeRaw));

        ChecklistItemStatus targetStatus = parseItemStatus(req.status());
        ChecklistItemStatus before = item.getStatus();
        User actor = currentUser(actorUsername);

        item.setStatus(targetStatus);
        item.setCompletedDate(
                targetStatus == ChecklistItemStatus.COMPLETE
                        ? (req.completedDate() != null ? req.completedDate() : LocalDate.now())
                        : null);
        item.setNote(req.note());
        item.setUpdatedBy(actor);
        item = checklistRepository.save(item);

        auditService.record(
                "SiteActivationChecklistItem",
                siteId + ":" + itemType,
                AuditAction.UPDATE,
                before.name(),
                targetStatus.name(),
                req.note());

        recheckAndPromoteIfComplete(site, actor);
        return ChecklistItemResponse.from(item);
    }

    /** Story 03: explicit activation attempt. Succeeds (and promotes) only if every checklist
     * item is COMPLETE; otherwise throws with the exact missing-item list. The attempt itself
     * is always audit-logged, win or lose (Story 03 AC4). */
    @Transactional
    public ActivationAttemptResponse attemptActivation(Long siteId, String actorUsername) {
        Site site = getSite(siteId);
        User actor = currentUser(actorUsername);
        List<SiteActivationChecklistItem> items = checklistRepository.findBySiteIdOrderByItemType(siteId);
        List<String> missing = missingLabels(items);

        auditService.record(
                "Site",
                String.valueOf(siteId),
                AuditAction.STATE_CHANGE,
                site.getStatus().name(),
                "ACTIVATION_ATTEMPTED",
                missing.isEmpty() ? "all prerequisites met" : "missing: " + String.join(", ", missing));

        if (!missing.isEmpty()) {
            throw new SiteActivationBlockedException(missing);
        }
        if (site.getStatus() == SiteStatus.PENDING_ACTIVATION) {
            promote(site, actor);
        }
        return new ActivationAttemptResponse(true, List.of(), SiteResponse.from(site));
    }

    private void recheckAndPromoteIfComplete(Site site, User actor) {
        if (site.getStatus() != SiteStatus.PENDING_ACTIVATION) {
            return;
        }
        List<SiteActivationChecklistItem> items = checklistRepository.findBySiteIdOrderByItemType(site.getId());
        if (missingLabels(items).isEmpty()) {
            promote(site, actor);
        }
    }

    private void promote(Site site, User actor) {
        site.setStatus(SiteStatus.ACTIVE);
        site.setActivationDate(LocalDate.now());
        site.setModifiedBy(actor);
        siteRepository.save(site);

        auditService.record(
                "Site",
                String.valueOf(site.getId()),
                AuditAction.STATE_CHANGE,
                SiteStatus.PENDING_ACTIVATION.name(),
                SiteStatus.ACTIVE.name(),
                "all activation prerequisites complete");

        notifyActivation(site);

        if (site.getAssignedCra() == null) {
            createCraAssignmentTask(site, actor);
        }
    }

    /** BRD Epic 5 Story 01: auto-create a task when a site activates with no CRA assigned yet --
     * skipped entirely if a CRA is already assigned, to avoid a noisy no-op task. */
    private void createCraAssignmentTask(Site site, User actor) {
        List<User> admins = userRepository.findByRoles_Code(Role.ADMIN);
        if (admins.isEmpty()) {
            return;
        }
        User admin = admins.get(0);
        taskService.createTask(
                "SITE_ACTIVATED",
                "Assign CRA to newly activated site: " + site.getSiteCode(),
                "Site " + site.getSiteCode() + " under study " + site.getStudy().getStudyCode()
                        + " is now Active but has no assigned CRA.",
                "Site", site.getId(), site.getStudy().getCreatedBy().getId(), admin.getId(), actor.getUsername());
    }

    private void notifyActivation(Site site) {
        notificationService.notify(
                site.getCreatedBy().getId(),
                "SITE_ACTIVATED",
                "Site " + site.getSiteCode() + " is now Active",
                "Site " + site.getName() + " under study " + site.getStudy().getStudyCode()
                        + " has met all activation prerequisites and is now Active.",
                "/sites/" + site.getId());

        if (site.getAssignedCra() != null) {
            notificationService.notify(
                    site.getAssignedCra().getId(),
                    "SITE_ACTIVATED",
                    "Site " + site.getSiteCode() + " is now Active",
                    "Site " + site.getName() + " is now Active and ready for subject enrollment.",
                    "/sites/" + site.getId());
        }
    }

    private List<String> missingLabels(List<SiteActivationChecklistItem> items) {
        return items.stream()
                .filter(i -> i.getStatus() != ChecklistItemStatus.COMPLETE)
                .map(i -> DISPLAY_LABEL.get(i.getItemType()))
                .toList();
    }

    private Site getSite(Long id) {
        return siteRepository.findById(id).orElseThrow(() -> new SiteNotFoundException(id));
    }

    private ChecklistItemType parseItemType(String value) {
        try {
            return ChecklistItemType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ChecklistItemNotFoundException(null, value);
        }
    }

    private ChecklistItemStatus parseItemStatus(String value) {
        try {
            return ChecklistItemStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidSiteTransitionException("Unknown checklist status: " + value);
        }
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
