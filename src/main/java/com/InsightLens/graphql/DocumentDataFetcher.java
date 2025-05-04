package com.InsightLens.graphql; // Or your preferred package for GraphQL components

import com.InsightLens.model.Document;
import com.InsightLens.model.DocumentComparison; // Import DocumentComparison
import com.InsightLens.service.ComparisonService; // Import ComparisonService
import com.InsightLens.service.DocumentService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * DGS Component responsible for handling GraphQL mutations and queries related to Documents.
 */
@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class DocumentDataFetcher {

    private final DocumentService documentService;
    private final ComparisonService comparisonService; // Inject ComparisonService

    /**
     * DGS Mutation implementation for handling document uploads via GraphQL.
     * (Method remains the same as before)
     */
    @DgsMutation(field = "uploadDocument")
    public Document uploadDocumentMutation(@InputArgument("file") MultipartFile file, DgsDataFetchingEnvironment dfe) throws IOException {
        log.info("GraphQL 'uploadDocument' mutation received for file: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            log.error("Upload attempt failed: File is empty.");
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }
        Document uploadedDocument = documentService.uploadDocument(file);
        log.info("Document record created via GraphQL upload. ID: {}, Status: {}", uploadedDocument.getId(), uploadedDocument.getStatus());
        return uploadedDocument;
    }

    /**
     * DGS Mutation implementation for comparing two documents.
     * Corresponds to the 'compareDocuments' field in the GraphQL Mutation type.
     *
     * @param docIdA ID of the first document (passed as GraphQL ID!, mapped to Long).
     * @param docIdB ID of the second document (passed as GraphQL ID!, mapped to Long).
     * @param dfe    The DgsDataFetchingEnvironment (optional).
     * @return The DocumentComparison entity containing the results.
     * @throws RuntimeException If comparison fails (e.g., documents not found, processing error).
     */
    @DgsMutation(field = "compareDocuments") // Maps to the 'compareDocuments' mutation field
    public DocumentComparison compareDocumentsMutation(
            @InputArgument("docIdA") Long docIdA, // Use Long for ID mapping
            @InputArgument("docIdB") Long docIdB,
            DgsDataFetchingEnvironment dfe) {

        log.info("GraphQL 'compareDocuments' mutation received for Doc ID {} and Doc ID {}", docIdA, docIdB);

        // Basic validation
        if (docIdA == null || docIdB == null) {
            throw new IllegalArgumentException("Both docIdA and docIdB must be provided.");
        }
        if (docIdA.equals(docIdB)) {
            throw new IllegalArgumentException("Cannot compare a document with itself.");
        }

        // Delegate to the ComparisonService
        // This service handles fetching, matching, LLM analysis, and saving the comparison.
        try {
            DocumentComparison comparisonResult = comparisonService.compareDocuments(docIdA, docIdB);
            log.info("Comparison completed successfully. Comparison ID: {}", comparisonResult.getId());
            // DGS will map the fields of the returned DocumentComparison object
            // to the DocumentComparison type in the GraphQL schema.
            return comparisonResult;
        } catch (Exception e) {
            // Log the error and rethrow or wrap in a specific GraphQL exception
            log.error("Document comparison failed for Doc IDs {} and {}: {}", docIdA, docIdB, e.getMessage(), e);
            // TODO: Consider mapping to a specific GraphQL error type for better client handling
            throw new RuntimeException("Document comparison failed: " + e.getMessage(), e);
        }
    }

    // --- Optional: Add DGS Query implementations here later if needed ---
    /*
    @DgsQuery(field = "getDocumentById")
    public Document getDocumentByIdQuery(@InputArgument Long id) { ... }

    @DgsQuery(field = "getAllDocuments")
    public List<Document> getAllDocumentsQuery() { ... }

    @DgsQuery(field = "getComparisonById")
    public DocumentComparison getComparisonByIdQuery(@InputArgument Long id) {
        log.info("GraphQL 'getComparisonById' query received for ID: {}", id);
        // Assuming you add a method to ComparisonService or use the repository directly
        return comparisonRepository.findById(id) // Example using repository directly
               .orElseThrow(() -> new RuntimeException("Comparison not found with ID: " + id));
    }
    */

}
