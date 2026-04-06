package com.bfsi.jpmc.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ProcessingLogService {

    private static final DateTimeFormatter LOG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final StringBuilder currentRunLogBuffer = new StringBuilder();
    private final StringBuilder completeLogBuffer = new StringBuilder();

    public synchronized void clear() {
        currentRunLogBuffer.setLength(0);
    }

    public synchronized void info(String message) {
        append("INFO", message);
    }

    public synchronized void warn(String message) {
        append("WARN", message);
    }

    public synchronized void error(String message) {
        append("ERROR", message);
    }

    public synchronized String getLogs() {
        return currentRunLogBuffer.toString();
    }

    public synchronized String getCompleteLogs() {
        return completeLogBuffer.toString();
    }

    public synchronized byte[] getLogsAsBytes(boolean complete) {
        return (complete ? completeLogBuffer.toString() : currentRunLogBuffer.toString())
                .getBytes(StandardCharsets.UTF_8);
    }

    public synchronized String getDownloadFilename(boolean complete) {
        String prefix = complete ? "COMPLETE_LOGS_" : "PROCESSING_LOGS_";
        return prefix + LocalDateTime.now().format(FILE_TIMESTAMP) + ".txt";
    }

    private void append(String level, String message) {
        String line = LocalDateTime.now().format(LOG_TIMESTAMP)
                + " [" + level + "] "
                + message
                + System.lineSeparator();
        currentRunLogBuffer.append(line);
        completeLogBuffer.append(line);
    }
}
