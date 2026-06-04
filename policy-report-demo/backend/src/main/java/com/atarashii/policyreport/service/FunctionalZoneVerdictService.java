package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.FileAnalysisEntity;
import com.atarashii.policyreport.persistence.FileAnalysisRepository;
import com.atarashii.policyreport.persistence.ProjectFileEntity;
import com.atarashii.policyreport.persistence.ProjectFileRepository;
import com.atarashii.policyreport.persistence.ProjectRecordEntity;
import com.atarashii.policyreport.persistence.ProjectRecordRepository;
import com.atarashii.policyreport.persistence.StandardTableEntity;
import com.atarashii.policyreport.persistence.StandardTableRepository;
import com.atarashii.policyreport.service.verdict.ZoneVerdictApi;
import com.atarashii.policyreport.service.verdict.ZoneVerdictApi.IndicatorVerdict;
import com.atarashii.policyreport.service.verdict.ZoneVerdictApi.TableVerdict;
import com.atarashii.policyreport.service.verdict.ZoneVerdictApi.ZoneVerdict;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按"功能区"查表 + 数值比对的合规判定引擎。
 *
 * 输入：项目 ID。
 * 输出：按功能区分组的合规结论 List<ZoneVerdict>，每个功能区包含：
 *   - 命中的标准表 + 行
 *   - 该行 valueCols 与项目字段的 一一比对结果
 *
 * 算法：
 *   1. 拿项目所有 DONE 文件的 extractedFieldsJson，解析出 (label, value, functionalZone) 三元组
 *   2. 按 functionalZone 分组项目字段
 *   3. 对每个 functionalZone：
 *      a. 找该项目类型下、annotation_json.functionalZone == 此功能区 的 standard_tables
 *      b. 对每张表：用 queryKeys 匹配行；对命中行的 valueCols，找项目字段做数值比对
 *      c. 没有标注的表（annotated=false）跳过
 *
 * 数值比对：value 中第一个数字串作为数值；semantic=upper_bound 表示项目实际 ≤ 标准 = 通过。
 */
@Service
@Profile("!client")  // 服务器/本地构建启用真算法；客户端构建用 RemoteZoneVerdictService 取代
public class FunctionalZoneVerdictService implements ZoneVerdictApi {

    private final ProjectRecordRepository projectRepository;
    private final ProjectFileRepository fileRepository;
    private final FileAnalysisRepository analysisRepository;
    private final StandardTableRepository tableRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final Pattern NUMBER = Pattern.compile("([-+]?\\d+(?:\\.\\d+)?)");

    public FunctionalZoneVerdictService(ProjectRecordRepository projectRepository,
                                        ProjectFileRepository fileRepository,
                                        FileAnalysisRepository analysisRepository,
                                        StandardTableRepository tableRepository) {
        this.projectRepository = projectRepository;
        this.fileRepository = fileRepository;
        this.analysisRepository = analysisRepository;
        this.tableRepository = tableRepository;
    }

    @Override
    public List<ZoneVerdict> verdictForProject(String projectId) {
        ProjectRecordEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));
        String projectType = project.getProjectType();
        if (projectType == null || projectType.isBlank()) return List.of();

        // 1. 收集项目所有字段
        List<ProjectField> allFields = collectProjectFields(projectId);

        // 2. 按 functionalZone 分组（空 zone 单独一组叫 "(通用)" ）
        Map<String, List<ProjectField>> fieldsByZone = new LinkedHashMap<>();
        for (ProjectField f : allFields) {
            String key = (f.functionalZone == null || f.functionalZone.isBlank()) ? "(通用)" : f.functionalZone;
            fieldsByZone.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }

        // 3. 拿该项目类型下所有已标注的表，按 functionalZone 分组
        List<StandardTableEntity> tables = tableRepository.findAllByProjectTypeOrderBySortOrderAscTableCodeAsc(projectType);
        Map<String, List<StandardTableEntity>> tablesByZone = new LinkedHashMap<>();
        for (StandardTableEntity t : tables) {
            if (!t.isAnnotated()) continue;
            String zone = extractZone(t.getAnnotationJson());
            String key = (zone == null || zone.isBlank()) ? "(通用)" : zone;
            tablesByZone.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        // 4. 合并 zone 集合：项目字段的 zone + 标准表的 zone
        Set<String> allZones = new LinkedHashSet<>();
        allZones.addAll(tablesByZone.keySet());
        allZones.addAll(fieldsByZone.keySet());

        List<ZoneVerdict> result = new ArrayList<>();
        for (String zone : allZones) {
            List<StandardTableEntity> zoneTables = tablesByZone.getOrDefault(zone, List.of());
            List<ProjectField> zoneFields = fieldsByZone.getOrDefault(zone, List.of());
            // 通用 zone 的字段（如项目代码、文号）也可以参与任意 zone 的查询键匹配
            List<ProjectField> generalFields = "(通用)".equals(zone) ? List.of() : fieldsByZone.getOrDefault("(通用)", List.of());
            List<ProjectField> joinedFields = new ArrayList<>(zoneFields);
            joinedFields.addAll(generalFields);

            List<TableVerdict> tableVerdicts = new ArrayList<>();
            for (StandardTableEntity tbl : zoneTables) {
                TableVerdict tv = verdictForTable(tbl, joinedFields);
                if (tv != null) tableVerdicts.add(tv);
            }
            result.add(new ZoneVerdict(zone, tableVerdicts.size(), zoneTables.size(), zoneFields.size(), tableVerdicts));
        }
        return result;
    }

    private TableVerdict verdictForTable(StandardTableEntity table, List<ProjectField> projectFields) {
        JsonNode ann;
        try { ann = mapper.readTree(table.getAnnotationJson()); }
        catch (Exception e) { return null; }

        List<KeyCol> queryKeys = parseKeyCols(ann.get("queryKeys"));
        List<ValueCol> valueCols = parseValueCols(ann.get("valueCols"));
        if (queryKeys.isEmpty() && valueCols.isEmpty()) return null;

        List<List<String>> headers = parseGrid(table.getHeadersJson());
        List<List<String>> rows = parseGrid(table.getRowsJson());
        if (rows.isEmpty()) return null;

        // 用项目字段匹配查询键 → 找命中行
        // 每个 queryKey: 在项目字段中找 label 跟 keyCol.name 匹配的字段，再用 value 在 rows 第 keyCol.col 列里找
        Map<String, String> matchedKeyValues = new LinkedHashMap<>();
        int matchedRow = -1;
        if (queryKeys.isEmpty()) {
            // 无查询键的表（罕见）：默认用第一行
            matchedRow = 0;
        } else {
            // 对每个 queryKey 找项目字段中的"实际值"
            Map<Integer, String> keyColToActual = new LinkedHashMap<>();
            for (KeyCol k : queryKeys) {
                ProjectField pf = findFieldByLabel(projectFields, k.name);
                if (pf != null) keyColToActual.put(k.col, pf.value);
            }
            // 在 rows 中找：所有有实际值的 keyCol 都能匹配上的第一行
            for (int r = 0; r < rows.size(); r++) {
                List<String> row = rows.get(r);
                boolean ok = !keyColToActual.isEmpty();
                for (Map.Entry<Integer, String> e : keyColToActual.entrySet()) {
                    int col = e.getKey();
                    String actual = e.getValue();
                    String cell = col < row.size() ? row.get(col) : "";
                    if (!cellMatchesValue(cell, actual)) { ok = false; break; }
                }
                if (ok) { matchedRow = r; break; }
            }
            // 记录匹配到的键值（不论是否找到行，便于前端展示）
            for (KeyCol k : queryKeys) {
                String actual = keyColToActual.get(k.col);
                matchedKeyValues.put(k.name, actual == null ? "(项目未填)" : actual);
            }
        }

        // 对每个 valueCol 做比对
        List<IndicatorVerdict> indicators = new ArrayList<>();
        for (ValueCol vc : valueCols) {
            String standardCell = "";
            if (matchedRow >= 0 && matchedRow < rows.size()) {
                List<String> row = rows.get(matchedRow);
                if (vc.col < row.size()) standardCell = row.get(vc.col);
            }
            ProjectField pf = findFieldByLabel(projectFields, vc.name);
            String actual = pf == null ? "" : pf.value;

            String verdict;
            Double deltaPct = null;
            String note = "";
            if (matchedRow < 0) {
                verdict = "UNKNOWN";
                note = "未在表中匹配到行（项目未提供" + queryKeys.stream().map(k -> k.name).reduce((a, b) -> a + " / " + b).orElse("查询键") + "）";
            } else if (actual.isBlank()) {
                verdict = "UNKNOWN";
                note = "项目未申报该指标";
            } else if (standardCell.isBlank()) {
                verdict = "UNKNOWN";
                note = "标准表该单元格为空";
            } else {
                Double std = parseNumber(standardCell);
                Double act = parseNumber(actual);
                if (std == null || act == null) {
                    verdict = "WARN";
                    note = "无法做数值比对（含非数字）";
                } else {
                    switch (vc.semantic) {
                        case "lower_bound" -> {
                            if (act >= std) { verdict = "PASS"; }
                            else { verdict = "FAIL"; deltaPct = (std - act) / std * 100; note = "低于下限"; }
                        }
                        case "exact" -> {
                            double tolerance = Math.abs(std) * 0.01;
                            if (Math.abs(act - std) <= tolerance) verdict = "PASS";
                            else { verdict = "FAIL"; deltaPct = (act - std) / std * 100; note = "偏离标准值"; }
                        }
                        default -> { // upper_bound
                            if (act <= std) { verdict = "PASS"; }
                            else { verdict = "FAIL"; deltaPct = (act - std) / std * 100; note = "超标"; }
                        }
                    }
                }
            }
            indicators.add(new IndicatorVerdict(
                vc.name,
                standardCell,
                actual,
                vc.semantic,
                verdict,
                deltaPct == null ? null : Math.round(deltaPct * 10) / 10.0,
                note,
                pf == null ? null : pf.sourceFileId
            ));
        }

        return new TableVerdict(
            table.getId(),
            table.getTableCode(),
            table.getTableTitle(),
            matchedKeyValues,
            matchedRow,
            indicators
        );
    }

    // ── 辅助 ──

    private List<ProjectField> collectProjectFields(String projectId) {
        List<ProjectField> out = new ArrayList<>();
        List<ProjectFileEntity> files = fileRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId);
        for (ProjectFileEntity f : files) {
            if (!"DONE".equals(f.getAnalysisStatus())) continue;
            Optional<FileAnalysisEntity> opt = analysisRepository.findByFileId(f.getId());
            if (opt.isEmpty()) continue;
            String json = opt.get().getExtractedFieldsJson();
            if (json == null || json.isBlank()) continue;
            try {
                List<Map<String, Object>> raw = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> row : raw) {
                    Object labelObj = row.get("label");
                    Object valueObj = row.get("value");
                    if (labelObj == null || valueObj == null) continue;
                    String label = String.valueOf(labelObj).trim();
                    String value = String.valueOf(valueObj).trim();
                    if (label.isBlank() || value.isBlank()) continue;
                    String zone = "";
                    Object z = row.get("functionalZone");
                    if (z != null) zone = String.valueOf(z).trim();
                    out.add(new ProjectField(label, value, zone, f.getId(), f.getOriginalName()));
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    private String extractZone(String annotationJson) {
        if (annotationJson == null || annotationJson.isBlank()) return "";
        try {
            JsonNode node = mapper.readTree(annotationJson);
            JsonNode z = node.get("functionalZone");
            return z == null || z.isNull() ? "" : z.asText("");
        } catch (Exception e) { return ""; }
    }

    private List<List<String>> parseGrid(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try { return mapper.readValue(json, new TypeReference<List<List<String>>>() {}); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    private List<KeyCol> parseKeyCols(JsonNode node) {
        List<KeyCol> out = new ArrayList<>();
        if (node == null || !node.isArray()) return out;
        for (JsonNode el : node) {
            JsonNode c = el.get("col");
            JsonNode n = el.get("name");
            if (c == null || !c.isNumber()) continue;
            out.add(new KeyCol(c.asInt(), n == null ? "" : n.asText("")));
        }
        return out;
    }

    private List<ValueCol> parseValueCols(JsonNode node) {
        List<ValueCol> out = new ArrayList<>();
        if (node == null || !node.isArray()) return out;
        for (JsonNode el : node) {
            JsonNode c = el.get("col");
            JsonNode n = el.get("name");
            JsonNode s = el.get("semantic");
            if (c == null || !c.isNumber()) continue;
            out.add(new ValueCol(c.asInt(), n == null ? "" : n.asText(""), s == null ? "upper_bound" : s.asText("upper_bound")));
        }
        return out;
    }

    private ProjectField findFieldByLabel(List<ProjectField> fields, String name) {
        if (name == null || name.isBlank()) return null;
        String lname = name.toLowerCase();
        // 优先精确匹配
        for (ProjectField f : fields) {
            if (f.label.equalsIgnoreCase(name)) return f;
        }
        // 退化为子串匹配（项目字段 label 包含查询键名 或 反之）
        for (ProjectField f : fields) {
            String l = f.label.toLowerCase();
            if (l.contains(lname) || lname.contains(l)) return f;
        }
        return null;
    }

    private boolean cellMatchesValue(String cell, String value) {
        if (cell == null || value == null) return false;
        String c = cell.replaceAll("\\s+", "").toLowerCase();
        String v = value.replaceAll("\\s+", "").toLowerCase();
        if (c.isEmpty() || v.isEmpty()) return false;
        if (c.equals(v)) return true;

        // 组合数值（"2×5"、"2x20"、"2+1" 等）禁用 parseNumber 近似 —— 否则
        // parseNumber 只抓第一个数字，"2×5" 和 "2×20" 都被算成 2 而错配。
        boolean compositeC = isCompositeNumber(c);
        boolean compositeV = isCompositeNumber(v);
        if (compositeC || compositeV) {
            // 含 × 等符号的值必须完全字符串相等
            return c.equals(v);
        }

        // 范围值匹配（"1250～2000"、"≤1000"、"≥2500"、"<=N"、">=N"）
        RangeMatch range = parseRange(c);
        if (range != null) {
            Double vn = parseNumber(value);
            return vn != null && range.contains(vn);
        }
        // 反向：项目值是范围，标准是单数（少见但兼容）
        RangeMatch reverse = parseRange(v);
        if (reverse != null) {
            Double cn = parseNumber(cell);
            return cn != null && reverse.contains(cn);
        }

        // 纯数值容差近似（不允许子串匹配以避免 "2000" 命中 "200"）
        Double cn = parseNumber(cell);
        Double vn = parseNumber(value);
        if (cn != null && vn != null) {
            double diff = Math.abs(cn - vn);
            double base = Math.max(Math.abs(cn), Math.abs(vn));
            return base == 0 ? cn == 0 : (diff / base) < 0.001;
        }

        // 文字 token 模糊匹配（如"进场道路"/"扩展基础"）：仅在双方都是纯文本时允许
        return c.contains(v) || v.contains(c);
    }

    private boolean isCompositeNumber(String s) {
        // 数字×数字 / 数字+数字 / 数字/数字 等组合表达
        return s.matches(".*\\d\\s*[×x*+/]\\s*\\d.*");
    }

    private static final Pattern RANGE_LE = Pattern.compile("^[≤<]=?\\s*([0-9.]+)$");
    private static final Pattern RANGE_GE = Pattern.compile("^[≥>]=?\\s*([0-9.]+)$");
    private static final Pattern RANGE_INTERVAL = Pattern.compile("^([0-9.]+)\\s*[~～\\-]\\s*([0-9.]+)$");

    private RangeMatch parseRange(String s) {
        Matcher m;
        if ((m = RANGE_LE.matcher(s)).matches()) {
            try { return RangeMatch.upper(Double.parseDouble(m.group(1))); } catch (Exception ignored) {}
        }
        if ((m = RANGE_GE.matcher(s)).matches()) {
            try { return RangeMatch.lower(Double.parseDouble(m.group(1))); } catch (Exception ignored) {}
        }
        if ((m = RANGE_INTERVAL.matcher(s)).matches()) {
            try {
                return RangeMatch.interval(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static class RangeMatch {
        enum Kind { UPPER, LOWER, INTERVAL }
        Kind kind;
        double lower;
        double upper;
        static RangeMatch upper(double u) { RangeMatch r = new RangeMatch(); r.kind = Kind.UPPER; r.upper = u; return r; }
        static RangeMatch lower(double l) { RangeMatch r = new RangeMatch(); r.kind = Kind.LOWER; r.lower = l; return r; }
        static RangeMatch interval(double l, double u) { RangeMatch r = new RangeMatch(); r.kind = Kind.INTERVAL; r.lower = l; r.upper = u; return r; }
        boolean contains(double v) {
            return switch (kind) {
                case UPPER -> v <= upper;
                case LOWER -> v >= lower;
                case INTERVAL -> v >= lower && v <= upper;
            };
        }
    }

    private Double parseNumber(String s) {
        if (s == null) return null;
        Matcher m = NUMBER.matcher(s);
        if (!m.find()) return null;
        try { return new BigDecimal(m.group(1)).setScale(6, RoundingMode.HALF_UP).doubleValue(); }
        catch (Exception e) { return null; }
    }

    // ── 数据结构 ──

    private record KeyCol(int col, String name) {}
    private record ValueCol(int col, String name, String semantic) {}
    private record ProjectField(String label, String value, String functionalZone, String sourceFileId, String sourceFileName) {}

    // IndicatorVerdict / TableVerdict / ZoneVerdict 已迁至 ZoneVerdictApi 接口，
    // 以便客户端构建剔除本类后仍能反序列化服务器返回的判定结果。
}
