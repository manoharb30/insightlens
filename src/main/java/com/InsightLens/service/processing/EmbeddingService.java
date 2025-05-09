package com.InsightLens.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions; // Import EmbeddingOptions
import org.springframework.ai.embedding.EmbeddingOptionsBuilder; // Import Builder
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import java.util.List;
// No CollectionUtils needed if handling float[] directly

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * Generates an embedding vector for the given text using the configured EmbeddingModel's
     * call() method. Assumes EmbeddingResult.getOutput() returns float[] based on errors.
     *
     * @param text The text content of a document chunk to embed.
     * @return A float array representing the embedding vector, or empty array if input is invalid.
     * @throws RuntimeException if embedding generation fails or returns an invalid result.
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Input text for embedding is null or empty. Returning empty float array.");
            return new float[0];
        }

        // Log entry into the method at DEBUG level
        log.debug("generateEmbedding called for text snippet: '{}...'",
                  truncateTextForLog(text, 80));

        try {
            // Prepare request
            EmbeddingOptions defaultOptions = EmbeddingOptionsBuilder.builder().build();
            EmbeddingRequest request = new EmbeddingRequest(List.of(text), defaultOptions);
            log.debug("Sending request to EmbeddingModel..."); // Log before API call

            // Make the API call
            EmbeddingResponse response = this.embeddingModel.call(request);
            log.debug("Received response from EmbeddingModel."); // Log after successful API call

            // Validate the response object and its results list
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                log.error("Embedding generation using EmbeddingModel.call() returned an invalid or empty response/results list.");
                throw new RuntimeException("Received invalid or empty response/results list from embedding model call().");
            }

            // Assume getOutput() returns float[] based on the recurring error
            float[] embeddingFloatArray = response.getResults().get(0).getOutput();
            log.debug("Extracted output from embedding response."); // Log after getting output

            // Validate the extracted float array
            if (embeddingFloatArray == null || embeddingFloatArray.length == 0) {
                 log.error("Extracted embedding array from EmbeddingModel.call() response is null or empty.");
                 throw new RuntimeException("Extracted null or empty embedding array from embedding model response.");
            }

            // Log success and dimension
            log.debug("Successfully generated embedding using EmbeddingModel.call() with dimension: {}", embeddingFloatArray.length);
            return embeddingFloatArray; // Directly return the float[] result

        } catch (Exception e) {
            // Keep existing error log
            log.error("Embedding generation failed using EmbeddingModel.call() for text snippet '{}...': {}",
                      truncateTextForLog(text, 80), e.getMessage(), e);
            // Re-throw a runtime exception to signal failure
            throw new RuntimeException("Embedding generation failed", e);
        }
    }

    // Helper method for truncating log output
    private String truncateTextForLog(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength).replace("\n", " "); // Replace newlines for cleaner log output
    }
}
