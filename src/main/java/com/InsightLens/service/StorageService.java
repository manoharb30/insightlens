package com.InsightLens.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class StorageService {

    private final String uploadDir = "uploads"; // You can later move to application.properties

    public StorageService() throws IOException {
        initUploadDirectory();
    }

    private void initUploadDirectory() throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
    }

    public String store(MultipartFile file) throws IOException {
        String filename = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(uploadDir, filename);
        Files.copy(file.getInputStream(), filePath);
        return filePath.toString(); // Save this path in DB
    }

    public File load(String filename) {
        return Paths.get(uploadDir, filename).toFile();
    }
}

