package com.bfsi.jpmc.controller;

import com.bfsi.jpmc.service.ProcessPaymentsInputData;
import com.bfsi.jpmc.service.SepaFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/sepa")
@CrossOrigin(origins = "*")
public class SepaController {

    private static final Logger logger = LoggerFactory.getLogger(SepaController.class);

    private final SepaFileService sepaFileService;
    private final ProcessPaymentsInputData processPaymentsInputData;

    @Autowired
    public SepaController(SepaFileService sepaFileService, ProcessPaymentsInputData processPaymentsInputData) {
        this.sepaFileService = sepaFileService;
        this.processPaymentsInputData = processPaymentsInputData;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Received file upload request: {}", file.getOriginalFilename());
            
            // Validate file type
            String filename = file.getOriginalFilename();
            String lowerFilename = filename != null ? filename.toLowerCase() : "";
            boolean isValidType = lowerFilename.endsWith(".xlsx") || lowerFilename.endsWith(".xls") ||
                                  lowerFilename.endsWith(".csv") || lowerFilename.endsWith(".txt");
            
            if (!isValidType) {
                response.put("success", false);
                response.put("message", "Invalid file type. Only .xlsx, .xls, .csv, and .txt files are allowed.");
                return ResponseEntity.badRequest().body(response);
            }

            // Save uploaded file
            File savedFile = sepaFileService.saveUploadedFile(file);
            logger.info("File saved to: {}", savedFile.getAbsolutePath());

            // Process the file
            processPaymentsInputData.processPaymentInputData(savedFile.getAbsolutePath());

            // Get generated files
            List<String> generatedFiles = sepaFileService.getGeneratedFiles();
            
            response.put("success", true);
            response.put("message", "File processed successfully");
            response.put("inputFile", filename);
            response.put("generatedFiles", generatedFiles);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing file: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error processing file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listGeneratedFiles() {
        Map<String, Object> response = new HashMap<>();
        List<String> files = sepaFileService.getGeneratedFiles();
        response.put("files", files);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            Path filePath = sepaFileService.getFilePath(filename);
            Resource resource = sepaFileService.loadFileAsResource(filename);
            
            String contentType = filename.toLowerCase().endsWith(".xml") 
                ? "application/xml" 
                : "text/plain";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
                    
        } catch (IOException e) {
            logger.error("Error downloading file: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "SEPA Generator");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/input-files")
    public ResponseEntity<Map<String, Object>> listInputFiles(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search) {
        Map<String, Object> response = new HashMap<>();
        try {
            Path inputPath = sepaFileService.getInputPath();
            List<String> files;
            try (Stream<Path> paths = Files.list(inputPath)) {
                files = paths
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> {
                            String lower = name.toLowerCase();
                            if (type != null && !type.isEmpty()) {
                                return lower.endsWith("." + type.toLowerCase());
                            }
                            return lower.endsWith(".xlsx") || lower.endsWith(".xls") || 
                                   lower.endsWith(".csv") || lower.endsWith(".txt");
                        })
                        .filter(name -> {
                            if (search != null && !search.isEmpty()) {
                                return name.toLowerCase().contains(search.toLowerCase());
                            }
                            return true;
                        })
                        .sorted()
                        .collect(Collectors.toList());
            }
            response.put("files", files);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            logger.error("Error listing input files: {}", e.getMessage());
            response.put("files", List.of());
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processFile(@RequestParam("filename") String filename) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Received process request for file: {}", filename);
            
            // Validate file type
            String lowerFilename = filename != null ? filename.toLowerCase() : "";
            boolean isValidType = lowerFilename.endsWith(".xlsx") || lowerFilename.endsWith(".xls") ||
                                  lowerFilename.endsWith(".csv") || lowerFilename.endsWith(".txt");
            if (!isValidType) {
                response.put("success", false);
                response.put("message", "Invalid file type. Only .xlsx, .xls, .csv, and .txt files are allowed.");
                return ResponseEntity.badRequest().body(response);
            }

            // Get the file from input directory
            Path inputPath = sepaFileService.getInputPath().resolve(filename);
            File file = inputPath.toFile();
            
            if (!file.exists()) {
                response.put("success", false);
                response.put("message", "File not found in input directory: " + filename);
                return ResponseEntity.badRequest().body(response);
            }

            // Capture time before processing
            Instant processingStart = Instant.now();
            
            // Process the file
            processPaymentsInputData.processPaymentInputData(file.getAbsolutePath());

            // Get files generated during this processing (within last 5 seconds)
            List<String> generatedFiles = sepaFileService.getRecentlyGeneratedFiles(processingStart.minusSeconds(1));
            
            response.put("success", true);
            response.put("message", "File processed successfully");
            response.put("inputFile", filename);
            response.put("generatedFiles", generatedFiles);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing file: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Error processing file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
