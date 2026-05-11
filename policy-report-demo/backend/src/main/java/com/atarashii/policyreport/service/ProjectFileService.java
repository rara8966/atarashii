package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.FileAnalysisEntity;
import com.atarashii.policyreport.persistence.FileAnalysisRepository;
import com.atarashii.policyreport.persistence.OperationLogEntity;
import com.atarashii.policyreport.persistence.OperationLogRepository;
import com.atarashii.policyreport.persistence.ProjectFileEntity;
import com.atarashii.policyreport.persistence.ProjectFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectFileService {
    private final ProjectFileRepository fileRepository;
    private final FileAnalysisRepository analysisRepository;
    private final OperationLogRepository logRepository;
    private final AsyncAnalysisService asyncAnalysisService;
    private final Path uploadDir = Path.of("data", "uploads");

    public ProjectFileService(ProjectFileRepository fileRepository,
                              FileAnalysisRepository analysisRepository,
                              OperationLogRepository logRepository,
                              AsyncAnalysisService asyncAnalysisService) {
        this.fileRepository = fileRepository;
        this.analysisRepository = analysisRepository;
        this.logRepository = logRepository;
        this.asyncAnalysisService = asyncAnalysisService;
    }

    public List<ProjectFileEntity> saveBatch(String projectId, List<MultipartFile> files,
                                             String aiProvider, String deepseekApiKey, String deepseekModel,
                                             String doubaoApiKey, String doubaoEndpoint,
                                             String operatedBy) {
        String batchId = UUID.randomUUID().toString();
        List<ProjectFileEntity> saved = new ArrayList<>();

        try { Files.createDirectories(uploadDir); } catch (Exception ignored) {}

        for (MultipartFile file : files) {
            String fileId = UUID.randomUUID().toString();
            String originalName = safeName(file.getOriginalFilename());
            Path target = uploadDir.resolve(fileId + "__" + originalName);
            try {
                Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                continue;
            }

            ProjectFileEntity pf = new ProjectFileEntity();
            pf.setId(fileId);
            pf.setProjectId(projectId);
            pf.setOriginalName(originalName);
            pf.setStoragePath(target.toAbsolutePath().toString());
            pf.setMimeType(file.getContentType());
            pf.setUploadBatch(batchId);
            pf.setAnalysisStatus("PENDING");
            fileRepository.save(pf);

            log(projectId, fileId, "FILE_UPLOAD", null, null, "批量上传：" + originalName, operatedBy);
            saved.add(pf);
        }

        for (ProjectFileEntity pf : saved) {
            asyncAnalysisService.analyzeFile(pf.getId(), aiProvider, deepseekApiKey, deepseekModel,
                    doubaoApiKey, doubaoEndpoint);
        }

        return saved;
    }

    @Transactional
    public ProjectFileEntity assignStep(String fileId, Integer step, String operatedBy) {
        ProjectFileEntity pf = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在: " + fileId));
        Integer oldStep = pf.getCurrentStep();
        pf.setCurrentStep(step);
        pf.setConfirmedByUser(true);
        fileRepository.save(pf);
        log(pf.getProjectId(), fileId, step == null ? "STEP_RETRACT" : "STEP_REASSIGN", oldStep, step, null, operatedBy);
        return pf;
    }

    @Transactional
    public ProjectFileEntity retract(String fileId, String operatedBy) {
        return assignStep(fileId, null, operatedBy);
    }

    @Transactional
    public void delete(String fileId, String operatedBy) {
        ProjectFileEntity pf = fileRepository.findById(fileId).orElse(null);
        if (pf == null) return;
        analysisRepository.findByFileId(fileId).ifPresent(analysisRepository::delete);
        try {
            if (pf.getStoragePath() != null) Files.deleteIfExists(Path.of(pf.getStoragePath()));
        } catch (Exception ignored) {}
        log(pf.getProjectId(), fileId, "STEP_RETRACT", pf.getCurrentStep(), null, "删除文件：" + pf.getOriginalName(), operatedBy);
        fileRepository.delete(pf);
    }

    public List<ProjectFileEntity> listByProject(String projectId) {
        return fileRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId);
    }

    public AsyncAnalysisService.AnalysisProgress progress(String projectId) {
        return asyncAnalysisService.progress(projectId);
    }

    public FileAnalysisEntity getAnalysis(String fileId) {
        return analysisRepository.findByFileId(fileId).orElse(null);
    }

    private void log(String projectId, String fileId, String action,
                     Integer fromStep, Integer toStep, String note, String operatedBy) {
        OperationLogEntity log = new OperationLogEntity();
        log.setProjectId(projectId);
        log.setFileId(fileId);
        log.setAction(action);
        log.setFromStep(fromStep);
        log.setToStep(toStep);
        log.setNote(note);
        log.setOperatedBy(operatedBy);
        logRepository.save(log);
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "upload.bin";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
