package com.ctms.ctms_backend.monitoring.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.monitoring.dto.LogMonitoringVisitRequest;
import com.ctms.ctms_backend.monitoring.dto.MonitoringVisitResponse;
import com.ctms.ctms_backend.monitoring.entity.MonitoringVisit;
import com.ctms.ctms_backend.monitoring.entity.MonitoringVisitType;
import com.ctms.ctms_backend.monitoring.exception.MonitoringVisitNotFoundException;
import com.ctms.ctms_backend.monitoring.repository.MonitoringVisitRepository;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.exception.SiteNotFoundException;
import com.ctms.ctms_backend.site.repository.SiteRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL Epic 6 Story 02. A log entry, not a lifecycle -- see MonitoringVisit's javadoc. Backup CRA
 * has symmetric permissions with the primary (business-continuity intent), enforced simply by not
 * distinguishing the two anywhere in this service's write-role check (both are just "a CRA"). */
@Service
public class MonitoringVisitService {

    private final MonitoringVisitRepository monitoringVisitRepository;
    private final SiteRepository siteRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public MonitoringVisitService(
            MonitoringVisitRepository monitoringVisitRepository,
            SiteRepository siteRepository,
            UserRepository userRepository,
            AuditService auditService) {
        this.monitoringVisitRepository = monitoringVisitRepository;
        this.siteRepository = siteRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional
    public MonitoringVisitResponse log(LogMonitoringVisitRequest req, String actorUsername) {
        Site site = siteRepository.findById(req.siteId()).orElseThrow(() -> new SiteNotFoundException(req.siteId()));
        User actor = currentUser(actorUsername);

        MonitoringVisit visit = new MonitoringVisit();
        visit.setSite(site);
        visit.setCra(actor);
        visit.setVisitType(MonitoringVisitType.valueOf(req.visitType()));
        visit.setVisitDate(req.visitDate());
        visit.setFindings(req.findings());
        visit.setIssuesIdentified(req.issuesIdentified());
        visit.setChecklistNotes(req.checklistNotes());
        visit.setCreatedBy(actor);
        visit.setModifiedBy(actor);
        visit = monitoringVisitRepository.save(visit);

        auditService.record(
                "MonitoringVisit", String.valueOf(visit.getId()), AuditAction.CREATE,
                null, req.visitType() + " logged for site " + site.getSiteCode(), null);

        return MonitoringVisitResponse.from(visit);
    }

    @Transactional
    public MonitoringVisitResponse update(Long id, LogMonitoringVisitRequest req, String actorUsername) {
        MonitoringVisit visit = findMonitoringVisit(id);
        User actor = currentUser(actorUsername);

        String before = visit.getVisitType() + " on " + visit.getVisitDate();
        visit.setVisitType(MonitoringVisitType.valueOf(req.visitType()));
        visit.setVisitDate(req.visitDate());
        visit.setFindings(req.findings());
        visit.setIssuesIdentified(req.issuesIdentified());
        visit.setChecklistNotes(req.checklistNotes());
        visit.setModifiedBy(actor);
        visit = monitoringVisitRepository.save(visit);

        auditService.record(
                "MonitoringVisit", String.valueOf(id), AuditAction.UPDATE,
                before, req.visitType() + " on " + req.visitDate(), null);

        return MonitoringVisitResponse.from(visit);
    }

    @Transactional(readOnly = true)
    public List<MonitoringVisitResponse> list(Long siteId) {
        return monitoringVisitRepository.findBySiteIdOrderByVisitDateDesc(siteId).stream()
                .map(MonitoringVisitResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitoringVisitResponse get(Long id) {
        return MonitoringVisitResponse.from(findMonitoringVisit(id));
    }

    @Transactional(readOnly = true)
    public MonitoringVisit findMonitoringVisit(Long id) {
        return monitoringVisitRepository.findById(id).orElseThrow(() -> new MonitoringVisitNotFoundException(id));
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
