package com.atarashii.policyreport.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "land_use_standard")
public class LandUseStandardEntity {
    @Id
    private String id;

    @Column(nullable = false, length = 60)
    private String projectType;

    @Column(nullable = false, length = 120)
    private String projectTypeLabel;

    @Column(nullable = false, length = 240)
    private String sourceFile;

    @Column(nullable = false, length = 300)
    private String chapterTitle;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(length = 600)
    private String keywords;

    private int sortOrder;
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }
    public String getProjectTypeLabel() { return projectTypeLabel; }
    public void setProjectTypeLabel(String projectTypeLabel) { this.projectTypeLabel = projectTypeLabel; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public String getChapterTitle() { return chapterTitle; }
    public void setChapterTitle(String chapterTitle) { this.chapterTitle = chapterTitle; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}