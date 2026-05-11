package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.StandardTableEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 调 DeepSeek 给二维表生成业务标注预填值：
 * - 哪几列是"查询键"（用来根据项目参数定位行）
 * - 哪几列是"标准值"（项目实际值 vs 这一列做合规比对）
 * - 该表适用于哪些情形（关联 SituationCatalog 的 group + option）
 * 输出 JSON 写入 standard_tables.annotation_json，aiPrelabeled=true，annotated=false（等人工确认）。
 */
@Service
public class TableAnnotationAiService {

    private final DeepSeekClient deepSeekClient;
    private final SituationCatalog situationCatalog;
    private final ObjectMapper mapper = new ObjectMapper();

    public TableAnnotationAiService(DeepSeekClient deepSeekClient, SituationCatalog situationCatalog) {
        this.deepSeekClient = deepSeekClient;
        this.situationCatalog = situationCatalog;
    }

    public String generateAnnotationJson(StandardTableEntity table, String deepseekApiKey, String deepseekModel) {
        if (deepseekApiKey == null || deepseekApiKey.isBlank()) {
            throw new IllegalArgumentException("缺少 DeepSeek API Key");
        }

        List<List<String>> headers = parseGrid(table.getHeadersJson());
        List<List<String>> rows = parseGrid(table.getRowsJson());
        if (headers.isEmpty() || rows.isEmpty()) {
            throw new IllegalStateException("表内容为空，无法预标注");
        }

        // 取表头第 1 行作为列名（最常见情况）
        List<String> columnNames = headers.get(0);
        int colCount = columnNames.size();

        StringBuilder headerBlock = new StringBuilder();
        for (int i = 0; i < colCount; i++) {
            headerBlock.append("  列").append(i).append(": ").append(columnNames.get(i)).append("\n");
        }

        // 抽样最多前 5 行数据
        StringBuilder rowBlock = new StringBuilder();
        int sample = Math.min(5, rows.size());
        for (int r = 0; r < sample; r++) {
            rowBlock.append("  行").append(r).append(": ");
            List<String> row = rows.get(r);
            for (int c = 0; c < Math.min(colCount, row.size()); c++) {
                if (c > 0) rowBlock.append(" | ");
                rowBlock.append(columnNames.get(c)).append("=").append(row.get(c));
            }
            rowBlock.append("\n");
        }

        // 拼所有情形选项（让 AI 选适用情形）
        StringBuilder situationBlock = new StringBuilder();
        for (Map.Entry<Integer, List<SituationCatalog.CaseGroup>> entry : situationCatalog.getAllGroups().entrySet()) {
            for (SituationCatalog.CaseGroup g : entry.getValue()) {
                situationBlock.append("  [第").append(entry.getKey()).append("步]")
                              .append(g.id()).append(" - ").append(g.title()).append("：\n");
                for (SituationCatalog.CaseOption o : g.options()) {
                    situationBlock.append("    选项 ").append(o.value()).append(") ").append(o.label()).append("\n");
                }
            }
        }

        String prompt = "你是建设用地报批审查报告专家。请为以下\"建设用地指标\"表格做业务标注。\n\n"
                + "表格信息：\n"
                + "  项目类型：" + (table.getProjectTypeLabel() == null ? "" : table.getProjectTypeLabel()) + "\n"
                + "  章节：" + (table.getChapter() == null ? "" : table.getChapter()) + "\n"
                + "  表号：" + table.getTableCode() + "\n"
                + "  表名：" + table.getTableTitle() + "\n"
                + "  单位：" + (table.getUnit() == null ? "" : table.getUnit()) + "\n\n"
                + "表头列：\n" + headerBlock + "\n"
                + "样本行（前 " + sample + " 行）：\n" + rowBlock + "\n"
                + "**重要的标注思路**：绝大多数建设用地指标表都是「行查表」结构：\n"
                + "  - 第 1~2 列是\"查询键\"（如「机组容量」「供水方式」「地形类型」），用项目参数命中某一行；\n"
                + "  - 命中的行里，剩余所有列都是\"标准值\"，每列代表一个不同的指标维度（如「直流供水管线」「桥梁长度」「用地指标」）；\n"
                + "  - VerdictEngine 会用项目实际值跟这些列分别比对。\n\n"
                + "所以标注时**默认应当把所有非查询键的列都纳入 valueCols**，除非某列明显不是指标（如纯描述文字、单位说明列、空列）。\n"
                + "如果某列就是「单位」(m、hm²、km 等)，可以不归入 valueCols（它配合主键列做语义说明，不参与数值比对）。\n\n"
                + "请输出：\n"
                + "1. queryKeys：查询键列，列索引(col) + 语义名(name)。一般只有 1~2 列。\n"
                + "2. valueCols：标准值列，列索引、语义名、semantic（upper_bound 上限 / lower_bound 下限 / exact 精确值，**用地指标默认 upper_bound**）。请尽可能完整覆盖所有指标列。\n"
                + "3. applicableSituations：该表适用于哪些项目情形？参照下方情形选项，给出 stepNo + groupId + value。若没有明显关联，输出 []。\n"
                + "4. notes：可选，用一两句说明表的用途。\n\n"
                + "情形选项（可选适用范围）：\n" + situationBlock + "\n"
                + "输出严格 JSON（不要 markdown、不要解释），结构示例：\n"
                + "{\n"
                + "  \"queryKeys\":[{\"col\":0,\"name\":\"机组容量\"}],\n"
                + "  \"valueCols\":[{\"col\":2,\"name\":\"直流供排水管线\",\"semantic\":\"upper_bound\"}],\n"
                + "  \"applicableSituations\":[{\"stepNo\":6,\"groupId\":\"caseSupply\",\"value\":\"1\"}],\n"
                + "  \"notes\":\"...\"\n"
                + "}\n\n"
                + "请直接输出 JSON：";

        DeepSeekClient.GenerationResult result = deepSeekClient.generate(prompt, deepseekApiKey, deepseekModel);
        if (!result.usedModel()) {
            throw new IllegalStateException("DeepSeek 调用失败：" + result.text());
        }

        String json = extractJsonObject(result.text());
        if (json == null) {
            throw new IllegalStateException("AI 返回结果无法解析：" + truncate(result.text(), 200));
        }

        // 给 JSON 加 sourceTag 表明这是 AI 产出的
        try {
            JsonNode node = mapper.readTree(json);
            ObjectNode out = node.isObject() ? (ObjectNode) node : mapper.createObjectNode();
            out.put("source", "ai");
            out.put("model", result.model());
            return mapper.writeValueAsString(out);
        } catch (Exception e) {
            // 解析失败就原样返回（前端仍能看到 AI 文本，便于排错）
            return json;
        }
    }

    private List<List<String>> parseGrid(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return mapper.readValue(json, new TypeReference<List<List<String>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String extractJsonObject(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return text.substring(start, end + 1);
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
