package com.atarashii.policyreport.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class PathMultipartFile implements MultipartFile {
    private final Path path;
    private final String originalName;
    private final String contentType;

    public PathMultipartFile(Path path, String originalName, String contentType) {
        this.path = path;
        this.originalName = originalName;
        this.contentType = contentType;
    }

    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return originalName; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return false; }

    @Override
    public long getSize() {
        try { return Files.size(path); } catch (IOException e) { return 0; }
    }

    @Override
    public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }

    @Override
    public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(getBytes()); }

    @Override
    public void transferTo(java.io.File dest) throws IOException {
        Files.copy(path, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
