package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreviewSnapshotRepository extends JpaRepository<PreviewSnapshotEntity, String> {
    Optional<PreviewSnapshotEntity> findByProjectId(String projectId);
    void deleteByProjectId(String projectId);
}
