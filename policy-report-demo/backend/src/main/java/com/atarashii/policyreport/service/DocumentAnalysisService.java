package com.atarashii.policyreport.service;

import com.atarashii.policyreport.config.AppProperties;
import com.atarashii.policyreport.model.DemoModels.AiAdvice;
import com.atarashii.policyreport.model.DemoModels.AiConfig;
import com.atarashii.policyreport.model.DemoModels.DocumentAnalysisResponse;
import com.atarashii.policyreport.model.DemoModels.ExtractedField;
import com.atarashii.policyreport.model.DemoModels.PolicyCheck;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentAnalysisService {
    private final TikaDocumentParser parser;
    private final PolicyKnowledgeService policyKnowledgeService;
    private final OllamaClient ollamaClient;
    private final DeepSeekClient deepSeekClient;
    private final OcrService ocrService;
    private final WorkspaceStateService workspaceStateService;
    private final UploadedFileService uploadedFileService;
    private final AppProperties properties;

    public DocumentAnalysisService(TikaDocumentParser parser,
                                   PolicyKnowledgeService policyKnowledgeService,
                                   OllamaClient ollamaClient,
                                   DeepSeekClient deepSeekClient,
                                   OcrService ocrService,
                                   WorkspaceStateService workspaceStateService,
                                   UploadedFileService uploadedFileService,
                                   AppProperties properties) {
        this.parser = parser;
        this.policyKnowledgeService = policyKnowledgeService;
        this.ollamaClient = ollamaClient;
        this.deepSeekClient = deepSeekClient;
        this.ocrService = ocrService;
        this.workspaceStateService = workspaceStateService;
        this.uploadedFileService = uploadedFileService;
        this.properties = properties;
    }

    public DocumentAnalysisResponse analyze(MultipartFile file, String targetStep, String fallbackStep, AiConfig aiConfig) {
        return analyze(file, targetStep, fallbackStep, aiConfig, null);
    }

    public DocumentAnalysisResponse analyze(MultipartFile file, String targetStep, String fallbackStep, AiConfig aiConfig, String projectId) {
        TikaDocumentParser.ParsedDocument parsed = parser.parse(file);
        String fileId = uploadedFileService.store(file);
        String tikaText = normalize(parsed.text());
        OcrService.OcrResult ocrResult = ocrService.analyze(uploadedFileService.path(fileId), parsed.fileName(), parsed.contentType(), tikaText);
        String text = mergeOcrText(tikaText, ocrResult);
        String documentType = detectDocumentType(parsed.fileName(), text);
        String stepId = resolveStepId(targetStep, guessStep(documentType, parsed.fileName(), text), fallbackStep);
        List<ExtractedField> fields = isStandardLibraryDocument(documentType) ? List.of() : attachSourceFile(extractFields(text), fileId);
        List<PolicyCheck> checks = runPolicyChecks(text, stepId, documentType, fields);
        addOcrCheck(checks, ocrResult);
        AiAdvice aiAdvice = buildAiAdvice(text, stepId, documentType, checks, fields, aiConfig);
        DocumentAnalysisResponse response = new DocumentAnalysisResponse(
                fileId,
                parsed.fileName(),
                parsed.contentType(),
                file.getSize(),
                documentType,
                stepId,
                preview(text),
                text.length(),
                fields,
                checks,
                aiAdvice
        );
                workspaceStateService.mergeAnalysis(projectId, response);
            return response;
    }

    public DocumentAnalysisResponse analyze(MultipartFile file, String targetStep, AiConfig aiConfig) {
        return analyze(file, targetStep, null, aiConfig);
    }

    private String detectDocumentType(String fileName, String text) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String sample = text.length() > 4_000 ? text.substring(0, 4_000) : text;
        if (name.contains("明白卡") || sample.contains("重大项目用地政策明白卡")) return "政策明白卡";
        if (name.contains("1009") || sample.contains("自然资办函") || sample.contains("附件3-3")) return "1009号模板文件";
        if (name.contains("标准汇编") || name.contains("土地使用标准") || name.contains("用地指标") || name.contains("用地标准")) return "用地标准库文件";
        String head = sample.length() > 300 ? sample.substring(0, 300) : sample;
        if (head.contains("土地使用标准汇编")) return "用地标准库文件";
        if (head.contains("建设用地指标") && (sample.contains("用地指标不应超过") || sample.contains("m²/台") || sample.contains("hm²/km") || sample.contains("hm²/MW") || sample.contains("用地指标（hm"))) return "用地标准库文件";
        if (name.contains("审查报告") || sample.contains("审查情况的报告")) return "审查报告样稿";
        if (name.contains("违法") || sample.contains("违法用地") || sample.contains("行政处罚决定书")) return "违法用地查处材料";
        if (name.contains("信访") || sample.contains("信访")) return "信访处理说明";
        if (name.contains("可研") && name.contains("变更")) return "可行性研究报告变更批复";
        if ((name.contains("初设") || name.contains("初步设计")) && name.contains("变更")) return "初步设计变更批复";
        if (name.contains("初设") || name.contains("初步设计")) return "初步设计批复";
        if (name.contains("预审") || sample.contains("用地预审")) return "建设用地预审批复";
        if (name.contains("核准") || name.contains("备案") || sample.contains("项目核准")) return "项目立项/核准批复";
        if (name.contains("初设") || name.contains("初步设计") || sample.contains("初步设计")) return "初步设计批复";
        if (name.contains("勘测") || sample.contains("勘测定界")) return "勘测定界材料";
        if (name.contains("土地分类") || sample.contains("土地分类权属面积")) return "土地分类权属面积汇总表";
        if (name.contains("权属") || sample.contains("权属")) return "权属证明材料";
        if (name.contains("年度国土变更") || name.contains("国土变更调查") || sample.contains("国土变更调查") || sample.contains("套合情况")) return "年度国土变更调查套合情况分析";
        if (name.contains("规划") || sample.contains("国土空间规划")) return "规划佐证材料";
        if (name.contains("计划") || name.contains("核销指标") || sample.contains("计划指标")) return "计划指标文件";
        if (name.contains("林地") || sample.contains("使用林地")) return "林地批复";
        if (name.contains("生态") || name.contains("自然保护区") || sample.contains("生态保护红线") || sample.contains("自然保护区")) return "生态保护地意见";
        if (name.contains("补充耕地") || sample.contains("补充耕地")) return "补充耕地方案";
        if (name.contains("占补") || sample.contains("占补平衡")) return "占补平衡挂钩信息单";
        if (name.contains("预公告") || sample.contains("征收土地预公告")) return "征收土地预公告";
        if (name.contains("土地现状调查") || name.contains("现状调查材料") || sample.contains("土地现状调查")) return "土地现状调查材料";
        if (name.contains("社稳") || name.contains("稳评") || name.contains("社会稳定") || sample.contains("社稳") || sample.contains("社会稳定风险")) return "社会稳定风险评估报告";
        if (name.contains("听证") || sample.contains("听证")) return "听证材料";
        if (name.contains("补偿安置公告") || sample.contains("补偿安置公告")) return "征地补偿安置公告及照片";
        if (name.contains("协议") || sample.contains("补偿安置协议")) return "征地补偿安置协议";
        if (name.contains("社保") || sample.contains("社会保障")) return "社保审核意见";
        if (name.contains("节约集约") || sample.contains("节约集约")) return "节约集约用地论证分析专章";
        if (name.contains("行业主管") || sample.contains("行业主管部门")) return "行业主管部门意见";
        if (name.contains("有偿使用费") || sample.contains("土地有偿使用费")) return "土地有偿使用费材料";
        if (name.contains("地灾评估") || name.contains("地质灾害") || sample.contains("地灾评估") || sample.contains("地质灾害评估")) return "地灾评估报告批复";
        if (name.contains("压覆矿查询") || name.contains("压覆矿") || name.contains("压矿查询") || sample.contains("压覆矿查询")) return "压覆矿查询表";
        if (sample.contains("补偿安置") || sample.contains("土地征收")) return "土地征收材料";
        if (sample.contains("地质灾害") || sample.contains("压覆")) return "地灾压矿材料";
        return "待识别材料";
    }

    private String guessStep(String documentType, String fileName, String text) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (documentType.contains("预审") || documentType.contains("核准") || documentType.contains("初步设计")) return "step1";
        if (documentType.contains("可行性研究") || name.contains("可研")) return "step1";
        if (documentType.contains("国土变更调查")) return "step2";
        if (documentType.contains("土地现状调查") || documentType.contains("征地补偿安置公告") || documentType.contains("社会稳定") || documentType.contains("听证")) return "step5";
        if (documentType.contains("勘测") || documentType.contains("权属") || name.contains("现状") || name.contains("权属")) return "step2";
        if (documentType.contains("规划") || documentType.contains("计划指标") || documentType.contains("林地") || documentType.contains("生态")) return "step3";
        if (documentType.contains("补充耕地") || documentType.contains("占补")) return "step4";
        if (documentType.contains("征收")) return "step5";
        if (documentType.contains("社保") || documentType.contains("协议") || documentType.contains("稳评")) return "step5";
        if (documentType.contains("节约集约") || documentType.contains("行业主管") || documentType.contains("有偿使用费") || text.contains("供地")) return "step6";
        if (documentType.contains("标准库")) return "step6";
        if (documentType.contains("地灾") || documentType.contains("压矿") || documentType.contains("压覆")) return "step7";
        if (documentType.contains("违法")) return "step8";
        if (documentType.contains("信访") || text.contains("信访")) return "step8";
        return "";
    }

    private String resolveStepId(String targetStep, String guessedStep, String fallbackStep) {
        if (isStepId(targetStep)) return targetStep;
        if (isStepId(guessedStep)) return guessedStep;
        if (isStepId(fallbackStep)) return fallbackStep;
        return "step1";
    }

    private boolean isStepId(String value) {
        return value != null && value.matches("step[1-8]");
    }

    private List<ExtractedField> extractFields(String text) {
        List<ExtractedField> fields = new ArrayList<>();
        addFirstMatch(fields, "项目名称", text, List.of("关于(.{2,80}?)(?:农用地转用|建设项目用地|用地|工程)", "(兴宁五塘风电场一期工程)"));
        addFirstMatch(fields, "项目代码", text, List.of("项目代码[:：]?\\s*([0-9A-Za-z-]{8,})"));
        addFirstMatch(fields, "预审批复", text, List.of("用字第[:：]?([0-9A-Za-z-]+号)", "预审[^。；，]*?([0-9A-Za-z-]+号)"));
        addFirstMatch(fields, "核准文号", text, List.of("(南发改能源〔\\d{4}〕\\d+号)", "([\\u4e00-\\u9fa5]+发改[^，。；]*〔\\d{4}〕\\d+号)"));
        addArea(fields, "总用地面积", text, "总用地面积");
        addArea(fields, "申报面积", text, "申报面积");
        addArea(fields, "项目用地面积", text, "项目用地面积");
        addArea(fields, "农用地面积", text, "农用地");
        addArea(fields, "征收面积", text, "征收土地");
        addArea(fields, "违法用地面积", text, "违法用地面积");
        addLength(fields, "线路/道路长度", text, List.of("线路长度", "正线长度", "路线全长", "线路全长", "建设长度", "道路长度"));
        addLength(fields, "桥梁长度", text, List.of("桥梁长度", "桥梁总长", "桥长"));
        addLength(fields, "隧道长度", text, List.of("隧道长度", "隧道总长", "隧长"));
        addLength(fields, "区间路基长度", text, List.of("区间路基长度", "路基长度"));
        addFirstMatch(fields, "桥隧比", text, List.of("桥隧比[:：]?\\s*([0-9]+(?:\\.[0-9]+)?%?)", "桥梁[^。；，\\n]{0,20}隧道[^。；，\\n]{0,20}([0-9]+(?:\\.[0-9]+)?%)"));
        addFirstMatch(fields, "地形类型", text, List.of("地形类型[:：]?\\s*(平原|丘陵|山区)", "按(平原|丘陵|山区)地形"));
        addFirstMatch(fields, "建设规模", text, List.of("建设规模[:：]?\\s*([^。；\\n]{3,120})"));
        addFirstMatch(fields, "功能分区", text, List.of("功能分区[:：]?\\s*([^。；\\n]{3,120})"));
        addFirstMatch(fields, "供地方式", text, List.of("(划拨|出让|租赁)方式供地", "供地方式[:：]?\\s*(划拨|出让|租赁)"));
        addFirstMatch(fields, "新增建设用地土地有偿使用费", text, List.of("土地有偿使用费[^0-9]{0,20}([0-9]+(?:\\.[0-9]+)?\\s*(?:万元|元))"));
        addFirstMatch(fields, "用地标准", text, List.of("符合(表[0-9.]+[^。；\\n]{0,60})", "执行(表[0-9.]+[^。；\\n]{0,60})"));
        if (text.contains("不涉及生态保护红线") || text.contains("不位于生态保护红线")) {
            fields.add(new ExtractedField("生态红线", "不涉及", "关键词判断", 0.82, null));
        }
        if (text.contains("不涉及占用耕地") || text.contains("耕地0公顷")) {
            fields.add(new ExtractedField("占用耕地", "0公顷", "关键词判断", 0.78, null));
        }
        if (text.contains("不压覆重要矿产")) {
            fields.add(new ExtractedField("压覆矿产", "不压覆重要矿产资源", "关键词判断", 0.84, null));
        }
        return dedupe(fields);
    }

    private void addFirstMatch(List<ExtractedField> fields, String label, String text, List<String> patterns) {
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(text);
            if (matcher.find()) {
                fields.add(new ExtractedField(label, clean(matcher.group(1)), "Tika文本正则", 0.72, null));
                return;
            }
        }
    }

    private void addArea(List<ExtractedField> fields, String label, String text, String keyword) {
        int index = text.indexOf(keyword);
        if (index < 0) return;
        String window = text.substring(index, Math.min(text.length(), index + 120));
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*公顷").matcher(window);
        if (matcher.find()) {
            fields.add(new ExtractedField(label, new BigDecimal(matcher.group(1)).stripTrailingZeros().toPlainString() + "公顷", "面积字段识别", 0.74, null));
        }
    }

    private void addLength(List<ExtractedField> fields, String label, String text, List<String> keywords) {
        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            if (index < 0) continue;
            String window = text.substring(index, Math.min(text.length(), index + 140));
            Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(公里|千米|km|KM|米|m)").matcher(window);
            if (matcher.find()) {
                fields.add(new ExtractedField(label, lengthToKm(matcher.group(1), matcher.group(2)) + "km", "长度字段识别", 0.76, null));
                return;
            }
        }
    }

    private String lengthToKm(String value, String unit) {
        BigDecimal number = new BigDecimal(value);
        String normalized = unit.toLowerCase(Locale.ROOT);
        BigDecimal km = normalized.equals("米") || normalized.equals("m") ? number.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP) : number;
        return km.stripTrailingZeros().toPlainString();
    }

    private List<ExtractedField> attachSourceFile(List<ExtractedField> fields, String fileId) {
        return fields.stream()
                .map(field -> new ExtractedField(field.label(), field.value(), field.source(), field.confidence(), fileId))
                .toList();
    }

    private List<ExtractedField> dedupe(List<ExtractedField> fields) {
        Set<String> seen = new LinkedHashSet<>();
        List<ExtractedField> result = new ArrayList<>();
        for (ExtractedField field : fields) {
            String key = field.label() + field.value();
            if (seen.add(key)) {
                result.add(field);
            }
        }
        return result;
    }

    private List<PolicyCheck> runPolicyChecks(String text, String targetStep, String documentType, List<ExtractedField> fields) {
        List<PolicyCheck> checks = new ArrayList<>();
        String step = targetStep == null ? "" : targetStep;
        checks.add(new PolicyCheck("info", "已完成Tika文本解析", "识别为“" + documentType + "”，抽取文本约" + text.length() + "字。", "Tika"));
        checks.add(new PolicyCheck(policyKnowledgeService.hasPolicyCardLoaded() ? "pass" : "warn", "政策明白卡加载", policyKnowledgeService.hasPolicyCardLoaded() ? "已读取根目录政策明白卡，可作为规则和AI提示依据。" : "未读取到政策明白卡，当前仅使用内置规则。", "2025重大项目用地政策明白卡"));

        if (isStandardLibraryDocument(documentType)) {
            checks.add(new PolicyCheck("warn", "标准库文件识别", "该文件更像土地用地标准/指标库资料，已避免把标准条文写入项目字段；建议走标准库导入或匹配流程。", "AI工作流"));
            return checks;
        }

        if (step.contains("3") || text.contains("生态保护红线") || text.contains("永久基本农田")) {
            if (text.contains("不涉及生态保护红线") || text.contains("不位于生态保护红线")) {
                checks.add(new PolicyCheck("pass", "生态保护红线", "材料已出现“不涉及/不位于生态保护红线”的明确结论。", "明白卡/1009号模板"));
            } else if (text.contains("生态保护红线")) {
                checks.add(new PolicyCheck("warn", "生态保护红线", "材料提到生态保护红线，但未识别到明确“不涉及”或主管部门意见，需要人工确认。", "明白卡/1009号模板"));
            }
            if (text.contains("永久基本农田0公顷") || text.contains("不涉及永久基本农田")) {
                checks.add(new PolicyCheck("pass", "永久基本农田", "材料显示不占用永久基本农田。", "1009号模板"));
            }
        }

        if (step.contains("2") || text.contains("国土变更调查") || text.contains("套合情况")) {
            if (text.contains("国土变更调查") || text.contains("套合情况")) {
                checks.add(new PolicyCheck("pass", "年度国土变更调查套合情况", "已识别年度国土变更调查套合情况分析材料或相关结论。", "1009号模板"));
            } else {
                checks.add(new PolicyCheck("warn", "年度国土变更调查套合情况", "第二步需补充年度国土变更调查套合情况分析材料。", "1009号模板"));
            }
        }

        if (step.contains("4") || text.contains("补充耕地") || text.contains("耕地")) {
            if (text.contains("不涉及占用耕地") || text.contains("耕地0公顷") || text.contains("无补充耕地任务")) {
                checks.add(new PolicyCheck("pass", "补充耕地", "材料能支撑“不占耕地、无补充耕地任务”的写法。", "明白卡/1009号模板"));
            } else if (text.contains("耕地")) {
                checks.add(new PolicyCheck("warn", "补充耕地", "材料出现耕地信息，请核对是否需要占补平衡挂钩信息单或补充耕地方案。", "明白卡"));
            }
        }

        if (step.contains("5") || text.contains("土地征收") || text.contains("补偿安置")) {
            if (text.contains("公共利益") || text.contains("第四十五条")) {
                checks.add(new PolicyCheck("pass", "征收公共利益", "材料有公共利益或土地管理法第四十五条相关表述。", "1009号模板"));
            } else {
                checks.add(new PolicyCheck("warn", "征收公共利益", "未看到公共利益依据，征收章节通常需要明确对应条款和项目类别。", "1009号模板"));
            }
            if (text.contains("全部") && text.contains("签订征地补偿安置协议")) {
                checks.add(new PolicyCheck("pass", "补偿协议", "材料包含全部签订补偿安置协议的表述。", "1009号模板"));
            }
        }

        if (step.contains("6") || text.contains("供地") || text.contains("节约集约")) {
            if (text.contains("出让方式供地") || text.contains("出让")) {
                checks.add(new PolicyCheck("pass", "供地方式", "识别到出让供地表述。", "明白卡"));
            }
            if (text.contains("土地有偿使用费") || text.contains("24.32")) {
                checks.add(new PolicyCheck("pass", "土地有偿使用费", "识别到新增建设用地土地有偿使用费信息。", "1009号模板"));
            }
        }

        if (step.contains("7") || text.contains("地质灾害") || text.contains("压覆")) {
            if (text.contains("地质灾害易发区") && text.contains("评估")) {
                checks.add(new PolicyCheck("pass", "地灾评估", "材料说明位于地灾易发区并已开展评估。", "1009号模板"));
            }
            if (text.contains("不压覆重要矿产")) {
                checks.add(new PolicyCheck("pass", "压覆矿产", "材料显示不压覆重要矿产资源。", "1009号模板"));
            }
        }

        if (step.contains("8") || text.contains("违法用地") || text.contains("信访")) {
            if (text.contains("不存在信访") || text.contains("不涉及信访")) {
                checks.add(new PolicyCheck("pass", "信访事项", "材料支持“不涉及/不存在信访事项”的写法。", "1009号模板"));
            }
            if (text.contains("违法用地") && text.contains("整改到位")) {
                checks.add(new PolicyCheck("pass", "违法整改", "材料显示违法用地已查处整改到位。", "1009号模板"));
            } else if (text.contains("违法用地")) {
                checks.add(new PolicyCheck("warn", "违法整改", "材料出现违法用地，但未明确识别到“整改到位”，需要补查处到位意见或处罚执行证明。", "1009号模板"));
            }
        }

        if (fields.isEmpty()) {
            checks.add(new PolicyCheck("warn", "结构化字段", "未抽取到明显字段，可能是扫描件、图片型PDF或格式不规整材料。", "Tika"));
        }
        return checks;
    }

    private AiAdvice buildAiAdvice(String text, String targetStep, String documentType, List<PolicyCheck> checks, List<ExtractedField> fields, AiConfig aiConfig) {
        List<String> editablePoints = new ArrayList<>();
        List<String> missingMaterials = new ArrayList<>();
        List<String> riskPoints = new ArrayList<>();

        for (PolicyCheck check : checks) {
            if ("warn".equals(check.level())) {
                riskPoints.add(check.title() + ": " + check.detail());
            }
        }
        if (fields.stream().anyMatch(field -> field.label().contains("面积"))) {
            editablePoints.add("核对面积字段：总面积、农用地、征收面积、违法面积必须前后一致。");
        }
        if (documentType.contains("审查报告") || documentType.contains("样稿")) {
            editablePoints.add("可直接复用已有报告段落，但要把“我局/我厅”口径和报送层级统一。 ");
        }
        if (targetStep != null && targetStep.contains("5") && !text.contains("社保")) {
            missingMaterials.add("土地征收步骤建议补社保审核意见或社保资金到账凭证。 ");
        }
        if (targetStep != null && targetStep.contains("8") && text.contains("违法用地") && !text.contains("查处到位")) {
            missingMaterials.add("违法用地材料建议补查处到位意见书、罚款到账、没收移交或整改证明。 ");
        }

        if (aiConfig != null && "rules".equalsIgnoreCase(aiConfig.provider())) {
            return new AiAdvice("rules", "规则初筛", false, "已完成 Tika 解析、字段抽取和政策规则初筛。", editablePoints, missingMaterials, riskPoints, "自动化接口自测使用规则模式，未调用外部大模型。");
        }
        if (aiConfig != null && "deepseek".equalsIgnoreCase(aiConfig.provider()) && aiConfig.deepseekApiKey() != null && !aiConfig.deepseekApiKey().isBlank()) {
            DeepSeekClient.GenerationResult deepSeek = generateDeepSeekFullText(text, targetStep, documentType, checks, fields, aiConfig);
            if (deepSeek.usedModel()) {
                String raw = deepSeek.text();
                return new AiAdvice("deepseek", deepSeek.model(), false, firstLine(raw), editablePoints, missingMaterials, riskPoints, raw);
            }
            riskPoints.add(deepSeek.text());
        }
        OllamaClient.GenerationResult generation = generateOllamaFullText(text, targetStep, documentType, checks, fields);
        if (generation.usedOllama()) {
            String raw = generation.text();
            return new AiAdvice("ollama", generation.model(), true, firstLine(raw), editablePoints, missingMaterials, riskPoints, raw);
        }
        return new AiAdvice("rules", generation.model(), false, "已完成规则初筛；大模型未返回有效结果，页面展示内置建议。", editablePoints, missingMaterials, riskPoints, generation.text());
    }

    private String buildPrompt(String text, String targetStep, String documentType, List<PolicyCheck> checks, List<ExtractedField> fields) {
        String knowledge = policyKnowledgeService.buildKnowledgeFor(targetStep, text);
        return "你是建设用地报批审查报告主审 AI，不是摘要工具。请基于政策明白卡、1009号模板、规则初筛和上传材料全文，完成本步骤审查。\n"
                + "要求: 用中文输出，面向项目经理和客户；规则没覆盖的也要根据材料语义判断。分成五段: 1材料摘要 2AI识别字段 3需要改 4可补充 5风险提醒。\n"
                + "请明确哪些结论来自材料原文，哪些只是推断；如果材料其实是标准库/规范文件，不要把标准条文误当成项目事实。\n"
                + "目标步骤: " + targetStep + "\n"
                + "文档类型: " + documentType + "\n"
                + "政策依据摘录:\n" + knowledge + "\n"
                + "已抽字段: " + fields + "\n"
                + "规则初筛: " + checks + "\n"
                + "上传材料文本:\n" + text;
    }

    private DeepSeekClient.GenerationResult generateDeepSeekFullText(String text, String targetStep, String documentType, List<PolicyCheck> checks, List<ExtractedField> fields, AiConfig aiConfig) {
        TextChunks chunks = splitForAi(text);
        if (chunks.parts().size() == 1) {
            return deepSeekClient.generate(buildPrompt(text, targetStep, documentType, checks, fields), aiConfig.deepseekApiKey(), aiConfig.deepseekModel());
        }
        List<String> chunkReviews = new ArrayList<>();
        String model = aiConfig.deepseekModel() == null || aiConfig.deepseekModel().isBlank() ? "deepseek-chat" : aiConfig.deepseekModel();
        for (int index = 0; index < chunks.parts().size(); index++) {
            String prompt = buildChunkPrompt(chunks.parts().get(index), index + 1, chunks.parts().size(), chunks, targetStep, documentType);
            DeepSeekClient.GenerationResult result = deepSeekClient.generate(prompt, aiConfig.deepseekApiKey(), aiConfig.deepseekModel());
            model = result.model();
            chunkReviews.add("## 分块 " + (index + 1) + "/" + chunks.parts().size() + (result.usedModel() ? "" : " 调用失败") + "\n" + result.text());
        }
        DeepSeekClient.GenerationResult finalResult = deepSeekClient.generate(buildSynthesisPrompt(chunkReviews, chunks, targetStep, documentType, checks, fields), aiConfig.deepseekApiKey(), aiConfig.deepseekModel());
        if (finalResult.usedModel()) {
            return new DeepSeekClient.GenerationResult(true, finalResult.model(), aiHeader(chunks) + finalResult.text());
        }
        return new DeepSeekClient.GenerationResult(true, model, aiHeader(chunks) + String.join("\n\n", chunkReviews) + "\n\n最终汇总调用失败：" + finalResult.text());
    }

    private OllamaClient.GenerationResult generateOllamaFullText(String text, String targetStep, String documentType, List<PolicyCheck> checks, List<ExtractedField> fields) {
        TextChunks chunks = splitForAi(text);
        if (chunks.parts().size() == 1) {
            return ollamaClient.generate(buildPrompt(text, targetStep, documentType, checks, fields));
        }
        List<String> chunkReviews = new ArrayList<>();
        String model = "";
        for (int index = 0; index < chunks.parts().size(); index++) {
            OllamaClient.GenerationResult result = ollamaClient.generate(buildChunkPrompt(chunks.parts().get(index), index + 1, chunks.parts().size(), chunks, targetStep, documentType));
            model = result.model();
            if (!result.usedOllama()) {
                return result;
            }
            chunkReviews.add("## 分块 " + (index + 1) + "/" + chunks.parts().size() + "\n" + result.text());
        }
        OllamaClient.GenerationResult finalResult = ollamaClient.generate(buildSynthesisPrompt(chunkReviews, chunks, targetStep, documentType, checks, fields));
        if (finalResult.usedOllama()) {
            return new OllamaClient.GenerationResult(true, finalResult.model(), aiHeader(chunks) + finalResult.text());
        }
        return new OllamaClient.GenerationResult(true, model, aiHeader(chunks) + String.join("\n\n", chunkReviews) + "\n\n最终汇总调用失败：" + finalResult.text());
    }

    private String buildChunkPrompt(String chunk, int index, int total, TextChunks chunks, String targetStep, String documentType) {
        return "你是建设用地报批审查报告主审 AI。下面是同一份上传材料的第 " + index + "/" + total + " 段。\n"
                + "先只分析本段，不要假装看到其他段。输出: 1片段摘要 2字段候选 3材料类型/步骤判断 4审查风险 5可写入报告的句子。\n"
                + "目标步骤: " + targetStep + "\n"
                + "文档类型: " + documentType + "\n"
                + "全文长度: " + chunks.originalLength() + " 字；本次已送入 " + chunks.sentLength() + " 字。\n"
                + "材料片段:\n" + chunk;
    }

    private String buildSynthesisPrompt(List<String> chunkReviews, TextChunks chunks, String targetStep, String documentType, List<PolicyCheck> checks, List<ExtractedField> fields) {
        return "你是建设用地报批审查报告主审 AI。下面是同一上传文件的分块审查记录，请综合成最终用户可读结论。\n"
                + "要求: 不要只复述分块；要合并重复点、消除冲突、指出证据不足处。输出五段: 1材料摘要 2AI识别字段 3需要改 4可补充 5风险提醒。\n"
                + "如果材料是土地标准/规范文件，明确提示它应进入标准库，不应当作项目事实字段。\n"
                + "目标步骤: " + targetStep + "\n"
                + "文档类型: " + documentType + "\n"
                + "规则初筛: " + checks + "\n"
                + "正则字段: " + fields + "\n"
                + "全文长度: " + chunks.originalLength() + " 字；分块数: " + chunks.parts().size() + "；是否达到上限: " + (chunks.truncated() ? "是" : "否") + "\n"
                + String.join("\n\n", chunkReviews);
    }

    private TextChunks splitForAi(String text) {
        int chunkSize = Math.max(4_000, properties.getAiFullTextChunkSize());
        int maxChunks = Math.max(1, properties.getAiFullTextMaxChunks());
        List<String> parts = new ArrayList<>();
        int sentLength = 0;
        for (int start = 0; start < text.length() && parts.size() < maxChunks; start += chunkSize) {
            int end = Math.min(text.length(), start + chunkSize);
            parts.add(text.substring(start, end));
            sentLength += end - start;
        }
        if (parts.isEmpty()) {
            parts.add("（上传材料未抽取到可读文本。）");
        }
        return new TextChunks(parts, text.length(), sentLength, sentLength < text.length());
    }

    private String aiHeader(TextChunks chunks) {
        String clipped = chunks.truncated() ? "，已达到当前分块上限，仍有后续文本未送入AI" : "，未截断";
        return "AI全文分块审查：已送入 " + chunks.sentLength() + "/" + chunks.originalLength() + " 字，共 " + chunks.parts().size() + " 个分块" + clipped + "。\n\n";
    }

    private void addOcrCheck(List<PolicyCheck> checks, OcrService.OcrResult result) {
        if (result.attempted()) {
            checks.add(new PolicyCheck(result.level(), result.title(), result.detail(), result.provider().isBlank() ? "OCR" : result.provider()));
        }
    }

    private String mergeOcrText(String tikaText, OcrService.OcrResult result) {
        if (result.appended() && result.text() != null && !result.text().isBlank()) {
            return normalize(tikaText + "\n\n【OCR识别文本】\n" + result.text());
        }
        return tikaText;
    }

    private boolean isStandardLibraryDocument(String documentType) {
        return documentType != null && documentType.contains("标准库");
    }

    private record TextChunks(List<String> parts, int originalLength, int sentLength, boolean truncated) {
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\t\\r]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private String preview(String text) {
        return text.length() > 1_600 ? text.substring(0, 1_600) + "..." : text;
    }

    private String clean(String value) {
        return value.replaceAll("[，。；：:]+$", "").trim();
    }

    private String firstLine(String raw) {
        if (raw == null || raw.isBlank()) return "AI 已返回分析结果。";
        String[] lines = raw.strip().split("\\R");
        for (String line : lines) {
            String cleaned = line.strip().replaceFirst("^#+\\s*", "");
            if (cleaned.isBlank()) continue;
            if (cleaned.matches("^[一二三四1234、.\\s]*(材料摘要|需要改|可补充|风险提醒).*")) continue;
            return cleaned.replace("**", "");
        }
        return "AI 已返回分析结果。";
    }
}