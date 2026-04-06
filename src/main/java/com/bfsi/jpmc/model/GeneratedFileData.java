package com.bfsi.jpmc.model;

public class GeneratedFileData {

    private final String filename;
    private final String contentType;
    private final byte[] content;

    public GeneratedFileData(String filename, String contentType, byte[] content) {
        this.filename = filename;
        this.contentType = contentType;
        this.content = content;
    }

    public String getFilename() {
        return filename;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }
}
