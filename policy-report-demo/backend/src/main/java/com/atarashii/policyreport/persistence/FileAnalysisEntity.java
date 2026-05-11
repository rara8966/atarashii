package com.atarashii.policyreport.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "file_analyses")
public class FileAnalysisEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String fileId;

    @Column(nullable = false, length = 36)
    private String projectId;

    @Column(columnDefinition = "LONGTEXT")
    private String extractedText;

    @Column(columnDefinition = "TEXT")
    private String extractedFieldsJson;

    private boolean ocrUsed;
    private boolean doubaoUsed;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    @Column(columnDefinition = "TEXT")
    private String aiAdvice;

    private String detectedDocumentType;

    private LocalDateTime analyzedAt;

    public Long getId() { return id; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
    public String getExtractedFieldsJson() { return extractedFieldsJson; }
    public void setExtractedFieldsJson(String extractedFieldsJson) { this.extractedFieldsJson = extractedFieldsJson; }
    public boolean isOcrUsed() { return ocrUsed; }
    public void setOcrUsed(boolean ocrUsed) { this.ocrUsed = ocrUsed; }
    public boolean isDoubaoUsed() { return doubaoUsed; }
    public void setDoubaoUsed(boolean doubaoUsed) { this.doubaoUsed = doubaoUsed; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public String getAiAdvice() { return aiAdvice; }
    public void setAiAdvice(String aiAdvice) { this.aiAdvice = aiAdvice; }
    public String getDetectedDocumentType() { return detectedDocumentType; }
    public void setDetectedDocumentType(String detectedDocumentType) { this.detectedDocumentType = detectedDocumentType; }
    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
}
