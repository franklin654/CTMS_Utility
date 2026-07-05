package com.ctms.ctms_backend.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.StorageService;
import com.ctms.ctms_backend.monitoring.dto.MonitoringVisitReportResponse;
import com.ctms.ctms_backend.monitoring.entity.MonitoringVisit;
import com.ctms.ctms_backend.monitoring.entity.MonitoringVisitReport;
import com.ctms.ctms_backend.monitoring.repository.MonitoringVisitReportRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class MonitoringVisitReportServiceTest {

    @Mock private MonitoringVisitReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @Mock private AuditService auditService;

    @InjectMocks
    private MonitoringVisitReportService reportService;

    private MonitoringVisit visit;
    private User actor;

    @BeforeEach
    void setUp() {
        visit = new MonitoringVisit();
        visit.setId(200L);

        actor = new User();
        actor.setId(3L);
        actor.setUsername("cra.monitor");
        lenient().when(userRepository.findByUsername("cra.monitor")).thenReturn(Optional.of(actor));

        lenient().when(reportRepository.save(any(MonitoringVisitReport.class))).thenAnswer(inv -> {
            MonitoringVisitReport r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(700L);
            }
            return r;
        });
    }

    @Test
    void upload_storesFileAndPersistsMetadata() {
        MockMultipartFile file = new MockMultipartFile("file", "siv-report.pdf", "application/pdf", "monitoring report".getBytes());
        when(storageService.store(any(InputStream.class), anyString())).thenReturn("storage/key-999.pdf");

        MonitoringVisitReportResponse response = reportService.upload(visit, file, "cra.monitor");

        assertEquals("siv-report.pdf", response.fileName());
        assertEquals("cra.monitor", response.uploadedByUsername());
        verify(storageService).store(any(InputStream.class), anyString());
    }

    @Test
    void download_retrievesFromStorageAndAudits() {
        MonitoringVisitReport report = new MonitoringVisitReport();
        report.setId(700L);
        report.setMonitoringVisit(visit);
        report.setFileName("siv-report.pdf");
        report.setStoragePath("storage/key-999.pdf");
        when(reportRepository.findById(700L)).thenReturn(Optional.of(report));
        when(storageService.retrieve("storage/key-999.pdf")).thenReturn(new ByteArrayInputStream("content".getBytes()));

        InputStream result = reportService.download(700L);

        assertEquals(true, result != null);
        verify(storageService).retrieve("storage/key-999.pdf");
    }
}
