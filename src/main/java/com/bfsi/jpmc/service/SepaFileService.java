package com.bfsi.jpmc.service;

import com.bfsi.jpmc.model.GeneratedFileData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SepaFileService {

    private static final Logger logger = LoggerFactory.getLogger(SepaFileService.class);

    private final Map<String, GeneratedFileData> generatedFiles = new ConcurrentHashMap<>();

    @Autowired
    public SepaFileService() {
    }

    @PostConstruct
    public void init() {
        logger.info("Generated files will be stored in memory for UI access only.");
    }

    public File createTemporaryUploadFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String suffix = ".tmp";
        if (filename != null) {
            int extensionIndex = filename.lastIndexOf('.');
            if (extensionIndex >= 0 && extensionIndex < filename.length() - 1) {
                suffix = filename.substring(extensionIndex);
            }
        }

        Path targetLocation = Files.createTempFile("sepa-upload-", suffix);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("Temporary uploaded file created at: {}", targetLocation);
        return targetLocation.toFile();
    }

    public void deleteTemporaryFile(File file) {
        if (file == null) {
            return;
        }

        try {
            Files.deleteIfExists(file.toPath());
            logger.info("Temporary uploaded file deleted: {}", file.getAbsolutePath());
        } catch (IOException e) {
            logger.warn("Could not delete temporary uploaded file: {}", file.getAbsolutePath(), e);
        }
    }

    public void clearGeneratedFiles() {
        generatedFiles.clear();
    }

    public void saveGeneratedFile(String filename, String contentType, byte[] content) {
        generatedFiles.put(filename, new GeneratedFileData(filename, contentType, content));
        logger.info("Generated file stored in memory: {}", filename);
    }

    public List<String> getGeneratedFiles() {
        List<String> files = new ArrayList<>(generatedFiles.keySet());
        files.sort(Comparator.naturalOrder());
        return files;
    }

    public GeneratedFileData getGeneratedFile(String filename) throws IOException {
        GeneratedFileData fileData = generatedFiles.get(filename);
        if (fileData == null) {
            throw new IOException("File not found: " + filename);
        }
        return fileData;
    }

    public Resource loadFileAsResource(String filename) throws IOException {
        return new ByteArrayResource(getGeneratedFile(filename).getContent());
    }
}
