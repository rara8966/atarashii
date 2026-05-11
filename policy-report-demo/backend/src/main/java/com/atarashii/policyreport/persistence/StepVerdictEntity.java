package com.atarashii.policyreport.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "step_verdicts", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "step_no"}))
public class StepVerdictEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(nullable = false, length = 10)
    private String verdict = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String passItemsJson;

    @Column(columnDefinition = "TEXT")
    private String warnItemsJson;

    @Column(columnDefinition = "TEXT")
    private String failItemsJson;

    private LocalDateTime generatedAt;

    public Long getId() { return id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public int getStepNo() { return stepNo; }
    public void setStepNo(int stepNo) { this.stepNo = stepNo; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public String getPassItemsJson() { return passItemsJson; }
    public void setPassItemsJson(String passItemsJson) { this.passItemsJson = passItemsJson; }
    public String getWarnItemsJson() { return warnItemsJson; }
    public void setWarnItemsJson(String warnItemsJson) { this.warnItemsJson = warnItemsJson; }
    public String getFailItemsJson() { return failItemsJson; }
    public void setFailItemsJson(String failItemsJson) { this.failItemsJson = failItemsJson; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
