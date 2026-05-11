package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.StandardTableEntity;
import com.atarashii.policyreport.persistence.StandardTableRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标准库二维表的查询与导入入口。
 * 解析逻辑委托给 {@link StandardTableParser}，本类负责落库与级联清理。
 */
@Service
public class StandardTableService {

    private final StandardTableRepository repository;
    private final StandardTableParser parser;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * source/ 目录下的 "数字前缀" → typeKey 映射（与 ProjectTypeCatalog 内置 9+1 顺序对齐）。
     * 通过资源路径自动识别，避免在启动 seed 时依赖 project_types 表已就绪。
     */
    private static final Map<String, String> DIR_PREFIX_TO_TYPE = Map.of(
        "1", "power-station",
        "2", "wind-power",
        "3", "oil-gas",
        "4", "coal",
        "5", "railway",
        "6", "highway",
        "7", "airport",
        "8", "public-facility"
    );

    private static final Pattern DIR_PREFIX = Pattern.compile("/(\\d+)\\.[^/]*/");

    public StandardTableService(StandardTableRepository repository, StandardTableParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    /**
     * 启动后扫描 classpath:land-standards/source/**\/*.docx，对每个 docx 解析二维表格入库。
     * 幂等：同 (project_type, table_code) 已存在则更新内容；首次启动会全部插入。
     * 用 @Order(Ordered.LOWEST_PRECEDENCE)，保证在 ProjectTypeCatalog seed 之后跑。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    @Transactional
    public void seedTablesFromResources() {
        if (repository.count() > 0) return; // 全表非空就跳过，避免重复扫描
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath*:/land-standards/source/**/*.docx");
            for (Resource resource : resources) {
                String name = resource.getFilename();
                if (name == null) continue;
                try {
                    String url = resource.getURL().toString();
                    String typeKey = inferTypeFromPath(url);
                    if (typeKey == null) continue; // 无法识别的（如根目录的汇总 docx）跳过，由用户手动导入
                    try (InputStream in = resource.getInputStream()) {
                        importFromDocx(in, name, typeKey, "", false);
                    }
                } catch (Exception ignored) {
                    // 单个 docx 解析失败不影响其它
                }
            }
        } catch (Exception ignored) {
            // 启动 seed 失败不阻塞应用
        }
    }

    private String inferTypeFromPath(String url) {
        Matcher m = DIR_PREFIX.matcher(url.replace('\\', '/'));
        String last = null;
        while (m.find()) last = m.group(1);
        return last == null ? null : DIR_PREFIX_TO_TYPE.get(last);
    }

    public List<StandardTableEntity> listByType(String typeKey) {
        return repository.findAllByProjectTypeOrderBySortOrderAscTableCodeAsc(typeKey);
    }

    public long countByType(String typeKey) {
        return repository.countByProjectType(typeKey);
    }

    @Transactional
    public void clearByType(String typeKey) {
        repository.deleteAllByProjectType(typeKey);
    }

    @Transactional
    public void clearByProject(String projectId) {
        // 占位：standard_tables 当前不挂 project 维度，删除项目时不需要清理
    }

    /**
     * 从 docx 二进制流解析所有表格并落库。
     * @param replace true=先清空 typeKey 下旧表再写入；false=仅在表号 (project_type, table_code) 不存在时插入
     */
    @Transactional
    public int importFromDocx(InputStream docxStream, String sourceFile, String typeKey, String typeLabel, boolean replace) {
        return importFromDocx(docxStream, sourceFile, typeKey, typeLabel, replace, null);
    }

    /**
     * 同上，但可指定 includeTableCodes（仅保留这些表号；null/空表示全部）。
     * 用于"汇总 docx 里只挑几张表"场景，避免污染目标项目类型。
     */
    @Transactional
    public int importFromDocx(InputStream docxStream, String sourceFile, String typeKey, String typeLabel,
                              boolean replace, java.util.Set<String> includeTableCodes) {
        List<StandardTableParser.ParsedTable> parsed;
        try {
            parsed = parser.parse(docxStream);
        } catch (IOException e) {
            throw new IllegalStateException("docx 表格解析失败：" + e.getMessage(), e);
        }
        if (parsed.isEmpty()) return 0;

        if (replace) repository.deleteAllByProjectType(typeKey);

        int order = (int) repository.countByProjectType(typeKey);
        int written = 0;
        for (StandardTableParser.ParsedTable t : parsed) {
            // 没有表号的"无效表"跳过（防止 docx 里的 layout 用表）
            if (t.tableCode == null || t.tableCode.isBlank()) continue;
            // 行数过少（<2）通常是装饰表，跳过
            if (t.rows == null || t.rows.size() < 1) continue;
            // 指定了白名单时，跳过不在列表里的表号
            if (includeTableCodes != null && !includeTableCodes.isEmpty()
                    && !includeTableCodes.contains(t.tableCode)) continue;

            if (!replace) {
                // 幂等：同 typeKey + tableCode 已存在则覆盖更新而非插入新行
                var existing = repository.findByProjectTypeAndTableCode(typeKey, t.tableCode);
                if (existing.isPresent()) {
                    StandardTableEntity e = existing.get();
                    fill(e, t, typeKey, typeLabel, sourceFile, e.getSortOrder());
                    repository.save(e);
                    written++;
                    continue;
                }
            }

            StandardTableEntity e = new StandardTableEntity();
            fill(e, t, typeKey, typeLabel, sourceFile, order++);
            repository.save(e);
            written++;
        }
        return written;
    }

    /**
     * 从已有的本地 docx 文件 idempotent 解析表格（启动 seed 用）。
     */
    @Transactional
    public int importFromPath(Path docxPath, String typeKey, String typeLabel) {
        if (!Files.isRegularFile(docxPath)) return 0;
        try (InputStream in = Files.newInputStream(docxPath)) {
            return importFromDocx(in, docxPath.getFileName().toString(), typeKey, typeLabel, false);
        } catch (Exception e) {
            // 单个 docx 失败不应阻塞其他 docx 的 seed
            return 0;
        }
    }

    @Transactional
    public int importFromBytes(byte[] bytes, String sourceFile, String typeKey, String typeLabel, boolean replace) {
        return importFromBytes(bytes, sourceFile, typeKey, typeLabel, replace, null);
    }

    @Transactional
    public int importFromBytes(byte[] bytes, String sourceFile, String typeKey, String typeLabel,
                                boolean replace, java.util.Set<String> includeTableCodes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            return importFromDocx(in, sourceFile, typeKey, typeLabel, replace, includeTableCodes);
        } catch (IOException e) {
            throw new IllegalStateException("读取 docx 失败：" + e.getMessage(), e);
        }
    }

    /** 把解析结果填充到实体（公用代码，给 insert/update 共用）。 */
    private void fill(StandardTableEntity e, StandardTableParser.ParsedTable t,
                      String typeKey, String typeLabel, String sourceFile, int order) {
        e.setProjectType(typeKey);
        e.setProjectTypeLabel(typeLabel);
        e.setSourceFile(sourceFile);
        e.setChapter(t.chapter);
        e.setTableCode(t.tableCode);
        e.setTableTitle(t.tableTitle);
        e.setUnit(t.unit);
        e.setHeadersJson(writeJson(t.headers));
        e.setRowsJson(writeJson(t.rows));
        e.setSortOrder(order);
    }

    private String writeJson(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { return "[]"; }
    }

    /** 简化的 DTO（前端使用）。 */
    public Map<String, Object> toDto(StandardTableEntity e) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("projectType", e.getProjectType());
        m.put("projectTypeLabel", e.getProjectTypeLabel() == null ? "" : e.getProjectTypeLabel());
        m.put("sourceFile", e.getSourceFile() == null ? "" : e.getSourceFile());
        m.put("chapter", e.getChapter() == null ? "" : e.getChapter());
        m.put("tableCode", e.getTableCode() == null ? "" : e.getTableCode());
        m.put("tableTitle", e.getTableTitle() == null ? "" : e.getTableTitle());
        m.put("unit", e.getUnit() == null ? "" : e.getUnit());
        m.put("headersJson", e.getHeadersJson() == null ? "[]" : e.getHeadersJson());
        m.put("rowsJson", e.getRowsJson() == null ? "[]" : e.getRowsJson());
        m.put("annotationJson", e.getAnnotationJson() == null ? "" : e.getAnnotationJson());
        m.put("annotated", e.isAnnotated());
        m.put("annotatedBy", e.getAnnotatedBy() == null ? "" : e.getAnnotatedBy());
        m.put("annotatedAt", e.getAnnotatedAt() == null ? "" : e.getAnnotatedAt().toString());
        m.put("aiPrelabeled", e.isAiPrelabeled());
        return m;
    }

    public StandardTableEntity getOrThrow(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("表不存在: " + id));
    }

    @Transactional
    public StandardTableEntity saveAnnotation(String id, String annotationJson, String username) {
        StandardTableEntity e = getOrThrow(id);
        e.setAnnotationJson(annotationJson);
        e.setAnnotated(annotationJson != null && !annotationJson.isBlank() && !"{}".equals(annotationJson.trim()));
        e.setAnnotatedBy(username);
        e.setAnnotatedAt(java.time.LocalDateTime.now());
        return repository.save(e);
    }

    @Transactional
    public StandardTableEntity markAiPrelabeled(String id, String annotationJson) {
        StandardTableEntity e = getOrThrow(id);
        e.setAnnotationJson(annotationJson);
        e.setAiPrelabeled(true);
        // 注意：AI 预标注不会把 annotated 设为 true —— 等人工复核后再点"保存"才算确认
        return repository.save(e);
    }
}
