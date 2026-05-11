package com.atarashii.policyreport.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LandUseStandardRepository extends JpaRepository<LandUseStandardEntity, String> {
    boolean existsBySourceFile(String sourceFile);

    long countByProjectType(String projectType);

    void deleteAllByProjectType(String projectType);

    List<LandUseStandardEntity> findAllByProjectTypeOrderBySortOrderAsc(String projectType);

    @Query("""
            select s from LandUseStandardEntity s
            where (:projectType = '' or s.projectType = :projectType)
              and (:query = '' or lower(s.chapterTitle) like lower(concat('%', :query, '%'))
                   or lower(s.content) like lower(concat('%', :query, '%'))
                   or lower(s.keywords) like lower(concat('%', :query, '%')))
            order by s.projectType asc, s.sortOrder asc
            """)
    List<LandUseStandardEntity> search(@Param("projectType") String projectType, @Param("query") String query, Pageable pageable);
}