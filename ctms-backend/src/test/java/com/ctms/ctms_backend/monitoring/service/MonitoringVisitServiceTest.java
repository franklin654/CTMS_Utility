package com.ctms.ctms_backend.monitoring.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.monitoring.dto.LogMonitoringVisitRequest;
import com.ctms.ctms_backend.monitoring.dto.MonitoringVisitResponse;
import com.ctms.ctms_backend.monitoring.entity.MonitoringVisit;
import com.ctms.ctms_backend.monitoring.repository.MonitoringVisitRepository;
import com.ctms.ctms_backend.site.entity.Site;
import com.ctms.ctms_backend.site.repository.SiteRepository;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitoringVisitServiceTest {

    @Mock private MonitoringVisitRepository monitoringVisitRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private MonitoringVisitService monitoringVisitService;

    private Site site;
    private User cra;

    @BeforeEach
    void setUp() {
        site = new Site();
        site.setId(50L);
        site.setSiteCode("SITE-050");

        cra = new User();
        cra.setId(3L);
        cra.setUsername("cra.monitor");

        lenient().when(siteRepository.findById(50L)).thenReturn(Optional.of(site));
        lenient().when(userRepository.findByUsername("cra.monitor")).thenReturn(Optional.of(cra));
        lenient().when(monitoringVisitRepository.save(any(MonitoringVisit.class))).thenAnswer(inv -> {
            MonitoringVisit v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(200L);
            }
            return v;
        });
    }

    @Test
    void log_happyPath_savesAndAudits() {
        LogMonitoringVisitRequest req = new LogMonitoringVisitRequest(50L, "SIV", LocalDate.of(2026, 1, 10), "All good", null, null);
        MonitoringVisitResponse response = monitoringVisitService.log(req, "cra.monitor");

        assertEquals("SIV", response.visitType());
        assertEquals("SITE-050", response.siteCode());
        assertEquals("cra.monitor", response.craUsername());
    }

    @Test
    void update_changesFieldsAndAudits() {
        MonitoringVisit existing = new MonitoringVisit();
        existing.setId(200L);
        existing.setSite(site);
        existing.setCra(cra);
        existing.setVisitType(com.ctms.ctms_backend.monitoring.entity.MonitoringVisitType.SIV);
        existing.setVisitDate(LocalDate.of(2026, 1, 10));
        existing.setCreatedBy(cra);
        lenient().when(monitoringVisitRepository.findById(200L)).thenReturn(Optional.of(existing));

        LogMonitoringVisitRequest req = new LogMonitoringVisitRequest(50L, "IMV", LocalDate.of(2026, 2, 1), "Updated findings", "Issue A", "Checked X");
        MonitoringVisitResponse response = monitoringVisitService.update(200L, req, "cra.monitor");

        assertEquals("IMV", response.visitType());
        assertEquals("Updated findings", response.findings());
    }
}
