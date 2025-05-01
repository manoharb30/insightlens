package com.InsightLens.service.processing; // Recommended package, adjust if needed

import com.InsightLens.model.processing.DocumentChunk; // Import the new DocumentChunk class
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service responsible for splitting raw document text into logical chunks
 * based on a heading/section-based strategy with a paragraph fallback,
 * and capturing metadata for each chunk.
 */
@Service // Marks this class as a Spring Service
@Slf4j // Lombok annotation for logging
public class TextSplitterService {

    // --- Configuration for Chunking Strategy ---
    // Regex to identify common headings/sections in Legal, Finance, Pharma documents.
    // This is an initial set and will need refinement based on backtesting (Task 2.1 refinement).
    // It also captures the heading text itself in a group.
    private static final String HEADING_REGEX =
            "(?m)^\\s*(ARTICLE\\s+[IVXLCDM]+\\.?|ARTICLE\\s+\\d+\\.?|SECTION\\s+\\d+(\\.\\d+)*\\.?|CHAPTER\\s+\\d+\\.?|[IVXLCDM]+\\.\\s+|\\d+\\.\\s*|[A-Za-z]\\.\\s*|Executive Summary|Introduction|Background|Findings|Conclusion)\\s*.*$";
    private static final Pattern HEADING_PATTERN = Pattern.compile(HEADING_REGEX);

    // Maximum size for a chunk after initial splitting.
    // If a chunk is larger than this, the fallback splitting will be applied.
    private static final int MAX_CHUNK_SIZE_CHARS = 2000; // Example: ~300-400 words

    // --- Implementation ---

    /**
     * Splits the full text of a document into logical chunks, capturing metadata.
     * Implements a heading/section-based strategy with a fallback.
     *
     * @param fullText The raw text extracted from the document.
     * @return A list of DocumentChunk objects.
     */
    public List<DocumentChunk> split(String fullText) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (fullText == null || fullText.trim().isEmpty()) {
            log.warn("Attempted to split empty or null text.");
            return chunks; // Return empty list for empty input
        }

        // Normalize line endings to simplify regex and index tracking
        fullText = fullText.replace("\r\n", "\n").replace("\r", "\n");

        Matcher matcher = HEADING_PATTERN.matcher(fullText);
        int currentStartIndex = 0; // Track the start index of the current chunk
        int chunkOrder = 0; // Track the order of the chunks

        // 1. Split based on detected headings
        while (matcher.find()) {
            int headingStartIndex = matcher.start();
            int headingEndIndex = matcher.end();
            String headingText = matcher.group(0).trim(); // Capture the full heading text

            // Add the text from the last boundary up to the start of the current heading
            String chunkText = fullText.substring(currentStartIndex, headingStartIndex).trim();
            if (!chunkText.isEmpty()) {
                chunks.add(DocumentChunk.builder()
                        .text(chunkText)
                        .originalOrder(chunkOrder++)
                        .startIndex(currentStartIndex)
                        .endIndex(headingStartIndex) // End before the heading starts
                        .sectionTitle(null) // This chunk is the content *before* the heading
                        .build());
            }

            // The heading itself can be considered part of the next chunk's context,
            // or added as a separate small chunk. For this strategy, we'll include
            // the heading text at the start of the chunk it introduces.
            // The start index for the next chunk begins at the start of the heading.
            currentStartIndex = headingStartIndex;
        }

        // Add the last chunk (from the last heading/boundary to the end of the document)
        String lastChunkText = fullText.substring(currentStartIndex).trim();
        if (!lastChunkText.isEmpty()) {
             // Need to re-match the heading for the last chunk if it started with one
             String sectionTitleForLastChunk = null;
             Matcher lastChunkMatcher = HEADING_PATTERN.matcher(lastChunkText);
             if (lastChunkMatcher.find() && lastChunkMatcher.start() == 0) {
                 sectionTitleForLastChunk = lastChunkMatcher.group(0).trim();
             }

            chunks.add(DocumentChunk.builder()
                    .text(lastChunkText)
                    .originalOrder(chunkOrder++)
                    .startIndex(currentStartIndex)
                    .endIndex(fullText.length()) // End at the end of the original text
                    .sectionTitle(sectionTitleForLastChunk) // Associate the heading if it exists at the start
                    .build());
        }

        // 2. Apply fallback splitting to large chunks and rebuild the list with potential new chunks
        List<DocumentChunk> finalChunks = new ArrayList<>();
        int finalChunkOrder = 0; // New order for the final list of chunks

        for (DocumentChunk chunk : chunks) {
            if (chunk.getText().length() > MAX_CHUNK_SIZE_CHARS) {
                log.debug("Chunk {} (Order {}) is large ({} chars), applying fallback splitting.",
                        chunk.getSectionTitle() != null ? chunk.getSectionTitle() : "No Title",
                        chunk.getOriginalOrder(),
                        chunk.getText().length());

                // Fallback: Split large chunks by paragraphs (double newlines)
                // Need to track indices relative to the original chunk's start index
                String[] paragraphs = chunk.getText().split("\\n\\n+");
                int currentRelativeIndex = 0; // Index within the current large chunk text

                for (String paragraph : paragraphs) {
                    String trimmedParagraph = paragraph.trim();
                    if (!trimmedParagraph.isEmpty()) {
                        int paragraphStartIndex = chunk.getStartIndex() + chunk.getText().indexOf(paragraph, currentRelativeIndex);
                        int paragraphEndIndex = paragraphStartIndex + paragraph.length();

                        // Further fallback: If paragraph is still too large, split by sentences (basic)
                        if (trimmedParagraph.length() > MAX_CHUNK_SIZE_CHARS) {
                            // This sentence splitting regex is basic and needs refinement for real NLP
                            String[] sentences = trimmedParagraph.split("(?<=[.!?])\\s+");
                            int currentSentenceRelativeIndex = 0;
                             for (String sentence : sentences) {
                                 String trimmedSentence = sentence.trim();
                                 if (!trimmedSentence.isEmpty()) {
                                     int sentenceStartIndex = paragraphStartIndex + trimmedParagraph.indexOf(sentence, currentSentenceRelativeIndex);
                                     int sentenceEndIndex = sentenceStartIndex + sentence.length();

                                     finalChunks.add(DocumentChunk.builder()
                                             .text(trimmedSentence)
                                             .originalOrder(finalChunkOrder++) // Assign new order
                                             .startIndex(sentenceStartIndex) // Use calculated start index
                                             .endIndex(sentenceEndIndex) // Use calculated end index
                                             .sectionTitle(chunk.getSectionTitle()) // Inherit parent section title
                                             .build());
                                     currentSentenceRelativeIndex = trimmedParagraph.indexOf(sentence, currentSentenceRelativeIndex) + sentence.length();
                                 }
                             }
                        } else {
                           finalChunks.add(DocumentChunk.builder()
                                   .text(trimmedParagraph)
                                   .originalOrder(finalChunkOrder++) // Assign new order
                                   .startIndex(paragraphStartIndex) // Use calculated start index
                                   .endIndex(paragraphEndIndex) // Use calculated end index
                                   .sectionTitle(chunk.getSectionTitle()) // Inherit parent section title
                                   .build());
                        }
                    }
                    currentRelativeIndex = chunk.getText().indexOf(paragraph, currentRelativeIndex) + paragraph.length();
                }
                log.debug("Split a large chunk into {} smaller chunks.", paragraphs.length);
            } else {
                // Chunk is within size limit, add it directly with updated order
                finalChunks.add(DocumentChunk.builder()
                        .text(chunk.getText())
                        .originalOrder(finalChunkOrder++) // Assign new order
                        .startIndex(chunk.getStartIndex())
                        .endIndex(chunk.getEndIndex())
                        .sectionTitle(chunk.getSectionTitle())
                        .build());
            }
        }

        // 3. Final Clean up (remove any empty strings that might have resulted from splitting/trimming)
        finalChunks.removeIf(c -> c.getText().isEmpty());

        log.info("Split text into {} final chunks.", finalChunks.size());
        return finalChunks;
    }

    // TODO: Refine HEADING_REGEX based on backtesting results (Task 2.1 refinement)
    // The current regex is a starting point and will likely need adjustments
    // to accurately capture headings in your specific sample documents.
}
