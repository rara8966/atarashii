package com.atarashii.policyreport.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 项目情形选择：用户在工作台 CaseSelectors 中为某步选择的情形（如"占用耕地/不占用"等）。
 * 用于驱动 condition 必传判定（cultivatedLand/forest/petition）。
 */
@Entity
@Table(name = "project_situations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "step_no", "group_id"})
})
public class ProjectSituationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(name = "group_id", nullable = false, length = 64)
    private String groupId;

    @Column(name = "value", length = 255)
    private String value;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public int getStepNo() { return stepNo; }
    public void setStepNo(int stepNo) { this.stepNo = stepNo; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
