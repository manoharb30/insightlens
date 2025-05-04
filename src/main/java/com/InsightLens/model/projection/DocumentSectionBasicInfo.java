package com.InsightLens.model.projection;

import java.time.LocalDateTime;

/**
 * Spring Data Projection interface to select basic DocumentSection fields,
 * EXCLUDING the 'embedding' field for bulk fetches, avoiding mapping issues.
 */
public interface DocumentSectionBasicInfo {
    Long getId();
    String getText(); // Keep text if needed for snippets later
    int getOriginalOrder();
    int getStartIndex();
    int getEndIndex();
    String getSectionTitle();
    // Timestamps might not be needed here unless specifically required
    // LocalDateTime getCreatedAt();
    // LocalDateTime getUpdatedAt();

    // Include a nested projection to get the parent document's ID
    // This ensures the link is available if needed without fetching the whole Document entity
    DocumentInfo getDocument();

    interface DocumentInfo {
        Long getId();
    }
}
