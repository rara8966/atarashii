package com.atarashii.policyreport.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoProjectServiceTest {
    @Test
    void demoProjectContainsEightStepsAndRealArea() {
        DemoProjectService service = new DemoProjectService();
        var project = service.getDemoProject();

        assertThat(project.steps()).hasSize(8);
        assertThat(project.baseFields()).containsEntry("总用地面积", "0.3800公顷");
        assertThat(project.baseFields()).containsEntry("违法用地", "0.0588公顷，已处罚整改到位");
    }
}