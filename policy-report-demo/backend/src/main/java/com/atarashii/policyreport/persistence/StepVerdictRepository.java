package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StepVerdictRepository extends JpaRepository<StepVerdictEntity, Long> {
    List<StepVerdictEntity> findAllByProjectId(String projectId);
    Optional<StepVerdictEntity> findByProjectIdAndStepNo(String projectId, int stepNo);
    void deleteAllByProjectId(String projectId);
}
