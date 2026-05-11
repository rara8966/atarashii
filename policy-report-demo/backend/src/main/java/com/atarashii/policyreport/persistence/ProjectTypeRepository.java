package com.atarashii.policyreport.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectTypeRepository extends JpaRepository<ProjectTypeEntity, Long> {
    List<ProjectTypeEntity> findAllByOrderBySortOrderAscIdAsc();
    List<ProjectTypeEntity> findAllByEnabledTrueOrderBySortOrderAscIdAsc();
    Optional<ProjectTypeEntity> findByTypeKey(String typeKey);
    boolean existsByTypeKey(String typeKey);
}
