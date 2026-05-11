package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectSituationRepository extends JpaRepository<ProjectSituationEntity, Long> {
    List<ProjectSituationEntity> findAllByProjectId(String projectId);

    Optional<ProjectSituationEntity> findByProjectIdAndStepNoAndGroupId(String projectId, int stepNo, String groupId);

    void deleteAllByProjectId(String projectId);
}
