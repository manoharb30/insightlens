package com.InsightLens.service;

import com.InsightLens.model.Document;
import com.InsightLens.model.DocumentSection;
import com.InsightLens.model.processing.DocumentChunk; // Import DocumentChunk
import com.InsightLens.repository.DocumentRepository;
import com.InsightLens.repository.DocumentSectionRepository;
import com.InsightLens.util.TikaTextExtractor;
import com.InsightLens.service.processing.TextSplitterService; // Import TextSplitterService
import com.InsightLens.service.processing.EmbeddingService; // Import EmbeddingService

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.io.InputStream; // Import InputStream

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentSectionRepository sectionRepository;
    private final StorageService storageService;
    private final TikaTextExtractor tikaTextExtractor;
    private final DocumentTypeDetectorService typeDetector;
    private final TextSplitterService splitter; // TextSplitterService is now injected
    private final EmbeddingService embeddingService; // EmbeddingService is now injected

    /**
     * Handles document upload, saves initial document record, and triggers
     * asynchronous processing. Returns immediately after saving the initial record.
     *
     * @param file The uploaded MultipartFile.
     * @return The saved Document entity with PROCESSING status.
     * @throws IOException if file storage fails.
     */
    @Transactional
    public Document uploadDocument(MultipartFile file) throws IOException {
        log.info("Starting upload process for file: {}", file.getOriginalFilename());

        // 1. Store file
        String filePath = storageService.store(file);
        log.info("File stored at: {}", filePath);

        // 2. Save basic Document with PROCESSING status
        Document document = Document.builder()
            .filename(file.getOriginalFilename())
            .originalFilename(file.getOriginalFilename())
            .fileType(file.getContentType())
            .fileSize(file.getSize())
            .status(Document.DocumentStatus.PROCESSING)
            .uploadDate(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .filePath(filePath)
            .build();

        document = documentRepository.save(document);
        log.info("Initial Document record saved with ID: {}", document.getId());

        // 3. Trigger asynchronous processing
        processDocumentAsync(document.getId());

        return document;
    }

    /**
     * Performs the heavy document processing tasks asynchronously.
     * This method runs in a separate thread.
     *
     * @param documentId The ID of the Document to process.
     */
    @Async
    @Transactional // Ensure the entire async process runs in one transaction for batch saving
    public void processDocumentAsync(Long documentId) {
        log.info("Starting asynchronous processing for Document ID: {}", documentId);

        Optional<Document> optionalDocument = documentRepository.findById(documentId);
        if (!optionalDocument.isPresent()) {
            log.error("Document with ID {} not found for async processing.", documentId);
            return;
        }
        Document document = optionalDocument.get();

        try {
            if (document.getStatus() != Document.DocumentStatus.PROCESSING) {
                 log.warn("Document ID {} is not in PROCESSING status. Skipping async processing.", documentId);
                 return;
            }

            String filePath = document.getFilePath();
            if (filePath == null || filePath.isEmpty()) {
                 log.error("File path not found for Document ID {}. Cannot extract text.", documentId);
                 document.setStatus(Document.DocumentStatus.FAILED);
                 document.setUpdatedAt(LocalDateTime.now());
                 documentRepository.save(document);
                 return;
            }

            // 1. Extract text using TikaTextExtractor
            String fullText = null;
            Path fileSystemPath = Paths.get(filePath);
            try (InputStream inputStream = java.nio.file.Files.newInputStream(fileSystemPath)) {
                fullText = tikaTextExtractor.extractText(inputStream);
            }

            if (fullText == null || fullText.trim().isEmpty()) {
                 log.warn("Extracted text is empty for Document ID {}.", documentId);
                 // Decide how to handle empty text - maybe mark as processed but with a warning?
                 // For now, let's proceed, subsequent steps might handle empty input.
                 document.setStatus(Document.DocumentStatus.PROCESSED); // Mark as processed if empty
                 document.setProcessedAt(LocalDateTime.now());
                 document.setUpdatedAt(LocalDateTime.now());
                 documentRepository.save(document);
                 log.info("Finished asynchronous processing for Document ID: {}. Status: PROCESSED (Empty Content)", documentId);
                 return; // Exit if no text to process further
            } else {
                 log.info("Text extracted for Document ID: {}. Snippet: {}", documentId, fullText.substring(0, Math.min(fullText.length(), 500)) + (fullText.length() > 500 ? "..." : ""));
            }

            // 2. Detect type
            String detectedType = typeDetector.detect(fullText);
            document.setDomainType(detectedType);
            log.info("Domain detected for Document ID {}: {}", documentId, detectedType);

            // 3. Split text into sections and capture metadata (Task 2.4)
            // ✅ Now calling the split method that returns List<DocumentChunk>
            List<DocumentChunk> documentChunks = splitter.split(fullText);
            log.info("Split Document ID {} into {} chunks.", documentId, documentChunks.size());

            // 4. Generate embedding for each chunk and prepare for batch saving
            List<DocumentSection> sectionsToSave = new ArrayList<>();
            // ✅ Iterate through DocumentChunk objects
            for (DocumentChunk chunk : documentChunks) {
                String sectionText = chunk.getText(); // Get text from the chunk object
                if (sectionText == null || sectionText.trim().isEmpty()) {
                    log.debug("Skipping empty chunk (Order {}) for Document ID {}", chunk.getOriginalOrder(), documentId);
                    continue; // Skip empty chunks
                }
                try {
                    // Call the embedding service to get the vector for the section text
                    float[] embedding = embeddingService.generateEmbedding(sectionText);

                    // ✅ Build DocumentSection using metadata from DocumentChunk
                    DocumentSection section = DocumentSection.builder()
                        .text(sectionText)
                        .embedding(embedding)
                        .document(document) // Link to the parent document
                        .originalOrder(chunk.getOriginalOrder()) // Populate originalOrder
                        .startIndex(chunk.getStartIndex()) // Populate startIndex
                        .endIndex(chunk.getEndIndex()) // Populate endIndex
                        .sectionTitle(chunk.getSectionTitle()) // Populate sectionTitle (can be null)
                        .build();
                    sectionsToSave.add(section); // Add section to the list
                    log.debug("Prepared section (Order {}) for batch saving for Document ID {}", chunk.getOriginalOrder(), documentId);
                } catch (Exception e) {
                    log.error("Failed to generate embedding for chunk (Order {}) of Document ID {}: {}", chunk.getOriginalOrder(), documentId, e.getMessage());
                    // Decide how to handle section-level errors
                    // For MVP, log and continue might be acceptable.
                }
            }
            log.info("Generated embeddings and prepared {} sections for batch saving for Document ID: {}", sectionsToSave.size(), documentId);

            // Implement Batch Saving for Sections
            if (!sectionsToSave.isEmpty()) {
                 sectionRepository.saveAll(sectionsToSave); // Save all collected sections in a batch
                 log.info("Batch saved {} sections for Document ID: {}", sectionsToSave.size(), documentId);
            } else {
                 log.warn("No sections to save for Document ID {}.", documentId);
            }


            // 5. Perform other async tasks if needed (e.g., initial summary, insights)
            // document.setSummary(generateSummary(fullText));
            // document.setInsights(generateInsights(fullText, detectedType));


            // 6. Mark document as processed
            document.setStatus(Document.DocumentStatus.PROCESSED);
            document.setProcessedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);
            log.info("Finished asynchronous processing for Document ID: {}. Status: PROCESSED", documentId);

        } catch (IOException e) {
            log.error("I/O error accessing file for Document ID {}: {}", documentId, e.getMessage(), e);
            document.setStatus(Document.DocumentStatus.FAILED);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);
        } catch (TikaException e) {
            log.error("Tika error during text extraction for Document ID {}: {}", documentId, e.getMessage(), e);
            document.setStatus(Document.DocumentStatus.FAILED);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);
        } catch (SAXException e) {
            log.error("SAX error during text extraction for Document ID {}: {}", documentId, e.getMessage(), e);
            document.setStatus(Document.DocumentStatus.FAILED);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);
        } catch (Exception e) {
             // Catch any other unexpected exceptions during the async process
            log.error("An unexpected error occurred during async processing for Document ID {}: {}", documentId, e.getMessage(), e);
            document.setStatus(Document.DocumentStatus.FAILED);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document);
        }
    }

    /**
     * Retrieves a Document by its ID.
     * @param id The ID of the document.
     * @return An Optional containing the Document if found.
     */
    public Optional<Document> getDocumentById(Long id) {
        return documentRepository.findById(id);
    }

    /**
     * Retrieves all Documents.
     * @return A list of all Documents.
     */
    public List<Document> getAllDocuments() {
        return documentRepository.findAll();
    }

    public List<Document> getDocumentsByStatus(String status) {
        log.info("Fetching documents with status: {}", status);
        return documentRepository.findByStatus(Document.DocumentStatus.valueOf(status));
    }
}
