package com.atarashii.policyreport.service;

import com.atarashii.policyreport.model.DemoModels.DocumentAnalysisResponse;
import com.atarashii.policyreport.model.DemoModels.EditableField;
import com.atarashii.policyreport.model.DemoModels.FieldUpdateRequest;
import com.atarashii.policyreport.model.DemoModels.StepChecklistItem;
import com.atarashii.policyreport.model.DemoModels.StepSummary;
import com.atarashii.policyreport.model.DemoModels.StepWorkspace;
import com.atarashii.policyreport.model.DemoModels.SituationUpdateRequest;
import com.atarashii.policyreport.model.DemoModels.WorkspaceState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkspaceStateService {
    private final Path stateFile = Path.of("data", "workspace-state.json");
    private final ObjectMapper objectMapper;
    private final DemoProjectService demoProjectService;
    private WorkspaceState state;

    public WorkspaceStateService(ObjectMapper objectMapper, DemoProjectService demoProjectService) {
        this.objectMapper = objectMapper;
        this.demoProjectService = demoProjectService;
    }

    public synchronized WorkspaceState getState() {
        if (state != null) {
            return state;
        }
        if (Files.isRegularFile(stateFile)) {
            try {
                state = objectMapper.readValue(stateFile.toFile(), WorkspaceState.class);
                state = normalizeState(state);
                return state;
            } catch (Exception ignored) {
                state = initialState();
                save();
                return state;
            }
        }
        state = initialState();
        save();
        return state;
    }

    public synchronized WorkspaceState updateField(FieldUpdateRequest request) {
        WorkspaceState current = getState();
        StepWorkspace step = current.steps().get(request.stepId());
        if (step == null) {
            return current;
        }
        List<EditableField> fields = new ArrayList<>();
        boolean updated = false;
        for (EditableField field : step.fields()) {
            if (field.key().equals(request.key())) {
                fields.add(new EditableField(field.stepId(), field.key(), field.label(), request.value(), "人工修改", safeStatus(request.status(), "warn"), field.required(), field.sourceFileId()));
                updated = true;
            } else {
                fields.add(field);
            }
        }
        if (!updated) {
            fields.add(new EditableField(request.stepId(), request.key(), request.key(), request.value(), "人工新增", safeStatus(request.status(), "warn"), false, null));
        }
        replaceStep(step.stepId(), new StepWorkspace(step.stepId(), fields, step.checklist(), step.analyses(), step.policyCardFileName(), step.situations()));
        save();
        return state;
    }

    public synchronized WorkspaceState updateSituation(SituationUpdateRequest request) {
        WorkspaceState current = getState();
        StepWorkspace step = current.steps().get(request.stepId());
        if (step == null) {
            return current;
        }
        Map<String, String> situations = new LinkedHashMap<>(safeSituations(step.stepId(), step.situations()));
        situations.put(request.groupId(), request.value());
        replaceStep(step.stepId(), new StepWorkspace(step.stepId(), step.fields(), step.checklist(), step.analyses(), step.policyCardFileName(), situations));
        save();
        return state;
    }

    public synchronized WorkspaceState mergeAnalysis(DocumentAnalysisResponse analysis) {
        WorkspaceState current = getState();
        String stepId = analysis.targetStep() == null || analysis.targetStep().isBlank() ? "step1" : analysis.targetStep();
        StepWorkspace step = current.steps().getOrDefault(stepId, emptyStep(stepId));
        Map<String, EditableField> fieldMap = step.fields().stream().collect(Collectors.toMap(EditableField::label, field -> field, (a, b) -> a, LinkedHashMap::new));
        analysis.extractedFields().forEach(field -> fieldMap.put(field.label(), new EditableField(stepId, keyOf(field.label()), field.label(), field.value(), analysis.detectedDocumentType() + " · " + analysis.fileName(), field.confidence() >= 0.8 ? "pass" : "warn", false, analysis.fileId())));

        Map<String, StepChecklistItem> checklistMap = step.checklist().stream().collect(Collectors.toMap(StepChecklistItem::title, item -> item, (a, b) -> a, LinkedHashMap::new));
        analysis.policyChecks().forEach(check -> checklistMap.put(check.title(), new StepChecklistItem("ai-" + keyOf(check.title()), check.title(), mapLevel(check.level()), check.detail())));

        List<DocumentAnalysisResponse> analyses = new ArrayList<>();
        analyses.add(analysis);
        analyses.addAll(step.analyses());
        if (analyses.size() > 6) {
            analyses = analyses.subList(0, 6);
        }
        replaceStep(stepId, new StepWorkspace(stepId, new ArrayList<>(fieldMap.values()), new ArrayList<>(checklistMap.values()), analyses, step.policyCardFileName(), step.situations()));
        save();
        return state;
    }

    public synchronized WorkspaceState removeAnalysis(String fileId) {
        WorkspaceState current = getState();
        if (fileId == null || fileId.isBlank()) {
            return current;
        }
        Map<String, StepWorkspace> steps = new LinkedHashMap<>();
        for (StepSummary stepSummary : demoProjectService.getDemoProject().steps()) {
            StepWorkspace step = current.steps().getOrDefault(stepSummary.id(), emptyStep(stepSummary.id()));
            List<DocumentAnalysisResponse> analyses = step.analyses().stream()
                    .filter(analysis -> !fileId.equals(analysis.fileId()))
                    .toList();
            List<EditableField> fields = step.fields().stream()
                    .filter(field -> !fileId.equals(field.sourceFileId()))
                    .toList();
            steps.put(step.stepId(), new StepWorkspace(step.stepId(), fields, rebuildChecklist(step.stepId(), analyses), analyses, step.policyCardFileName(), step.situations()));
        }
        state = new WorkspaceState(current.projectName(), steps);
        save();
        return state;
    }

    public synchronized WorkspaceState registerPolicyCard(String fileName) {
        WorkspaceState current = getState();
        current.steps().forEach((stepId, step) -> replaceStep(stepId, new StepWorkspace(step.stepId(), step.fields(), step.checklist(), step.analyses(), fileName, step.situations())));
        save();
        return state;
    }

    private WorkspaceState initialState() {
        var project = demoProjectService.getDemoProject();
        Map<String, StepWorkspace> steps = new LinkedHashMap<>();
        for (StepSummary step : project.steps()) {
            steps.put(step.id(), new StepWorkspace(step.id(), initialFields(step.id()), initialChecklist(step.id()), new ArrayList<>(), "根目录明白卡", defaultSituations(step.id())));
        }
        return new WorkspaceState(project.projectName(), steps);
    }

    private WorkspaceState normalizeState(WorkspaceState loaded) {
        var defaults = initialState();
        Map<String, StepWorkspace> normalized = new LinkedHashMap<>();
        for (StepSummary stepSummary : demoProjectService.getDemoProject().steps()) {
            StepWorkspace fallback = defaults.steps().get(stepSummary.id());
            StepWorkspace existing = loaded.steps() == null ? null : loaded.steps().get(stepSummary.id());
            if (existing == null) {
                normalized.put(stepSummary.id(), fallback);
                continue;
            }
            normalized.put(stepSummary.id(), new StepWorkspace(
                    stepSummary.id(),
                    mergeFields(fallback.fields(), existing.fields()),
                    mergeChecklist(fallback.checklist(), existing.checklist()),
                    existing.analyses() == null ? new ArrayList<>() : existing.analyses(),
                    existing.policyCardFileName() == null || existing.policyCardFileName().isBlank() ? fallback.policyCardFileName() : existing.policyCardFileName(),
                    safeSituations(stepSummary.id(), existing.situations())
            ));
        }
        return new WorkspaceState(loaded.projectName() == null || loaded.projectName().isBlank() ? defaults.projectName() : loaded.projectName(), normalized);
    }

    private List<EditableField> mergeFields(List<EditableField> defaults, List<EditableField> existing) {
        Map<String, EditableField> fields = defaults.stream()
                .collect(Collectors.toMap(EditableField::key, field -> field, (a, b) -> a, LinkedHashMap::new));
        if (existing != null) {
            existing.forEach(field -> fields.put(field.key(), field));
        }
        return new ArrayList<>(fields.values());
    }

    private List<StepChecklistItem> mergeChecklist(List<StepChecklistItem> defaults, List<StepChecklistItem> existing) {
        Map<String, StepChecklistItem> checklist = defaults.stream()
                .collect(Collectors.toMap(StepChecklistItem::title, item -> item, (a, b) -> a, LinkedHashMap::new));
        if (existing != null) {
            existing.forEach(item -> checklist.put(item.title(), item));
        }
        return new ArrayList<>(checklist.values());
    }

    private List<EditableField> initialFields(String stepId) {
        return switch (stepId) {
            case "step1" -> List.of(
                    field(stepId, "projectName", "项目名称", "兴宁五塘风电场一期工程", "真实报告样例", "pass", true),
                    field(stepId, "projectCode", "项目代码", "2025-450000-44-01-008976", "真实报告样例", "pass", true),
                    field(stepId, "preApproval", "预审批复文号", "450101202300039号", "真实报告样例", "pass", true),
                    field(stepId, "approvalNo", "核准文号", "南发改能源〔2023〕12号", "真实报告样例", "pass", true),
                    field(stepId, "started", "是否已动工", "2024年7月违法动工，正在建设", "真实报告样例", "warn", true));
            case "step2" -> List.of(
                    field(stepId, "totalArea", "总用地面积", "0.3800公顷", "真实报告样例", "pass", true),
                    field(stepId, "farmArea", "农用地", "0.3800公顷", "真实报告样例", "pass", true),
                    field(stepId, "collectiveArea", "集体土地", "0.0236公顷", "真实报告样例", "pass", true),
                    field(stepId, "stateArea", "国有土地", "0.3564公顷", "真实报告样例", "pass", true),
                    field(stepId, "landChangeOverlay", "年度国土变更调查套合情况", "需结合年度国土变更调查套合情况分析材料核对", "真实报告样例", "warn", true));
            case "step3" -> List.of(
                    field(stepId, "ecology", "生态保护红线", "不涉及", "真实报告样例", "pass", true),
                    field(stepId, "permanentFarm", "永久基本农田", "0公顷", "真实报告样例", "pass", true),
                    field(stepId, "planQuota", "计划指标", "由自治区核销", "真实报告样例", "warn", true));
            case "step4" -> List.of(
                    field(stepId, "cultivatedLand", "占用耕地", "0公顷", "真实报告样例", "pass", true),
                    field(stepId, "balance", "补充耕地任务", "不涉及", "真实报告样例", "pass", true));
            case "step5" -> List.of(
                    field(stepId, "expropriation", "征收集体土地", "0.0236公顷", "真实报告样例", "pass", true),
                    field(stepId, "publicInterest", "公共利益依据", "符合土地管理法第四十五条", "真实报告样例", "pass", true),
                    field(stepId, "socialSecurity", "社保材料", "需核对附件", "真实报告样例", "warn", true));
            case "step6" -> List.of(
                    field(stepId, "supplyMode", "供地方式", "出让方式供地", "真实报告样例", "pass", true),
                    field(stepId, "landFee", "新增建设用地土地有偿使用费", "24.32万元", "真实报告样例", "pass", true),
                    field(stepId, "standard", "用地标准", "需结合功能分区核对", "真实报告样例", "warn", true));
            case "step7" -> List.of(
                    field(stepId, "geo", "地灾评估", "位于地灾易发区，已完成一级评估", "真实报告样例", "pass", true),
                    field(stepId, "mine", "压覆矿产", "不压覆重要矿产资源", "真实报告样例", "pass", true));
            case "step8" -> List.of(
                    field(stepId, "petition", "信访事项", "不存在信访问题", "真实报告样例", "pass", true),
                    field(stepId, "illegalArea", "违法用地面积", "0.0588公顷", "真实报告样例", "pass", true),
                    field(stepId, "rectified", "查处整改", "行政处罚和整改已执行到位", "真实报告样例", "pass", true),
                    field(stepId, "protectedArea", "是否涉及保护地", "不涉及生态红线或自然保护区", "真实报告样例", "pass", true));
            default -> List.of();
        };
    }

    private List<StepChecklistItem> initialChecklist(String stepId) {
        return switch (stepId) {
            case "step1" -> List.of(check("pre", "预审批复已识别", "pass", "已有预审批复文号，需确认有效期。"), check("approval", "核准批复已识别", "pass", "核准文号已进入底稿。"), check("start", "违法动工需要重点说明", "warn", "已动工且存在违法用地，后续第八步必须闭合。"));
            case "step2" -> List.of(check("area", "总面积与权属面积已对齐", "pass", "总面积0.3800公顷。"), check("owner", "国有林场权属需保留说明", "warn", "国有土地未办证但实际权利人同意。"), check("land-change", "年度国土变更调查套合情况需核对", "warn", "建议上传年度国土变更调查套合情况分析材料。"));
            case "step3" -> List.of(check("eco", "生态保护红线不涉及", "pass", "材料已有明确结论。"), check("plan", "计划指标来源需确认", "warn", "建议上传或引用自治区核销依据。"));
            case "step4" -> List.of(check("no-farm", "不占耕地", "pass", "无补充耕地任务。"), check("wording", "需统一无任务表述", "warn", "报告中要明确耕地、水田、产能均为0。"));
            case "step5" -> List.of(check("public", "公共利益依据已出现", "pass", "可支撑征收章节。"), check("announcement", "补偿安置公告及照片需核对", "warn", "需上传征地补偿安置公告及照片。"), check("hearing", "听证材料需核对", "warn", "需上传听证告知、听证笔录或放弃听证等材料。"), check("social", "社保凭证需核对", "warn", "建议补齐社保审核意见或到账凭证。"));
            case "step6" -> List.of(check("supply", "供地方式已识别", "pass", "拟以出让方式供地。"), check("fee", "土地有偿使用费已识别", "pass", "需核对金额和缴库口径。"), check("standard", "用地标准需人工确认", "warn", "风机、箱变、道路等功能分区需核指标。"));
            case "step7" -> List.of(check("geo", "地灾评估已闭合", "pass", "一级评估并通过专家审查。"), check("mine", "不压覆重要矿产", "pass", "有明确查询结论。"));
            case "step8" -> List.of(check("petition", "无信访事项无需信访材料", "pass", "报告写明不存在信访，信访材料不作为本项目必传。"), check("illegal", "违法用地已处罚整改", "pass", "0.0588公顷已执行到位。"), check("protect", "保护地风险不涉及", "pass", "不涉及生态红线或自然保护区。"));
            default -> List.of();
        };
    }

    private EditableField field(String stepId, String key, String label, String value, String source, String status, boolean required) {
        return new EditableField(stepId, key, label, value, source, status, required, null);
    }

    private StepChecklistItem check(String id, String title, String status, String detail) {
        return new StepChecklistItem(id, title, status, detail);
    }

    private List<StepChecklistItem> rebuildChecklist(String stepId, List<DocumentAnalysisResponse> analyses) {
        Map<String, StepChecklistItem> checklistMap = initialChecklist(stepId).stream()
                .collect(Collectors.toMap(StepChecklistItem::title, item -> item, (a, b) -> a, LinkedHashMap::new));
        for (DocumentAnalysisResponse analysis : analyses) {
            analysis.policyChecks().forEach(check -> checklistMap.put(check.title(), new StepChecklistItem("ai-" + keyOf(check.title()), check.title(), mapLevel(check.level()), check.detail())));
        }
        return new ArrayList<>(checklistMap.values());
    }

    private StepWorkspace emptyStep(String stepId) {
        return new StepWorkspace(stepId, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "根目录明白卡", defaultSituations(stepId));
    }

    private Map<String, String> defaultSituations(String stepId) {
        Map<String, String> situations = new LinkedHashMap<>();
        switch (stepId) {
            case "step1" -> {
                situations.put("approvalSituation", "4");
                situations.put("designChange", "3");
                situations.put("projectPhase", "2");
                situations.put("landUseType", "3");
                situations.put("forestryApproval", "1");
                situations.put("constructionStatus", "3");
                situations.put("reductionStatus", "1");
            }
            case "step2" -> {
                situations.put("caseInconsistency", "1");
                situations.put("caseNature53", "1");
                situations.put("caseFlood", "1");
            }
            case "step3" -> {
                situations.put("caseNatureReserve", "1");
                situations.put("caseEcoRedline", "1");
                situations.put("casePlan", "2");
                situations.put("caseBasicFarmland", "2");
            }
            case "step4" -> {
                situations.put("caseSupplement", "1");
                situations.put("casePaddy", "1");
            }
            case "step5" -> {
                situations.put("casePublicInterest", "1");
                situations.put("caseAgreement", "1");
            }
            case "step6" -> {
                situations.put("caseIndustry", "2");
                situations.put("caseSupply", "2");
                situations.put("supplyMethod", "1");
            }
            case "step7" -> {
                situations.put("caseGeo", "1");
                situations.put("caseMineral", "1");
            }
            case "step8" -> {
                situations.put("petitionType", "1");
                situations.put("selectedCase", "5");
            }
            default -> {
            }
        }
        return situations;
    }

    private Map<String, String> safeSituations(String stepId, Map<String, String> existing) {
        Map<String, String> situations = defaultSituations(stepId);
        if (existing != null) {
            situations.putAll(existing);
        }
        return situations;
    }

    private void replaceStep(String stepId, StepWorkspace nextStep) {
        Map<String, StepWorkspace> steps = new LinkedHashMap<>(getState().steps());
        steps.put(stepId, nextStep);
        state = new WorkspaceState(getState().projectName(), steps);
    }

    private void save() {
        try {
            Files.createDirectories(stateFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(stateFile.toFile(), state);
        } catch (Exception ignored) {
        }
    }

    private String safeStatus(String status, String fallback) {
        return status == null || status.isBlank() ? fallback : status;
    }

    private String mapLevel(String level) {
        if ("pass".equals(level)) return "pass";
        if ("block".equals(level)) return "block";
        if ("warn".equals(level)) return "warn";
        return "todo";
    }

    private String keyOf(String label) {
        return label == null ? "field" : label.replaceAll("[^0-9A-Za-z\\u4e00-\\u9fa5]", "");
    }
}