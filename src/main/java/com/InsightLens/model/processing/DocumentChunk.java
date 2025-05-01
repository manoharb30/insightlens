package com.InsightLens.model.processing; // Recommended package for processing models

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; // Add NoArgsConstructor for potential deserialization
import lombok.AllArgsConstructor; // Add AllArgsConstructor

/**
 * Data class representing a chunk of a document's text along with its metadata
 * during the processing pipeline. This is NOT a JPA entity.
 */
@Data // Lombok annotation for getters, setters, toString, equals, hashCode
@Builder // Lombok annotation for builder pattern
@NoArgsConstructor // Needed for some frameworks/libraries
@AllArgsConstructor // Needed for @Builder
public class DocumentChunk {
    private String text; // The actual text content of the chunk
    private int originalOrder; // The zero-based index of this chunk in the document sequence
    private int startIndex; // The starting character index of this chunk in the original raw text
    private int endIndex; // The ending character index (exclusive) of this chunk in the original raw text
    private String sectionTitle; // The detected heading/section title associated with this chunk (can be null)

    // You might add a constructor here if not using Lombok, or if you need specific initialization logic
    // public DocumentChunk(String text, int originalOrder, int startIndex, int endIndex, String sectionTitle) {
    //     this.text = text;
    //     this.originalOrder = originalOrder;
    //     this.startIndex = startIndex;
    //     this.endIndex = endIndex;
    //     this.sectionTitle = sectionTitle;
    // }
}