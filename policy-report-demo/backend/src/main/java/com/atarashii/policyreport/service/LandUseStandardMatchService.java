package com.atarashii.policyreport.service;

import com.atarashii.policyreport.model.DemoModels.LandUseStandardDto;
import com.atarashii.policyreport.model.DemoModels.LandUseStandardMatchDto;
import com.atarashii.policyreport.model.DemoModels.EditableField;
import com.atarashii.policyreport.model.DemoModels.StepChecklistItem;
import com.atarashii.policyreport.model.DemoModels.WorkspaceState;
import com.atarashii.policyreport.persistence.LandUseStandardMatchEntity;
import com.atarashii.policyreport.persistence.LandUseStandardMatchRepository;
import com.atarashii.policyreport.persistence.ProjectRecordEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LandUseStandardMatchService {
    private static final int MATCH_LIMIT = 10;
    private static final Pattern AREA_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(公顷|hm²|hm2|平方米|㎡|亩)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAPACITY_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(万千瓦|兆瓦|MW|mw|千瓦|kW|KW)");
    private static final Pattern LENGTH_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(公里|千米|km|KM)");
    private static final Pattern TURBINE_COUNT_PATTERN = Pattern.compile("(?:风电机组|风机|机组)[^0-9]{0,12}([0-9]+)\\s*台|([0-9]+)\\s*台[^，。；;\\n]{0,12}(?:风电机组|风机|机组)");
    private static final Pattern SINGLE_TURBINE_PATTERN = Pattern.compile("(?:单机容量|单台容量|风机容量)[^0-9]{0,12}([0-9]+(?:\\.[0-9]+)?)\\s*(兆瓦|MW|mw|千瓦|kW|KW)");
    private static final Pattern DIRECT_PER_KM_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(?:hm²|hm2|公顷)\\s*/\\s*(?:km|公里|千米)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECT_PER_MW_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(?:hm²|hm2|公顷)\\s*/\\s*(?:MW|兆瓦)", Pattern.CASE_INSENSITIVE);

    private final LandUseStandardMatchRepository repository;
    private final ProjectRecordService projectRecordService;
    private final LandUseStandardService landUseStandardService;
    private final WorkspaceStateService workspaceStateService;

    public LandUseStandardMatchService(LandUseStandardMatchRepository repository,
                                       ProjectRecordService projectRecordService,
                                       LandUseStandardService landUseStandardService,
                                       WorkspaceStateService workspaceStateService) {
        this.repository = repository;
        this.projectRecordService = projectRecordService;
        this.landUseStandardService = landUseStandardService;
        this.workspaceStateService = workspaceStateService;
    }

    public List<LandUseStandardMatchDto> list(String projectId) {
        return repository.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<LandUseStandardMatchDto> refresh(String projectId) {
        ProjectRecordEntity project = projectRecordService.getEntity(projectId);
        repository.deleteByProjectId(projectId);
        ProjectFacts facts = collectFacts(project, workspaceStateService.getState(projectId));
        List<ScoredStandard> selected = landUseStandardService.search(project.getProjectType(), "", 100).stream()
                .map(standard -> new ScoredStandard(standard, score(standard, facts)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredStandard::score).reversed())
                .limit(MATCH_LIMIT)
            .toList();
        List<LandUseStandardMatchEntity> matches = selected.stream()
                .map(item -> buildMatch(projectId, project, facts, item.standard()))
                .toList();
        repository.saveAll(matches);
        workspaceStateService.mergeStandardReview(projectId, reviewFields(facts, matches, selected), reviewChecks(matches, selected), reviewSummary(matches, selected), reviewSummaryStatus(matches));
        return list(projectId);
    }

    private LandUseStandardMatchEntity buildMatch(String projectId, ProjectRecordEntity project, ProjectFacts facts, LandUseStandardDto standard) {
        StandardEvaluation evaluation = evaluate(standard, facts);
        LandUseStandardMatchEntity entity = new LandUseStandardMatchEntity();
        entity.setProjectId(projectId);
        entity.setStandardId(standard.id());
        entity.setMatchStatus(evaluation.status());
        entity.setMatchedFields(limit(evaluation.matchedFields(), 600));
        entity.setConclusion(limit(evaluation.conclusion(project, standard), 1600));
        return entity;
    }

    private int score(LandUseStandardDto standard, ProjectFacts facts) {
        String haystack = (standard.projectTypeLabel() + " " + standard.chapterTitle() + " " + standard.content() + " " + standard.keywords()).toLowerCase(Locale.ROOT);
        int score = 8;
        if (haystack.contains("用地指标")) score += 12;
        if (haystack.contains("总体指标") || haystack.contains("综合建设用地指标")) score += 10;
        if (haystack.contains("表")) score += 4;
        if (facts.areaHa() != null && (haystack.contains("建设用地") || haystack.contains("用地面积"))) score += 10;
        if (facts.lengthKm() != null && containsPerKmUnit(haystack)) score += 36;
        if (facts.capacityMw() != null && containsPerMwUnit(haystack)) score += 30;
        if (facts.turbineCount() != null && haystack.contains("m²/台")) score += 46;
        if (facts.singleTurbineKw() != null && haystack.contains("单机容量")) score += 22;
        return score;
    }

    private StandardEvaluation evaluate(LandUseStandardDto standard, ProjectFacts facts) {
        List<StandardIndicator> indicators = extractIndicators(standard);
        Optional<StandardIndicator> selected = selectIndicator(indicators, facts);
        if (selected.isEmpty()) {
            return StandardEvaluation.attention(facts, manualAttentionMessage(standard), null);
        }
        StandardIndicator indicator = selected.get();
        Optional<Calculation> calculation = calculate(indicator, facts);
        if (calculation.isEmpty()) {
            String missing = missingFor(indicator);
            return StandardEvaluation.missing(facts, "已识别标准指标“" + indicator.description() + "”，但项目材料中缺少" + missing + "。需要在解析字段核对中补到该参数，来源优先看" + materialSourceFor(indicator) + "；补齐后系统会按该指标重新测算是否超标准。", indicator);
        }
        Calculation calc = calculation.get();
        if (facts.areaHa() == null) {
            return StandardEvaluation.missing(facts, "已按标准指标估算用地上限约 " + format(calc.limitHa()) + " 公顷，但项目材料中缺少申报总用地面积，暂不能判断是否超指标。", indicator);
        }
        BigDecimal delta = facts.areaHa().subtract(calc.limitHa());
        if (delta.compareTo(BigDecimal.ZERO) <= 0) {
            return StandardEvaluation.pass(facts, "按" + indicator.description() + "测算，上限约 " + format(calc.limitHa()) + " 公顷；当前识别申报面积 " + format(facts.areaHa()) + " 公顷，未超过该条目估算上限。", indicator);
        }
        return StandardEvaluation.attention(facts, "按" + indicator.description() + "测算，上限约 " + format(calc.limitHa()) + " 公顷；当前识别申报面积 " + format(facts.areaHa()) + " 公顷，超出约 " + format(delta) + " 公顷。请结合功能分区和行业主管部门意见复核。", indicator);
    }

    private String materialSourceFor(StandardIndicator indicator) {
        return switch (indicator.basis()) {
            case PER_KM -> "初步设计说明、主要工程数量表、线路/道路长度表、用地统计表";
            case PER_MW -> "核准批复、初步设计批复、设备清单或建设规模说明";
            case PER_TURBINE -> "核准批复、初步设计批复、风机设备清单";
            case ABSOLUTE -> "勘测定界报告、土地分类权属面积表、用地申请表";
        };
    }

    private String manualAttentionMessage(LandUseStandardDto standard) {
        String text = standard.chapterTitle() + " " + standard.content();
        List<String> concerns = new ArrayList<>();
        if (text.contains("桥梁") || text.contains("桥隧")) concerns.add("桥梁总长、正线长度、桥梁计算长度比重、地形类型和平纵断面是否一致");
        if (text.contains("隧道")) concerns.add("隧道总长、正线长度、隧道计算长度比重、地形类型和平纵断面是否一致");
        if (text.contains("路基")) concerns.add("区间路基长度、路基宽度、地形类型和主要工程数量表是否一致");
        if (text.contains("车站") || text.contains("中间站") || text.contains("站场")) concerns.add("车站数量、站型、股道数、站场面积是否单列");
        if (text.contains("电缆")) concerns.add("电缆敷设长度、直埋/电缆沟方式及是否并入集电线路用地");
        if (text.contains("架空")) concerns.add("架空线路长度、电压等级、塔基数量和塔基永久占地");
        if (text.contains("升压") || text.contains("变电站") || text.contains("运行管理中心")) concerns.add("升压站电压等级、主变容量、站址及运行管理中心用地是否单列");
        if (text.contains("交通") || text.contains("道路")) concerns.add("进场道路/检修道路长度、路基宽度和是否计入永久用地");
        if (text.contains("综合") || text.contains("总体")) concerns.add("各功能分区面积汇总是否与申报总面积一致");
        if (concerns.isEmpty()) concerns.add("建设规模、功能分区面积、申报总面积与该标准适用口径是否一致");
        return "当前条文更偏适用条件或表格说明，暂未提取出可直接换算的单一指标。智能关注点：" + String.join("；", concerns) + "。";
    }

    private List<EditableField> reviewFields(ProjectFacts facts, List<LandUseStandardMatchEntity> matches, List<ScoredStandard> selected) {
        List<EditableField> fields = new ArrayList<>();
        String indicators = standardIndicatorSummary(matches, selected);
        if (!indicators.isBlank()) fields.add(reviewField("standardReview.indicators", "标准库指标-已匹配条目", indicators, reviewSummaryStatus(matches)));
        String missing = missingParameterSummary(matches);
        if (!missing.isBlank()) fields.add(reviewField("standardReview.missingParameters", "标准库指标-缺失项目参数", missing, "warn"));
        if (facts.areaHa() != null) fields.add(reviewField("standardReview.areaHa", "项目识别-申报面积", format(facts.areaHa()) + "公顷", "pass"));
        if (facts.capacityMw() != null) fields.add(reviewField("standardReview.capacityMw", "项目识别-装机容量", format(facts.capacityMw()) + "MW", "pass"));
        if (facts.turbineCount() != null) fields.add(reviewField("standardReview.turbineCount", "项目识别-风机台数", facts.turbineCount() + "台", "pass"));
        if (facts.singleTurbineKw() != null) fields.add(reviewField("standardReview.singleTurbineKw", "项目识别-单机容量", format(facts.singleTurbineKw()) + "kW", "pass"));
        if (facts.lengthKm() != null) {
            fields.add(reviewField("standardReview.lengthKm", "项目识别-线路/道路长度", format(facts.lengthKm()) + "km", "pass"));
        } else if (matches.stream().anyMatch(match -> match.getConclusion().contains("缺少线路/道路长度"))) {
            fields.add(reviewField("standardReview.lengthKm", "待补项目参数-线路/道路长度", "缺少，需补充线路或道路长度用于 hm²/km 指标测算", "warn"));
        }
        firstUsableMatch(matches, selected).ifPresent(item -> fields.add(reviewField("standardReview.primaryConclusion", "标准测算-首要结论", shortText(item.match().getConclusion(), 220), fieldStatus(item.match().getMatchStatus()))));
        return fields;
    }

    private String standardIndicatorSummary(List<LandUseStandardMatchEntity> matches, List<ScoredStandard> selected) {
        List<String> items = new ArrayList<>();
        for (int index = 0; index < matches.size() && index < selected.size() && items.size() < 6; index++) {
            LandUseStandardMatchEntity match = matches.get(index);
            String indicator = extractIndicatorText(match.getMatchedFields());
            String title = selected.get(index).standard().chapterTitle();
            if (!indicator.isBlank()) {
                items.add(title + "：" + indicator);
            } else if (!match.getMatchStatus().contains("通过")) {
                items.add(title + "：条文型/表格型指标，需先补建设规模、功能分区和对应长度面积后判定");
            }
        }
        return shortText(String.join("；", items), 520);
    }

    private String missingParameterSummary(List<LandUseStandardMatchEntity> matches) {
        List<String> items = new ArrayList<>();
        String text = matches.stream().map(LandUseStandardMatchEntity::getConclusion).reduce("", (a, b) -> a + " " + b);
        if (text.contains("缺少线路/道路长度")) items.add("线路/道路长度（km）：从初设说明、主要工程数量表、线路路径或用地统计表提取");
        if (text.contains("缺少申报总用地面积")) items.add("申报总用地面积：从勘测定界报告或用地申请表提取");
        if (text.contains("缺少装机容量")) items.add("装机容量（MW）：从核准批复或初步设计批复提取");
        if (text.contains("缺少风机台数")) items.add("风机台数：从设备清单或初步设计批复提取");
        return String.join("；", items);
    }

    private String extractIndicatorText(String matchedFields) {
        if (matchedFields == null || matchedFields.isBlank()) return "";
        for (String part : matchedFields.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("标准指标=")) return trimmed.substring("标准指标=".length());
        }
        return "";
    }

    private EditableField reviewField(String key, String label, String value, String status) {
        return new EditableField("step6", key, label, value, "标准库匹配", status, false, null);
    }

    private List<StepChecklistItem> reviewChecks(List<LandUseStandardMatchEntity> matches, List<ScoredStandard> selected) {
        List<StepChecklistItem> checks = new ArrayList<>();
        for (int index = 0; index < matches.size() && index < selected.size(); index++) {
            LandUseStandardMatchEntity match = matches.get(index);
            LandUseStandardDto standard = selected.get(index).standard();
            checks.add(new StepChecklistItem("standard-review-" + index, "标准复核：" + standard.chapterTitle(), checkStatus(match.getMatchStatus()), shortText(match.getConclusion(), 300)));
        }
        return checks;
    }

    private String reviewSummary(List<LandUseStandardMatchEntity> matches, List<ScoredStandard> selected) {
        long pass = matches.stream().filter(match -> match.getMatchStatus().contains("通过")).count();
        long attention = matches.stream().filter(match -> match.getMatchStatus().contains("需关注")).count();
        long missing = matches.stream().filter(match -> match.getMatchStatus().contains("缺参数")).count();
        String primary = firstUsableMatch(matches, selected)
                .map(item -> "首要标准“" + item.standard().chapterTitle() + "”为“" + item.match().getMatchStatus() + "”。")
                .orElse("尚未形成可用标准匹配。 ");
        return "标准库命中 " + matches.size() + " 条：可直接项目测算 " + pass + " 条、需复核适用性 " + attention + " 条、缺项目参数 " + missing + " 条。" + primary + "标准库指标、缺失参数和具体关注点已写入字段核对与下方校验条目。";
    }

    private String reviewSummaryStatus(List<LandUseStandardMatchEntity> matches) {
        if (matches.stream().anyMatch(match -> match.getMatchStatus().contains("未通过"))) return "block";
        if (matches.stream().anyMatch(match -> match.getMatchStatus().contains("需关注") || match.getMatchStatus().contains("缺参数"))) return "warn";
        return "pass";
    }

    private Optional<MatchedStandard> firstUsableMatch(List<LandUseStandardMatchEntity> matches, List<ScoredStandard> selected) {
        for (int index = 0; index < matches.size() && index < selected.size(); index++) {
            if (matches.get(index).getMatchStatus().contains("通过")) {
                return Optional.of(new MatchedStandard(matches.get(index), selected.get(index).standard()));
            }
        }
        return matches.isEmpty() || selected.isEmpty() ? Optional.empty() : Optional.of(new MatchedStandard(matches.get(0), selected.get(0).standard()));
    }

    private String fieldStatus(String matchStatus) {
        if (matchStatus.contains("通过")) return "pass";
        if (matchStatus.contains("未通过")) return "block";
        return "warn";
    }

    private String checkStatus(String matchStatus) {
        return fieldStatus(matchStatus);
    }

    private String shortText(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    private Optional<Calculation> calculate(StandardIndicator indicator, ProjectFacts facts) {
        return switch (indicator.basis()) {
            case PER_KM -> facts.lengthKm() == null ? Optional.empty() : Optional.of(new Calculation(indicator.valueHa().multiply(facts.lengthKm())));
            case PER_MW -> facts.capacityMw() == null ? Optional.empty() : Optional.of(new Calculation(indicator.valueHa().multiply(facts.capacityMw())));
            case PER_TURBINE -> facts.turbineCount() == null ? Optional.empty() : Optional.of(new Calculation(indicator.valueHa().multiply(BigDecimal.valueOf(facts.turbineCount()))));
            case ABSOLUTE -> Optional.of(new Calculation(indicator.valueHa()));
        };
    }

    private String missingFor(StandardIndicator indicator) {
        return switch (indicator.basis()) {
            case PER_KM -> "线路/道路长度（km）";
            case PER_MW -> "装机容量（MW）";
            case PER_TURBINE -> "风机台数";
            case ABSOLUTE -> "申报总用地面积";
        };
    }

    private Optional<StandardIndicator> selectIndicator(List<StandardIndicator> indicators, ProjectFacts facts) {
        if (indicators.isEmpty()) {
            return Optional.empty();
        }
        if (facts.turbineCount() != null) {
            Optional<StandardIndicator> perTurbine = indicators.stream()
                    .filter(item -> item.basis() == IndicatorBasis.PER_TURBINE)
                    .min(Comparator.comparing(item -> turbineDistance(item.capacityKw(), facts.singleTurbineKw())));
            if (perTurbine.isPresent()) return perTurbine;
        }
        if (facts.lengthKm() != null) {
            Optional<StandardIndicator> perKm = indicators.stream().filter(item -> item.basis() == IndicatorBasis.PER_KM).findFirst();
            if (perKm.isPresent()) return perKm;
        }
        if (facts.capacityMw() != null) {
            Optional<StandardIndicator> perMw = indicators.stream().filter(item -> item.basis() == IndicatorBasis.PER_MW).findFirst();
            if (perMw.isPresent()) return perMw;
        }
        return indicators.stream().filter(item -> item.basis() == IndicatorBasis.ABSOLUTE).findFirst().or(() -> indicators.stream().findFirst());
    }

    private BigDecimal turbineDistance(BigDecimal indicatorKw, BigDecimal actualKw) {
        if (indicatorKw == null || actualKw == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff = indicatorKw.subtract(actualKw);
        if (diff.compareTo(BigDecimal.ZERO) >= 0) {
            return diff;
        }
        return diff.abs().add(BigDecimal.valueOf(100000));
    }

    private List<StandardIndicator> extractIndicators(LandUseStandardDto standard) {
        String text = standard.chapterTitle() + "\n" + standard.content();
        List<StandardIndicator> indicators = new ArrayList<>();
        addDirectIndicators(indicators, text);
        addPerKmTableIndicator(indicators, text);
        addWindTurbineIndicators(indicators, text);
        addAbsoluteHaIndicator(indicators, text);
        return indicators;
    }

    private void addDirectIndicators(List<StandardIndicator> indicators, String text) {
        Matcher perKm = DIRECT_PER_KM_PATTERN.matcher(text);
        while (perKm.find()) {
            BigDecimal value = decimal(perKm.group(1));
            if (value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(BigDecimal.valueOf(40)) <= 0) {
                indicators.add(new StandardIndicator(value, IndicatorBasis.PER_KM, value + " 公顷/km", null));
            }
        }
        Matcher perMw = DIRECT_PER_MW_PATTERN.matcher(text);
        while (perMw.find()) {
            BigDecimal value = decimal(perMw.group(1));
            if (value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(BigDecimal.valueOf(10)) <= 0) {
                indicators.add(new StandardIndicator(value, IndicatorBasis.PER_MW, value + " 公顷/MW", null));
            }
        }
    }

    private void addPerKmTableIndicator(List<StandardIndicator> indicators, String text) {
        if (!containsPerKmUnit(text)) {
            return;
        }
        List<String> lines = text.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        BigDecimal max = null;
        for (int index = 1; index < lines.size(); index++) {
            boolean valueAfterWidth = isPlainNumber(lines.get(index - 1)) && isPlainNumber(lines.get(index)) && lines.get(index).contains(".");
            boolean valueAfterCategory = index >= 2
                    && isPlainNumber(lines.get(index - 2))
                    && !isPlainNumber(lines.get(index - 1))
                    && isPlainNumber(lines.get(index))
                    && lines.get(index).contains(".")
                    && decimal(lines.get(index)).compareTo(BigDecimal.valueOf(2)) <= 0;
            if (valueAfterWidth || valueAfterCategory) {
                BigDecimal value = decimal(lines.get(index));
                if (value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(BigDecimal.valueOf(30)) <= 0 && (max == null || value.compareTo(max) > 0)) {
                    max = value;
                }
            }
        }
        if (max != null) {
            indicators.add(new StandardIndicator(max, IndicatorBasis.PER_KM, max + " 公顷/km（按表内可识别最大指标）", null));
        }
    }

    private void addWindTurbineIndicators(List<StandardIndicator> indicators, String text) {
        if (!text.contains("m²/台") && !text.contains("平方米/台")) {
            return;
        }
        List<String> lines = text.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        for (int index = 0; index + 2 < lines.size(); index++) {
            if (!isPlainNumber(lines.get(index))) {
                continue;
            }
            BigDecimal capacityKw = decimal(lines.get(index));
            if (capacityKw.compareTo(BigDecimal.valueOf(100)) < 0 || capacityKw.compareTo(BigDecimal.valueOf(10000)) > 0) {
                continue;
            }
            for (int offset = 1; offset <= 3 && index + offset < lines.size(); offset++) {
                if (!isPlainNumber(lines.get(index + offset))) {
                    continue;
                }
                BigDecimal squareMeters = decimal(lines.get(index + offset));
                if (squareMeters.compareTo(BigDecimal.valueOf(10)) >= 0 && squareMeters.compareTo(BigDecimal.valueOf(20000)) <= 0) {
                    BigDecimal haPerTurbine = squareMeters.divide(BigDecimal.valueOf(10000), 6, RoundingMode.HALF_UP);
                    indicators.add(new StandardIndicator(haPerTurbine, IndicatorBasis.PER_TURBINE, squareMeters.stripTrailingZeros().toPlainString() + " m²/台（单机容量 " + format(capacityKw) + " kW 档）", capacityKw));
                    break;
                }
            }
        }
    }

    private void addAbsoluteHaIndicator(List<StandardIndicator> indicators, String text) {
        if (!text.contains("用地指标") || containsPerKmUnit(text) || text.contains("m²/台")) {
            return;
        }
        BigDecimal max = null;
        Matcher matcher = AREA_PATTERN.matcher(text);
        while (matcher.find()) {
            BigDecimal value = areaToHa(matcher.group(1), matcher.group(2));
            if (value.compareTo(BigDecimal.ZERO) > 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0 && (max == null || value.compareTo(max) > 0)) {
                max = value;
            }
        }
        if (max != null) {
            indicators.add(new StandardIndicator(max, IndicatorBasis.ABSOLUTE, max + " 公顷（条文内可识别面积上限）", null));
        }
    }

    private ProjectFacts collectFacts(ProjectRecordEntity project, WorkspaceState workspace) {
        StringBuilder text = new StringBuilder(project.getProjectName()).append(' ')
                .append(project.getProjectTypeLabel()).append(' ')
                .append(project.getOwner() == null ? "" : project.getOwner()).append(' ')
                .append(project.getLocation() == null ? "" : project.getLocation()).append(' ');
        if (workspace != null && workspace.steps() != null) {
            workspace.steps().values().forEach(step -> {
                if (step.fields() != null) {
                    step.fields().forEach(field -> text.append(field.label()).append('=').append(field.value()).append(' '));
                }
                if (step.analyses() != null) {
                    step.analyses().forEach(analysis -> {
                        text.append(analysis.fileName()).append(' ').append(analysis.detectedDocumentType()).append(' ').append(analysis.textPreview()).append(' ');
                        analysis.extractedFields().forEach(field -> text.append(field.label()).append('=').append(field.value()).append(' '));
                    });
                }
            });
        }
        String haystack = text.toString();
        BigDecimal areaHa = firstArea(haystack);
        Integer turbineCount = firstTurbineCount(haystack);
        BigDecimal singleTurbineKw = firstSingleTurbineKw(haystack);
        BigDecimal capacityMw = firstCapacityMw(haystack);
        if (singleTurbineKw == null && capacityMw != null && turbineCount != null && turbineCount > 0) {
            singleTurbineKw = capacityMw.multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(turbineCount), 2, RoundingMode.HALF_UP);
        }
        if (capacityMw == null && singleTurbineKw != null && turbineCount != null) {
            capacityMw = singleTurbineKw.multiply(BigDecimal.valueOf(turbineCount)).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        }
        return new ProjectFacts(areaHa, capacityMw, firstLengthKm(haystack), turbineCount, singleTurbineKw);
    }

    private BigDecimal firstArea(String text) {
        Matcher matcher = AREA_PATTERN.matcher(text);
        while (matcher.find()) {
            String start = text.substring(Math.max(0, matcher.start() - 20), matcher.start());
            if (start.contains("违法") || start.contains("农用地") || start.contains("集体") || start.contains("国有")) {
                continue;
            }
            return areaToHa(matcher.group(1), matcher.group(2));
        }
        matcher = AREA_PATTERN.matcher(text);
        return matcher.find() ? areaToHa(matcher.group(1), matcher.group(2)) : null;
    }

    private BigDecimal firstCapacityMw(String text) {
        Matcher matcher = CAPACITY_PATTERN.matcher(text);
        while (matcher.find()) {
            String prefix = text.substring(Math.max(0, matcher.start() - 16), matcher.start());
            if (prefix.contains("单机") || prefix.contains("单台") || prefix.contains("风机容量")) {
                continue;
            }
            return capacityToMw(matcher.group(1), matcher.group(2));
        }
        return null;
    }

    private BigDecimal firstLengthKm(String text) {
        Matcher matcher = LENGTH_PATTERN.matcher(text);
        return matcher.find() ? decimal(matcher.group(1)) : null;
    }

    private Integer firstTurbineCount(String text) {
        Matcher matcher = TURBINE_COUNT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
        return Integer.parseInt(value);
    }

    private BigDecimal firstSingleTurbineKw(String text) {
        Matcher matcher = SINGLE_TURBINE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        BigDecimal value = decimal(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        if (unit.contains("mw") || unit.contains("兆瓦")) {
            return value.multiply(BigDecimal.valueOf(1000));
        }
        return value;
    }

    private BigDecimal areaToHa(String value, String unit) {
        BigDecimal number = decimal(value);
        String normalized = unit.toLowerCase(Locale.ROOT);
        if (normalized.contains("亩")) {
            return number.divide(BigDecimal.valueOf(15), 6, RoundingMode.HALF_UP);
        }
        if (normalized.contains("平方米") || normalized.contains("㎡")) {
            return number.divide(BigDecimal.valueOf(10000), 6, RoundingMode.HALF_UP);
        }
        return number;
    }

    private BigDecimal capacityToMw(String value, String unit) {
        BigDecimal number = decimal(value);
        String normalized = unit.toLowerCase(Locale.ROOT);
        if (normalized.contains("万千瓦")) {
            return number.multiply(BigDecimal.TEN);
        }
        if (normalized.contains("千瓦") || normalized.contains("kw")) {
            return number.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
        }
        return number;
    }

    private boolean containsPerKmUnit(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("hm²/km") || normalized.contains("hm2/km") || normalized.contains("公顷/km") || normalized.contains("公顷/公里");
    }

    private boolean containsPerMwUnit(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("hm²/mw") || normalized.contains("hm2/mw") || normalized.contains("公顷/mw") || normalized.contains("公顷/兆瓦");
    }

    private boolean isPlainNumber(String value) {
        return value.matches("[0-9]+(?:\\.[0-9]+)?");
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value.trim());
    }

    private String format(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private LandUseStandardMatchDto toDto(LandUseStandardMatchEntity entity) {
        LandUseStandardDto standard = landUseStandardService.toDto(landUseStandardService.getEntity(entity.getStandardId()));
        return new LandUseStandardMatchDto(
                entity.getId(),
                entity.getProjectId(),
                entity.getStandardId(),
                entity.getMatchStatus(),
                entity.getMatchedFields(),
                entity.getConclusion(),
                entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString(),
                standard
        );
    }

    private enum IndicatorBasis {
        PER_KM, PER_MW, PER_TURBINE, ABSOLUTE
    }

    private record ProjectFacts(BigDecimal areaHa, BigDecimal capacityMw, BigDecimal lengthKm, Integer turbineCount, BigDecimal singleTurbineKw) {
        String summary(StandardIndicator indicator) {
            StringJoiner joiner = new StringJoiner("; ");
            if (areaHa != null) joiner.add("申报面积=" + areaHa.stripTrailingZeros().toPlainString() + "公顷");
            if (capacityMw != null) joiner.add("装机容量=" + capacityMw.stripTrailingZeros().toPlainString() + "MW");
            if (lengthKm != null) joiner.add("线路/道路长度=" + lengthKm.stripTrailingZeros().toPlainString() + "km");
            if (turbineCount != null) joiner.add("风机台数=" + turbineCount + "台");
            if (singleTurbineKw != null) joiner.add("单机容量=" + singleTurbineKw.stripTrailingZeros().toPlainString() + "kW");
            if (indicator != null) joiner.add("标准指标=" + indicator.description());
            String result = joiner.toString();
            return result.isBlank() ? "暂未从项目材料识别到可测算参数" : result;
        }
    }

    private record StandardIndicator(BigDecimal valueHa, IndicatorBasis basis, String description, BigDecimal capacityKw) {
    }

    private record ScoredStandard(LandUseStandardDto standard, int score) {
    }

    private record MatchedStandard(LandUseStandardMatchEntity match, LandUseStandardDto standard) {
    }

    private record Calculation(BigDecimal limitHa) {
    }

    private record StandardEvaluation(String status, String message, ProjectFacts facts, StandardIndicator indicator) {
        static StandardEvaluation pass(ProjectFacts facts, String message, StandardIndicator indicator) {
            return new StandardEvaluation("通过", message, facts, indicator);
        }

        static StandardEvaluation attention(ProjectFacts facts, String message, StandardIndicator indicator) {
            return new StandardEvaluation("需关注", message, facts, indicator);
        }

        static StandardEvaluation missing(ProjectFacts facts, String message, StandardIndicator indicator) {
            return new StandardEvaluation("缺参数", message, facts, indicator);
        }

        String matchedFields() {
            return facts.summary(indicator);
        }

        String conclusion(ProjectRecordEntity project, LandUseStandardDto standard) {
            return "项目“" + project.getProjectName() + "”已匹配“" + standard.chapterTitle() + "”。" + message;
        }
    }
}