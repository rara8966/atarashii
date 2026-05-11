package com.atarashii.policyreport.controller;

import com.atarashii.policyreport.model.DemoModels.LandUseStandardDto;
import com.atarashii.policyreport.persistence.ProjectTypeEntity;
import com.atarashii.policyreport.persistence.StandardTableEntity;
import com.atarashii.policyreport.service.LandUseStandardService;
import com.atarashii.policyreport.service.ProjectTypeCatalog;
import com.atarashii.policyreport.service.StandardTableService;
import com.atarashii.policyreport.service.TableAnnotationAiService;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/standards")
public class LandStandardController {
    private final ProjectTypeCatalog catalog;
    private final LandUseStandardService standardService;
    private final StandardTableService tableService;
    private final TableAnnotationAiService annotationAi;

    public LandStandardController(ProjectTypeCatalog catalog,
                                  LandUseStandardService standardService,
                                  StandardTableService tableService,
                                  TableAnnotationAiService annotationAi) {
        this.catalog = catalog;
        this.standardService = standardService;
        this.tableService = tableService;
        this.annotationAi = annotationAi;
    }

    /** 列出所有项目类型（含停用），附带每类型下的标准条数 + 结构化表格数。 */
    @GetMapping("/types")
    public List<TypeDto> listTypes() {
        return catalog.allEntities().stream().map(e -> new TypeDto(
                e.getTypeKey(),
                e.getLabel(),
                e.isEnabled(),
                e.getSourceFile(),
                e.getSortOrder(),
                standardService.countByType(e.getTypeKey()),
                tableService.countByType(e.getTypeKey()),
                e.getCreatedAt() == null ? "" : e.getCreatedAt().toString(),
                e.getUpdatedAt() == null ? "" : e.getUpdatedAt().toString()
        )).toList();
    }

    /** 列出某项目类型下的所有结构化表格（含 headers/rows JSON）。 */
    @GetMapping("/types/{key}/tables")
    public List<Map<String, Object>> listTables(@PathVariable("key") String key) {
        return tableService.listByType(key).stream().map(tableService::toDto).toList();
    }

    /**
     * 通过上传 docx 解析二维表入库（不影响 land_use_standard 切片）。
     * includeTableCodes：逗号分隔的表号白名单（如 "表4.0.6-1,表4.0.6-2"），用于"只挑几张"场景。
     */
    @PostMapping(value = "/tables/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadTables(
            @RequestParam("file") MultipartFile file,
            @RequestParam("typeKey") String typeKey,
            @RequestParam(value = "typeLabel", required = false) String typeLabel,
            @RequestParam(value = "replace", defaultValue = "false") boolean replace,
            @RequestParam(value = "includeTableCodes", required = false) String includeTableCodes) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "请选择 .docx 文件"));
            }
            String name = file.getOriginalFilename() == null ? "tables.docx" : file.getOriginalFilename();
            if (!name.toLowerCase().endsWith(".docx")) {
                return ResponseEntity.badRequest().body(Map.of("error", "仅支持 .docx 格式"));
            }
            ProjectTypeEntity type = catalog.getOrThrow(typeKey);
            String label = (typeLabel == null || typeLabel.isBlank()) ? type.getLabel() : typeLabel;
            java.util.Set<String> whitelist = null;
            if (includeTableCodes != null && !includeTableCodes.isBlank()) {
                whitelist = new java.util.HashSet<>();
                for (String s : includeTableCodes.split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) whitelist.add(t);
                }
            }
            int count = tableService.importFromBytes(file.getBytes(), name, typeKey, label, replace, whitelist);
            return ResponseEntity.ok(Map.of(
                "typeKey", typeKey,
                "imported", count,
                "total", tableService.countByType(typeKey),
                "message", "已导入 " + count + " 张结构化表格"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (java.io.IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "读取上传文件失败：" + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "导入失败：" + e.getMessage()));
        }
    }

    /** 清空某项目类型下所有结构化表格。 */
    @DeleteMapping("/types/{key}/tables")
    public Map<String, Object> clearTables(@PathVariable("key") String key) {
        long before = tableService.countByType(key);
        tableService.clearByType(key);
        return Map.of("typeKey", key, "removed", before);
    }

    /** 保存单张表的人工标注（标注 JSON 由前端构造好后整体提交）。 */
    @PutMapping("/tables/{id}/annotation")
    public ResponseEntity<Map<String, Object>> saveAnnotation(@PathVariable("id") String id,
                                                              @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body,
                                                              Authentication auth) {
        try {
            Object annotation = body.get("annotation");
            String json = annotation == null ? "{}" : new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(annotation);
            String username = auth != null ? auth.getName() : "anonymous";
            StandardTableEntity e = tableService.saveAnnotation(id, json, username);
            return ResponseEntity.ok(tableService.toDto(e));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "保存失败：" + e.getMessage()));
        }
    }

    /** AI 预标注单张表：调 DeepSeek 生成 annotation_json 并写入 standard_tables（annotated 不会被设为 true）。 */
    @PostMapping("/tables/{id}/ai-prelabel")
    public ResponseEntity<Map<String, Object>> aiPrelabel(@PathVariable("id") String id,
                                                          @org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        try {
            String key = body == null ? "" : body.getOrDefault("deepseekApiKey", "");
            String model = body == null ? "" : body.getOrDefault("deepseekModel", "");
            StandardTableEntity e = tableService.getOrThrow(id);
            String json = annotationAi.generateAnnotationJson(e, key, model);
            StandardTableEntity saved = tableService.markAiPrelabeled(id, json);
            return ResponseEntity.ok(tableService.toDto(saved));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "AI 预标注失败：" + e.getMessage()));
        }
    }

    /** 批量 AI 预标注某项目类型下所有未标注的表（同步执行；表多时较慢，前端建议加 loading）。 */
    @PostMapping("/types/{key}/tables/ai-prelabel-all")
    public ResponseEntity<Map<String, Object>> aiPrelabelAll(@PathVariable("key") String key,
                                                             @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        try {
            String apiKey = body == null ? "" : String.valueOf(body.getOrDefault("deepseekApiKey", ""));
            String model = body == null ? "" : String.valueOf(body.getOrDefault("deepseekModel", ""));
            boolean skipDone = body != null && Boolean.TRUE.equals(body.getOrDefault("skipAlreadyAnnotated", true));
            List<StandardTableEntity> all = tableService.listByType(key);
            int ok = 0, fail = 0;
            for (StandardTableEntity e : all) {
                if (skipDone && (e.isAnnotated() || e.isAiPrelabeled())) continue;
                try {
                    String json = annotationAi.generateAnnotationJson(e, apiKey, model);
                    tableService.markAiPrelabeled(e.getId(), json);
                    ok++;
                } catch (Exception ex) {
                    fail++;
                }
            }
            return ResponseEntity.ok(Map.of(
                "typeKey", key,
                "succeeded", ok,
                "failed", fail,
                "total", all.size(),
                "message", "AI 预标注完成：成功 " + ok + " / 失败 " + fail
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /** 翻转启用状态。 */
    @PutMapping("/types/{key}/toggle")
    public TypeDto toggle(@PathVariable("key") String key) {
        ProjectTypeEntity e = catalog.toggleEnabled(key);
        return new TypeDto(e.getTypeKey(), e.getLabel(), e.isEnabled(), e.getSourceFile(),
                e.getSortOrder(), standardService.countByType(e.getTypeKey()),
                tableService.countByType(e.getTypeKey()),
                e.getCreatedAt() == null ? "" : e.getCreatedAt().toString(),
                e.getUpdatedAt() == null ? "" : e.getUpdatedAt().toString());
    }

    /** 列出某个项目类型下的所有标准条目。 */
    @GetMapping("/types/{key}/items")
    public List<LandUseStandardDto> items(@PathVariable("key") String key) {
        return standardService.listByType(key);
    }

    /** 上传 .docx 创建/更新项目类型 + 写入标准条目。 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("typeKey") String typeKey,
            @RequestParam("typeLabel") String typeLabel,
            @RequestParam(value = "replace", defaultValue = "true") boolean replace) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "请选择 .docx 文件"));
            }
            String name = file.getOriginalFilename() == null ? "standard.docx" : file.getOriginalFilename();
            if (!name.toLowerCase().endsWith(".docx")) {
                return ResponseEntity.badRequest().body(Map.of("error", "仅支持 .docx 格式"));
            }
            // 创建/更新项目类型
            catalog.upsert(typeKey.trim(), typeLabel.trim(), name);
            // 解析并写入标准条目
            int count = standardService.importFromDocx(file.getInputStream(), name, typeKey.trim(), typeLabel.trim(), replace);
            return ResponseEntity.ok(Map.of(
                    "typeKey", typeKey,
                    "typeLabel", typeLabel,
                    "sourceFile", name,
                    "imported", count,
                    "message", "已导入 " + count + " 条标准条目"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of("error", "读取上传文件失败：" + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "导入失败：" + e.getMessage()));
        }
    }

    /** 清空某项目类型下的所有标准条目（不删除类型本身，只重置标准）。 */
    @DeleteMapping("/types/{key}/items")
    public Map<String, Object> clearItems(@PathVariable("key") String key) {
        long before = standardService.countByType(key);
        standardService.clearByType(key);
        return Map.of("typeKey", key, "removed", before);
    }

    /** 批量追加条目到指定项目类型（支持按 chapterTitle 幂等替换）。 */
    @PostMapping("/types/{key}/items")
    public ResponseEntity<?> addItems(@PathVariable("key") String key,
                                       @RequestParam(value = "replaceByTitle", defaultValue = "true") boolean replaceByTitle,
                                       @org.springframework.web.bind.annotation.RequestBody java.util.List<java.util.Map<String, String>> items) {
        try {
            ProjectTypeEntity type = catalog.getOrThrow(key);
            int count = standardService.appendItems(key, type.getLabel(), items, replaceByTitle);
            return ResponseEntity.ok(Map.of(
                    "typeKey", key,
                    "appended", count,
                    "total", standardService.countByType(key),
                    "message", "已写入 " + count + " 条标准"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "写入失败：" + e.getMessage()));
        }
    }

    public record TypeDto(String typeKey, String label, boolean enabled, String sourceFile,
                          int sortOrder, long standardCount, long tableCount,
                          String createdAt, String updatedAt) {}
}
