package com.ctms.ctms_backend.milestone.service;

import com.ctms.ctms_backend.milestone.entity.Milestone;
import com.ctms.ctms_backend.milestone.repository.MilestoneRepository;
import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.study.entity.Study;
import java.time.LocalDate;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BL Epic 6 Story 04 ("system alerts stakeholders nearing deadlines"). Mirrors
 * VisitAlertService's exact pattern: fixed 7-day lookahead, notification-only (no Task), each
 * alert sent at most once per milestone via NotificationService.alreadyNotified dedup. */
@Service
public class MilestoneAlertService {

    static final String TYPE_MILESTONE_NEARING_DEADLINE = "MILESTONE_NEARING_DEADLINE";
    private static final int LOOKAHEAD_DAYS = 7;

    private final MilestoneRepository milestoneRepository;
    private final NotificationService notificationService;

    public MilestoneAlertService(MilestoneRepository milestoneRepository, NotificationService notificationService) {
        this.milestoneRepository = milestoneRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void runDailyAlertSweep() {
        LocalDate today = LocalDate.now();
        List<Milestone> nearingDeadline =
                milestoneRepository.findByActualDateIsNullAndPlannedDateBetween(today, today.plusDays(LOOKAHEAD_DAYS));

        for (Milestone milestone : nearingDeadline) {
            Study study = milestone.getStudy();
            String link = milestoneLink(study.getId(), milestone.getId());
            String title = milestone.getMilestoneType() + " nearing deadline: " + study.getStudyCode();
            String body = "Study " + study.getStudyCode() + "'s " + milestone.getMilestoneType()
                    + " milestone is planned for " + milestone.getPlannedDate() + " and has not yet been reached.";

            if (!notificationService.alreadyNotified(study.getCreatedBy().getId(), TYPE_MILESTONE_NEARING_DEADLINE, link)) {
                notificationService.notify(study.getCreatedBy().getId(), TYPE_MILESTONE_NEARING_DEADLINE, title, body, link);
            }
        }
    }

    private String milestoneLink(Long studyId, Long milestoneId) {
        return "/studies/" + studyId + "/milestones?highlight=" + milestoneId;
    }
}
