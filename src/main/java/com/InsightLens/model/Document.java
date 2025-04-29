package com.InsightLens.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_domain_type", columnList = "domainType"),
    @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;
    private String originalFilename;
    private String fileType; // pdf/docx/txt
    private Long fileSize; // bytes

    @Enumerated(EnumType.STRING)
    private DocumentStatus status;

    private String domainType; // legal/finance/medical (auto-detected)
    private Integer version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    private Document previousVersion; // For version tracking

    // Relationships
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentSection> sections; // Text chunks + embeddings

    @OneToMany(mappedBy = "documentA", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentComparison> comparisons;

    // Large fields (lazy-loaded)
    @Lob
    @Basic(fetch = FetchType.LAZY)
    private String summary;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> tags;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.JSON)
    private Object insights; // Structured insights (not raw JSON)

    // Timestamps
    private LocalDateTime uploadDate;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum DocumentStatus {
        UPLOADED, PROCESSING, PROCESSED, FAILED
    }
}