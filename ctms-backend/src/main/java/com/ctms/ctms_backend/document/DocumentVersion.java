package com.ctms.ctms_backend.document;

import com.ctms.ctms_backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Once created, a version's storage/checksum fields are never mutated -- only {@code status}
 * transitions (e.g. CURRENT -> ARCHIVED when superseded, or the Phase 2 approval workflow states). */
@Entity
@Table(name = "document_version")
@Getter
@Setter
@NoArgsConstructor
public class DocumentVersion {

    public static final String STATUS_CURRENT = "CURRENT";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    void onCreate() {
        this.uploadedAt = Instant.now();
    }
}
