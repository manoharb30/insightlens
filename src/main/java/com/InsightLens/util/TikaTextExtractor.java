package com.InsightLens.util; // Recommended package, adjust if needed

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component; // Optional: if you want Spring to manage it
import org.springframework.web.multipart.MultipartFile; // If you want to handle MultipartFile directly
import org.xml.sax.SAXException; // Import SAXException

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class for extracting text content from various document formats
 * using Apache Tika.
 */
@Component // Use @Component or @Service if you want Spring to manage instances
public class TikaTextExtractor {

    private final Parser parser; // Use AutoDetectParser to handle multiple formats

    /**
     * Constructor initializes the AutoDetectParser.
     */
    public TikaTextExtractor() {
        this.parser = new AutoDetectParser();
    }

    /**
     * Extracts text content from an InputStream.
     *
     * @param inputStream The InputStream of the document file.
     * @return The extracted text content as a String.
     * @throws IOException If an I/O error occurs during reading the stream.
     * @throws TikaException If a Tika-specific error occurs during parsing.
     * @throws SAXException If a SAX error occurs during parsing (e.g., XML related).
     */
    public String extractText(InputStream inputStream) throws IOException, TikaException, SAXException {
        // BodyContentHandler writes the text content to a StringWriter by default
        BodyContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata(); // To capture document metadata
        ParseContext context = new ParseContext(); // Context for the parser

        try {
            // Parse the document stream
            parser.parse(inputStream, handler, metadata, context);
        } finally {
            // Ensure the input stream is closed
            if (inputStream != null) {
                inputStream.close();
            }
        }

        // Return the extracted text
        return handler.toString();
    }

    /**
     * Extracts text content directly from a Spring MultipartFile.
     * This is a convenience method often used in web applications.
     *
     * @param file The MultipartFile representing the uploaded document.
     * @return The extracted text content as a String.
     * @throws IOException If an I/O error occurs.
     * @throws TikaException If a Tika-specific error occurs during parsing.
     * @throws SAXException If a SAX error occurs during parsing.
     */
    public String extractText(MultipartFile file) throws IOException, TikaException, SAXException {
        // Get the InputStream from the MultipartFile and delegate to the other method
        return extractText(file.getInputStream());
    }

    // Optional: Method to extract text AND metadata if needed later
    // public TextAndMetadata extractTextAndMetadata(InputStream inputStream) throws IOException, TikaException, SAXException {
    //     BodyContentHandler handler = new BodyContentHandler();
    //     Metadata metadata = new Metadata();
    //     ParseContext context = new ParseContext();
    //
    //     try {
    //         parser.parse(inputStream, handler, metadata, context);
    //     } finally {
    //         if (inputStream != null) {
    //             inputStream.close();
    //         }
    //     }
    //     // You would need a simple class like TextAndMetadata { String text; Metadata metadata; }
    //     return new TextAndMetadata(handler.toString(), metadata);
    // }
}
