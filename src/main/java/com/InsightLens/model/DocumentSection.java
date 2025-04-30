package com.InsightLens.model;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_sections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    private String text;

    @Column(columnDefinition = "vector(384)")
    private float[] embedding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;
}
