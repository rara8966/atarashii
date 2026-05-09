package com.atarashii.policyreport.controller;

import com.atarashii.policyreport.model.DemoModels.CreateProjectRequest;
import com.atarashii.policyreport.model.DemoModels.LandUseStandardDto;
import com.atarashii.policyreport.model.DemoModels.LandUseStandardMatchDto;
import com.atarashii.policyreport.model.DemoModels.ProjectDashboard;
import com.atarashii.policyreport.model.DemoModels.ProjectRecordDto;
import com.atarashii.policyreport.service.LandUseStandardMatchService;
import com.atarashii.policyreport.service.LandUseStandardService;
import com.atarashii.policyreport.service.ProjectRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProjectLibraryController {
    private final ProjectRecordService projectRecordService;
    private final LandUseStandardService landUseStandardService;
    private final LandUseStandardMatchService landUseStandardMatchService;

    public ProjectLibraryController(ProjectRecordService projectRecordService,
                                    LandUseStandardService landUseStandardService,
                                    LandUseStandardMatchService landUseStandardMatchService) {
        this.projectRecordService = projectRecordService;
        this.landUseStandardService = landUseStandardService;
        this.landUseStandardMatchService = landUseStandardMatchService;
    }

    @GetMapping("/projects/dashboard")
    public ProjectDashboard dashboard() {
        return projectRecordService.dashboard();
    }

    @PostMapping("/projects")
    public ProjectRecordDto createProject(@RequestBody CreateProjectRequest request) {
        return projectRecordService.create(request);
    }

    @GetMapping("/projects/{projectId}")
    public ProjectRecordDto getProject(@PathVariable String projectId) {
        return projectRecordService.get(projectId);
    }

    @GetMapping("/land-standards")
    public List<LandUseStandardDto> standards(@RequestParam(value = "projectType", required = false) String projectType,
                                              @RequestParam(value = "query", required = false) String query,
                                              @RequestParam(value = "limit", defaultValue = "30") int limit) {
        return landUseStandardService.search(projectType, query, limit);
    }

    @GetMapping("/projects/{projectId}/standard-matches")
    public List<LandUseStandardMatchDto> standardMatches(@PathVariable String projectId) {
        return landUseStandardMatchService.list(projectId);
    }

    @PostMapping("/projects/{projectId}/standard-matches/refresh")
    public List<LandUseStandardMatchDto> refreshStandardMatches(@PathVariable String projectId) {
        return landUseStandardMatchService.refresh(projectId);
    }
}