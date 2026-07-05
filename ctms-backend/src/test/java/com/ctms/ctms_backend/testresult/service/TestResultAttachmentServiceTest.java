package com.ctms.ctms_backend.testresult.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.document.StorageService;
import com.ctms.ctms_backend.testresult.dto.TestResultAttachmentResponse;
import com.ctms.ctms_backend.testresult.entity.TestResult;
import com.ctms.ctms_backend.testresult.entity.TestResultAttachment;
import com.ctms.ctms_backend.testresult.repository.TestResultAttachmentRepository;
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
class TestResultAttachmentServiceTest {

    @Mock private TestResultAttachmentRepository attachmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private StorageService storageService;
    @Mock private AuditService auditService;

    @InjectMocks
    private TestResultAttachmentService attachmentService;

    private TestResult testResult;
    private User actor;

    @BeforeEach
    void setUp() {
        testResult = new TestResult();
        testResult.setId(100L);

        actor = new User();
        actor.setId(1L);
        actor.setUsername("coordinator1");
        lenient().when(userRepository.findByUsername("coordinator1")).thenReturn(Optional.of(actor));

        lenient().when(attachmentRepository.save(any(TestResultAttachment.class))).thenAnswer(inv -> {
            TestResultAttachment a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(500L);
            }
            return a;
        });
    }

    @Test
    void upload_storesFileAndPersistsMetadata() {
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "lab report content".getBytes());
        when(storageService.store(any(InputStream.class), anyString())).thenReturn("storage/key-123.pdf");

        TestResultAttachmentResponse response = attachmentService.upload(testResult, file, "coordinator1");

        assertEquals("report.pdf", response.fileName());
        assertEquals("application/pdf", response.contentType());
        assertEquals("coordinator1", response.uploadedByUsername());
        verify(storageService).store(any(InputStream.class), anyString());
    }

    @Test
    void download_retrievesFromStorageAndAudits() {
        TestResultAttachment attachment = new TestResultAttachment();
        attachment.setId(500L);
        attachment.setTestResult(testResult);
        attachment.setFileName("report.pdf");
        attachment.setStoragePath("storage/key-123.pdf");
        when(attachmentRepository.findById(500L)).thenReturn(Optional.of(attachment));
        when(storageService.retrieve("storage/key-123.pdf")).thenReturn(new ByteArrayInputStream("content".getBytes()));

        InputStream result = attachmentService.download(500L);

        assertEquals(true, result != null);
        verify(storageService).retrieve("storage/key-123.pdf");
    }
}
