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
    private static final String HEADING_REGEX =
            "(?m)^\\s*(ARTICLE\\s+[IVXLCDM]+\\.?|ARTICLE\\s+\\d+\\.?|SECTION\\s+\\d+(\\.\\d+)*\\.?|CHAPTER\\s+\\d+\\.?|[IVXLCDM]+\\.\\s+|\\d+\\.\\s*|[A-Za-z]\\.\\s*|Executive Summary|Introduction|Background|Findings|Conclusion)\\s*.*$";
    private static final Pattern HEADING_PATTERN = Pattern.compile(HEADING_REGEX);
    private static final int MAX_CHUNK_SIZE_CHARS = 2000;

    // --- Implementation ---

    public List<DocumentChunk> split(String fullText) {
        log.debug("Starting text splitting process..."); // Log start
        List<DocumentChunk> initialChunks = new ArrayList<>();
        if (fullText == null || fullText.trim().isEmpty()) {
            log.warn("Attempted to split empty or null text. Returning empty list.");
            return initialChunks;
        }

        fullText = fullText.replace("\r\n", "\n").replace("\r", "\n");
        log.trace("Normalized line endings. Full text length: {}", fullText.length()); // Use TRACE for very verbose info

        Matcher matcher = HEADING_PATTERN.matcher(fullText);
        int currentStartIndex = 0;
        int chunkOrder = 0;

        log.debug("Starting initial split based on headings...");
        // 1. Split based on detected headings
        while (matcher.find()) {
            int headingStartIndex = matcher.start();
            String headingText = matcher.group(0).trim(); // Get heading text early for logging
            log.debug("Found potential heading at index {}: '{}'", headingStartIndex, headingText);

            String chunkText = fullText.substring(currentStartIndex, headingStartIndex).trim();
            if (!chunkText.isEmpty()) {
                log.debug("Adding initial chunk (Order {}) from index {} to {}. Title: null", chunkOrder, currentStartIndex, headingStartIndex);
                initialChunks.add(DocumentChunk.builder()
                        .text(chunkText)
                        .originalOrder(chunkOrder++)
                        .startIndex(currentStartIndex)
                        .endIndex(headingStartIndex)
                        .sectionTitle(null)
                        .build());
            } else {
                 log.debug("Skipping empty chunk between index {} and {}", currentStartIndex, headingStartIndex);
            }
            currentStartIndex = headingStartIndex; // Move start index to the beginning of the heading
        }
        log.debug("Finished initial split based on headings. Current start index: {}", currentStartIndex);

        // Add the last chunk
        String lastChunkText = fullText.substring(currentStartIndex).trim();
        if (!lastChunkText.isEmpty()) {
             String sectionTitleForLastChunk = null;
             Matcher lastChunkMatcher = HEADING_PATTERN.matcher(lastChunkText);
             if (lastChunkMatcher.find() && lastChunkMatcher.start() == 0) {
                 sectionTitleForLastChunk = lastChunkMatcher.group(0).trim();
                 log.debug("Identified title for last chunk: '{}'", sectionTitleForLastChunk);
             }
             log.debug("Adding final initial chunk (Order {}) from index {} to {}. Title: '{}'", chunkOrder, currentStartIndex, fullText.length(), sectionTitleForLastChunk);
            initialChunks.add(DocumentChunk.builder()
                    .text(lastChunkText)
                    .originalOrder(chunkOrder++)
                    .startIndex(currentStartIndex)
                    .endIndex(fullText.length())
                    .sectionTitle(sectionTitleForLastChunk)
                    .build());
        } else {
             log.debug("Skipping empty last chunk from index {}.", currentStartIndex);
        }
        log.info("Generated {} initial chunks based on headings.", initialChunks.size());


        // 2. Apply fallback splitting to large chunks
        log.debug("Applying fallback splitting for chunks larger than {} chars...", MAX_CHUNK_SIZE_CHARS);
        List<DocumentChunk> finalChunks = new ArrayList<>();
        int finalChunkOrder = 0;

        for (DocumentChunk chunk : initialChunks) {
             log.trace("Processing initial chunk order: {}", chunk.getOriginalOrder()); // Use TRACE
            if (chunk.getText().length() > MAX_CHUNK_SIZE_CHARS) {
                log.debug("Chunk (Initial Order {}) is large ({} chars), applying fallback splitting by paragraph.",
                        chunk.getOriginalOrder(), chunk.getText().length());

                String[] paragraphs = chunk.getText().split("\\n\\n+");
                int currentRelativeIndex = 0; // Tracks position within the current large chunk's text
                log.debug("Split large chunk into {} potential paragraphs.", paragraphs.length);

                for (int i = 0; i < paragraphs.length; i++) { // Loop with index for logging
                    String paragraph = paragraphs[i];
                    String trimmedParagraph = paragraph.trim();
                    int paragraphStartIndexInChunk = -1; // Initialize before the block

                    if (!trimmedParagraph.isEmpty()) {
                        // Find the start index of this paragraph within the original chunk's text
                        paragraphStartIndexInChunk = chunk.getText().indexOf(paragraph, currentRelativeIndex);
                        if (paragraphStartIndexInChunk == -1) {
                             log.warn("Could not reliably find start index for paragraph {} in chunk {}. Skipping.", i, chunk.getOriginalOrder());
                             // Update relative index cautiously to avoid infinite loops if indexOf fails repeatedly
                             currentRelativeIndex += paragraph.length();
                             continue; // Skip if index finding fails
                        }
                        int paragraphStartIndex = chunk.getStartIndex() + paragraphStartIndexInChunk;
                        int paragraphEndIndex = paragraphStartIndex + paragraph.length(); // Use original paragraph length for index

                        log.trace("Processing paragraph {} (StartIdx: {}, EndIdx: {})", i, paragraphStartIndex, paragraphEndIndex);

                        if (trimmedParagraph.length() > MAX_CHUNK_SIZE_CHARS) {
                            log.debug("Paragraph {} within chunk {} is still too large ({} chars), splitting by sentence.", i, chunk.getOriginalOrder(), trimmedParagraph.length());
                            String[] sentences = trimmedParagraph.split("(?<=[.!?])\\s+"); // Basic sentence split
                            int currentSentenceRelativeIndex = 0; // Tracks position within the current paragraph's text
                             log.debug("Split paragraph into {} potential sentences.", sentences.length);

                             for (int j = 0; j < sentences.length; j++) { // Loop with index for logging
                                 String sentence = sentences[j];
                                 String trimmedSentence = sentence.trim();
                                 if (!trimmedSentence.isEmpty()) {
                                     // Find start index of sentence within the paragraph
                                     int sentenceStartIndexInPara = trimmedParagraph.indexOf(sentence, currentSentenceRelativeIndex);
                                     if (sentenceStartIndexInPara == -1) {
                                         log.warn("Could not reliably find start index for sentence {} in paragraph {}. Skipping.", j, i);
                                         currentSentenceRelativeIndex += sentence.length(); // Update cautiously
                                         continue; // Skip if index finding fails
                                     }
                                     int sentenceStartIndex = paragraphStartIndex + sentenceStartIndexInPara;
                                     int sentenceEndIndex = sentenceStartIndex + sentence.length(); // Use original sentence length

                                     log.trace("Adding sentence chunk (Final Order {}): StartIdx={}, EndIdx={}", finalChunkOrder, sentenceStartIndex, sentenceEndIndex);
                                     finalChunks.add(DocumentChunk.builder()
                                             .text(trimmedSentence)
                                             .originalOrder(finalChunkOrder++)
                                             .startIndex(sentenceStartIndex)
                                             .endIndex(sentenceEndIndex)
                                             .sectionTitle(chunk.getSectionTitle()) // Inherit parent section title
                                             .build());
                                     // Update relative index within the paragraph
                                     currentSentenceRelativeIndex = sentenceStartIndexInPara + sentence.length();
                                 } else {
                                      log.trace("Skipping empty sentence chunk (Index {}).", j);
                                 }
                             }
                        } else {
                           // Paragraph is within size limit
                            log.trace("Adding paragraph chunk (Final Order {}): StartIdx={}, EndIdx={}", finalChunkOrder, paragraphStartIndex, paragraphEndIndex);
                           finalChunks.add(DocumentChunk.builder()
                                   .text(trimmedParagraph)
                                   .originalOrder(finalChunkOrder++)
                                   .startIndex(paragraphStartIndex)
                                   .endIndex(paragraphEndIndex)
                                   .sectionTitle(chunk.getSectionTitle()) // Inherit parent section title
                                   .build());
                        }
                    } else {
                         log.trace("Skipping empty paragraph chunk (Index {}).", i);
                    }

                    // Update relative index within the original large chunk's text
                    // Ensure paragraphStartIndexInChunk was found before using it
                    if (paragraphStartIndexInChunk != -1) {
                         currentRelativeIndex = paragraphStartIndexInChunk + paragraph.length();
                    } else {
                         // If index wasn't found, advance cautiously based on paragraph length
                         // This might drift if indexOf keeps failing, but prevents getting stuck
                         currentRelativeIndex += paragraph.length();
                    }
                } // End of paragraph loop
            } else {
                // Chunk is within size limit, add it directly with updated order
                 log.trace("Adding original chunk (Initial Order {}) as final chunk (Final Order {}). Size: {}", chunk.getOriginalOrder(), finalChunkOrder, chunk.getText().length());
                finalChunks.add(DocumentChunk.builder()
                        .text(chunk.getText())
                        .originalOrder(finalChunkOrder++) // Assign new order
                        .startIndex(chunk.getStartIndex())
                        .endIndex(chunk.getEndIndex())
                        .sectionTitle(chunk.getSectionTitle())
                        .build());
            }
        } // End of initialChunks loop

        // 3. Final Clean up (remove any empty strings)
        int countBeforeCleanup = finalChunks.size();
        finalChunks.removeIf(c -> c.getText() == null || c.getText().trim().isEmpty());
        int countAfterCleanup = finalChunks.size();
        if (countBeforeCleanup != countAfterCleanup) {
            log.debug("Removed {} empty chunks during final cleanup.", countBeforeCleanup - countAfterCleanup);
        }


        // --- >>> EXISTING FINAL LOGGING <<< ---
        if (log.isDebugEnabled()) {
            log.debug("--- Final Document Chunks Generated (Count: {}) ---", finalChunks.size());
            for (DocumentChunk finalChunk : finalChunks) {
                log.debug("Chunk Order: {}, Title: '{}', StartIdx: {}, EndIdx: {}, Text Snippet: '{}...'",
                        finalChunk.getOriginalOrder(),
                        finalChunk.getSectionTitle() != null ? finalChunk.getSectionTitle() : "N/A",
                        finalChunk.getStartIndex(),
                        finalChunk.getEndIndex(),
                        truncateTextForLog(finalChunk.getText(), 100)
                );
            }
            log.debug("--- End of Final Document Chunks ---");
        }
        // --- >>> END OF EXISTING FINAL LOGGING <<< ---

        log.info("Split text into {} final chunks.", finalChunks.size());
        return finalChunks;
    }

    // Helper method for truncating log output
    private String truncateTextForLog(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength).replace("\n", " "); // Replace newlines for cleaner log output
    }

    // TODO: Refine HEADING_REGEX based on backtesting results.
}
