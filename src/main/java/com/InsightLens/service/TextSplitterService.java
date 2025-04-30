package com.InsightLens.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service responsible for splitting the full text of a document into smaller sections.
 * These sections are used for generating embeddings and detailed analysis.
 */
@Service
@Slf4j
public class TextSplitterService {

    // A simple strategy: split by paragraph breaks (double newline)
    private static final String PARAGRAPH_DELIMITER = "\\r?\\n\\r?\\n"; // Handles Windows and Unix newlines

    /**
     * Splits the given full text of a document into a list of sections.
     *
     * @param fullText The complete text extracted from the document.
     * @return A List of strings, where each string is a section.
     * Returns an empty list if the input text is null or empty.
     */
    public List<String> split(String fullText) {
        if (fullText == null || fullText.trim().isEmpty()) {
            log.warn("Input text for splitting is null or empty. Returning empty list.");
            return new ArrayList<>();
        }

        log.debug("Splitting text into sections...");

        // Split the text using the paragraph delimiter
        // Using Arrays.asList and then creating a new ArrayList is common to get a mutable list
        List<String> sections = new ArrayList<>(Arrays.asList(fullText.split(PARAGRAPH_DELIMITER)));

        // Optional: Clean up sections (trim whitespace, remove empty sections)
        sections.removeIf(section -> section == null || section.trim().isEmpty());

        log.info("Split text into {} sections.", sections.size());

        return sections;
    }

    // TODO: Consider implementing more advanced splitting strategies later:
    // - Splitting by sentence (requires NLP libraries)
    // - Splitting by fixed token count (better for consistency with embedding models)
    // - Recursive splitting (attempt paragraphs, then sentences if too large)
    // - Splitting based on headings/structure (requires parsing document structure, more complex)
}
