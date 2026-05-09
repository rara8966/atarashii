package com.atarashii.policyreport.service;

import com.atarashii.policyreport.model.DemoModels.CreateProjectRequest;
import com.atarashii.policyreport.model.DemoModels.ProjectDashboard;
import com.atarashii.policyreport.model.DemoModels.ProjectRecordDto;
import com.atarashii.policyreport.persistence.ProjectRecordEntity;
import com.atarashii.policyreport.persistence.ProjectRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProjectRecordService {
    private final ProjectRecordRepository repository;
    private final ProjectTypeCatalog projectTypeCatalog;
    private final LandUseStandardService landUseStandardService;

    public ProjectRecordService(ProjectRecordRepository repository,
                                ProjectTypeCatalog projectTypeCatalog,
                                LandUseStandardService landUseStandardService) {
        this.repository = repository;
        this.projectTypeCatalog = projectTypeCatalog;
        this.landUseStandardService = landUseStandardService;
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