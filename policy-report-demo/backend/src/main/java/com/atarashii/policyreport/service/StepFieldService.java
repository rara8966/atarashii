package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.FieldOverrideEntity;
import com.atarashii.policyreport.persistence.FieldOverrideRepository;
import com.atarashii.policyreport.persistence.FileAnalysisEntity;
import com.atarashii.policyreport.persistence.FileAnalysisRepository;
import com.atarashii.policyreport.persistence.ProjectFileEntity;
import com.atarashii.policyreport.persistence.ProjectFileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 按步骤聚合字段：从该步骤已分析文件的 extractedFieldsJson 收集字段，
 * 同名字段保留第一份非空值，sources 合并。FieldOverride 优先覆盖。
 */
@Service
public class StepFieldService {
    private final ProjectFileRepository fileRepository;
    private final FileAnalysisRepository analysisRepository;
    private final FieldOverrideRepository overrideRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public StepFieldService(ProjectFileRepository fileRepository,
                            FileAnalysisRepository analysisRepository,
                            FieldOverrideRepository overrideRepository) {
        this.fileRepository = fileRepository;
        this.analysisRepository = analysisRepository;
        this.overrideRepository = overrideRepository;
    }

    public List<StepFieldDto> getStepFields(String projectId, int stepNo) {
        List<ProjectFileEntity> stepFiles = fileRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId).stream()
                .filter(f -> f.getCurrentStep() != null && f.getCurrentStep() == stepNo)
                .toList();

        Map<String, StepFieldDto> aggregated = new LinkedHashMap<>();
        for (ProjectFileEntity f : stepFiles) {
            Optional<FileAnalysisEntity> opt = analysisRepository.findByFileId(f.getId());
            if (opt.isEmpty()) continue;
            FileAnalysisEntity analysis = opt.get();
            for (RawField raw : parseFields(analysis.getExtractedFieldsJson())) {
                String key = raw.label;
                if (key == null || key.isBlank()) continue;
                StepFieldDto existing = aggregated.get(key);
                if (existing == null) {
                    aggregated.put(key, new StepFieldDto(
                        key,
                        raw.label,
                        raw.value == null ? "" : raw.value,
                        raw.source == null || raw.source.isBlank() ? f.getOriginalName() : raw.source,
                        f.getId(),
                        false,
                        raw.confidence
                    ));
                } else if ((existing.value == null || existing.value.isBlank()) && raw.value != null && !raw.value.isBlank()) {
                    aggregated.put(key, new StepFieldDto(
                        existing.key,
                        existing.label,
                        raw.value,
                        raw.source == null || raw.source.isBlank() ? f.getOriginalName() : raw.source,
                        f.getId(),
                        false,
                        raw.confidence
                    ));
                }
            }
        }

        // 应用 override
        List<FieldOverrideEntity> overrides = overrideRepository.findAllByProjectIdAndStepNo(projectId, stepNo);
        for (FieldOverrideEntity ov : overrides) {
            StepFieldDto base = aggregated.get(ov.getFieldKey());
            if (base != null) {
                aggregated.put(ov.getFieldKey(), new StepFieldDto(
                    base.key, base.label, ov.getValue(), ov.getSource(), base.sourceFileId, true, base.confidence
                ));
            } else {
                // override 自身可作为新字段（用户手动添加情形）
                aggregated.put(ov.getFieldKey(), new StepFieldDto(
                    ov.getFieldKey(), ov.getFieldKey(), ov.getValue(), ov.getSource(), null, true, 0.0
                ));
            }
        }

        return new ArrayList<>(aggregated.values());
    }

    @Transactional
    public StepFieldDto upsertOverride(String projectId, int stepNo, String fieldKey, String value, String username) {
        if (fieldKey == null || fieldKey.isBlank()) throw new IllegalArgumentException("fieldKey 不能为空");
        FieldOverrideEntity entity = overrideRepository
                .findByProjectIdAndStepNoAndFieldKey(projectId, stepNo, fieldKey)
                .orElseGet(() -> {
                    FieldOverrideEntity e = new FieldOverrideEntity();
                    e.setProjectId(projectId);
                    e.setStepNo(stepNo);
                    e.setFieldKey(fieldKey);
                    return e;
                });
        entity.setValue(value);
        entity.setSource("人工修改");
        entity.setUpdatedBy(username);
        overrideRepository.save(entity);

        // 返回最新聚合结果中对应字段
        return getStepFields(projectId, stepNo).stream()
                .filter(f -> fieldKey.equals(f.key))
                .findFirst()
                .orElse(new StepFieldDto(fieldKey, fieldKey, value, "人工修改", null, true, 0.0));
    }

    private List<RawField> parseFields(String json) {
        List<RawField> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isArray()) return out;
            for (JsonNode node : root) {
                RawField rf = new RawField();
                rf.label = textOrNull(node.get("label"));
                if (rf.label == null) rf.label = textOrNull(node.get("key"));
                rf.value = textOrNull(node.get("value"));
                rf.source = textOrNull(node.get("source"));
                JsonNode conf = node.get("confidence");
                rf.confidence = conf != null && conf.isNumber() ? conf.asDouble() : 0.0;
                out.add(rf);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.asText();
    }

    private static class RawField {
        String label;
        String value;
        String source;
        double confidence;
    }

    public record StepFieldDto(String key, String label, String value, String source,
                               String sourceFileId, boolean override, double confidence) {}
}
