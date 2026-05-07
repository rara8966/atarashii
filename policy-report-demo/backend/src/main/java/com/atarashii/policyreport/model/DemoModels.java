package com.atarashii.policyreport.model;

import java.util.List;
import java.util.Map;

public final class DemoModels {
    private DemoModels() {
    }

    public record StepSummary(
            String id,
            String title,
            String goal,
            List<String> requiredMaterials,
            List<String> ruleFocus
    ) {
    }

    public record ReportSection(String title, String content) {
    }

    public record DemoProject(
            String projectName,
            String projectCode,
            String owner,
            String location,
            Map<String, String> baseFields,
            List<StepSummary> steps,
            List<ReportSection> reportSections
    ) {
    }

        public record ExtractedField(String label, String value, String source, double confidence, String sourceFileId) {
    }

    public record PolicyCheck(String level, String title, String detail, String source) {
    }

    public record AiAdvice(
            String provider,
            String model,
            boolean usedOllama,
            String summary,
            List<String> editablePoints,
            List<String> missingMaterials,
            List<String> riskPoints,
            String rawText
    ) {
    }

    public record DocumentAnalysisResponse(
            String fileId,
            String fileName,
            String contentType,
            long size,
            String detectedDocumentType,
            String targetStep,
            String textPreview,
            int textLength,
            List<ExtractedField> extractedFields,
            List<PolicyCheck> policyChecks,
            AiAdvice aiAdvice
    ) {
    }

        public record ReportRequest(List<DocumentAnalysisResponse> analyses, String markdown) {
    }

    public record ReportResponse(String title, String markdown, List<String> highlights) {
    }

    public record OllamaStatus(boolean reachable, String defaultModel, String fallbackModel, List<String> installedModels) {
    }

    public record AiConfig(String provider, String deepseekApiKey, String deepseekModel) {
    }

    public record EditableField(
            String stepId,
            String key,
            String label,
            String value,
            String source,
            String status,
            boolean required,
            String sourceFileId
    ) {
    }

    public record StepChecklistItem(String id, String title, String status, String detail) {
    }

    public record StepWorkspace(
            String stepId,
            List<EditableField> fields,
            List<StepChecklistItem> checklist,
            List<DocumentAnalysisResponse> analyses,
            String policyCardFileName,
            Map<String, String> situations
    ) {
    }

    public record WorkspaceState(String projectName, Map<String, StepWorkspace> steps) {
    }

    public record FieldUpdateRequest(String stepId, String key, String value, String status) {
    }

        public record SituationUpdateRequest(String stepId, String groupId, String value) {
        }

    public record PolicyCardUploadResponse(String fileName, int textLength, String message) {
    }
}