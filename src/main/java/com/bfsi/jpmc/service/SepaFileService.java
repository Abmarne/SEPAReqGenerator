package com.bfsi.jpmc.service;

import com.bfsi.jpmc.util.SepaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SepaFileService {

    private static final Logger logger = LoggerFactory.getLogger(SepaFileService.class);

    private final SepaUtil sepaUtil;
    private Path outputPath;

    @Autowired
    public SepaFileService(SepaUtil sepaUtil) {
        this.sepaUtil = sepaUtil;
    }

    @PostConstruct
    public void init() {
        this.outputPath = Paths.get(sepaUtil.getOutputFileDir()).toAbsolutePath().normalize();
        
        try {
            Files.createDirectories(outputPath);
            logger.info("Output directory: {}", outputPath);
        } catch (IOException e) {
            logger.error("Could not create directories", e);
            throw new RuntimeException("Could not create upload directories", e);
        }
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

    public List<String> getGeneratedFiles() {
        List<String> files = new ArrayList<>();
        
        try (Stream<Path> paths = Files.list(outputPath)) {
            files = paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("STA_") || name.startsWith("ERR_"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error listing files: {}", e.getMessage());
        }
        
        return files;
    }
    
    public List<String> getRecentlyGeneratedFiles(Instant since) {
        List<String> files = new ArrayList<>();
        
        try (Stream<Path> paths = Files.list(outputPath)) {
            files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            FileTime lastModified = Files.getLastModifiedTime(path);
                            return lastModified.toInstant().isAfter(since);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("STA_") || name.startsWith("ERR_"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error listing files: {}", e.getMessage());
        }
        
        return files;
    }

    public Path getFilePath(String filename) {
        return outputPath.resolve(filename).normalize();
    }

    public Resource loadFileAsResource(String filename) throws IOException {
        Path filePath = getFilePath(filename);
        Resource resource = new UrlResource(filePath.toUri());
        
        if (resource.exists()) {
            return resource;
        } else {
            throw new IOException("File not found: " + filename);
        }
    }
    public Path getOutputPath() {
        return outputPath;
    }
}
