package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectFileRepository extends JpaRepository<ProjectFileEntity, String> {
    List<ProjectFileEntity> findAllByProjectIdOrderByCreatedAtAsc(String projectId);
    List<ProjectFileEntity> findAllByProjectIdAndCurrentStep(String projectId, Integer step);
    List<ProjectFileEntity> findAllByProjectIdAndCurrentStepIsNull(String projectId);
    long countByProjectIdAndAnalysisStatus(String projectId, String status);
    long countByProjectId(String projectId);
    void deleteAllByProjectId(String projectId);
}
