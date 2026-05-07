package com.atarashii.policyreport.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentAnalysisServiceTest {
    private final DocumentAnalysisService service = new DocumentAnalysisService(null, null, null, null, null, null);

    @Test
    void recognizesCustomerNamedMaterials() {
        assertDocument("年度国土变更调查套合情况分析.pdf", "", "年度国土变更调查套合情况分析", "step2");
        assertDocument("土地现状调查材料.docx", "", "土地现状调查材料", "step5");
        assertDocument("社稳报告.pdf", "", "社会稳定风险评估报告", "step5");
        assertDocument("听证材料.pdf", "", "听证材料", "step5");
        assertDocument("征地补偿安置公告及照片.zip", "", "征地补偿安置公告及照片", "step5");
        assertDocument("地灾评估报告批复.pdf", "", "地灾评估报告批复", "step7");
        assertDocument("压覆矿查询表.xlsx", "", "压覆矿查询表", "step7");
    }

    private void assertDocument(String fileName, String text, String expectedType, String expectedStep) {
        String documentType = ReflectionTestUtils.invokeMethod(service, "detectDocumentType", fileName, text);
        String step = ReflectionTestUtils.invokeMethod(service, "guessStep", documentType, fileName, text);

        assertThat(documentType).isEqualTo(expectedType);
        assertThat(step).isEqualTo(expectedStep);
    }

    @Test
    void unresolvedMaterialFallsBackToCurrentStep() {
        String documentType = ReflectionTestUtils.invokeMethod(service, "detectDocumentType", "无法识别材料.txt", "普通附件内容");
        String guessedStep = ReflectionTestUtils.invokeMethod(service, "guessStep", documentType, "无法识别材料.txt", "普通附件内容");
        String resolvedStep = ReflectionTestUtils.invokeMethod(service, "resolveStepId", "", guessedStep, "step7");

        assertThat(documentType).isEqualTo("待识别材料");
        assertThat(guessedStep).isBlank();
        assertThat(resolvedStep).isEqualTo("step7");
    }
}