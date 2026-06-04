package com.atarashii.policyreport.controller;

import com.atarashii.policyreport.model.DemoModels;
import com.atarashii.policyreport.persistence.FileAnalysisEntity;
import com.atarashii.policyreport.persistence.PreviewSnapshotEntity;
import com.atarashii.policyreport.persistence.ProjectFileEntity;
import com.atarashii.policyreport.persistence.ProjectRecordEntity;
import com.atarashii.policyreport.persistence.StepVerdictEntity;
import com.atarashii.policyreport.service.AsyncAnalysisService;
import com.atarashii.policyreport.service.PreviewService;
import com.atarashii.policyreport.service.ProjectFileService;
import com.atarashii.policyreport.service.ProjectRecordService;
import com.atarashii.policyreport.service.ProjectSituationService;
import com.atarashii.policyreport.service.ProjectTypeCatalog;
import com.atarashii.policyreport.service.ReportGenerationService;
import com.atarashii.policyreport.service.SituationAutoDetectService;
import com.atarashii.policyreport.service.StepFieldService;
import com.atarashii.policyreport.service.VerdictEngine;
import com.atarashii.policyreport.service.verdict.ZoneVerdictApi;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/projects")
public class ProjectController {

    private final ProjectRecordService projectRecordService;
    private final ProjectFileService projectFileService;
    private final VerdictEngine verdictEngine;
    private final PreviewService previewService;
    private final ReportGenerationService reportGenerationService;
    private final ProjectTypeCatalog projectTypeCatalog;
    private final StepFieldService stepFieldService;
    private final ProjectSituationService situationService;
    private final SituationAutoDetectService situationAutoDetectService;
    private final ZoneVerdictApi zoneVerdictService;

    public ProjectController(ProjectRecordService projectRecordService,
                             ProjectFileService projectFileService,
                             VerdictEngine verdictEngine,
                             PreviewService previewService,
                             ReportGenerationService reportGenerationService,
                             ProjectTypeCatalog projectTypeCatalog,
                             StepFieldService stepFieldService,
                             ProjectSituationService situationService,
                             SituationAutoDetectService situationAutoDetectService,
                             ZoneVerdictApi zoneVerdictService) {
        this.projectRecordService = projectRecordService;
        this.projectFileService = projectFileService;
        this.verdictEngine = verdictEngine;
        this.previewService = previewService;
        this.reportGenerationService = reportGenerationService;
        this.projectTypeCatalog = projectTypeCatalog;
        this.stepFieldService = stepFieldService;
        this.situationService = situationService;
        this.situationAutoDetectService = situationAutoDetectService;
        this.zoneVerdictService = zoneVerdictService;
    }

    // ── 项目 CRUD ──

    @GetMapping
    public DemoModels.ProjectDashboard list() {
        return projectRecordService.dashboard();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> deleteProject(@PathVariable String id, Authentication auth) {
        projectRecordService.deleteProject(id);
        return Map.of("status", "DELETED", "projectId", id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','REPORTER')")
    public DemoModels.ProjectRecordDto create(@RequestBody DemoModels.CreateProjectRequest req, Authentication auth) {
        DemoModels.ProjectRecordDto dto = projectRecordService.create(req);
        if (auth != null) {
            ProjectRecordEntity entity = projectRecordService.getEntity(dto.id());
            entity.setCreatedBy(auth.getName());
            projectRecordService.update(entity);
        }
        return dto;
    }

    @GetMapping("/{id}")
    public DemoModels.ProjectRecordDto get(@PathVariable String id) {
        return projectRecordService.get(id);
    }

    // ── 文件批量上传 ──

    @PostMapping(value = "/{id}/files/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','REPORTER')")
    public Map<String, Object> uploadBatch(
            @PathVariable String id,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "aiProvider", defaultValue = "rules") String aiProvider,
            @RequestParam(value = "deepseekApiKey", defaultValue = "") String deepseekApiKey,
            @RequestParam(value = "deepseekModel", defaultValue = "") String deepseekModel,
            @RequestParam(value = "doubaoApiKey", defaultValue = "") String doubaoApiKey,
            @RequestParam(value = "doubaoEndpoint", defaultValue = "") String doubaoEndpoint,
            Authentication auth) {
        String username = auth != null ? auth.getName() : "system";
        List<ProjectFileEntity> saved = projectFileService.saveBatch(id, files, aiProvider, deepseekApiKey, deepseekModel,
                doubaoApiKey, doubaoEndpoint, username);
        return Map.of(
            "uploaded", saved.size(),
            "projectId", id,
            "message", "上传成功，后台正在分析文件"
        );
    }

    // ── 文件列表 & 分析进度 ──

    @GetMapping("/{id}/files")
    public List<FileDto> listFiles(@PathVariable String id) {
        return projectFileService.listByProject(id).stream().map(this::toFileDto).toList();
    }

    @GetMapping("/{id}/analysis-status")
    public AsyncAnalysisService.AnalysisProgress analysisStatus(@PathVariable String id) {
        return projectFileService.progress(id);
    }

    // ── 文件改挂 / 撤回 ──

    @PutMapping("/{id}/files/{fileId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','REPORTER')")
    public FileDto assignStep(@PathVariable String id, @PathVariable String fileId,
                              @RequestBody Map<String, Integer> body, Authentication auth) {
        Integer step = body.get("step");
        return toFileDto(projectFileService.assignStep(fileId, step, auth != null ? auth.getName() : null));
    }

    @PostMapping("/{id}/files/{fileId}/retract")
    @PreAuthorize("hasAnyRole('ADMIN','REPORTER')")
    public FileDto retractFile(@PathVariable String id, @PathVariable String fileId, Authentication auth) {
        return toFileDto(projectFileService.retract(fileId, auth != null ? auth.getName() : null));
    }

    @DeleteMapping("/{id}/files/{fileId}")
    @PreAuthorize("hasAnyRole('ADMIN','REPORTER')")
    public Map<String, String> deleteFile(@PathVariable String id, @PathVariable String fileId, Authentication auth) {
        projectFileService.delete(fileId, auth != null ? auth.getName() : null);
        return Map.of("status", "DELETED", "fileId", fileId);
    }

    // ── 文件分析详情 ──

    @GetMapping("/{id}/files/{fileId}/analysis")
    public FileAnalysisEntity getFileAnalysis(@PathVariable String id, @PathVariable String fileId) {
        FileAnalysisEntity analysis = projectFileService.getAnalysis(fileId);
        if (analysis == null) throw new IllegalArgumentException("分析尚未完成或文件不存在");
        return analysis;
    }

    // ── 项目类型确认 ──

    @PutMapping("/{id}/confirm-type")
    @PreAuthorize("hasAnyRole('ADMIN','REPORTER')")
    public DemoModels.ProjectRecordDto confirmType(@PathVariable String id,
                                                    @RequestBody Map<String, String> body,
                                                    Authentication auth) {
        ProjectRecordEntity entity = projectRecordService.getEntity(id);
        String projectType = body.getOrDefault("projectType", entity.getProjectType());
        String typeLabel = projectTypeCatalog.labelOf(projectType);
        entity.setProjectType(projectType);
        entity.setProjectTypeLabel(typeLabel);
        entity.setStandardSet(typeLabel);
        entity.setStatus("INFO_CONFIRM");
        return projectRecordService.update(entity);
    }

    // ── 项目基本信息确认 ──

    @PutMapping("/{id}/confirm-info")
    @PreAuthorize("hasAnyRole('ADMIN','REPORTER')")
    public DemoModels.ProjectRecordDto confirmInfo(@PathVariable String id,
                                                    @RequestBody Map<String, String> body) {
        ProjectRecordEntity entity = projectRecordService.getEntity(id);
        if (body.containsKey("name")) entity.setProjectName(body.get("name"));
        if (body.containsKey("owner")) entity.setOwner(body.get("owner"));
        if (body.containsKey("location")) entity.setLocation(body.get("location"));
        entity.setStatus("IN_PROGRESS");
        return projectRecordService.update(entity);
    }

    // ── 工作台 ──

    @GetMapping("/{id}/workspace")
    public WorkspaceDto getWorkspace(@PathVariable String id) {
        List<ProjectFileEntity> files = projectFileService.listByProject(id);
        List<StepVerdictEntity> verdicts = verdictEngine.getVerdicts(id);
        return new WorkspaceDto(
            files.stream().map(this::toFileDto).toList(),
            verdicts.stream().map(this::toVerdictDto).toList()
        );
    }

    @PostMapping("/{id}/verdicts/refresh")
    @PreAuthorize("hasAnyRole('ADMIN','REPORTER')")
    public List<VerdictDto> refreshVerdicts(@PathVariable String id) {
        return verdictEngine.generateAllVerdicts(id).stream().map(this::toVerdictDto).toList();
    }

    @GetMapping("/{id}/steps/{stepNo}/verdict")
    public VerdictDto getStepVerdict(@PathVariable String id, @PathVariable int stepNo) {
        return toVerdictDto(verdictEngine.generateVerdict(id, stepNo));
    }

    // ── 步骤字段聚合（FieldsTable） ──

    @GetMapping("/{id}/steps/{stepNo}/fields")
    public List<StepFieldService.StepFieldDto> getStepFields(@PathVariable String id, @PathVariable int stepNo) {
        return stepFieldService.getStepFields(id, stepNo);
    }

    @PutMapping("/{id}/steps/{stepNo}/fields/{key}")
    @PreAuthorize("hasAnyRole('ADMIN','REPORTER')")
    public StepFieldService.StepFieldDto updateStepField(@PathVariable String id,
                                                         @PathVariable int stepNo,
                                                         @PathVariable String key,
                                                         @RequestBody Map<String, String> body,
                                                         Authentication auth) {
        String username = auth != null ? auth.getName() : "system";
        return stepFieldService.upsertOverride(id, stepNo, key, body.getOrDefault("value", ""), username);
    }

    // ── 情形选择（CaseSelectors） ──

    @GetMapping("/{id}/situations")
    public List<ProjectSituationService.SituationDto> listSituations(@PathVariable String id) {
        return situationService.listByProject(id);
    }

    @PutMapping("/{id}/situations/{stepNo}/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN','REPORTER')")
    public ProjectSituationService.SituationDto upsertSituation(@PathVariable String id,
                                                                @PathVariable int stepNo,
                                                                @PathVariable String groupId,
                                                                @RequestBody Map<String, String> body) {
        return situationService.upsert(id, stepNo, groupId, body.getOrDefault("value", ""));
    }

    /** 功能区合规分析：按功能区拿对应已标注的标准表，跟项目字段做查表 + 数值比对。 */
    @GetMapping("/{id}/functional-zone-verdict")
    public List<ZoneVerdictApi.ZoneVerdict> functionalZoneVerdict(@PathVariable String id) {
        return zoneVerdictService.verdictForProject(id);
    }

    /**
     * dev 工具：直接往项目里注入一份"模拟分析完成的文件"，extractedFieldsJson 由调用方传入。
     * 用于在没有真实 docx 的情况下测试 VerdictEngine。
     * body: {"fileName": "...", "fields": [{label, value, functionalZone}]}
     */
    @PostMapping("/{id}/_debug/inject-mock-fields")
    public Map<String, Object> injectMockFields(@PathVariable String id, @RequestBody Map<String, Object> body) {
        try {
            String fileName = String.valueOf(body.getOrDefault("fileName", "模拟项目说明.txt"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) body.getOrDefault("fields", List.of());
            if (fields.isEmpty()) return Map.of("error", "fields 不能为空");
            String fileId = projectFileService.injectMock(id, fileName, fields);
            return Map.of("fileId", fileId, "fieldCount", fields.size(), "message", "已注入 mock 文件 " + fileId);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /** AI 自动识别项目情形：基于项目所有已分析文件让 DeepSeek 推断每组应选选项。 */
    @PostMapping("/{id}/situations/auto-detect")
    public ResponseEntity<?> autoDetectSituations(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        Map<String, String> payload = body == null ? Map.of() : body;
        String key = payload.getOrDefault("deepseekApiKey", "");
        String model = payload.getOrDefault("deepseekModel", "");
        try {
            List<ProjectSituationService.SituationDto> result = situationAutoDetectService.autoDetect(id, key, model);
            return ResponseEntity.ok(Map.of(
                "detected", result.size(),
                "situations", result,
                "message", "已识别 " + result.size() + " 项情形"
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 预览 ──

    @PostMapping("/{id}/preview")
    public Map<String, Object> initiatePreview(@PathVariable String id) {
        PreviewSnapshotEntity snap = previewService.initiate(id);
        return Map.of("snapshotId", snap.getId(), "status", snap.getStatus(), "message", "预览报告生成中，请稍后刷新查看");
    }

    @GetMapping("/{id}/preview")
    public Map<String, Object> getPreview(@PathVariable String id) {
        PreviewSnapshotEntity snap;
        try {
            snap = previewService.getSnapshot(id);
        } catch (IllegalArgumentException e) {
            return Map.of("status", "NONE", "markdown", "", "generatedAt", "");
        }
        // snapshotData 是 {"markdown":"...","generatedAt":"..."} 的 JSON 字符串，
        // 必须从中提取 markdown 字段，否则前端会原样显示带大括号的 JSON。
        return Map.of(
            "status", snap.getStatus(),
            "markdown", extractMarkdown(snap.getSnapshotData()),
            "generatedAt", snap.getGeneratedAt() != null ? snap.getGeneratedAt().toString() : ""
        );
    }

    @PostMapping("/{id}/preview/confirm")
    public Map<String, String> confirmPreview(@PathVariable String id) {
        previewService.confirm(id);
        return Map.of("status", "CONFIRMED", "message", "预览已确认，可以导出正式报告");
    }

    // ── 导出 ──

    @PostMapping("/{id}/export/{format}")
    public ResponseEntity<byte[]> exportReport(@PathVariable String id, @PathVariable String format) {
        boolean docx = "docx".equalsIgnoreCase(format);
        PreviewSnapshotEntity snap = previewService.getSnapshot(id);
        if (!"CONFIRMED".equals(snap.getStatus())) throw new IllegalStateException("请先确认预览报告再导出");

        String markdown = extractMarkdown(snap.getSnapshotData());
        DemoModels.ReportRequest req = new DemoModels.ReportRequest(List.of(), markdown);
        byte[] bytes = docx ? reportGenerationService.exportDocx(req) : reportGenerationService.exportMarkdown(req);
        String fileName = "policy-report-" + id.substring(0, 8) + (docx ? ".docx" : ".md");
        MediaType mediaType = docx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                : MediaType.parseMediaType("text/markdown;charset=UTF-8");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(bytes);
    }

    // ── DTOs ──

    private FileDto toFileDto(ProjectFileEntity f) {
        return new FileDto(f.getId(), f.getProjectId(), f.getOriginalName(),
            f.getMimeType(), f.getAiSuggestedStep(), f.getCurrentStep(),
            f.isConfirmedByUser(), f.getAnalysisStatus(), f.getUploadBatch(),
            f.getCreatedAt() != null ? f.getCreatedAt().toString() : "");
    }

    private VerdictDto toVerdictDto(StepVerdictEntity v) {
        return new VerdictDto(v.getProjectId(), v.getStepNo(), v.getVerdict(),
            v.getPassItemsJson(), v.getWarnItemsJson(), v.getFailItemsJson(),
            v.getGeneratedAt() != null ? v.getGeneratedAt().toString() : "");
    }

    private String extractMarkdown(String snapshotJson) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(snapshotJson);
            return node.path("markdown").asText("");
        } catch (Exception e) { return snapshotJson == null ? "" : snapshotJson; }
    }

    public record FileDto(String id, String projectId, String originalName, String mimeType,
                          Integer aiSuggestedStep, Integer currentStep, boolean confirmedByUser,
                          String analysisStatus, String uploadBatch, String createdAt) {}

    public record VerdictDto(String projectId, int stepNo, String verdict,
                             String passItemsJson, String warnItemsJson, String failItemsJson,
                             String generatedAt) {}

    public record WorkspaceDto(List<FileDto> files, List<VerdictDto> verdicts) {}
}
