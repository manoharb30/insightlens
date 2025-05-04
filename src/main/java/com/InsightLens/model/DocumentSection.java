package com.InsightLens.model; // Adjust package as necessary

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
// Removed import java.util.List; as it's not used directly here

@Entity // Marks this class as a JPA entity
@Table(name = "document_section") // Explicit table name often good practice
@Data // Lombok annotation
@NoArgsConstructor // Lombok annotation
@AllArgsConstructor // Lombok annotation
@Builder // Lombok annotation
public class DocumentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(columnDefinition = "TEXT")
    private String text;

    private int originalOrder;
    private int startIndex;
    private int endIndex;

    @Column(columnDefinition = "VARCHAR(500)")
    private String sectionTitle;

    // Keep the column definition for schema generation by Hibernate
    @Column(columnDefinition = "VECTOR(1536)", nullable = true) // Ensure dimension is correct (1536 for text-embedding-3-small)
    // Mark as Transient so Hibernate doesn't try to load/map it directly
    @Transient
    private float[] embedding;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Add PrePersist and PreUpdate methods to set timestamps automatically
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // NOTE: Because 'embedding' is @Transient, Hibernate will NOT automatically
    // persist the float[] array when using standard repository.save().
    // The saving logic in DocumentService might need adjustment later.
}
