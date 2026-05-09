package com.atarashii.policyreport.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "land_use_standard_match")
public class LandUseStandardMatchEntity {
    @Id
    private String id;

    @Column(nullable = false, length = 80)
    private String projectId;

    @Column(nullable = false, length = 80)
    private String standardId;

    @Column(nullable = false, length = 30)
    private String matchStatus;

    @Column(length = 600)
    private String matchedFields;

    @Column(nullable = false, length = 1600)
    private String conclusion;

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
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getStandardId() { return standardId; }
    public void setStandardId(String standardId) { this.standardId = standardId; }
    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }
    public String getMatchedFields() { return matchedFields; }
    public void setMatchedFields(String matchedFields) { this.matchedFields = matchedFields; }
    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}