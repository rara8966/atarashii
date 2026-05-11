package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.FileAnalysisEntity;
import com.atarashii.policyreport.persistence.FileAnalysisRepository;
import com.atarashii.policyreport.persistence.ProjectFileEntity;
import com.atarashii.policyreport.persistence.ProjectFileRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用 DeepSeek 推断项目情形（CaseSelectors）。
 * 输入：项目所有 DONE 文件的 extractedText/extractedFields/aiSummary 拼接。
 * 输出：每组情形（groupId）对应推荐的选项 value，写入 project_situations 表。
 */
@Service
public class SituationAutoDetectService {

    private final ProjectFileRepository fileRepository;
    private final FileAnalysisRepository analysisRepository;
    private final ProjectSituationService situationService;
    private final SituationCatalog catalog;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public SituationAutoDetectService(ProjectFileRepository fileRepository,
                                      FileAnalysisRepository analysisRepository,
                                      ProjectSituationService situationService,
                                      SituationCatalog catalog,
                                      DeepSeekClient deepSeekClient) {
        this.fileRepository = fileRepository;
        this.analysisRepository = analysisRepository;
        this.situationService = situationService;
        this.catalog = catalog;
        this.deepSeekClient = deepSeekClient;
    }

    @Transactional
    public List<ProjectSituationService.SituationDto> autoDetect(String projectId, String deepseekApiKey, String deepseekModel) {
        if (deepseekApiKey == null || deepseekApiKey.isBlank()) {
            throw new IllegalArgumentException("缺少 DeepSeek API Key，请先在「AI 配置」中填写");
        }

        // 1. 收集所有 DONE 文件的分析摘要
        List<ProjectFileEntity> files = fileRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId);
        StringBuilder context = new StringBuilder();
        for (ProjectFileEntity f : files) {
            if (!"DONE".equals(f.getAnalysisStatus())) continue;
            FileAnalysisEntity a = analysisRepository.findByFileId(f.getId()).orElse(null);
            if (a == null) continue;
            context.append("== 文件：").append(f.getOriginalName())
                   .append("（").append(a.getDetectedDocumentType() == null ? "未识别类型" : a.getDetectedDocumentType()).append("）==\n");
            if (a.getAiSummary() != null && !a.getAiSummary().isBlank()) {
                context.append("摘要：").append(truncate(a.getAiSummary(), 400)).append("\n");
            }
            if (a.getExtractedFieldsJson() != null && !a.getExtractedFieldsJson().isBlank()) {
                context.append("字段：").append(truncate(a.getExtractedFieldsJson(), 600)).append("\n");
            }
            if (a.getExtractedText() != null) {
                context.append("正文摘录：").append(truncate(a.getExtractedText(), 1200)).append("\n");
            }
            context.append("\n");
            if (context.length() > 18_000) break; // 总长度上限
        }

        if (context.length() < 100) {
            throw new IllegalStateException("项目尚无足够的已分析文件内容供推断，请先上传材料并等待 AI 分析完成");
        }

        // 2. 拼接所有 group 定义
        StringBuilder groupDefs = new StringBuilder();
        for (Map.Entry<Integer, List<SituationCatalog.CaseGroup>> entry : catalog.getAllGroups().entrySet()) {
            int stepNo = entry.getKey();
            for (SituationCatalog.CaseGroup g : entry.getValue()) {
                groupDefs.append("[第").append(stepNo).append("步][").append(g.id()).append("] ")
                         .append(g.title()).append("：");
                for (SituationCatalog.CaseOption o : g.options()) {
                    groupDefs.append("\n  - 选项 ").append(o.value()).append(") ").append(o.label());
                }
                groupDefs.append("\n\n");
            }
        }

        // 3. 拼 prompt 调 DeepSeek
        String prompt = "你是建设用地报批审查报告专家。请基于以下项目材料，为每个\"情形选择组\"判断最匹配的选项。\n\n"
                + "判断规则：\n"
                + "1. 仅基于材料中实际提及的内容判断\n"
                + "2. 材料未提及该组相关内容时，优先选\"未涉及/不涉及/无xxx\"类的保守选项；若都不像则选第 1 项\n"
                + "3. 涉及某情形但材料未明确处理状态时，选最贴近的选项\n"
                + "4. 输出严格 JSON 数组，每元素 {\"groupId\": \"...\", \"value\": \"1/2/3...\"}\n"
                + "5. 不要输出 JSON 以外的内容（不要 markdown、不要解释）\n"
                + "6. 必须为每一个 groupId 输出一条结果\n\n"
                + "项目材料：\n===\n" + context + "===\n\n"
                + "情形选择组：\n" + groupDefs + "\n"
                + "请直接输出 JSON 数组：";

        DeepSeekClient.GenerationResult result = deepSeekClient.generate(prompt, deepseekApiKey, deepseekModel);
        if (!result.usedModel()) {
            throw new IllegalStateException("DeepSeek 调用失败：" + result.text());
        }

        // 4. 解析 + 落库
        String jsonText = extractJsonArray(result.text());
        if (jsonText == null) {
            throw new IllegalStateException("AI 返回结果无法解析：" + truncate(result.text(), 200));
        }

        List<Map<String, String>> rows;
        try {
            rows = mapper.readValue(jsonText, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("AI 返回 JSON 解析失败：" + e.getMessage());
        }

        // 建立 groupId → stepNo 映射
        Map<String, Integer> groupStepMap = new java.util.HashMap<>();
        for (Map.Entry<Integer, List<SituationCatalog.CaseGroup>> entry : catalog.getAllGroups().entrySet()) {
            for (SituationCatalog.CaseGroup g : entry.getValue()) {
                groupStepMap.put(g.id(), entry.getKey());
            }
        }

        List<ProjectSituationService.SituationDto> saved = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String groupId = row.get("groupId");
            String value = row.get("value");
            if (groupId == null || groupId.isBlank() || value == null || value.isBlank()) continue;
            Integer stepNo = groupStepMap.get(groupId);
            if (stepNo == null) continue; // 忽略不认识的 groupId
            saved.add(situationService.upsert(projectId, stepNo, groupId, value));
        }
        return saved;
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    private String extractJsonArray(String text) {
        if (text == null) return null;
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }
}
