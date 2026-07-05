package com.ctms.ctms_backend.dashboard.service;

import com.ctms.ctms_backend.adverseevent.entity.AdverseEventSeverity;
import com.ctms.ctms_backend.adverseevent.entity.AdverseEventStatus;
import com.ctms.ctms_backend.adverseevent.repository.AdverseEventRepository;
import com.ctms.ctms_backend.dashboard.dto.DashboardFilterOptionsResponse;
import com.ctms.ctms_backend.dashboard.dto.DashboardSummaryResponse;
import com.ctms.ctms_backend.dashboard.dto.HighRiskSiteResponse;
import com.ctms.ctms_backend.milestone.dto.MilestoneResponse;
import com.ctms.ctms_backend.milestone.repository.MilestoneRepository;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.entity.SiteStatus;
import com.ctms.ctms_backend.site.repository.SiteRepository;
import com.ctms.ctms_backend.study.repository.StudyRepository;
import com.ctms.ctms_backend.subject.entity.SubjectStatus;
import com.ctms.ctms_backend.subject.repository.SubjectRepository;
import com.ctms.ctms_backend.user.Role;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL Epic 6 Story 03 (Real-Time Study Dashboards). "High-risk site" is a FIXED, deterministic
 * threshold in code (missed-visit rate > 20% OR 2+ open SEVERE/LIFE_THREATENING AEs) -- CLAUDE.md
 * S2.1 bans scored/predictive risk models, and this is intentionally kept simple for now. Per an
 * explicit product decision, this should be revisited via the Drools rules engine (like
 * TaskRuleService) for per-study configurability in a later phase -- these constants are named
 * and centralized here specifically so that migration is easy when it happens.
 *
 * <p>Dashboard scoping: CRA_MONITOR-only callers (no broader role) see only their
 * assigned/backup sites; every other role sees portfolio-wide data. Site.principalInvestigatorName
 * is a free-text string, not a User FK, so Investigator/Site Coordinator dashboards cannot
 * actually be site-scoped with data that exists today -- a known, documented gap, not a silent
 * drop. */
@Service
public class DashboardService {

    static final double HIGH_RISK_MISSED_VISIT_RATE_PERCENT_THRESHOLD = 20.0;
    static final long HIGH_RISK_OPEN_AE_COUNT_THRESHOLD = 2;
    private static final List<AdverseEventSeverity> HIGH_SEVERITIES =
            List.of(AdverseEventSeverity.SEVERE, AdverseEventSeverity.LIFE_THREATENING);

    private final SiteRepository siteRepository;
    private final SubjectRepository subjectRepository;
    private final VisitRepository visitRepository;
    private final AdverseEventRepository adverseEventRepository;
    private final MilestoneRepository milestoneRepository;
    private final UserRepository userRepository;
    private final StudyRepository studyRepository;

    public DashboardService(
            SiteRepository siteRepository,
            SubjectRepository subjectRepository,
            VisitRepository visitRepository,
            AdverseEventRepository adverseEventRepository,
            MilestoneRepository milestoneRepository,
            UserRepository userRepository,
            StudyRepository studyRepository) {
        this.siteRepository = siteRepository;
        this.subjectRepository = subjectRepository;
        this.visitRepository = visitRepository;
        this.adverseEventRepository = adverseEventRepository;
        this.milestoneRepository = milestoneRepository;
        this.userRepository = userRepository;
        this.studyRepository = studyRepository;
    }

    /** Backs the country/phase autocomplete filters -- both fields are free text (no fixed
     * vocabulary in the BRD), so suggestions are the distinct values already in use rather than a
     * hardcoded list. */
    @Transactional(readOnly = true)
    public DashboardFilterOptionsResponse filterOptions() {
        return new DashboardFilterOptionsResponse(siteRepository.findDistinctCountries(), studyRepository.findDistinctPhases());
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(Long studyId, String country, Long siteId, String phase, String actorUsername) {
        List<Site> sites = siteRepository.findForDashboard(studyId, country, siteId, phase);

        if (isCraScoped(currentRoleCodes())) {
            User actor = currentUser(actorUsername);
            Set<Long> craSiteIds = siteRepository.findByAssignedOrBackupCra(actor.getId()).stream()
                    .map(Site::getId)
                    .collect(Collectors.toSet());
            sites = sites.stream().filter(s -> craSiteIds.contains(s.getId())).toList();
        }

        List<Long> siteIds = sites.stream().map(Site::getId).toList();

        Map<String, Long> enrollmentByStatus = new LinkedHashMap<>();
        for (SubjectStatus status : SubjectStatus.values()) {
            enrollmentByStatus.put(status.name(), siteIds.isEmpty() ? 0L : subjectRepository.countBySiteIdInAndStatus(siteIds, status));
        }

        Map<String, Long> siteActivationByStatus = new LinkedHashMap<>();
        for (SiteStatus status : SiteStatus.values()) {
            siteActivationByStatus.put(status.name(), sites.stream().filter(s -> s.getStatus() == status).count());
        }

        Map<String, Long> sitesByCountry =
                sites.stream().collect(Collectors.groupingBy(Site::getCountry, LinkedHashMap::new, Collectors.counting()));

        Map<Long, Map<VisitStatus, Long>> visitCountsBySite = new HashMap<>();
        if (!siteIds.isEmpty()) {
            for (Object[] row : visitRepository.countByStatusGroupedBySite(siteIds)) {
                Long sid = (Long) row[0];
                VisitStatus status = (VisitStatus) row[1];
                Long count = (Long) row[2];
                visitCountsBySite.computeIfAbsent(sid, k -> new EnumMap<>(VisitStatus.class)).put(status, count);
            }
        }
        long totalCompleted = visitCountsBySite.values().stream().mapToLong(m -> m.getOrDefault(VisitStatus.COMPLETED, 0L)).sum();
        long totalMissed = visitCountsBySite.values().stream().mapToLong(m -> m.getOrDefault(VisitStatus.MISSED, 0L)).sum();
        Double visitAdherenceRatePercent =
                (totalCompleted + totalMissed) == 0 ? null : (100.0 * totalCompleted / (totalCompleted + totalMissed));

        Map<Long, Long> openHighSeverityAeCountBySite = new HashMap<>();
        if (!siteIds.isEmpty()) {
            for (Object[] row : adverseEventRepository.countHighSeverityOpenGroupedBySite(siteIds, HIGH_SEVERITIES, AdverseEventStatus.RESOLVED)) {
                openHighSeverityAeCountBySite.put((Long) row[0], (Long) row[1]);
            }
        }

        List<HighRiskSiteResponse> highRiskSites = new ArrayList<>();
        for (Site site : sites) {
            Map<VisitStatus, Long> counts = visitCountsBySite.getOrDefault(site.getId(), Map.of());
            long completed = counts.getOrDefault(VisitStatus.COMPLETED, 0L);
            long missed = counts.getOrDefault(VisitStatus.MISSED, 0L);
            double missedVisitRatePercent = (completed + missed) == 0 ? 0.0 : (100.0 * missed / (completed + missed));
            long openAeCount = openHighSeverityAeCountBySite.getOrDefault(site.getId(), 0L);

            if (missedVisitRatePercent > HIGH_RISK_MISSED_VISIT_RATE_PERCENT_THRESHOLD
                    || openAeCount >= HIGH_RISK_OPEN_AE_COUNT_THRESHOLD) {
                highRiskSites.add(new HighRiskSiteResponse(site.getId(), site.getSiteCode(), site.getName(), missedVisitRatePercent, openAeCount));
            }
        }

        Set<Long> studyIds = sites.stream().map(s -> s.getStudy().getId()).collect(Collectors.toSet());
        List<MilestoneResponse> milestones = studyIds.stream()
                .flatMap(id -> milestoneRepository.findByStudyId(id).stream())
                .map(MilestoneResponse::from)
                .toList();

        return new DashboardSummaryResponse(enrollmentByStatus, siteActivationByStatus, visitAdherenceRatePercent, sitesByCountry, highRiskSites, milestones);
    }

    private boolean isCraScoped(Set<String> roleCodes) {
        return roleCodes.size() == 1 && roleCodes.contains(Role.CRA_MONITOR);
    }

    private Set<String> currentRoleCodes() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }
}
