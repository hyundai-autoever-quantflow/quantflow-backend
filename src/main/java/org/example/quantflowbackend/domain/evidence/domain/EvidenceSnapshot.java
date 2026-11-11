package org.example.quantflowbackend.domain.evidence.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "evidence_snapshots",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_evidence_snapshots_1",
                        columnNames = {"provider", "document_key", "version"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id")
    private Long evidenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 20)
    private EvidenceType evidenceType;

    @Column(name = "source", nullable = false, length = 200)
    private String source;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "provider_timestamp", nullable = false)
    private LocalDateTime providerTimestamp;

    @Column(name = "public_at")
    private LocalDateTime publicAt;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "dart_receipt_no", length = 30)
    private String dartReceiptNo;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "document_key", nullable = false, length = 255)
    private String documentKey;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "source_updated_at")
    private LocalDateTime sourceUpdatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "previous_evidence_id", nullable = true)
    private EvidenceSnapshot previousEvidence;

    @Builder
    private EvidenceSnapshot(
            EvidenceType evidenceType,
            String source,
            String title,
            String originalUrl,
            String summary,
            LocalDateTime providerTimestamp,
            LocalDateTime publicAt,
            LocalDateTime collectedAt,
            String contentHash,
            String dartReceiptNo,
            String provider,
            String documentKey,
            Integer version,
            LocalDateTime sourceUpdatedAt,
            EvidenceSnapshot previousEvidence
    ) {
        this.evidenceType = Objects.requireNonNull(evidenceType, "필수 값입니다: evidenceType");
        this.source = Objects.requireNonNull(source, "필수 값입니다: source");
        this.title = Objects.requireNonNull(title, "필수 값입니다: title");
        this.originalUrl = Objects.requireNonNull(originalUrl, "필수 값입니다: originalUrl");
        this.summary = summary;
        this.providerTimestamp = Objects.requireNonNull(providerTimestamp, "필수 값입니다: providerTimestamp");
        this.publicAt = publicAt;
        this.collectedAt = Objects.requireNonNull(collectedAt, "필수 값입니다: collectedAt");
        this.contentHash = Objects.requireNonNull(contentHash, "필수 값입니다: contentHash");
        this.dartReceiptNo = dartReceiptNo;
        this.provider = Objects.requireNonNull(provider, "필수 값입니다: provider");
        this.documentKey = Objects.requireNonNull(documentKey, "필수 값입니다: documentKey");
        this.version = Objects.requireNonNull(version, "필수 값입니다: version");
        this.sourceUpdatedAt = sourceUpdatedAt;
        this.previousEvidence = previousEvidence;
    }

    public static EvidenceSnapshot create(
            EvidenceType evidenceType,
            String source,
            String title,
            String originalUrl,
            String summary,
            LocalDateTime providerTimestamp,
            LocalDateTime publicAt,
            LocalDateTime collectedAt,
            String contentHash,
            String dartReceiptNo,
            String provider,
            String documentKey,
            Integer version,
            LocalDateTime sourceUpdatedAt,
            EvidenceSnapshot previousEvidence
    ) {
        return EvidenceSnapshot.builder()
                .evidenceType(evidenceType)
                .source(source)
                .title(title)
                .originalUrl(originalUrl)
                .summary(summary)
                .providerTimestamp(providerTimestamp)
                .publicAt(publicAt)
                .collectedAt(collectedAt)
                .contentHash(contentHash)
                .dartReceiptNo(dartReceiptNo)
                .provider(provider)
                .documentKey(documentKey)
                .version(version)
                .sourceUpdatedAt(sourceUpdatedAt)
                .previousEvidence(previousEvidence)
                .build();
    }
}
