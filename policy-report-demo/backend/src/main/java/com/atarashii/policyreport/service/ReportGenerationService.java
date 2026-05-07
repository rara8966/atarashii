package com.atarashii.policyreport.service;

import com.atarashii.policyreport.model.DemoModels.DocumentAnalysisResponse;
import com.atarashii.policyreport.model.DemoModels.EditableField;
import com.atarashii.policyreport.model.DemoModels.ReportRequest;
import com.atarashii.policyreport.model.DemoModels.ReportResponse;
import com.atarashii.policyreport.model.DemoModels.ReportSection;
import com.atarashii.policyreport.model.DemoModels.StepWorkspace;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportGenerationService {
    private final DemoProjectService demoProjectService;
    private final WorkspaceStateService workspaceStateService;

    public ReportGenerationService(DemoProjectService demoProjectService, WorkspaceStateService workspaceStateService) {
        this.demoProjectService = demoProjectService;
        this.workspaceStateService = workspaceStateService;
    }

    public ReportResponse generate(ReportRequest request) {
        var project = demoProjectService.getDemoProject();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 南宁市兴宁区自然资源局关于兴宁五塘风电场一期农用地转用和土地征收审查情况的报告\n\n");
        markdown.append("南兴自然资报〔2026〕16号\n\n");
        markdown.append("签发人：李阳俐\n\n");
        for (ReportSection section : project.reportSections()) {
            markdown.append("## ").append(section.title()).append("\n\n");
            markdown.append(section.content()).append("\n\n");
        }
        appendFieldReview(markdown);
        appendAiReview(markdown, request);
        markdown.append("## 综上所述\n\n");
        markdown.append("兴宁五塘风电场一期申请用地情况真实，符合土地管理法律法规和有关规定；当前 Demo 已把真实样例数据、上传材料解析、政策规则提示和报告拼接串成闭环。请结合后续真实机密数据继续校验字段和模板。\n\n");
        markdown.append("生成日期：").append(LocalDate.now()).append("\n");

        List<String> highlights = new ArrayList<>();
        highlights.add("已按真实报告样例生成八个章节。 ");
        highlights.add("上传材料的AI/规则审查结果会汇总到“AI审查摘要”。 ");
        highlights.add("后续接真实数据时，重点补齐字段映射、材料清单和Word导出。 ");
        return new ReportResponse(project.projectName() + "审查报告Demo", markdown.toString(), highlights);
    }

    public byte[] exportMarkdown(ReportRequest request) {
        return exportMarkdownText(request).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] exportDocx(ReportRequest request) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String markdown = exportMarkdownText(request);
            for (String line : markdown.split("\\R")) {
                if (line.isBlank()) {
                    continue;
                }
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                if (line.startsWith("# ")) {
                    paragraph.setAlignment(ParagraphAlignment.CENTER);
                    run.setBold(true);
                    run.setFontSize(18);
                    run.setText(line.substring(2));
                } else if (line.startsWith("## ")) {
                    run.setBold(true);
                    run.setFontSize(14);
                    run.setText(line.substring(3));
                } else if (line.startsWith("- ")) {
                    run.setText("• " + line.substring(2));
                } else {
                    run.setFontSize(11);
                    run.setText(line);
                }
            }
            document.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("生成 Word 文件失败：" + ex.getMessage(), ex);
        }
    }

    private String exportMarkdownText(ReportRequest request) {
        if (request != null && request.markdown() != null && !request.markdown().isBlank()) {
            return request.markdown();
        }
        return generate(request).markdown();
    }

    private void appendFieldReview(StringBuilder markdown) {
        markdown.append("## 字段核对与人工修改记录\n\n");
        var state = workspaceStateService.getState();
        for (StepWorkspace step : state.steps().values()) {
            List<EditableField> editedFields = step.fields().stream().filter(field -> "人工修改".equals(field.source()) || "warn".equals(field.status()) || "block".equals(field.status())).toList();
            if (editedFields.isEmpty()) {
                continue;
            }
            markdown.append("### ").append(step.stepId()).append("\n\n");
            for (EditableField field : editedFields) {
                markdown.append("- ").append(field.label()).append("：").append(field.value()).append("（").append(statusText(field.status())).append("，").append(field.source()).append("）\n");
            }
            markdown.append("\n");
        }
    }

    private void appendAiReview(StringBuilder markdown, ReportRequest request) {
        if (request == null || request.analyses() == null || request.analyses().isEmpty()) {
            markdown.append("## AI审查摘要\n\n");
            markdown.append("当前尚未上传新材料，报告按内置兴宁五塘真实样例数据生成。\n\n");
            return;
        }
        markdown.append("## AI审查摘要\n\n");
        for (DocumentAnalysisResponse analysis : request.analyses()) {
            markdown.append("- ").append(analysis.fileName()).append("：").append(analysis.aiAdvice().summary()).append("\n");
            analysis.policyChecks().stream()
                    .filter(check -> "warn".equals(check.level()))
                    .limit(3)
                    .forEach(check -> markdown.append("  - 需关注：").append(check.title()).append("，").append(check.detail()).append("\n"));
        }
        markdown.append("\n");
    }

    private String statusText(String status) {
        return switch (status) {
            case "pass" -> "已做到";
            case "warn" -> "需确认";
            case "block" -> "未通过";
            default -> "待补";
        };
    }
}