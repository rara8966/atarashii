package com.atarashii.policyreport.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentAnalysisServiceTest {
    private final DocumentAnalysisService service = new DocumentAnalysisService(null, null, null, null, null, null);

    @Test
    void recognizesCustomerNamedMaterials() {
        assertDocument("年度国土变更调查套合情况分析.pdf", "", "年度国土变更调查套合情况分析", "step2");
        assertDocument("土地分类面积汇总表兴宁五塘风电场一期.xls", "", "土地分类权属面积汇总表", "step2");
        assertDocument("南宁市发展和改革委员会关于兴宁五塘风电场一期项目初步设计的批复.pdf", "用地预审", "初步设计批复", "step1");
        assertDocument("关于项目不涉及自然保护区的意见.PDF", "", "生态保护地意见", "step3");
        assertDocument("南宁市兴宁区自然资源局关于兴宁五塘风电场一期建设用地核销指标情况的说明.PDF", "", "计划指标文件", "step3");
        assertDocument("土地现状调查材料.docx", "", "土地现状调查材料", "step5");
        assertDocument("社稳报告.pdf", "", "社会稳定风险评估报告", "step5");
        assertDocument("社会稳定风险评估.PDF", "", "社会稳定风险评估报告", "step5");
        assertDocument("听证材料.pdf", "", "听证材料", "step5");
        assertDocument("征地补偿安置公告及照片.zip", "", "征地补偿安置公告及照片", "step5");
        assertDocument("地灾评估报告批复.pdf", "", "地灾评估报告批复", "step7");
        assertDocument("压覆矿查询表.xlsx", "", "压覆矿查询表", "step7");
        assertDocument("压矿查询表（3份）.pdf", "", "压覆矿查询表", "step7");
        assertDocument("土地有偿使用费材料.pdf", "", "土地有偿使用费材料", "step6");
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