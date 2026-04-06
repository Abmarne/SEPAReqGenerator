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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        File uploadedFile = null;
        
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
            uploadedFile = sepaFileService.createTemporaryUploadFile(file);
            logger.info("File prepared for processing: {}", uploadedFile.getAbsolutePath());

            // Process the file
            processPaymentsInputData.processPaymentInputData(uploadedFile.getAbsolutePath());

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
        } finally {
            sepaFileService.deleteTemporaryFile(uploadedFile);
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
}
