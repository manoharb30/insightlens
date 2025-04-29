package com.InsightLens.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_comparisons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_a_id")
    private Document documentA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_b_id")
    private Document documentB;

    @Lob
    private String diffSummary; // AI-generated differences
}