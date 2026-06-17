package com.atarashii.policyreport.controller;

import com.atarashii.policyreport.config.AppProperties;
import com.atarashii.policyreport.model.DemoModels.AiConfig;
import com.atarashii.policyreport.model.DemoModels.DemoProject;
import com.atarashii.policyreport.model.DemoModels.DocumentAnalysisResponse;
import com.atarashii.policyreport.model.DemoModels.FieldUpdateRequest;
import com.atarashii.policyreport.model.DemoModels.OllamaStatus;
import com.atarashii.policyreport.model.DemoModels.PolicyCardUploadResponse;
import com.atarashii.policyreport.model.DemoModels.ReportRequest;
import com.atarashii.policyreport.model.DemoModels.ReportResponse;
import com.atarashii.policyreport.model.DemoModels.SituationUpdateRequest;
import com.atarashii.policyreport.model.DemoModels.SynthesizeRequest;
import com.atarashii.policyreport.model.DemoModels.SynthesizeResponse;
import com.atarashii.policyreport.model.DemoModels.WorkspaceState;
import com.atarashii.policyreport.service.DeepSeekClient;
import com.atarashii.policyreport.service.DemoProjectService;
import com.atarashii.policyreport.service.DocumentAnalysisService;
import com.atarashii.policyreport.service.OllamaClient;
import com.atarashii.policyreport.service.PolicyKnowledgeService;
import com.atarashii.policyreport.service.ReportGenerationService;
import com.atarashii.policyreport.service.UploadedFileService;
import com.atarashii.policyreport.service.WorkspaceStateService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class DemoController {
    private final DemoProjectService demoProjectService;
    private final DocumentAnalysisService documentAnalysisService;
    private final ReportGenerationService reportGenerationService;
    private final OllamaClient ollamaClient;
    private final WorkspaceStateService workspaceStateService;
    private final PolicyKnowledgeService policyKnowledgeService;
    private final UploadedFileService uploadedFileService;
    private final AppProperties properties;
    private final DeepSeekClient deepSeekClient;

    public DemoController(DemoProjectService demoProjectService,
                          DocumentAnalysisService documentAnalysisService,
                          ReportGenerationService reportGenerationService,
                          OllamaClient ollamaClient,
                          WorkspaceStateService workspaceStateService,
                          PolicyKnowledgeService policyKnowledgeService,
                          UploadedFileService uploadedFileService,
                          AppProperties properties,
                          DeepSeekClient deepSeekClient) {
        this.demoProjectService = demoProjectService;
        this.documentAnalysisService = documentAnalysisService;
        this.reportGenerationService = reportGenerationService;
        this.ollamaClient = ollamaClient;
        this.workspaceStateService = workspaceStateService;
        this.policyKnowledgeService = policyKnowledgeService;
        this.uploadedFileService = uploadedFileService;
        this.properties = properties;
        this.deepSeekClient = deepSeekClient;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    /**
     * 测试 DeepSeek API Key 连通性：发一条极短 prompt，根据是否拿到有效回复判定 key 可用。
     * 前端"AI 配置"和"新建项目"引导用它在保存前先验证 key。
     */
    @PostMapping("/ai/test-key")
    public java.util.Map<String, Object> testAiKey(@RequestBody java.util.Map<String, String> body) {
        String apiKey = body.getOrDefault("deepseekApiKey", "");
        String model = body.getOrDefault("deepseekModel", "");
        if (apiKey == null || apiKey.isBlank()) {
            return java.util.Map.of("ok", false, "message", "请先填写 DeepSeek API Key");
        }
        DeepSeekClient.GenerationResult result = deepSeekClient.generate("请只回复两个字：正常", apiKey, model);
        String modelName = result.model() == null ? "deepseek-chat" : result.model();
        if (result.usedModel()) {
            return java.util.Map.of("ok", true, "message", "连通正常（" + modelName + "）");
        }
        return java.util.Map.of("ok", false, "message", result.text() == null ? "连接失败" : result.text());
    }

    @GetMapping("/demo/project")
    public DemoProject demoProject() {
        return demoProjectService.getDemoProject();
    }

    @GetMapping("/ollama/status")
    public OllamaStatus ollamaStatus() {
        var models = ollamaClient.installedModels();
        return new OllamaStatus(!models.isEmpty(), properties.getOllamaModel(), properties.getOllamaFallbackModel(), models);
    }

    @GetMapping("/workspace/state")
    public WorkspaceState workspaceState(@RequestParam(value = "projectId", required = false) String projectId) {
        return workspaceStateService.getState(projectId);
    }

    @PutMapping("/workspace/fields")
    public WorkspaceState updateField(@RequestParam(value = "projectId", required = false) String projectId, @RequestBody FieldUpdateRequest request) {
        return workspaceStateService.updateField(projectId, request);
    }

    @PutMapping("/workspace/situations")
    public WorkspaceState updateSituation(@RequestParam(value = "projectId", required = false) String projectId, @RequestBody SituationUpdateRequest request) {
        return workspaceStateService.updateSituation(projectId, request);
    }

    @PostMapping(value = "/documents/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentAnalysisResponse analyzeDocument(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(value = "targetStep", required = false) String targetStep,
                                                    @RequestParam(value = "fallbackStep", required = false) String fallbackStep,
                                                    @RequestParam(value = "aiProvider", required = false) String aiProvider,
                                                    @RequestParam(value = "deepseekApiKey", required = false) String deepseekApiKey,
                                                    @RequestParam(value = "deepseekModel", required = false) String deepseekModel,
                                                    @RequestParam(value = "doubaoApiKey", required = false) String doubaoApiKey,
                                                    @RequestParam(value = "doubaoEndpoint", required = false) String doubaoEndpoint,
                                                    @RequestParam(value = "projectId", required = false) String projectId) {
        return documentAnalysisService.analyze(file, targetStep, fallbackStep, new AiConfig(aiProvider, deepseekApiKey, deepseekModel, doubaoApiKey, doubaoEndpoint), projectId);
    }

    @PostMapping("/documents/synthesize")
    public SynthesizeResponse synthesize(@RequestBody SynthesizeRequest request) {
        AiConfig aiConfig = new AiConfig(request.aiProvider(), request.deepseekApiKey(), request.deepseekModel(), null, null);
        return documentAnalysisService.synthesize(request.analyses(), aiConfig);
    }

    @PostMapping(value = "/policy-card/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PolicyCardUploadResponse uploadPolicyCard(@RequestParam("file") MultipartFile file,
                                                     @RequestParam(value = "projectId", required = false) String projectId) {
        var parsed = policyKnowledgeService.replacePolicyCard(file);
        workspaceStateService.registerPolicyCard(projectId, parsed.fileName());
        int textLength = parsed.text() == null ? 0 : parsed.text().length();
        return new PolicyCardUploadResponse(parsed.fileName(), textLength, "明白卡已单独载入，后续分析会优先使用这份材料。");
    }

    @GetMapping("/documents/source/{fileId}")
    public ResponseEntity<Resource> sourceFile(@PathVariable String fileId) {
        Resource resource = uploadedFileService.resource(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(uploadedFileService.contentType(fileId)))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(uploadedFileService.fileName(fileId), StandardCharsets.UTF_8).build().toString())
                .body(resource);
    }

    @DeleteMapping("/documents/{fileId}")
    public WorkspaceState withdrawDocument(@PathVariable String fileId, @RequestParam(value = "projectId", required = false) String projectId) {
        uploadedFileService.delete(fileId);
        return workspaceStateService.removeAnalysis(projectId, fileId);
    }

    @PostMapping("/reports/generate")
    public ReportResponse generateReport(@RequestBody(required = false) ReportRequest request) {
        return reportGenerationService.generate(request);
    }

    @PostMapping("/reports/export/{format}")
    public ResponseEntity<byte[]> exportReport(@PathVariable String format, @RequestBody(required = false) ReportRequest request) {
        boolean docx = "docx".equalsIgnoreCase(format);
        byte[] bytes = docx ? reportGenerationService.exportDocx(request) : reportGenerationService.exportMarkdown(request);
        String fileName = docx ? "policy-report-demo.docx" : "policy-report-demo.md";
        MediaType mediaType = docx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                : MediaType.parseMediaType("text/markdown;charset=UTF-8");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(bytes);
    }
}