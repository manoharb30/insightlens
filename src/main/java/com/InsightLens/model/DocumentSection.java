package com.InsightLens.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode; // Needed for JSON/Vector types if using Hibernate
import org.hibernate.type.SqlTypes; // Needed for JSON/Vector types if using Hibernate

@Entity
@Table(name = "document_sections", indexes = {
     @Index(name = "idx_section_document", columnList = "document_id") // Index on FK
})
@Data
@Builder // Generates the builder, including sectionOrder()
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) // Lazy fetch for document
    @JoinColumn(name = "document_id") // Define FK column name
    private Document document; // Link back to the Document

    @Lob // Use Lob for potentially large text
    @Column(columnDefinition = "TEXT") // Store as TEXT in DB
    private String text;

    // ✅ Add the missing sectionOrder field
    private int sectionOrder; // To maintain original document order

    // PGVector embedding - columnDefinition specifies the type and dimension
    // Dimension 384 is common for models like bge-small
    @Column(name = "embedding", columnDefinition = "vector(384)")
    private float[] embedding; // Use float[] for vector type

    // Optional: Start and end character indices for the section in the original text
    // private int startChar;
    // private int endChar;

    // Optional: Section type (e.g., "heading", "paragraph", "list_item")
    // private String sectionType;
}
