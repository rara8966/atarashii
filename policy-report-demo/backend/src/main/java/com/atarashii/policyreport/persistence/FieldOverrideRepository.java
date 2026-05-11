package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FieldOverrideRepository extends JpaRepository<FieldOverrideEntity, Long> {
    List<FieldOverrideEntity> findAllByProjectIdAndStepNo(String projectId, int stepNo);

    Optional<FieldOverrideEntity> findByProjectIdAndStepNoAndFieldKey(String projectId, int stepNo, String fieldKey);

    void deleteAllByProjectId(String projectId);
}
