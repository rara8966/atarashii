package com.atarashii.policyreport.service;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class TikaDocumentParser {
    private final AutoDetectParser parser = new AutoDetectParser();

    public ParsedDocument parse(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return parse(inputStream, file.getOriginalFilename(), file.getContentType());
        } catch (Exception ex) {
            throw new IllegalStateException("Tika 解析上传文件失败: " + ex.getMessage(), ex);
        }
    }

    public ParsedDocument parse(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            return parse(inputStream, path.getFileName().toString(), Files.probeContentType(path));
        } catch (Exception ex) {
            throw new IllegalStateException("Tika 解析本地政策文件失败: " + ex.getMessage(), ex);
        }
    }

    private ParsedDocument parse(InputStream inputStream, String fileName, String contentType) throws Exception {
        ContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        if (fileName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        if (contentType != null) {
            metadata.set(Metadata.CONTENT_TYPE, contentType);
        }
        parser.parse(inputStream, handler, metadata, new ParseContext());
        String text = handler.toString().replace('\u0000', ' ').trim();
        return new ParsedDocument(fileName == null ? "未命名文件" : fileName, contentType, text, metadata);
    }

    public record ParsedDocument(String fileName, String contentType, String text, Metadata metadata) {
    }
}