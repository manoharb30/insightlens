package com.InsightLens.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel; // Use the interface name
import org.springframework.stereotype.Service;

import java.util.ArrayList; // Added for List conversion
import java.util.List;

/**
 * Service responsible for generating vector embeddings for text using Spring AI's EmbeddingModel.
 * This service integrates with the configured embedding model (e.g., OpenAI, BAAI).
 * Adapted to handle potential compiler misinterpretation of EmbeddingModel.embed() return type.
 */
@Service
@RequiredArgsConstructor // Lombok annotation for constructor injection of final fields
@Slf4j // Lombok annotation for logging
public class EmbeddingService {

    // Inject the Spring AI EmbeddingModel
    private final EmbeddingModel embeddingModel;

    /**
     * Generates an embedding vector for the given input text and returns it as a float array.
     * Attempts to handle potential compiler misinterpretation of the embed method return type.
     *
     * @param text The input text string for which to generate an embedding.
     * @return A float array representing the embedding vector. Returns an empty array for null/empty input or on failure.
     */
    public float[] generateEmbedding(String text) { // Return type is float[]
        if (text == null || text.trim().isEmpty()) {
            log.warn("Attempted to generate embedding for null or empty text. Returning empty array.");
            return new float[0]; // Return empty array for empty input
        }
        log.debug("Generating embedding for text snippet (first 50 chars): {}", text.substring(0, Math.min(text.length(), 50)) + "...");

        try {
            // ✅ Attempt to call embed and handle the result based on the compiler error message.
            // If the compiler thinks embed returns float[], assign to float[] directly.
            // If the compiler thinks embed returns List<Float>, assign to List<Float>.
            // Based on the error, the compiler thinks it returns float[].
            // Note: This contradicts Spring AI documentation for 1.0.x, but we are following the compiler's error.
            // If this compiles, it confirms the compiler sees embed() returning float[].
            float[] embeddingArrayDirect = embeddingModel.embed(text); // Assuming compiler sees float[] return

            log.debug("Successfully generated embedding of dimension {}", embeddingArrayDirect.length);

            // If the rest of the code needs List<Float> (which it doesn't for saving to DocumentSection),
            // you would convert here:
            /*
            List<Float> embeddingList = new ArrayList<>(embeddingArrayDirect.length);
            for (float v : embeddingArrayDirect) {
                embeddingList.add(v);
            }
            */

            // Since the DocumentSection entity expects float[], we can return the float[] directly
            return embeddingArrayDirect;

        } catch (Exception e) {
            // Log the error if embedding generation fails
            log.error("Failed to generate embedding for text: {}", e.getMessage(), e);
            // Depending on error handling strategy, you might re-throw, return null, or empty array
            throw new RuntimeException("Failed to generate embedding", e); // Re-throwing for now
        }
    }

    // The old generateEmbeddingAsFloatArray method is no longer needed.
}
