package com.InsightLens.repository;

import com.InsightLens.model.DocumentSection;
import com.InsightLens.model.projection.DocumentSectionBasicInfo; // Import projection
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
// No @Param needed for positional parameters
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentSectionRepository extends JpaRepository<DocumentSection, Long> {

    /**
     * Finds basic info for all sections belonging to a specific document,
     * ordered by their original appearance. Uses a projection to avoid
     * fetching the 'embedding' column directly in this query.
     *
     * @param documentId The ID of the parent Document.
     * @return A list of DocumentSectionBasicInfo projections.
     */
    List<DocumentSectionBasicInfo> findByDocumentIdOrderByOriginalOrderAsc(Long documentId); // Return projection


    /**
     * Finds the IDs of the top N sections in a target document that are most
     * similar to the given query embedding using native pgvector operators.
     * Uses positional parameters and explicit casting for the vector parameter.
     *
     * @param queryEmbedding The embedding vector to compare against (?1).
     * @param targetDocId    The ID of the document to search within (?2).
     * @param limit          The maximum number of similar section IDs to return (?3).
     * @return A list of Long IDs for the most similar sections.
     */
    @Query(value = "SELECT ds.id FROM document_section ds " +
                   "WHERE ds.document_id = ?2 " +
                   // Explicitly cast the first parameter (?1) to vector
                   "ORDER BY ds.embedding <=> CAST(?1 AS vector) " +
                   "LIMIT ?3", nativeQuery = true)
    List<Long> findMostSimilarSectionIds(
            float[] queryEmbedding, // ?1
            Long targetDocId,       // ?2
            int limit               // ?3
    );
}
