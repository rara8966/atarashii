package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.FileAnalysisRepository;
import com.atarashii.policyreport.persistence.PreviewSnapshotEntity;
import com.atarashii.policyreport.persistence.PreviewSnapshotRepository;
import com.atarashii.policyreport.persistence.ProjectFileRepository;
import com.atarashii.policyreport.persistence.ProjectRecordEntity;
import com.atarashii.policyreport.persistence.ProjectRecordRepository;
import com.atarashii.policyreport.persistence.StepVerdictEntity;
import com.atarashii.policyreport.persistence.StepVerdictRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AsyncPreviewWorker {

    private final PreviewSnapshotRepository snapshotRepository;
    private final ProjectRecordRepository projectRepository;
    private final StepVerdictRepository verdictRepository;
    private final ProjectFileRepository fileRepository;
    private final VerdictEngine verdictEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] STEP_NAMES = {
        "", "项目基本情况", "申请用地现状", "农用地转用", "补充耕地",
        "土地征收", "土地利用", "地灾压矿", "信访违法"
    };

    public AsyncPreviewWorker(PreviewSnapshotRepository snapshotRepository,
                              ProjectRecordRepository projectRepository,
                              StepVerdictRepository verdictRepository,
                              ProjectFileRepository fileRepository,
                              VerdictEngine verdictEngine) {
        this.snapshotRepository = snapshotRepository;
        this.projectRepository = projectRepository;
        this.verdictRepository = verdictRepository;
        this.fileRepository = fileRepository;
        this.verdictEngine = verdictEngine;
    }

    @Async("analysisExecutor")
    public void generateAsync(String projectId, String snapshotId) {
        try {
            ProjectRecordEntity project = projectRepository.findById(projectId).orElse(null);
            if (project == null) return;

            verdictEngine.generateAllVerdicts(projectId);
            List<StepVerdictEntity> verdicts = verdictRepository.findAllByProjectId(projectId);

            StringBuilder md = new StringBuilder();
            md.append("# 建设用地报批审查预览报告\n\n");
            md.append("**项目名称：**").append(safe(project.getProjectName())).append("  \n");
            md.append("**建设单位：**").append(safe(project.getOwner())).append("  \n");
            md.append("**建设地点：**").append(safe(project.getLocation())).append("  \n");
            md.append("**系统编号：**").append(safe(project.getProjectCode())).append("  \n");
            md.append("**项目类型：**").append(safe(project.getProjectTypeLabel())).append("  \n");
            if (project.getStandardSet() != null) {
                md.append("**适用标准：**").append(project.getStandardSet()).append("  \n");
            }
            md.append("\n---\n\n## 八步审查汇总\n\n");

            int passCount = 0, warnCount = 0, failCount = 0;
            for (StepVerdictEntity v : verdicts) {
                if ("PASS".equals(v.getVerdict())) passCount++;
                else if ("WARN".equals(v.getVerdict())) warnCount++;
                else if ("FAIL".equals(v.getVerdict())) failCount++;
            }
            md.append("- 通过步骤：**").append(passCount).append("** / 8\n");
            md.append("- 注意步骤：**").append(warnCount).append("** / 8\n");
            md.append("- 不通过步骤：**").append(failCount).append("** / 8\n\n");

            for (StepVerdictEntity v : verdicts) {
                String stepName = v.getStepNo() <= 8 ? STEP_NAMES[v.getStepNo()] : "第" + v.getStepNo() + "步";
                String emoji = switch (v.getVerdict()) {
                    case "PASS" -> "✅";
                    case "WARN" -> "⚠️";
                    case "FAIL" -> "❌";
                    default -> "⏳";
                };
                md.append("### ").append(emoji).append(" 第").append(v.getStepNo()).append("步 · ").append(stepName).append("\n\n");

                md.append("本步骤已归类文件：").append(
                    fileRepository.findAllByProjectIdAndCurrentStep(projectId, v.getStepNo()).size()
                ).append(" 份\n\n");

                if (!"[]".equals(v.getPassItemsJson()) && v.getPassItemsJson() != null) {
                    md.append("**通过项：**\n");
                    appendItems(md, v.getPassItemsJson(), "✅");
                }
                if (!"[]".equals(v.getFailItemsJson()) && v.getFailItemsJson() != null) {
                    md.append("**不通过项：**\n");
                    appendItems(md, v.getFailItemsJson(), "❌");
                }
                if (!"[]".equals(v.getWarnItemsJson()) && v.getWarnItemsJson() != null) {
                    md.append("**注意项：**\n");
                    appendItems(md, v.getWarnItemsJson(), "⚠️");
                }
                md.append("\n");
            }

            Map<String, Object> snapshotData = new LinkedHashMap<>();
            snapshotData.put("markdown", md.toString());
            snapshotData.put("generatedAt", LocalDateTime.now().toString());

            PreviewSnapshotEntity snap = snapshotRepository.findById(snapshotId).orElse(null);
            if (snap == null) return;
            snap.setSnapshotData(objectMapper.writeValueAsString(snapshotData));
            snap.setStatus("READY");
            snap.setGeneratedAt(LocalDateTime.now());
            snapshotRepository.save(snap);

        } catch (Exception e) {
            snapshotRepository.findById(snapshotId).ifPresent(snap -> {
                snap.setStatus("FAILED");
                snapshotRepository.save(snap);
            });
        }
    }

    private void appendItems(StringBuilder sb, String json, String prefix) {
        try {
            var items = objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            for (var item : items) {
                sb.append("- ").append(prefix).append(" **").append(item.getOrDefault("materialName", "")).append("**");
                String matched = item.get("matchedFile");
                if (matched != null) sb.append("：").append(matched);
                sb.append("  ").append(item.getOrDefault("detail", "")).append("\n");
            }
        } catch (Exception ignored) {}
    }

    private String safe(String s) { return s == null ? "" : s; }
}
