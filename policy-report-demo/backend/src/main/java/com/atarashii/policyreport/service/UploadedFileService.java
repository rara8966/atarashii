package com.atarashii.policyreport.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

@Service
public class UploadedFileService {
    private final Path uploadDir = Path.of("data", "uploads");

    public String store(MultipartFile file) {
        try {
            Files.createDirectories(uploadDir);
            String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
            String originalName = safeFileName(file.getOriginalFilename());
            Path target = uploadDir.resolve(id + "__" + originalName);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return id;
        } catch (IOException ex) {
            throw new IllegalStateException("保存上传文件失败：" + ex.getMessage(), ex);
        }
    }

    public Resource resource(String fileId) {
        Path path = locate(fileId);
        return new FileSystemResource(path);
    }

    public Path path(String fileId) {
        return locate(fileId);
    }

    public String fileName(String fileId) {
        String storedName = locate(fileId).getFileName().toString();
        int index = storedName.indexOf("__");
        return index >= 0 ? storedName.substring(index + 2) : storedName;
    }

    public String contentType(String fileId) {
        try {
            String type = Files.probeContentType(locate(fileId));
            return type == null ? "application/octet-stream" : type;
        } catch (IOException ex) {
            return "application/octet-stream";
        }
    }

    public void delete(String fileId) {
        try {
            Files.deleteIfExists(locate(fileId));
        } catch (IllegalArgumentException ignored) {
        } catch (IOException ex) {
            throw new IllegalStateException("删除上传文件失败：" + ex.getMessage(), ex);
        }
    }

    private Path locate(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("文件编号为空");
        }
        try {
            if (!Files.isDirectory(uploadDir)) {
                throw new IllegalArgumentException("未找到上传文件");
            }
            try (var stream = Files.list(uploadDir)) {
                return stream
                        .filter(path -> path.getFileName().toString().startsWith(fileId + "__"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("未找到上传文件"));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传文件失败：" + ex.getMessage(), ex);
        }
    }

    private String safeFileName(String name) {
        String fallback = name == null || name.isBlank() ? "upload.bin" : name;
        return fallback.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}