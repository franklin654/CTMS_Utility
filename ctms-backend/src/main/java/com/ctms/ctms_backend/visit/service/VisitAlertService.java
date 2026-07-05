package com.ctms.ctms_backend.visit.service;

import com.ctms.ctms_backend.notification.NotificationService;
import com.ctms.ctms_backend.subject.entity.Subject;
import com.ctms.ctms_backend.visit.entity.Visit;
import com.ctms.ctms_backend.visit.entity.VisitStatus;
import com.ctms.ctms_backend.visit.repository.VisitRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BRD Epic 4 Story 03 (Generate Visit Alerts and Notifications). Fixed, BRD-literal thresholds
 * (not per-study configurable, since these are the exact examples the BRD gives): "due tomorrow"
 * = scheduledDate == today + 1; "overdue" = today is past scheduledDate + windowLateDays. Each
 * alert is sent at most once per visit (dedup via NotificationService.alreadyNotified) -- the
 * "Visit missed" alert (the third BRD example) fires synchronously from VisitService.markMissed
 * instead of this batch sweep. */
@Service
public class VisitAlertService {

    static final String TYPE_DUE_TOMORROW = "VISIT_DUE_TOMORROW";
    static final String TYPE_OVERDUE = "VISIT_OVERDUE";

    private final VisitRepository visitRepository;
    private final NotificationService notificationService;

    public VisitAlertService(VisitRepository visitRepository, NotificationService notificationService) {
        this.visitRepository = visitRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void runDailyAlertSweep() {
        LocalDate today = LocalDate.now();

        List<Visit> dueTomorrow = visitRepository.findByStatusAndScheduledDate(VisitStatus.SCHEDULED, today.plusDays(1));
        for (Visit visit : dueTomorrow) {
            sendIfNotAlreadySent(visit, TYPE_DUE_TOMORROW, "Visit due tomorrow: " + visit.getName(),
                    "Subject " + visit.getSubject().getSubjectCode() + "'s visit \"" + visit.getName()
                            + "\" is scheduled for tomorrow (" + visit.getScheduledDate() + ").");
        }

        List<Visit> candidates = visitRepository.findByStatusAndScheduledDateLessThan(VisitStatus.SCHEDULED, today);
        for (Visit visit : candidates) {
            if (today.isAfter(visit.getScheduledDate().plusDays(visit.getWindowLateDays()))) {
                long overdueDays = java.time.temporal.ChronoUnit.DAYS.between(visit.getScheduledDate(), today);
                sendIfNotAlreadySent(visit, TYPE_OVERDUE, "Visit overdue by " + overdueDays + " days: " + visit.getName(),
                        "Subject " + visit.getSubject().getSubjectCode() + "'s visit \"" + visit.getName()
                                + "\" is overdue by " + overdueDays + " days (was due " + visit.getScheduledDate() + ").");
            }
        }
    }

    private void sendIfNotAlreadySent(Visit visit, String type, String title, String body) {
        Subject subject = visit.getSubject();
        String link = visitLink(visit);

        if (!notificationService.alreadyNotified(subject.getCreatedBy().getId(), type, link)) {
            notificationService.notify(subject.getCreatedBy().getId(), type, title, body, link);
        }
        if (subject.getSite().getAssignedCra() != null
                && !notificationService.alreadyNotified(subject.getSite().getAssignedCra().getId(), type, link)) {
            notificationService.notify(subject.getSite().getAssignedCra().getId(), type, title, body, link);
        }
    }

    private String visitLink(Visit visit) {
        return "/subjects/" + visit.getSubject().getId() + "/visits/" + visit.getId();
    }
}
