package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLogEntity, Long> {
    List<OperationLogEntity> findAllByProjectIdOrderByCreatedAtAsc(String projectId);
    void deleteAllByProjectId(String projectId);
}
