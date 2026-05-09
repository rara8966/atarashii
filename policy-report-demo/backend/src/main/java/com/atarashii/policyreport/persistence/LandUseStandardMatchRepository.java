package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LandUseStandardMatchRepository extends JpaRepository<LandUseStandardMatchEntity, String> {
    List<LandUseStandardMatchEntity> findByProjectIdOrderByCreatedAtAsc(String projectId);
    void deleteByProjectId(String projectId);
}