package com.InsightLens.service;

import com.InsightLens.model.Document;
import com.InsightLens.model.DocumentSection;
import com.InsightLens.repository.DocumentRepository;
import com.InsightLens.repository.DocumentSectionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentSectionRepository sectionRepository;
    private final StorageService storageService;
    private final TextExtractorService textExtractor;
    private final DocumentTypeDetectorService typeDetector;
    private final TextSplitterService splitter;
    private final EmbeddingService embeddingService;

    /**
     * Handles document upload, saves initial document record, and triggers
     * asynchronous processing. Returns immediately after saving the initial record.
     *
     * @param file The uploaded MultipartFile.
     * @return The saved Document entity with PROCESSING status.
     * @throws IOException if file storage fails.
     */
    @Transactional // Apply transaction to the initial save
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
            .status(Document.DocumentStatus.PROCESSING) // Set status to PROCESSING
            .uploadDate(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            // domainType will be detected asynchronously
            // sections, summary, tags, insights populated asynchronously
            .build();

        document = documentRepository.save(document);
        log.info("Initial Document record saved with ID: {}", document.getId());

        // 3. Trigger asynchronous processing
        processDocumentAsync(document.getId(), filePath); // Pass ID and file path

        // Return the document immediately, processing continues in the background
        return document;
    }

    /**
     * Performs the heavy document processing tasks asynchronously.
     * This method runs in a separate thread.
     *
     * @param documentId The ID of the Document to process.
     * @param filePath The path where the file is stored.
     */
    @Async // Marks this method for asynchronous execution
    @Transactional // Apply transaction to the processing logic
    public void processDocumentAsync(Long documentId, String filePath) {
        log.info("Starting asynchronous processing for Document ID: {}", documentId);

        Optional<Document> optionalDocument = documentRepository.findById(documentId);
        if (!optionalDocument.isPresent()) {
            log.error("Document with ID {} not found for async processing.", documentId);
            return; // Cannot process if document not found
        }
        Document document = optionalDocument.get();

        try {
            // Ensure status is still PROCESSING before starting heavy tasks
            if (document.getStatus() != Document.DocumentStatus.PROCESSING) {
                 log.warn("Document ID {} is not in PROCESSING status. Skipping async processing.", documentId);
                 return;
            }

            // 1. Extract text (using the stored file path)
            // Assuming TextExtractorService has extractTextFromPath(String filePath)
            // and it handles the file type internally or defaults to PDF for MVP
            String fullText = textExtractor.extractTextFromPath(filePath); // Corrected method call
            log.info("Text extracted for Document ID: {}", documentId);

            // 2. Detect type (can be done here asynchronously for better accuracy on full text)
            // Assuming DocumentTypeDetectorService has a public String detect(String text) method
            String detectedType = typeDetector.detect(fullText); // Corrected method call
            document.setDomainType(detectedType);
            log.info("Domain detected for Document ID {}: {}", documentId, detectedType);


            // 3. Split text into sections
            // Assuming TextSplitterService has public List<String> split(String text) method
            List<String> sectionsText = splitter.split(fullText); // Corrected method call
            log.info("Split Document ID {} into {} sections.", documentId, sectionsText.size());

            // 4. Generate embedding for each section and save DocumentSection entities
            // This loop should be optimized, potentially batching embedding calls
            for (int i = 0; i < sectionsText.size(); i++) {
                String sectionText = sectionsText.get(i);
                try {
                    // Call the embedding service to get the vector for the section text
                    // ✅ Corrected method name from getEmbedding to generateEmbedding
                    // ✅ generateEmbedding now returns float[]
                    float[] embedding = embeddingService.generateEmbedding(sectionText); // Corrected method call

                    // ✅ Corrected builder method name from sectionOrder to sectionOrder
                    // Assuming DocumentSection model has a field named 'sectionOrder'
                    DocumentSection section = DocumentSection.builder()
                        .text(sectionText)
                        .embedding(embedding) // embedding is float[]
                        .document(document) // Link to the parent document
                        .sectionOrder(i) // Corrected builder method name
                        .build();
                    // Consider batch saving sections for performance
                    sectionRepository.save(section);
                    log.debug("Saved section {} for Document ID {}", i, documentId);
                } catch (Exception e) {
                    log.error("Failed to generate/save embedding for section {} of Document ID {}: {}", i, documentId, e.getMessage());
                    // Decide how to handle section-level errors (skip section? fail document?)
                    // For MVP, maybe log and continue, or mark document as partially processed
                }
            }
            log.info("Processed and saved sections/embeddings for Document ID: {}", documentId);


            // 5. Perform other async tasks if needed (e.g., initial summary, insights)
            // document.setSummary(generateSummary(fullText));
            // document.setInsights(generateInsights(fullText, detectedType));


            // 6. Mark document as processed
            document.setStatus(Document.DocumentStatus.PROCESSED);
            document.setProcessedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document); // Save final status and async-populated fields
            log.info("Finished asynchronous processing for Document ID: {}. Status: PROCESSED", documentId);

        } catch (Exception e) {
            // Handle any exception during processing
            log.error("Asynchronous processing failed for Document ID {}: {}", documentId, e.getMessage(), e);

            // Update document status to FAILED
            document.setStatus(Document.DocumentStatus.FAILED);
            document.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(document); // Save FAILED status
            log.info("Marked Document ID {} as FAILED.", documentId);
        }
    }

    // Note: This service assumes the existence of other services like:
    // StorageService, TextExtractorService, DocumentTypeDetectorService,
    // TextSplitterService, and EmbeddingService, as well as DocumentRepository
    // and DocumentSectionRepository with correct methods and builder setup.
}
