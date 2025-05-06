package com.InsightLens.service.processing; // Recommended package, adjust if needed

import com.InsightLens.model.processing.DocumentChunk; // Import the new DocumentChunk class
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    // Dynamic chunk sizes based on content type
    private static final int LEGAL_CHUNK_SIZE = 800;    // Smaller for precise legal analysis
    private static final int FINANCIAL_CHUNK_SIZE = 1200; // Larger for financial context
    private static final int MEDICAL_CHUNK_SIZE = 1000;  // Standard for medical reports
    private static final int DEFAULT_CHUNK_SIZE = 1000;
    private static final int DEFAULT_CHUNK_OVERLAP = 200;

    // Enhanced patterns for better section detection
    private static final Pattern LEGAL_SECTION_PATTERN = Pattern.compile(
        "(?m)^(?:[0-9]+\\.|[A-Z]+\\.|§|Article|Section|Clause|WHEREAS|NOW, THEREFORE|IN WITNESS WHEREOF)\\s+.*$"
    );
    
    private static final Pattern FINANCIAL_SECTION_PATTERN = Pattern.compile(
        "(?m)^(?:Financial Highlights|Revenue|Expenses|Net Income|Balance Sheet|Cash Flow|Notes to Financial Statements)\\s*.*$"
    );
    
    private static final Pattern MEDICAL_SECTION_PATTERN = Pattern.compile(
        "(?m)^(?:History of Present Illness|Past Medical History|Family History|Social History|Review of Systems|Physical Examination|Assessment|Plan)\\s*.*$"
    );

    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("(?<=[.!?])\\s+");

    // --- Configuration for Chunking Strategy ---
    private static final String HEADING_REGEX =
            "(?m)^\\s*(ARTICLE\\s+[IVXLCDM]+\\.?|ARTICLE\\s+\\d+\\.?|SECTION\\s+\\d+(\\.\\d+)*\\.?|CHAPTER\\s+\\d+\\.?|[IVXLCDM]+\\.\\s+|\\d+\\.\\s*|[A-Za-z]\\.\\s*|Executive Summary|Introduction|Background|Findings|Conclusion)\\s*.*$";
    private static final Pattern HEADING_PATTERN = Pattern.compile(HEADING_REGEX);
    private static final int MAX_CHUNK_SIZE_CHARS = 2000;

    // --- Implementation ---

    /**
     * Splits text into chunks based on document type and content structure
     */
    public List<TextChunk> splitText(String text, String documentType) {
        log.debug("Starting text splitting for document type: {}", documentType);
        
        if (!StringUtils.hasText(text)) {
            log.warn("Empty text provided for splitting");
            return new ArrayList<>();
        }

        List<TextChunk> chunks;
        switch (documentType.toLowerCase()) {
            case "legal":
                chunks = splitLegalDocument(text);
                break;
            case "financial":
                chunks = splitFinancialDocument(text);
                break;
            case "medical":
                chunks = splitMedicalDocument(text);
                break;
            default:
                chunks = splitGenericDocument(text);
        }

        log.debug("Split text into {} chunks", chunks.size());
        return chunks;
    }

    /**
     * Enhanced legal document splitting with semantic awareness
     */
    private List<TextChunk> splitLegalDocument(String text) {
        List<TextChunk> chunks = new ArrayList<>();
        String[] sections = LEGAL_SECTION_PATTERN.split(text);
        
        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            if (section.isEmpty()) continue;

            // Extract section header and metadata
            String header = extractSectionHeader(section);
            String content = section.substring(header.length()).trim();
            
            // Identify clause type
            String clauseType = identifyClauseType(content);
            
            // Split content into smaller chunks if needed
            if (content.length() > LEGAL_CHUNK_SIZE) {
                List<String> subChunks = splitIntoOverlappingChunks(content, LEGAL_CHUNK_SIZE);
                for (String subChunk : subChunks) {
                    chunks.add(new TextChunk(
                        header + "\n" + subChunk,
                        "legal",
                        clauseType,
                        i,
                        extractKeyEntities(subChunk) // New: Extract key entities
                    ));
                }
            } else {
                chunks.add(new TextChunk(
                    section,
                    "legal",
                    clauseType,
                    i,
                    extractKeyEntities(content)
                ));
            }
        }
        return chunks;
    }

    /**
     * Enhanced financial document splitting with table awareness
     */
    private List<TextChunk> splitFinancialDocument(String text) {
        List<TextChunk> chunks = new ArrayList<>();
        String[] sections = FINANCIAL_SECTION_PATTERN.split(text);
        
        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            if (section.isEmpty()) continue;

            // Enhanced table detection
            if (isTable(section)) {
                chunks.add(new TextChunk(
                    section,
                    "financial",
                    "table",
                    i,
                    extractTableMetadata(section)
                ));
            } else {
                // Split long text sections with financial context
                if (section.length() > FINANCIAL_CHUNK_SIZE) {
                    List<String> subChunks = splitIntoOverlappingChunks(section, FINANCIAL_CHUNK_SIZE);
                    for (String subChunk : subChunks) {
                        chunks.add(new TextChunk(
                            subChunk,
                            "financial",
                            identifyFinancialSectionType(subChunk),
                            i,
                            extractFinancialMetrics(subChunk)
                        ));
                    }
                } else {
                    chunks.add(new TextChunk(
                        section,
                        "financial",
                        identifyFinancialSectionType(section),
                        i,
                        extractFinancialMetrics(section)
                    ));
                }
            }
        }
        return chunks;
    }

    /**
     * Enhanced medical document splitting with structured data extraction
     */
    private List<TextChunk> splitMedicalDocument(String text) {
        List<TextChunk> chunks = new ArrayList<>();
        String[] sections = MEDICAL_SECTION_PATTERN.split(text);
        
        for (int i = 0; i < sections.length; i++) {
            String section = sections[i].trim();
            if (section.isEmpty()) continue;

            String sectionType = identifyMedicalSectionType(section);
            List<String> keyFindings = extractMedicalFindings(section);
            
            if (section.length() > MEDICAL_CHUNK_SIZE) {
                List<String> subChunks = splitIntoOverlappingChunks(section, MEDICAL_CHUNK_SIZE);
                for (String subChunk : subChunks) {
                    chunks.add(new TextChunk(
                        subChunk,
                        "medical",
                        sectionType,
                        i,
                        keyFindings
                    ));
                }
            } else {
                chunks.add(new TextChunk(
                    section,
                    "medical",
                    sectionType,
                    i,
                    keyFindings
                ));
            }
        }
        return chunks;
    }

    /**
     * Generic document splitting for unknown document types
     */
    private List<TextChunk> splitGenericDocument(String text) {
        List<TextChunk> chunks = new ArrayList<>();
        String[] paragraphs = PARAGRAPH_PATTERN.split(text);
        
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i].trim();
            if (paragraph.isEmpty()) continue;

            if (paragraph.length() > DEFAULT_CHUNK_SIZE) {
                List<String> subChunks = splitIntoOverlappingChunks(paragraph, DEFAULT_CHUNK_SIZE);
                for (String subChunk : subChunks) {
                    chunks.add(new TextChunk(
                        subChunk,
                        "generic",
                        "paragraph",
                        i,
                        extractKeyEntities(subChunk)
                    ));
                }
            } else {
                chunks.add(new TextChunk(
                    paragraph,
                    "generic",
                    "paragraph",
                    i,
                    extractKeyEntities(paragraph)
                ));
            }
        }
        return chunks;
    }

    /**
     * Splits text into overlapping chunks of specified size
     */
    private List<String> splitIntoOverlappingChunks(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            
            // Try to find a natural break point
            if (end < text.length()) {
                // First try to break at sentence boundary
                int lastSentence = findLastSentenceBoundary(text, start, end);
                if (lastSentence > start) {
                    end = lastSentence;
                } else {
                    // Fallback to word boundary
                    int lastSpace = text.lastIndexOf(' ', end);
                    if (lastSpace > start) {
                        end = lastSpace;
                    }
                }
            }
            
            chunks.add(text.substring(start, end).trim());
            start = end - DEFAULT_CHUNK_OVERLAP;
        }
        
        return chunks;
    }

    /**
     * Extracts section header from text
     */
    private String extractSectionHeader(String text) {
        int firstNewline = text.indexOf('\n');
        if (firstNewline > 0) {
            return text.substring(0, firstNewline).trim();
        }
        return "";
    }

    /**
     * Identifies the type of medical section
     */
    private String identifyMedicalSectionType(String text) {
        String lowerText = text.toLowerCase();
        if (lowerText.contains("diagnosis") || lowerText.contains("dx")) {
            return "diagnosis";
        } else if (lowerText.contains("treatment") || lowerText.contains("rx")) {
            return "treatment";
        } else if (lowerText.contains("history") || lowerText.contains("hx")) {
            return "history";
        } else if (lowerText.contains("examination") || lowerText.contains("exam")) {
            return "examination";
        } else {
            return "other";
        }
    }

    /**
     * Enhanced TextChunk with additional metadata
     */
    public static class TextChunk {
        private final String text;
        private final String documentType;
        private final String sectionType;
        private final int order;
        private final List<String> metadata; // New: Additional metadata

        public TextChunk(String text, String documentType, String sectionType, int order, List<String> metadata) {
            this.text = text;
            this.documentType = documentType;
            this.sectionType = sectionType;
            this.order = order;
            this.metadata = metadata;
        }

        public String getText() {
            return text;
        }

        public String getDocumentType() {
            return documentType;
        }

        public String getSectionType() {
            return sectionType;
        }

        public int getOrder() {
            return order;
        }

        public List<String> getMetadata() {
            return metadata;
        }
    }

    // Helper method for truncating log output
    private String truncateTextForLog(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength).replace("\n", " "); // Replace newlines for cleaner log output
    }

    // TODO: Refine HEADING_REGEX based on backtesting results.

    // New helper methods for enhanced chunking

    private String identifyClauseType(String text) {
        String lowerText = text.toLowerCase();
        if (lowerText.contains("whereas")) return "recital";
        if (lowerText.contains("now, therefore")) return "operative";
        if (lowerText.contains("in witness whereof")) return "signature";
        if (lowerText.contains("definitions")) return "definition";
        return "general";
    }

    private boolean isTable(String text) {
        return text.contains("|") || text.contains("\t") || 
               text.matches(".*\\d+\\s*\\|\\s*\\d+.*"); // Matches number|number pattern
    }

    private List<String> extractTableMetadata(String table) {
        List<String> metadata = new ArrayList<>();
        // Extract column headers, row count, etc.
        return metadata;
    }

    private String identifyFinancialSectionType(String text) {
        String lowerText = text.toLowerCase();
        if (lowerText.contains("revenue") || lowerText.contains("income")) return "revenue";
        if (lowerText.contains("expense") || lowerText.contains("cost")) return "expense";
        if (lowerText.contains("asset") || lowerText.contains("liability")) return "balance";
        return "other";
    }

    private List<String> extractFinancialMetrics(String text) {
        List<String> metrics = new ArrayList<>();
        // Extract numbers, percentages, dates
        return metrics;
    }

    private List<String> extractMedicalFindings(String text) {
        List<String> findings = new ArrayList<>();
        // Extract medical terms, measurements, observations
        return findings;
    }

    private List<String> extractKeyEntities(String text) {
        List<String> entities = new ArrayList<>();
        // Extract named entities, dates, numbers
        return entities;
    }

    private int findLastSentenceBoundary(String text, int start, int end) {
        String substring = text.substring(start, end);
        String[] sentences = SENTENCE_PATTERN.split(substring);
        if (sentences.length > 1) {
            return start + sentences[sentences.length - 2].length() + 1;
        }
        return -1;
    }
}
