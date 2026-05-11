package com.atarashii.policyreport.service;

import com.atarashii.policyreport.persistence.ProjectSituationEntity;
import com.atarashii.policyreport.persistence.ProjectSituationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectSituationService {
    private final ProjectSituationRepository repository;

    public ProjectSituationService(ProjectSituationRepository repository) {
        this.repository = repository;
    }

    public List<SituationDto> listByProject(String projectId) {
        return repository.findAllByProjectId(projectId).stream()
                .map(e -> new SituationDto(e.getStepNo(), e.getGroupId(), e.getValue()))
                .toList();
    }

    @Transactional
    public SituationDto upsert(String projectId, int stepNo, String groupId, String value) {
        ProjectSituationEntity entity = repository
                .findByProjectIdAndStepNoAndGroupId(projectId, stepNo, groupId)
                .orElseGet(() -> {
                    ProjectSituationEntity e = new ProjectSituationEntity();
                    e.setProjectId(projectId);
                    e.setStepNo(stepNo);
                    e.setGroupId(groupId);
                    return e;
                });
        entity.setValue(value);
        repository.save(entity);
        return new SituationDto(stepNo, groupId, value);
    }

    public record SituationDto(int stepNo, String groupId, String value) {}
}
