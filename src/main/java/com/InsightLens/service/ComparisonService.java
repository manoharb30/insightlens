package com.InsightLens.service;

import com.InsightLens.model.Document;
import com.InsightLens.model.DocumentComparison;
import com.InsightLens.model.DocumentSection;
import com.InsightLens.repository.DocumentComparisonRepository;
import com.InsightLens.repository.DocumentRepository;
import com.InsightLens.repository.DocumentSectionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
// Removed Lombok's @RequiredArgsConstructor to manually define constructor for @Qualifier
// import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel; // Spring AI interface for LLMs
import org.springframework.ai.chat.model.ChatResponse; // Represents the LLM response
import org.springframework.ai.chat.model.Generation; // Represents a single generation/result
import org.springframework.ai.chat.messages.AssistantMessage; // Represents the message from the LLM
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier; // Import Qualifier
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // For DB operations

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for comparing two documents section by section
 * using vector embeddings for similarity and an LLM for difference analysis.
 */
@Service
// @RequiredArgsConstructor // Removed to manually define constructor for @Qualifier
@Slf4j
public class ComparisonService {

    // Dependencies will be injected via constructor
    private final DocumentRepository documentRepository;
    private final DocumentSectionRepository sectionRepository;
    private final DocumentComparisonRepository comparisonRepository;
    private final ChatModel chatModel; // Will be qualified in constructor

    // --- Constants ---
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

    // --- Constructor for Dependency Injection with @Qualifier ---
    public ComparisonService(
            DocumentRepository documentRepository,
            DocumentSectionRepository sectionRepository,
            DocumentComparisonRepository comparisonRepository,
            @Qualifier("openAiChatModel") ChatModel chatModel // Specify OpenAI bean
    ) {
        this.documentRepository = documentRepository;
        this.sectionRepository = sectionRepository;
        this.comparisonRepository = comparisonRepository;
        this.chatModel = chatModel; // Injects the qualified bean
    }


    /**
     * Compares two documents section by section using embeddings and an LLM.
     * (Method body remains the same as the previous version)
     *
     * @param docIdA ID of the first document (e.g., older version).
     * @param docIdB ID of the second document (e.g., newer version).
     * @return A DocumentComparison entity containing the summary of differences.
     * @throws EntityNotFoundException if either document ID is invalid.
     */
    @Transactional
    public DocumentComparison compareDocuments(Long docIdA, Long docIdB) {
        log.info("Starting comparison between Document ID {} and Document ID {}", docIdA, docIdB);

        // 1. Fetch Documents and their Sections
        Document docA = documentRepository.findById(docIdA)
                .orElseThrow(() -> new EntityNotFoundException("Document with ID " + docIdA + " not found."));
        Document docB = documentRepository.findById(docIdB)
                .orElseThrow(() -> new EntityNotFoundException("Document with ID " + docIdB + " not found."));

        List<DocumentSection> sectionsA = sectionRepository.findByDocumentIdOrderByOriginalOrderAsc(docIdA);
        List<DocumentSection> sectionsB = sectionRepository.findByDocumentIdOrderByOriginalOrderAsc(docIdB);

        log.debug("Fetched {} sections for Doc A and {} sections for Doc B.", sectionsA.size(), sectionsB.size());

        Set<Long> matchedSectionBIds = new HashSet<>();
        StringBuilder diffSummaryBuilder = new StringBuilder();

        // 2. Match Sections from A to B and Analyze Changes
        for (DocumentSection sectionA : sectionsA) {
            float[] embeddingA = sectionA.getEmbedding();
            if (embeddingA == null || embeddingA.length == 0) {
                log.warn("Skipping Section {} from Document A (ID: {}) due to missing or empty embedding.", sectionA.getId(), docIdA);
                diffSummaryBuilder.append("--- Section Skipped (from A: ").append(getSectionIdentifier(sectionA)).append(") due to missing embedding ---\n\n");
                continue;
            }

            List<DocumentSection> potentialMatches = sectionRepository.findMostSimilarSections(
                    embeddingA, docIdB, MAX_SIMILAR_SECTIONS_TO_CHECK);
            DocumentSection bestMatchB = potentialMatches.isEmpty() ? null : potentialMatches.get(0);
            boolean matchFoundAndAboveThreshold = false;

            if (bestMatchB != null) {
                float[] embeddingB = bestMatchB.getEmbedding();
                if (embeddingB != null && embeddingB.length > 0) {
                    double similarityScore = calculateCosineSimilarity(embeddingA, embeddingB);
                    log.trace("Similarity score between A:{} and B:{} is {}", sectionA.getId(), bestMatchB.getId(), similarityScore);
                    matchFoundAndAboveThreshold = similarityScore >= SIMILARITY_THRESHOLD;
                } else {
                     log.warn("Best match Section B (ID: {}) has missing or empty embedding. Cannot calculate similarity.", bestMatchB.getId());
                }
            }

            if (matchFoundAndAboveThreshold && !matchedSectionBIds.contains(bestMatchB.getId())) {
                log.debug("Section A (ID: {}) matches Section B (ID: {}). Analyzing...", sectionA.getId(), bestMatchB.getId());
                matchedSectionBIds.add(bestMatchB.getId());

                String analysis = analyzeSectionPair(sectionA, bestMatchB);
                if (!analysis.toLowerCase().contains("no significant changes detected")) {
                    diffSummaryBuilder.append("--- Section Comparison (A: ").append(getSectionIdentifier(sectionA))
                                     .append(" vs B: ").append(getSectionIdentifier(bestMatchB)).append(") ---\n");
                    diffSummaryBuilder.append(analysis).append("\n\n");
                } else {
                     log.debug("No significant changes detected between A:{} and B:{}", sectionA.getId(), bestMatchB.getId());
                }
            } else {
                // Log reason for not matching before marking as deleted
                if (bestMatchB != null && matchedSectionBIds.contains(bestMatchB.getId())) {
                     log.debug("Best match for Section A (ID: {}) was Section B (ID: {}), but it was already matched.", sectionA.getId(), bestMatchB.getId());
                } else if (bestMatchB != null) {
                     log.debug("Best match for Section A (ID: {}) was Section B (ID: {}), but similarity was below threshold.", sectionA.getId(), bestMatchB.getId());
                } else {
                     log.debug("Section A (ID: {}) has no potential match in Document B.", sectionA.getId());
                }
                // Treat as deleted
                diffSummaryBuilder.append("--- Section Deleted (from A: ").append(getSectionIdentifier(sectionA)).append(") ---\n");
                diffSummaryBuilder.append("Content Snippet:\n```\n")
                                 .append(truncateText(sectionA.getText(), 200))
                                 .append("\n```\n\n");
            }
        }

        // 3. Identify Added Sections
        for (DocumentSection sectionB : sectionsB) {
            if (!matchedSectionBIds.contains(sectionB.getId())) {
                log.debug("Section B (ID: {}) is new (not matched from A).", sectionB.getId());
                diffSummaryBuilder.append("--- Section Added (in B: ").append(getSectionIdentifier(sectionB)).append(") ---\n");
                diffSummaryBuilder.append("Content Snippet:\n```\n")
                                 .append(truncateText(sectionB.getText(), 200))
                                 .append("\n```\n\n");
            }
        }

        // 4. Create and Save Comparison Result
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

    /**
     * Helper method to call the ChatModel (LLM) to compare two text sections.
     * Uses the method confirmed to work in the target environment (e.g., getText()).
     */
    private String analyzeSectionPair(DocumentSection sectionA, DocumentSection sectionB) {
        // ... (analyzeSectionPair method body remains the same as previous version) ...
        try {
            PromptTemplate promptTemplate = new PromptTemplate(COMPARE_SECTIONS_PROMPT_TEMPLATE);
            Prompt prompt = promptTemplate.create(Map.of(
                    "sectionA_text", sectionA.getText() != null ? sectionA.getText() : "",
                    "sectionB_text", sectionB.getText() != null ? sectionB.getText() : ""
            ));

            ChatResponse chatResponse = chatModel.call(prompt);

            Generation generation = chatResponse.getResult();
            if (generation == null) {
                log.error("LLM response contained no generation/result for Sections A:{} vs B:{}", sectionA.getId(), sectionB.getId());
                return "Error: LLM response contained no result.";
            }
            AssistantMessage assistantMessage = generation.getOutput();
            if (assistantMessage == null) {
                log.error("LLM generation contained no output message for Sections A:{} vs B:{}", sectionA.getId(), sectionB.getId());
                return "Error: LLM generation contained no output message.";
            }

            String responseContent = assistantMessage.getText(); // Using getText() as confirmed previously

            log.debug("LLM analysis result for Sections A:{} vs B:{}: {}", sectionA.getId(), sectionB.getId(), responseContent);
            return responseContent != null ? responseContent.trim() : "Error: LLM returned null content.";

        } catch (Exception e) {
            log.error("Failed to analyze section pair (A:{}, B:{}) using LLM: {}",
                      sectionA.getId(), sectionB.getId(), e.getMessage(), e);
            return "Error: Could not analyze differences between sections due to an exception.";
        }
    }

     /**
      * Helper method to truncate text for summary snippets, trying to break at spaces.
      */
     private String truncateText(String text, int maxLength) {
        // ... (truncateText method remains the same) ...
         if (text == null) return "";
         if (text.length() <= maxLength) return text;
         int boundary = text.lastIndexOf(' ', maxLength);
         if (boundary > maxLength / 2) {
             return text.substring(0, boundary) + "...";
         } else {
             return text.substring(0, maxLength) + "...";
         }
     }

     /**
      * Helper to get a user-friendly identifier for a section (Title or ID) for logging/summaries.
      */
     private String getSectionIdentifier(DocumentSection section) {
        // ... (getSectionIdentifier method remains the same) ...
         if (section == null) return "null";
         return section.getSectionTitle() != null && !section.getSectionTitle().trim().isEmpty() ?
                "'" + section.getSectionTitle().trim() + "' (ID: " + section.getId() + ")" :
                "ID: " + section.getId();
     }

    /**
     * Calculates the cosine similarity between two vectors.
     */
    private static double calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        // ... (calculateCosineSimilarity method remains the same) ...
        if (vectorA == null || vectorB == null || vectorA.length == 0 || vectorB.length == 0) {
            log.warn("Cannot calculate cosine similarity for null or empty vectors.");
            return 0.0;
        }
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must have the same dimensions for cosine similarity calculation.");
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        if (normA == 0.0 || normB == 0.0) {
             log.warn("Cannot calculate cosine similarity with zero vector(s).");
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
