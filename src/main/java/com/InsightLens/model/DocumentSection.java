package com.InsightLens.model; // Adjust package as necessary

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity // Marks this class as a JPA entity
@Data // Lombok annotation to generate getters, setters, toString, equals, and hashCode
@NoArgsConstructor // Lombok annotation to generate a no-argument constructor
@AllArgsConstructor // Lombok annotation to generate an all-argument constructor
@Builder // Lombok annotation to generate a builder pattern
public class DocumentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-generate ID
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // Many sections belong to One document
    @JoinColumn(name = "document_id", nullable = false) // Foreign key column
    private Document document; // Link back to the parent document

    @Column(columnDefinition = "TEXT") // Use TEXT type for potentially large section text
    private String text; // The text content of the section/chunk

    // Add the new metadata fields captured during chunking (Task 2.4)
    private int originalOrder; // The original order of this section within the document
    private int startIndex; // The starting character index in the original raw text
    private int endIndex; // The ending character index (exclusive) in the original raw text

    @Column(columnDefinition = "VARCHAR(500)") // Adjust size as needed for section titles
    private String sectionTitle; // The detected heading/section title (can be null)

    // Use columnDefinition = "VECTOR(384)" if your database supports pgvector or similar
    // Otherwise, store as byte[] or another suitable type depending on your DB and JPA provider
    @Column(columnDefinition = "VECTOR(384)") // Example for pgvector
    private float[] embedding; // The vector embedding for this section's text

    // Consider adding fields for AI insights specific to this section later

    private LocalDateTime createdAt; // Timestamp of entity creation
    private LocalDateTime updatedAt; // Timestamp of last update

    // Note: Add or adjust columnDefinition based on your specific database and its vector type support.
    // If not using a vector type directly, you might need to handle serialization/deserialization of float[]
    // or store as a BLOB/BYTEA and manage it manually or with a custom type.
}
