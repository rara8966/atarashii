package com.atarashii.policyreport.service;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用 Apache POI 直接读取 .docx 内的 &lt;w:tbl&gt; 表格元素，按行列结构化输出。
 * 同时记录每个表前面的章节标题和表号标题，便于落库到 StandardTableEntity。
 *
 * 处理要点：
 *  1. 合并单元格：横向 gridSpan 展开为重复值；纵向 vMerge 用上一行同列值填充。
 *  2. 表号识别：扫描每个 &lt;w:tbl&gt; 前面最近的段落，匹配 "表N.M.K" / "表N-K" 等格式。
 *  3. 章节追踪：维护当前所在的"第X篇/章/节"路径，作为每个表的 chapter 字段。
 *  4. 表头识别：取第一行作为 headers；如果第一行的某些列在原始 XML 标记为 tblHeader，
 *     或第二行结构上是"参数项详细说明"，则把多行作为多级表头。简化版只取第一行。
 */
@Component
public class StandardTableParser {

    private static final Pattern TABLE_CODE = Pattern.compile("(表\\s*\\d+(?:\\.\\d+)*(?:-\\d+)?)");
    private static final Pattern CHAPTER = Pattern.compile("^(第[一二三四五六七八九十百千]+[篇章节])");
    private static final Pattern UNIT = Pattern.compile("[\\(（]\\s*(hm[²2]/?[a-zA-Z²]*|m²/[a-zA-Z]+|km|m|m²|km²)\\s*[\\)）]");

    public List<ParsedTable> parse(InputStream docxStream) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(docxStream)) {
            return parse(doc);
        }
    }

    public List<ParsedTable> parse(XWPFDocument doc) {
        List<ParsedTable> result = new ArrayList<>();
        String currentChapter = "";
        String lastTitle = "";  // 最近一个非空段落，作为表的标题候选

        for (IBodyElement el : doc.getBodyElements()) {
            if (el instanceof XWPFParagraph p) {
                String text = p.getText() == null ? "" : p.getText().trim();
                if (text.isEmpty()) continue;
                Matcher chapMatch = CHAPTER.matcher(text);
                if (chapMatch.find()) {
                    // 章节切换；保留章节层级路径（简化为合并最近 3 段）
                    currentChapter = mergeChapter(currentChapter, text);
                }
                lastTitle = text;
            } else if (el instanceof XWPFTable tbl) {
                ParsedTable parsed = parseSingleTable(tbl, lastTitle, currentChapter);
                if (parsed != null) result.add(parsed);
                lastTitle = "";  // 用完即清，避免下一个表错误复用
            }
        }
        return result;
    }

    private ParsedTable parseSingleTable(XWPFTable tbl, String titleHint, String chapter) {
        List<XWPFTableRow> rows = tbl.getRows();
        if (rows == null || rows.isEmpty()) return null;

        // 计算最大列数（用于对齐合并单元格）
        int maxCols = 0;
        for (XWPFTableRow r : rows) {
            int cnt = 0;
            for (XWPFTableCell c : r.getTableCells()) cnt += getGridSpan(c);
            if (cnt > maxCols) maxCols = cnt;
        }
        if (maxCols == 0) return null;

        // 构建 grid：[rowIdx][colIdx] -> cellText（处理 vMerge：把"继续"标记的列继承上一行）
        String[][] grid = new String[rows.size()][maxCols];
        for (int r = 0; r < rows.size(); r++) {
            List<XWPFTableCell> cells = rows.get(r).getTableCells();
            int col = 0;
            for (XWPFTableCell c : cells) {
                int span = getGridSpan(c);
                String text = cellText(c);
                String vMerge = getVMerge(c);
                if ("continue".equalsIgnoreCase(vMerge)) {
                    // 纵向合并的继续行：用上一行同列的值
                    for (int s = 0; s < span && col + s < maxCols; s++) {
                        int targetCol = col + s;
                        String inherited = r > 0 ? grid[r - 1][targetCol] : "";
                        grid[r][targetCol] = inherited == null ? "" : inherited;
                    }
                } else {
                    // 横向合并：把同样的值填充到 span 列
                    for (int s = 0; s < span && col + s < maxCols; s++) {
                        grid[r][col + s] = text;
                    }
                }
                col += span;
                if (col >= maxCols) break;
            }
            // 未触及的位置填空字符串
            for (int x = 0; x < maxCols; x++) if (grid[r][x] == null) grid[r][x] = "";
        }

        // 提取表号/表名/单位
        String code = "";
        String title = titleHint;
        String unit = "";
        if (titleHint != null) {
            Matcher m = TABLE_CODE.matcher(titleHint);
            if (m.find()) code = m.group(1).replaceAll("\\s+", "");
            Matcher u = UNIT.matcher(titleHint);
            if (u.find()) unit = u.group(1);
        }

        // 拆 headers vs rows：第一行视为表头
        List<List<String>> headers = new ArrayList<>();
        List<List<String>> data = new ArrayList<>();
        headers.add(List.of(grid[0]));
        for (int r = 1; r < grid.length; r++) {
            data.add(List.of(grid[r]));
        }

        ParsedTable pt = new ParsedTable();
        pt.tableCode = code;
        pt.tableTitle = title == null ? "" : title;
        pt.chapter = chapter == null ? "" : chapter;
        pt.unit = unit;
        pt.headers = headers;
        pt.rows = data;
        pt.maxCols = maxCols;
        return pt;
    }

    private int getGridSpan(XWPFTableCell cell) {
        CTTcPr tcPr = cell.getCTTc().getTcPr();
        if (tcPr != null && tcPr.getGridSpan() != null) {
            try { return tcPr.getGridSpan().getVal().intValue(); } catch (Exception ignored) {}
        }
        return 1;
    }

    private String getVMerge(XWPFTableCell cell) {
        CTTcPr tcPr = cell.getCTTc().getTcPr();
        if (tcPr != null && tcPr.getVMerge() != null) {
            // null val 表示 "continue"（合并继续行）
            if (tcPr.getVMerge().getVal() == null) return "continue";
            return tcPr.getVMerge().getVal().toString();
        }
        return null;
    }

    private String cellText(XWPFTableCell cell) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : cell.getParagraphs()) {
            String t = p.getText();
            if (t != null && !t.isBlank()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(t.trim());
            }
        }
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private String mergeChapter(String current, String newSegment) {
        if (newSegment.startsWith("第") && newSegment.contains("篇")) return newSegment;
        if (current.isEmpty()) return newSegment;
        // 替换同层级（章 / 节）部分
        String[] parts = current.split(" / ");
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (sameLevel(part, newSegment)) break;
            kept.add(part);
        }
        kept.add(newSegment);
        return String.join(" / ", kept);
    }

    private boolean sameLevel(String a, String b) {
        String aLvl = chapterLevel(a);
        String bLvl = chapterLevel(b);
        return !aLvl.isEmpty() && aLvl.equals(bLvl);
    }

    private String chapterLevel(String s) {
        if (s.contains("篇")) return "篇";
        if (s.contains("章")) return "章";
        if (s.contains("节")) return "节";
        return "";
    }

    public static class ParsedTable {
        public String tableCode = "";
        public String tableTitle = "";
        public String chapter = "";
        public String unit = "";
        public List<List<String>> headers;
        public List<List<String>> rows;
        public int maxCols;
    }
}
