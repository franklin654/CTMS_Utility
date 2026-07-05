package com.ctms.ctms_backend.monitoring.service;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.StorageService;
import com.ctms.ctms_backend.monitoring.dto.MonitoringVisitReportResponse;
import com.ctms.ctms_backend.monitoring.entity.MonitoringVisit;
import com.ctms.ctms_backend.monitoring.entity.MonitoringVisitReport;
import com.ctms.ctms_backend.monitoring.exception.MonitoringVisitReportNotFoundException;
import com.ctms.ctms_backend.monitoring.repository.MonitoringVisitReportRepository;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** BL Epic 6 Story 02 ("reports can be uploaded"). Mirrors TestResultAttachmentService's exact
 * storeVersion/downloadVersion pattern -- same StorageService, same SHA-256 checksum computation. */
@Service
public class MonitoringVisitReportService {

    private final MonitoringVisitReportRepository reportRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    public MonitoringVisitReportService(
            MonitoringVisitReportRepository reportRepository,
            UserRepository userRepository,
            StorageService storageService,
            AuditService auditService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @Transactional
    public MonitoringVisitReportResponse upload(MonitoringVisit visit, MultipartFile file, String actorUsername) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded file", e);
        }
        String checksum = sha256(bytes);
        String storageKey = storageService.store(new ByteArrayInputStream(bytes), file.getOriginalFilename());
        User actor = currentUser(actorUsername);

        MonitoringVisitReport report = new MonitoringVisitReport();
        report.setMonitoringVisit(visit);
        report.setFileName(file.getOriginalFilename());
        report.setStoragePath(storageKey);
        report.setContentType(file.getContentType());
        report.setSizeBytes(file.getSize());
        report.setChecksumSha256(checksum);
        report.setUploadedBy(actor);
        report = reportRepository.save(report);

        auditService.record(
                "MonitoringVisitReport", String.valueOf(report.getId()), AuditAction.CREATE,
                null, "uploaded " + report.getFileName() + " for monitoring visit " + visit.getId(), null);

        return MonitoringVisitReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public List<MonitoringVisitReportResponse> list(Long monitoringVisitId) {
        return reportRepository.findByMonitoringVisitId(monitoringVisitId).stream()
                .map(MonitoringVisitReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitoringVisitReport findReport(Long id) {
        return reportRepository.findById(id).orElseThrow(() -> new MonitoringVisitReportNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public InputStream download(Long reportId) {
        MonitoringVisitReport report = findReport(reportId);
        auditService.record(
                "MonitoringVisitReport", String.valueOf(reportId), AuditAction.ACCESS,
                null, "downloaded " + report.getFileName(), null);
        return storageService.retrieve(report.getStoragePath());
    }

    private User currentUser(String username) {
        return userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
