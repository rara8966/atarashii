package com.atarashii.policyreport.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用地标准库的"结构化表格"。
 * 每一行代表一个 docx 表（如「表4.0.6-1 高速、一级公路边坡联体分离式路基工程用地指标」），
 * 内部的二维数据用 headersJson + rowsJson 存储；后续 VerdictEngine 可按列查表。
 */
@Entity
@Table(name = "standard_tables")
public class StandardTableEntity {
    @Id
    private String id;

    @Column(name = "project_type", nullable = false, length = 64)
    private String projectType;

    @Column(name = "project_type_label", length = 200)
    private String projectTypeLabel;

    @Column(name = "source_file", length = 500)
    private String sourceFile;

    /** 章节路径，如 "第一篇 火力发电厂 / 第七章 厂外工程" */
    @Column(name = "chapter", length = 500)
    private String chapter;

    /** 表号，如 "表7.0.1" / "表4.0.6-1" */
    @Column(name = "table_code", length = 64)
    private String tableCode;

    /** 表名，如 "厂外取水建筑物建设用地指标" */
    @Column(name = "table_title", length = 500)
    private String tableTitle;

    /** 单位（hm² / hm²/km），从表名括号里提取 */
    @Column(name = "unit", length = 50)
    private String unit;

    /**
     * 表头 JSON。形如 [["参数项","单位","高速公路","一级公路"]]。
     * 若多级表头则多行。
     */
    @Column(name = "headers_json", columnDefinition = "LONGTEXT")
    private String headersJson;

    /**
     * 数据行 JSON。形如 [["半幅路基宽度","m","16","13"],["边坡坡率","1:n","1.5","1.5"]]。
     */
    @Column(name = "rows_json", columnDefinition = "LONGTEXT")
    private String rowsJson;

    /** 表下方的备注文字 */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * 业务标注 JSON。结构示例：
     * { "queryKeys":   [{"col":0,"name":"机组容量"}],
     *   "valueCols":   [{"col":2,"name":"直流供水管线","semantic":"upper_bound"}],
     *   "applicableSituations": [{"stepNo":6,"groupId":"caseSupply","value":"1"}],
     *   "notes": "..." }
     * VerdictEngine（Layer F）按这个标注做精确查表与数值比对。
     */
    @Column(name = "annotation_json", columnDefinition = "LONGTEXT")
    private String annotationJson;

    @Column(name = "annotated", nullable = false)
    private boolean annotated = false;

    @Column(name = "annotated_by", length = 64)
    private String annotatedBy;

    @Column(name = "annotated_at")
    private LocalDateTime annotatedAt;

    @Column(name = "ai_prelabeled", nullable = false)
    private boolean aiPrelabeled = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }
    public String getProjectTypeLabel() { return projectTypeLabel; }
    public void setProjectTypeLabel(String projectTypeLabel) { this.projectTypeLabel = projectTypeLabel; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public String getChapter() { return chapter; }
    public void setChapter(String chapter) { this.chapter = chapter; }
    public String getTableCode() { return tableCode; }
    public void setTableCode(String tableCode) { this.tableCode = tableCode; }
    public String getTableTitle() { return tableTitle; }
    public void setTableTitle(String tableTitle) { this.tableTitle = tableTitle; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getHeadersJson() { return headersJson; }
    public void setHeadersJson(String headersJson) { this.headersJson = headersJson; }
    public String getRowsJson() { return rowsJson; }
    public void setRowsJson(String rowsJson) { this.rowsJson = rowsJson; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getAnnotationJson() { return annotationJson; }
    public void setAnnotationJson(String annotationJson) { this.annotationJson = annotationJson; }
    public boolean isAnnotated() { return annotated; }
    public void setAnnotated(boolean annotated) { this.annotated = annotated; }
    public String getAnnotatedBy() { return annotatedBy; }
    public void setAnnotatedBy(String annotatedBy) { this.annotatedBy = annotatedBy; }
    public LocalDateTime getAnnotatedAt() { return annotatedAt; }
    public void setAnnotatedAt(LocalDateTime annotatedAt) { this.annotatedAt = annotatedAt; }
    public boolean isAiPrelabeled() { return aiPrelabeled; }
    public void setAiPrelabeled(boolean aiPrelabeled) { this.aiPrelabeled = aiPrelabeled; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
