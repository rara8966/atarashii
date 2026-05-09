package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRecordRepository extends JpaRepository<ProjectRecordEntity, String> {
    long countByProjectCodeStartingWith(String prefix);
    List<ProjectRecordEntity> findAllByOrderByUpdatedAtDesc();
}