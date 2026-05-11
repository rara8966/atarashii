package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StandardTableRepository extends JpaRepository<StandardTableEntity, String> {
    List<StandardTableEntity> findAllByProjectTypeOrderBySortOrderAscTableCodeAsc(String projectType);
    long countByProjectType(String projectType);
    long countByProjectTypeAndAnnotated(String projectType, boolean annotated);
    void deleteAllByProjectType(String projectType);
    Optional<StandardTableEntity> findByProjectTypeAndTableCode(String projectType, String tableCode);
    boolean existsByProjectTypeAndSourceFile(String projectType, String sourceFile);
}
