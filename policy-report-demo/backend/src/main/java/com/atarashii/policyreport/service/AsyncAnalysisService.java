package com.atarashii.policyreport.service;

import com.atarashii.policyreport.model.DemoModels.AiConfig;
import com.atarashii.policyreport.model.DemoModels.DocumentAnalysisResponse;
import com.atarashii.policyreport.persistence.FileAnalysisEntity;
import com.atarashii.policyreport.persistence.FileAnalysisRepository;
import com.atarashii.policyreport.persistence.ProjectFileEntity;
import com.atarashii.policyreport.persistence.ProjectFileRepository;
import com.atarashii.policyreport.persistence.ProjectRecordEntity;
import com.atarashii.policyreport.persistence.ProjectRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AsyncAnalysisService {
    private final ProjectFileRepository fileRepository;
    private final FileAnalysisRepository analysisRepository;
    private final ProjectRecordRepository projectRepository;
    private final DocumentAnalysisService documentAnalysisService;
    private final UploadedFileService uploadedFileService;
    private final ProjectTypeCatalog projectTypeCatalog;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AsyncAnalysisService(ProjectFileRepository fileRepository,
                                FileAnalysisRepository analysisRepository,
                                ProjectRecordRepository projectRepository,
                                DocumentAnalysisService documentAnalysisService,
                                UploadedFileService uploadedFileService,
                                ProjectTypeCatalog projectTypeCatalog) {
        this.fileRepository = fileRepository;
        this.analysisRepository = analysisRepository;
        this.projectRepository = projectRepository;
        this.documentAnalysisService = documentAnalysisService;
        this.uploadedFileService = uploadedFileService;
        this.projectTypeCatalog = projectTypeCatalog;
    }

    @Async("analysisExecutor")
    @Transactional
    public void analyzeFile(String projectFileId, String aiProvider, String deepseekApiKey, String deepseekModel,
                            String doubaoApiKey, String doubaoEndpoint) {
        ProjectFileEntity pf = fileRepository.findById(projectFileId).orElse(null);
        if (pf == null) return;

        pf.setAnalysisStatus("RUNNING");
        fileRepository.save(pf);

        try {
            AiConfig aiConfig = new AiConfig(aiProvider, deepseekApiKey, deepseekModel, doubaoApiKey, doubaoEndpoint);
            PathMultipartFile mockFile = buildMockFile(pf);
            DocumentAnalysisResponse result = documentAnalysisService.analyze(mockFile, null, null, aiConfig, pf.getProjectId());

            FileAnalysisEntity analysis = analysisRepository.findByFileId(projectFileId)
                    .orElseGet(FileAnalysisEntity::new);
            analysis.setFileId(projectFileId);
            analysis.setProjectId(pf.getProjectId());
            analysis.setExtractedText(result.textPreview());
            analysis.setDetectedDocumentType(result.detectedDocumentType());
            analysis.setOcrUsed(false);
            analysis.setDoubaoUsed(result.doubaoAnalysis() != null && !result.doubaoAnalysis().isBlank());
            if (result.aiAdvice() != null) {
                analysis.setAiSummary(result.aiAdvice().summary());
            }
            try {
                analysis.setExtractedFieldsJson(objectMapper.writeValueAsString(result.extractedFields()));
            } catch (Exception ignored) {}
            analysis.setAnalyzedAt(LocalDateTime.now());
            analysisRepository.save(analysis);

            Integer suggestedStep = guessStep(result.detectedDocumentType(), pf.getOriginalName());
            pf.setAiSuggestedStep(suggestedStep);
            if (!pf.isConfirmedByUser()) {
                pf.setCurrentStep(suggestedStep);
            }
            pf.setAnalysisStatus("DONE");
            fileRepository.save(pf);

            maybeUpdateProjectType(pf.getProjectId(), result.detectedDocumentType());

        } catch (Exception e) {
            pf.setAnalysisStatus("FAILED");
            fileRepository.save(pf);
        }
    }

    private PathMultipartFile buildMockFile(ProjectFileEntity pf) {
        java.nio.file.Path path = java.nio.file.Path.of(pf.getStoragePath());
        return new PathMultipartFile(path, pf.getOriginalName(),
                pf.getMimeType() != null ? pf.getMimeType() : "application/octet-stream");
    }

    private Integer guessStep(String docType, String fileName) {
        String combined = ((docType == null ? "" : docType) + " " + (fileName == null ? "" : fileName)).toLowerCase();
        if (combined.contains("预审") || combined.contains("立项") || combined.contains("核准") || combined.contains("批复")) return 1;
        if (combined.contains("勘测") || combined.contains("定界") || combined.contains("权属")) return 2;
        if (combined.contains("规划") || combined.contains("农转用") || combined.contains("计划指标")) return 3;
        if (combined.contains("补充耕地") || combined.contains("占补平衡")) return 4;
        if (combined.contains("征收") || combined.contains("社稳") || combined.contains("听证") || combined.contains("预公告")) return 5;
        if (combined.contains("土地利用") || combined.contains("有偿使用") || combined.contains("节约集约") || combined.contains("用地标准")) return 6;
        if (combined.contains("地灾") || combined.contains("压覆矿")) return 7;
        if (combined.contains("信访") || combined.contains("违法用地")) return 8;
        return null;
    }

    private void maybeUpdateProjectType(String projectId, String docType) {
        if (docType == null || docType.isBlank()) return;
        ProjectRecordEntity project = projectRepository.findById(projectId).orElse(null);
        if (project == null || !"general".equals(project.getProjectType())) return;
        String guessed = projectTypeCatalog.classify(docType);
        if (!"general".equals(guessed)) {
            project.setProjectType(guessed);
            project.setProjectTypeLabel(projectTypeCatalog.labelOf(guessed));
            projectRepository.save(project);
        }
    }

    public record AnalysisProgress(long total, long done, long failed, long running) {}

    public AnalysisProgress progress(String projectId) {
        long total = fileRepository.countByProjectId(projectId);
        long done = fileRepository.countByProjectIdAndAnalysisStatus(projectId, "DONE");
        long failed = fileRepository.countByProjectIdAndAnalysisStatus(projectId, "FAILED");
        long running = fileRepository.countByProjectIdAndAnalysisStatus(projectId, "RUNNING");
        return new AnalysisProgress(total, done, failed, running);
    }
}
