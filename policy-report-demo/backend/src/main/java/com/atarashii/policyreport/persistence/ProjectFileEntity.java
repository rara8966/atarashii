package com.atarashii.policyreport.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_files")
public class ProjectFileEntity {
    @Id
    private String id;

    @Column(nullable = false, length = 36)
    private String projectId;

    @Column(nullable = false, length = 500)
    private String originalName;

    @Column(nullable = false, length = 1000)
    private String storagePath;

    @Column(length = 128)
    private String mimeType;

    private Integer pageCount;

    @Column(length = 36)
    private String uploadBatch;

    private Integer aiSuggestedStep;
    private Integer currentStep;

    @Column(nullable = false)
    private boolean confirmedByUser = false;

    @Column(nullable = false, length = 20)
    private String analysisStatus = "PENDING";

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getUploadBatch() { return uploadBatch; }
    public void setUploadBatch(String uploadBatch) { this.uploadBatch = uploadBatch; }
    public Integer getAiSuggestedStep() { return aiSuggestedStep; }
    public void setAiSuggestedStep(Integer aiSuggestedStep) { this.aiSuggestedStep = aiSuggestedStep; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public boolean isConfirmedByUser() { return confirmedByUser; }
    public void setConfirmedByUser(boolean confirmedByUser) { this.confirmedByUser = confirmedByUser; }
    public String getAnalysisStatus() { return analysisStatus; }
    public void setAnalysisStatus(String analysisStatus) { this.analysisStatus = analysisStatus; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
