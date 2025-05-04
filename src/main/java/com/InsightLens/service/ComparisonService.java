package com.InsightLens.service;

import com.InsightLens.model.Document;
import com.InsightLens.model.DocumentComparison;
import com.InsightLens.model.DocumentSection;
import com.InsightLens.model.projection.DocumentSectionBasicInfo; // Import projection
import com.InsightLens.repository.DocumentComparisonRepository;
import com.InsightLens.repository.DocumentRepository;
import com.InsightLens.repository.DocumentSectionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
// Import JdbcTemplate and related exceptions
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException; // For queryForObject exception
import org.springframework.jdbc.core.JdbcTemplate; // Import JdbcTemplate
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays; // For stream processing
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for comparing two documents section by section
 * using vector embeddings for similarity and an LLM for difference analysis.
 * Uses projections and JdbcTemplate to handle vector data reading.
 */
@Service
@Slf4j
public class ComparisonService {

    // Dependencies will be injected via constructor
    private final DocumentRepository documentRepository;
    private final DocumentSectionRepository sectionRepository;
    private final DocumentComparisonRepository comparisonRepository;
    private final ChatModel chatModel;
    private final JdbcTemplate jdbcTemplate; // Inject JdbcTemplate

    // Constants remain the same
    private static final double SIMILARITY_THRESHOLD = 0.80;
    private static final int MAX_SIMILAR_SECTIONS_TO_CHECK = 1;
    private static final String COMPARE_SECTIONS_PROMPT_TEMPLATE = """
            You are an expert document comparison assistant. Compare Section A (Original) with Section B (New).
            Focus ONLY on substantive changes (additions, deletions, modifications in values, dates, obligations, wording affecting meaning).
            Ignore minor formatting or punctuation changes unless they change meaning.
            If sections are substantially similar with no meaningful changes, state "No significant changes detected.".
            If different, provide a brief bulleted list summarizing ONLY the key differences. Start each bullet with '* '. Keep the summary concise.

            Section A (Original):
            ```
            {sectionA_text}
            ```

            Section B (New):
            ```
            {sectionB_text}
            ```

            Differences Summary:
            """;

    // Updated Constructor to inject JdbcTemplate and use @Qualifier
    public ComparisonService(
            DocumentRepository documentRepository,
            DocumentSectionRepository sectionRepository,
            DocumentComparisonRepository comparisonRepository,
            @Qualifier("openAiChatModel") ChatModel chatModel, // Specify OpenAI bean
            JdbcTemplate jdbcTemplate // Add JdbcTemplate
    ) {
        this.documentRepository = documentRepository;
        this.sectionRepository = sectionRepository;
        this.comparisonRepository = comparisonRepository;
        this.chatModel = chatModel;
        this.jdbcTemplate = jdbcTemplate; // Assign injected JdbcTemplate
    }


    /**
     * Compares two documents section by section using embeddings and an LLM.
     * Fetches basic section info first, then full details and embeddings as needed.
     *
     * @param docIdA ID of the first document (e.g., older version).
     * @param docIdB ID of the second document (e.g., newer version).
     * @return A DocumentComparison entity containing the summary of differences.
     * @throws EntityNotFoundException if either document ID is invalid.
     */
    @Transactional
    public DocumentComparison compareDocuments(Long docIdA, Long docIdB) {
        log.info("Starting comparison between Document ID {} and Document ID {}", docIdA, docIdB);

        // 1. Fetch Documents and Basic Section Info for Doc A
        Document docA = documentRepository.findById(docIdA)
                .orElseThrow(() -> new EntityNotFoundException("Document with ID " + docIdA + " not found."));
        Document docB = documentRepository.findById(docIdB)
                .orElseThrow(() -> new EntityNotFoundException("Document with ID " + docIdB + " not found."));

        // Fetch basic info using the projection
        List<DocumentSectionBasicInfo> sectionsAInfo = sectionRepository.findByDocumentIdOrderByOriginalOrderAsc(docIdA);
        log.debug("Fetched {} section infos for Doc A.", sectionsAInfo.size());

        Set<Long> matchedSectionBIds = new HashSet<>();
        StringBuilder diffSummaryBuilder = new StringBuilder();

        // 2. Match Sections from A to B and Analyze Changes
        for (DocumentSectionBasicInfo sectionAInfo : sectionsAInfo) {
            // Fetch embedding for section A using JdbcTemplate
            float[] embeddingA = fetchEmbeddingById(sectionAInfo.getId());

            if (embeddingA == null || embeddingA.length == 0) {
                log.warn("Skipping Section Info {} from Document A (ID: {}) due to missing or empty embedding fetched via JdbcTemplate.", sectionAInfo.getId(), docIdA);
                diffSummaryBuilder.append("--- Section Skipped (from A: ").append(getSectionIdentifierFromInfo(sectionAInfo)).append(") due to missing embedding ---\n\n");
                continue;
            }

            // Find potential match IDs in Document B using the native query
            List<Long> potentialMatchIds = sectionRepository.findMostSimilarSectionIds(
                    embeddingA,
                    docIdB,
                    MAX_SIMILAR_SECTIONS_TO_CHECK
            );

            DocumentSection bestMatchB = null; // Will fetch full entity later if needed
            Long bestMatchId = potentialMatchIds.isEmpty() ? null : potentialMatchIds.get(0);
            float[] embeddingB = null; // Will fetch embedding later if needed

            boolean matchFoundAndAboveThreshold = false;

            // Fetch embedding B and calculate similarity only if a potential match ID was found
            if (bestMatchId != null) {
                 embeddingB = fetchEmbeddingById(bestMatchId);
                 if (embeddingB != null && embeddingB.length > 0) {
                    double similarityScore = calculateCosineSimilarity(embeddingA, embeddingB);
                    log.trace("Similarity score between A:{} and B:{} is {}", sectionAInfo.getId(), bestMatchId, similarityScore);
                    matchFoundAndAboveThreshold = similarityScore >= SIMILARITY_THRESHOLD;
                 } else {
                     log.warn("Potential match Section B (ID: {}) has missing or empty embedding fetched via JdbcTemplate. Cannot calculate similarity.", bestMatchId);
                 }
            }

            // Process match/no match based on the threshold check
            if (matchFoundAndAboveThreshold && !matchedSectionBIds.contains(bestMatchId)) {
                // Fetch full entity for section B ONLY when needed for LLM analysis
                 bestMatchB = sectionRepository.findById(bestMatchId).orElse(null);

                 if (bestMatchB != null) {
                    log.debug("Section A (ID: {}) matches Section B (ID: {}). Analyzing...", sectionAInfo.getId(), bestMatchB.getId());
                    matchedSectionBIds.add(bestMatchB.getId());

                    // Create a temporary sectionA object with info from projection for analysis
                    // (Avoids fetching full sectionA entity unless absolutely necessary)
                    DocumentSection tempSectionA = DocumentSection.builder()
                                                    .id(sectionAInfo.getId())
                                                    .text(sectionAInfo.getText())
                                                    .sectionTitle(sectionAInfo.getSectionTitle())
                                                    .build();

                    String analysis = analyzeSectionPair(tempSectionA, bestMatchB); // Pass objects with text
                    if (!analysis.toLowerCase().contains("no significant changes detected")) {
                        diffSummaryBuilder.append("--- Section Comparison (A: ").append(getSectionIdentifierFromInfo(sectionAInfo))
                                         .append(" vs B: ").append(getSectionIdentifier(bestMatchB)).append(") ---\n");
                        diffSummaryBuilder.append(analysis).append("\n\n");
                    } else {
                         log.debug("No significant changes detected between A:{} and B:{}", sectionAInfo.getId(), bestMatchB.getId());
                    }
                 } else {
                      log.error("Failed to fetch full entity for matched section B:{}", bestMatchId);
                      // Handle error appropriately, maybe skip this pair or mark as deleted
                       diffSummaryBuilder.append("--- Section Deleted (from A: ").append(getSectionIdentifierFromInfo(sectionAInfo)).append(") Error fetching match B ---\n\n");
                 }
            } else {
                // Fetch required info for the "Deleted" summary using projection info
                String textSnippetA = sectionAInfo.getText() != null ? truncateText(sectionAInfo.getText(), 200) : "[Text not available in projection]";

                // Log reason for not matching
                if (bestMatchId != null && matchedSectionBIds.contains(bestMatchId)) {
                     log.debug("Best match for Section A (ID: {}) was Section B (ID: {}), but it was already matched.", sectionAInfo.getId(), bestMatchId);
                } else if (bestMatchId != null) {
                     log.debug("Best match for Section A (ID: {}) was Section B (ID: {}), but similarity was below threshold.", sectionAInfo.getId(), bestMatchId);
                } else {
                     log.debug("Section A (ID: {}) has no potential match in Document B.", sectionAInfo.getId());
                }
                // Treat as deleted
                diffSummaryBuilder.append("--- Section Deleted (from A: ").append(getSectionIdentifierFromInfo(sectionAInfo)).append(") ---\n");
                diffSummaryBuilder.append("Content Snippet:\n```\n")
                                 .append(textSnippetA)
                                 .append("\n```\n\n");
            }
        } // End of loop for sectionsAInfo

        // 3. Identify Added Sections
        List<DocumentSectionBasicInfo> sectionsBInfo = sectionRepository.findByDocumentIdOrderByOriginalOrderAsc(docIdB);
        for (DocumentSectionBasicInfo sectionBInfo : sectionsBInfo) {
            if (!matchedSectionBIds.contains(sectionBInfo.getId())) {
                log.debug("Section B (ID: {}) is new (not matched from A).", sectionBInfo.getId());
                // Use text directly from projection if available
                String textSnippetB = sectionBInfo.getText() != null ? truncateText(sectionBInfo.getText(), 200) : "[Text not available]";
                diffSummaryBuilder.append("--- Section Added (in B: ").append(getSectionIdentifierFromInfo(sectionBInfo)).append(") ---\n");
                diffSummaryBuilder.append("Content Snippet:\n```\n")
                                 .append(textSnippetB)
                                 .append("\n```\n\n");
            }
        }

        // 4. Create and Save Comparison Result (remains the same)
        String finalSummary = diffSummaryBuilder.toString().trim();
        if (finalSummary.isEmpty()) {
            finalSummary = "No differences identified between the documents based on the comparison criteria.";
            log.info("No differences identified between Document ID {} and Document ID {}", docIdA, docIdB);
        }
        DocumentComparison comparison = DocumentComparison.builder()
                .documentA(docA)
                .documentB(docB)
                .diffSummary(finalSummary)
                .build();
        comparison = comparisonRepository.save(comparison);
        log.info("Comparison saved with ID: {}", comparison.getId());
        return comparison;
    }

    // --- Helper Methods ---

    /** Fetches embedding as String using JdbcTemplate and parses it */
    private float[] fetchEmbeddingById(Long sectionId) {
        if (sectionId == null) return null;
        try {
            // Cast vector to text for reliable retrieval via standard JDBC
            String sql = "SELECT embedding::text FROM document_section WHERE id = ?";
            // Use queryForObject - expects exactly one result or throws exception if none found
            String vectorString = jdbcTemplate.queryForObject(sql, String.class, sectionId);
            return parseVectorString(vectorString);
        } catch (EmptyResultDataAccessException e) {
             log.warn("No embedding found for section ID {} via JdbcTemplate.", sectionId);
             return null; // Return null if no row found
        } catch (Exception e) {
            log.error("Failed to fetch or parse embedding for section ID {}: {}", sectionId, e.getMessage());
            return null; // Return null or empty array on other failures
        }
    }

    /** Parses a vector string like "[0.1,0.2,...]" into a float array */
    private float[] parseVectorString(String vectorString) {
        if (vectorString == null || vectorString.length() < 2 || !vectorString.startsWith("[") || !vectorString.endsWith("]")) {
            log.trace("Cannot parse null or invalid format vector string: {}", vectorString);
            return null;
        }
        // Remove brackets and split by comma
        String[] numberStrings = vectorString.substring(1, vectorString.length() - 1).split(",");
        float[] vector = new float[numberStrings.length];
        try {
            for (int i = 0; i < numberStrings.length; i++) {
                vector[i] = Float.parseFloat(numberStrings[i].trim());
            }
            return vector;
        } catch (NumberFormatException e) {
            log.error("Failed to parse float from vector string element: '{}'. Full string: {}", e.getMessage(), vectorString);
            return null; // Return null if parsing fails
        }
    }

    // analyzeSectionPair method needs objects with text (can use full or temp objects)
    private String analyzeSectionPair(DocumentSection sectionA, DocumentSection sectionB) {
         try {
            PromptTemplate promptTemplate = new PromptTemplate(COMPARE_SECTIONS_PROMPT_TEMPLATE);
            Prompt prompt = promptTemplate.create(Map.of(
                    "sectionA_text", sectionA.getText() != null ? sectionA.getText() : "",
                    "sectionB_text", sectionB.getText() != null ? sectionB.getText() : ""
            ));
            ChatResponse chatResponse = chatModel.call(prompt);
            Generation generation = chatResponse.getResult();
            if (generation == null) { return "Error: LLM response contained no result."; }
            AssistantMessage assistantMessage = generation.getOutput();
            if (assistantMessage == null) { return "Error: LLM generation contained no output message.";}
            String responseContent = assistantMessage.getText(); // Using getText()
            log.debug("LLM analysis result for Sections A:{} vs B:{}: {}", sectionA.getId(), sectionB.getId(), responseContent);
            return responseContent != null ? responseContent.trim() : "Error: LLM returned null content.";
        } catch (Exception e) {
            log.error("Failed to analyze section pair (A:{}, B:{}) using LLM: {}",
                      sectionA.getId(), sectionB.getId(), e.getMessage(), e);
            return "Error: Could not analyze differences between sections due to an exception.";
        }
    }

     // Helper method to truncate text
     private String truncateText(String text, int maxLength) {
         if (text == null) return "";
         if (text.length() <= maxLength) return text;
         int boundary = text.lastIndexOf(' ', maxLength);
         if (boundary > maxLength / 2) { return text.substring(0, boundary) + "..."; }
         else { return text.substring(0, maxLength) + "..."; }
     }

     // Helper for projection identifier
     private String getSectionIdentifierFromInfo(DocumentSectionBasicInfo sectionInfo) {
         if (sectionInfo == null) return "null";
         return sectionInfo.getSectionTitle() != null && !sectionInfo.getSectionTitle().trim().isEmpty() ?
                "'" + sectionInfo.getSectionTitle().trim() + "' (ID: " + sectionInfo.getId() + ")" :
                "ID: " + sectionInfo.getId();
     }

     // Helper for full entity identifier
     private String getSectionIdentifier(DocumentSection section) {
         if (section == null) return "null";
         return section.getSectionTitle() != null && !section.getSectionTitle().trim().isEmpty() ?
                "'" + section.getSectionTitle().trim() + "' (ID: " + section.getId() + ")" :
                "ID: " + section.getId();
     }

    // Helper for cosine similarity
    private static double calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length == 0 || vectorB.length == 0) { return 0.0; }
        if (vectorA.length != vectorB.length) { throw new IllegalArgumentException("Vectors must have the same dimensions...");}
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        if (normA == 0.0 || normB == 0.0) { return 0.0; }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
