package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileAnalysisRepository extends JpaRepository<FileAnalysisEntity, Long> {
    Optional<FileAnalysisEntity> findByFileId(String fileId);
    List<FileAnalysisEntity> findAllByProjectId(String projectId);
    void deleteAllByProjectId(String projectId);
}
