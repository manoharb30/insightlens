package com.InsightLens.repository;

import com.InsightLens.model.DocumentSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;     // Keep this if you added findMostSimilarSections
import org.springframework.data.repository.query.Param; // Keep this if you added findMostSimilarSections
import org.springframework.stereotype.Repository;

import java.util.List; // <-- Add this import

@Repository
public interface DocumentSectionRepository extends JpaRepository<DocumentSection, Long> {

    // +++ Add this method definition +++
    /**
     * Finds all sections belonging to a specific document, ordered by their
     * original appearance in the document. Spring Data JPA generates the query.
     *
     * @param documentId The ID of the parent Document.
     * @return A list of DocumentSection entities, ordered by originalOrder ascending.
     */
    List<DocumentSection> findByDocumentIdOrderByOriginalOrderAsc(Long documentId);
    // +++ End of added method +++


    // --- Keep the native query method findMostSimilarSections if you added it ---
    @Query(value = "SELECT ds.* FROM document_section ds " +
                   "WHERE ds.document_id = :targetDocId " +
                   // Use <=> for COSINE distance, adjust dimension (e.g., 1536)
                   "ORDER BY ds.embedding <=> CAST(CAST(:queryEmbedding AS text) AS vector(1536)) " +
                   "LIMIT :limit", nativeQuery = true)
    List<DocumentSection> findMostSimilarSections(
            @Param("queryEmbedding") float[] queryEmbedding,
            @Param("targetDocId") Long targetDocId,
            @Param("limit") int limit
    );
    // --- End of native query method ---

}