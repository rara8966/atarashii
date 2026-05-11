package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.FileAnalysisRepository;
import com.atarashii.policyreport.persistence.ProjectFileEntity;
import com.atarashii.policyreport.persistence.ProjectFileRepository;
import com.atarashii.policyreport.persistence.StepVerdictEntity;
import com.atarashii.policyreport.persistence.StepVerdictRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class VerdictEngine {

    private static final Map<Integer, List<MaterialSpec>> STEP_SPECS = Map.of(
        1, List.of(
            new MaterialSpec("预审批复", true, List.of("预审", "用地预审", "预审批复")),
            new MaterialSpec("立项批复", true, List.of("核准", "立项", "备案", "项目批复")),
            new MaterialSpec("初步设计批复", false, List.of("初步设计", "初设"))
        ),
        2, List.of(
            new MaterialSpec("勘测定界报告", true, List.of("勘测", "定界")),
            new MaterialSpec("土地分类权属面积汇总表", true, List.of("分类", "权属面积", "汇总表")),
            new MaterialSpec("权属情况汇总表", true, List.of("权属", "所有权", "使用权")),
            new MaterialSpec("年度国土变更调查套合情况", true, List.of("年度国土变更", "套合情况"))
        ),
        3, List.of(
            new MaterialSpec("规划佐证材料", true, List.of("规划", "三区三线", "用途管制")),
            new MaterialSpec("年度计划指标文件", true, List.of("计划指标", "指标配置")),
            new MaterialSpec("林地批复", false, List.of("林地", "林草"))
        ),
        4, List.of(
            new MaterialSpec("补充耕地方案审查表", false, List.of("补充耕地", "方案审查")),
            new MaterialSpec("耕地占补平衡挂钩信息单", false, List.of("占补平衡", "挂钩信息"))
        ),
        5, List.of(
            new MaterialSpec("征收土地预公告", true, List.of("预公告", "征收土地预公告")),
            new MaterialSpec("土地现状调查材料", true, List.of("土地现状调查", "现状调查")),
            new MaterialSpec("社会稳定风险评估报告", true, List.of("社稳", "稳评", "社会稳定风险")),
            new MaterialSpec("征地补偿安置公告及照片", true, List.of("征地补偿安置公告", "补偿安置公告")),
            new MaterialSpec("听证材料", true, List.of("听证", "听证告知")),
            new MaterialSpec("社保审核意见", true, List.of("社保", "社会保障"))
        ),
        6, List.of(
            new MaterialSpec("节约集约用地论证分析专章", false, List.of("节约集约", "论证分析专章")),
            new MaterialSpec("土地有偿使用费材料", true, List.of("有偿使用费", "缴纳", "缴库"))
        ),
        7, List.of(
            new MaterialSpec("地灾评估报告批复", true, List.of("地质灾害", "地灾评估")),
            new MaterialSpec("压覆矿查询表", false, List.of("压覆矿查询", "压覆矿", "压矿"))
        ),
        8, List.of(
            new MaterialSpec("信访处理说明", false, List.of("信访", "来信", "上访")),
            new MaterialSpec("违法用地查处案卷", false, List.of("违法用地", "行政处罚", "查处"))
        )
    );

    private final ProjectFileRepository fileRepository;
    private final FileAnalysisRepository analysisRepository;
    private final StepVerdictRepository verdictRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VerdictEngine(ProjectFileRepository fileRepository,
                         FileAnalysisRepository analysisRepository,
                         StepVerdictRepository verdictRepository) {
        this.fileRepository = fileRepository;
        this.analysisRepository = analysisRepository;
        this.verdictRepository = verdictRepository;
    }

    @Transactional
    public StepVerdictEntity generateVerdict(String projectId, int stepNo) {
        List<ProjectFileEntity> files = fileRepository.findAllByProjectIdAndCurrentStep(projectId, stepNo);
        List<MaterialSpec> specs = STEP_SPECS.getOrDefault(stepNo, List.of());

        List<VerdictItem> passItems = new ArrayList<>();
        List<VerdictItem> warnItems = new ArrayList<>();
        List<VerdictItem> failItems = new ArrayList<>();

        for (MaterialSpec spec : specs) {
            boolean matched = false;
            String matchedFile = null;
            String matchedFileId = null;
            for (ProjectFileEntity file : files) {
                if (matchesKeywords(file.getOriginalName(), spec.keywords())) {
                    matched = true;
                    matchedFile = file.getOriginalName();
                    matchedFileId = file.getId();
                    break;
                }
                String analysis = getAnalysisText(file.getId());
                if (analysis != null && matchesKeywords(analysis, spec.keywords())) {
                    matched = true;
                    matchedFile = file.getOriginalName();
                    matchedFileId = file.getId();
                    break;
                }
            }
            if (matched) {
                passItems.add(new VerdictItem(spec.name(), matchedFile, matchedFileId, "文件已上传并命中关键词"));
            } else if (spec.required()) {
                failItems.add(new VerdictItem(spec.name(), null, null, "必传文件缺失或关键词未命中"));
            } else {
                warnItems.add(new VerdictItem(spec.name(), null, null, "选传文件未上传，如适用请补充"));
            }
        }

        String overall;
        if (!failItems.isEmpty()) overall = "FAIL";
        else if (!warnItems.isEmpty()) overall = "WARN";
        else if (!passItems.isEmpty()) overall = "PASS";
        else overall = "PENDING";

        StepVerdictEntity entity = verdictRepository.findByProjectIdAndStepNo(projectId, stepNo)
                .orElseGet(StepVerdictEntity::new);
        entity.setProjectId(projectId);
        entity.setStepNo(stepNo);
        entity.setVerdict(overall);
        entity.setPassItemsJson(toJson(passItems));
        entity.setWarnItemsJson(toJson(warnItems));
        entity.setFailItemsJson(toJson(failItems));
        entity.setGeneratedAt(LocalDateTime.now());
        return verdictRepository.save(entity);
    }

    public List<StepVerdictEntity> generateAllVerdicts(String projectId) {
        List<StepVerdictEntity> results = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            results.add(generateVerdict(projectId, i));
        }
        return results;
    }

    public List<StepVerdictEntity> getVerdicts(String projectId) {
        return verdictRepository.findAllByProjectId(projectId);
    }

    private boolean matchesKeywords(String text, List<String> keywords) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return keywords.stream().anyMatch(k -> lower.contains(k.toLowerCase()));
    }

    private String getAnalysisText(String fileId) {
        return analysisRepository.findByFileId(fileId)
                .map(a -> a.getExtractedText() == null ? "" : a.getExtractedText().substring(0, Math.min(500, a.getExtractedText().length())))
                .orElse(null);
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "[]"; }
    }

    public record MaterialSpec(String name, boolean required, List<String> keywords) {}
    public record VerdictItem(String materialName, String matchedFile, String matchedFileId, String detail) {}
}
