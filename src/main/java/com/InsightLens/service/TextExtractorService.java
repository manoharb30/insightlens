package com.InsightLens.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Simplified for MVP to only handle PDF extraction from a file path.
// TODO: Add support for other file types (DOCX, TXT, XLSX, etc.) later if needed.

@Service
@Slf4j
public class TextExtractorService {

    /**
     * Extracts text from a PDF document file located at a given file path.
     * This method is suitable for use in the asynchronous processing pipeline
     * and is simplified for the MVP to only handle PDFs.
     *
     * @param filePath The absolute path to the stored PDF file.
     * @return The extracted text content.
     * @throws IOException if reading the file or extracting text fails.
     * @throws IllegalArgumentException if the file path is invalid, file is empty, or file is not a PDF.
     */
    public String extractTextFromPath(String filePath) throws IOException { // Signature changed to accept only filePath
        if (filePath == null || filePath.trim().isEmpty()) {
            log.error("Provided file path is null or empty.");
            throw new IllegalArgumentException("File path cannot be null or empty.");
        }

        File file = Paths.get(filePath).toFile();
        if (!file.exists() || !file.isFile()) {
             log.error("File not found or is not a regular file at path: {}", filePath);
             throw new IOException("File not found or is not a regular file at path: " + filePath);
        }
         if (file.length() == 0) {
             log.warn("File at path {} is empty.", filePath);
             return ""; // Return empty string for empty files
         }

        log.info("Attempting to extract text from PDF file at path: {}", filePath);

        // Directly call the PDF extraction method, assuming the file is a PDF
        String extractedText = extractTextFromPdfFile(file);

        log.info("Successfully extracted text from PDF file at path: {}", filePath);
        return extractedText;
    }

    /**
     * Extracts text directly from a PDF MultipartFile (original method - less suitable for async).
     * Keeping for reference.
     *
     * @param file The uploaded MultipartFile.
     * @return The extracted text content.
     * @throws IOException if reading the file or extracting text fails.
     * @throws IllegalArgumentException if the uploaded file is empty.
     */
    public String extractTextFromPDF(MultipartFile file) throws IOException {
        log.warn("Using extractTextFromPDF(MultipartFile) - consider using extractTextFromPath(String) for async processing.");
        if (file.isEmpty()) {
            log.error("Uploaded MultipartFile is empty.");
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String extractedText = pdfStripper.getText(document);
            log.info("Successfully extracted text from MultipartFile: {}", file.getOriginalFilename());
            return extractedText;
        } catch (IOException ex) {
            log.error("Failed to extract text from MultipartFile {}: {}", file.getOriginalFilename(), ex.getMessage());
            throw new IOException("Failed to extract text from PDF MultipartFile", ex);
        }
    }


    /**
     * Private helper method to extract text from a PDF File object.
     *
     * @param pdfFile The PDF File object.
     * @return The extracted text.
     * @throws IOException if extraction fails.
     */
    private String extractTextFromPdfFile(File pdfFile) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            return pdfStripper.getText(document);
        } catch (IOException ex) {
            log.error("Failed to extract text from PDF file {}: {}", pdfFile.getAbsolutePath(), ex.getMessage());
            throw new IOException("Failed to extract text from PDF file " + pdfFile.getAbsolutePath(), ex);
        }
    }

    // Removed placeholder methods for other file types to simplify for MVP
}
