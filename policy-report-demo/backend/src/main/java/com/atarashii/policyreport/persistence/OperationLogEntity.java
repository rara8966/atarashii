package com.atarashii.policyreport.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "operation_logs")
public class OperationLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String projectId;

    @Column(length = 36)
    private String fileId;

    @Column(nullable = false, length = 30)
    private String action;

    private Integer fromStep;
    private Integer toStep;

    @Column(length = 500)
    private String note;

    @Column(length = 64)
    private String operatedBy;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Integer getFromStep() { return fromStep; }
    public void setFromStep(Integer fromStep) { this.fromStep = fromStep; }
    public Integer getToStep() { return toStep; }
    public void setToStep(Integer toStep) { this.toStep = toStep; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getOperatedBy() { return operatedBy; }
    public void setOperatedBy(String operatedBy) { this.operatedBy = operatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
