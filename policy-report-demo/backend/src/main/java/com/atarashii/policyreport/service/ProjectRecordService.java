package com.atarashii.policyreport.service;

import com.atarashii.policyreport.model.DemoModels.CreateProjectRequest;
import com.atarashii.policyreport.model.DemoModels.ProjectDashboard;
import com.atarashii.policyreport.model.DemoModels.ProjectRecordDto;
import com.atarashii.policyreport.persistence.FieldOverrideRepository;
import com.atarashii.policyreport.persistence.FileAnalysisRepository;
import com.atarashii.policyreport.persistence.LandUseStandardMatchRepository;
import com.atarashii.policyreport.persistence.OperationLogRepository;
import com.atarashii.policyreport.persistence.PreviewSnapshotRepository;
import com.atarashii.policyreport.persistence.ProjectFileEntity;
import com.atarashii.policyreport.persistence.ProjectFileRepository;
import com.atarashii.policyreport.persistence.ProjectRecordEntity;
import com.atarashii.policyreport.persistence.ProjectRecordRepository;
import com.atarashii.policyreport.persistence.ProjectSituationRepository;
import com.atarashii.policyreport.persistence.StepVerdictRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProjectRecordService {
    private final ProjectRecordRepository repository;
    private final ProjectTypeCatalog projectTypeCatalog;
    private final LandUseStandardService landUseStandardService;
    private final ProjectFileRepository fileRepository;
    private final FileAnalysisRepository analysisRepository;
    private final OperationLogRepository logRepository;
    private final StepVerdictRepository verdictRepository;
    private final PreviewSnapshotRepository snapshotRepository;
    private final LandUseStandardMatchRepository matchRepository;
    private final FieldOverrideRepository fieldOverrideRepository;
    private final ProjectSituationRepository situationRepository;

    public ProjectRecordService(ProjectRecordRepository repository,
                                ProjectTypeCatalog projectTypeCatalog,
                                LandUseStandardService landUseStandardService,
                                ProjectFileRepository fileRepository,
                                FileAnalysisRepository analysisRepository,
                                OperationLogRepository logRepository,
                                StepVerdictRepository verdictRepository,
                                PreviewSnapshotRepository snapshotRepository,
                                LandUseStandardMatchRepository matchRepository,
                                FieldOverrideRepository fieldOverrideRepository,
                                ProjectSituationRepository situationRepository) {
        this.repository = repository;
        this.projectTypeCatalog = projectTypeCatalog;
        this.landUseStandardService = landUseStandardService;
        this.fileRepository = fileRepository;
        this.analysisRepository = analysisRepository;
        this.logRepository = logRepository;
        this.verdictRepository = verdictRepository;
        this.snapshotRepository = snapshotRepository;
        this.matchRepository = matchRepository;
        this.fieldOverrideRepository = fieldOverrideRepository;
        this.situationRepository = situationRepository;
    }

    public ProjectDashboard dashboard() {
        return new ProjectDashboard(
                projectTypeCatalog.options(),
                repository.findAllByOrderByUpdatedAtDesc().stream().map(this::toDto).toList(),
                landUseStandardService.summaries()
        );
    }

    @Transactional
    public ProjectRecordDto create(CreateProjectRequest request) {
        if (request == null || blank(request.projectName())) {
            throw new IllegalArgumentException("项目名称不能为空。 ");
        }
        String projectType = blank(request.projectType()) ? "general" : request.projectType();
        ProjectRecordEntity entity = new ProjectRecordEntity();
        entity.setProjectCode(nextProjectCode());
        entity.setProjectName(request.projectName().trim());
        entity.setProjectType(projectType);
        entity.setProjectTypeLabel(projectTypeCatalog.labelOf(projectType));
        entity.setOwner(safe(request.owner()));
        entity.setLocation(safe(request.location()));
        entity.setStatus("草稿");
        return toDto(repository.save(entity));
    }

    public ProjectRecordEntity getEntity(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("项目不存在: " + id));
    }

    public ProjectRecordDto get(String id) {
        return toDto(getEntity(id));
    }

    private String nextProjectCode() {
        String prefix = "PRJ-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-";
        long count = repository.countByProjectCodeStartingWith(prefix) + 1;
        return prefix + String.format("%04d", count);
    }

    @Transactional
    public ProjectRecordDto update(ProjectRecordEntity entity) {
        return toDto(repository.save(entity));
    }

    @Transactional
    public void deleteProject(String id) {
        ProjectRecordEntity entity = repository.findById(id).orElse(null);
        if (entity == null) return;
        List<ProjectFileEntity> files = fileRepository.findAllByProjectIdOrderByCreatedAtAsc(id);
        for (ProjectFileEntity file : files) {
            try {
                if (file.getStoragePath() != null) Files.deleteIfExists(Path.of(file.getStoragePath()));
            } catch (Exception ignored) {}
        }
        analysisRepository.deleteAllByProjectId(id);
        logRepository.deleteAllByProjectId(id);
        verdictRepository.deleteAllByProjectId(id);
        snapshotRepository.deleteByProjectId(id);
        matchRepository.deleteByProjectId(id);
        fieldOverrideRepository.deleteAllByProjectId(id);
        situationRepository.deleteAllByProjectId(id);
        fileRepository.deleteAllByProjectId(id);
        repository.delete(entity);
    }

    public ProjectRecordDto toDto(ProjectRecordEntity entity) {
        return new ProjectRecordDto(
                entity.getId(),
                entity.getProjectCode(),
                entity.getProjectName(),
                entity.getProjectType(),
                entity.getProjectTypeLabel(),
                entity.getOwner(),
                entity.getLocation(),
                entity.getStatus(),
                entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString(),
                entity.getUpdatedAt() == null ? "" : entity.getUpdatedAt().toString()
        );
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}