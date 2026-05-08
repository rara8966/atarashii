package com.atarashii.policyreport.service;

import com.atarashii.policyreport.model.DemoModels.DemoProject;
import com.atarashii.policyreport.model.DemoModels.ReportSection;
import com.atarashii.policyreport.model.DemoModels.StepSummary;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DemoProjectService {
    public DemoProject getDemoProject() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("项目名称", "兴宁五塘风电场一期工程");
        fields.put("项目代码", "2025-450000-44-01-008976");
        fields.put("建设单位", "广西兴宁风电开发有限公司");
        fields.put("项目位置", "南宁市兴宁区五塘镇");
        fields.put("项目类型", "单独选址能源项目");
        fields.put("总用地面积", "0.3800公顷");
        fields.put("农用地", "0.3800公顷");
        fields.put("耕地/永久基本农田", "均为0公顷");
        fields.put("征收集体土地", "0.0236公顷");
        fields.put("使用国有土地", "0.3564公顷");
        fields.put("违法用地", "0.0588公顷，已处罚整改到位");

        return new DemoProject(
                "兴宁五塘风电场一期工程",
                "2025-450000-44-01-008976",
                "广西兴宁风电开发有限公司",
                "南宁市兴宁区五塘镇",
                fields,
                buildSteps(),
                buildSections()
        );
    }

    private List<StepSummary> buildSteps() {
        return List.of(
                new StepSummary("step1", "项目基本情况", "确认预审、核准、初设、林地手续和是否动工。",
                        List.of("建设用地预审批复", "项目核准/备案/立项批复"),
                        List.of("预审有效期", "核准与初设时间顺序", "林地手续", "是否动工和是否核减")),
                new StepSummary("step2", "申请用地现状", "核对勘测定界、权属、地类和面积。",
                        List.of("勘测定界报告", "土地分类权属面积汇总表", "权属情况汇总表", "年度国土变更调查套合情况分析"),
                        List.of("总面积是否一致", "集体/国有权属是否清楚", "年度国土变更调查套合情况", "是否占耕地和永久基本农田")),
                new StepSummary("step3", "农用地转用", "判断规划、生态红线、自然保护区、计划指标和预审落实。",
                        List.of("规划佐证", "年度计划指标文件", "林地批复（涉及林地时条件必传）", "生态/保护地意见"),
                        List.of("是否符合国土空间规划", "是否触碰生态红线", "计划指标来源", "预审规模变化原因")),
                new StepSummary("step4", "补充耕地", "核对占补平衡、水田规模和粮食产能。",
                        List.of(),
                        List.of("占一补一", "占水田补水田", "本项目不占耕地时应生成无任务结论，无需上传补充耕地材料")),
                new StepSummary("step5", "土地征收", "审查公共利益、征地程序、补偿安置和社保。",
                        List.of("征收土地预公告", "土地现状调查材料", "社会稳定风险评估报告", "征地补偿安置公告及照片", "听证材料", "社保审核意见"),
                        List.of("公共利益是否明确", "程序时间线", "公告照片是否齐全", "听证材料是否齐全", "所有权人100%签约", "使用权人不低于90%签约")),
                new StepSummary("step6", "土地利用", "核对供地政策、用地标准、节约集约和土地有偿使用费。",
                        List.of("节约集约用地论证分析专章", "行业主管部门意见", "土地有偿使用费材料"),
                        List.of("是否符合供地政策", "功能分区是否超标", "是否需专章", "土地有偿使用费")),
                new StepSummary("step7", "地灾压矿", "确认地灾评估和压覆矿产结论。",
                        List.of("地灾评估报告批复"),
                        List.of("是否位于地灾易发区", "评估资质与级别", "压覆矿查询表涉及时选传", "是否压覆重要矿产")),
                new StepSummary("step8", "信访违法", "判断信访、违法用地情形和查处整改。",
                        List.of(),
                        List.of("第八步材料均按条件选传", "违法用地六类情形", "处罚是否到位", "是否涉及保护地"))
        );
    }

    private List<ReportSection> buildSections() {
        return List.of(
                new ReportSection("一、建设项目基本情况", "项目通过南宁市自然资源局用地预审，南宁市发展和改革委员会批复核准。项目装机容量50兆瓦，位于南宁市兴宁区五塘镇，属单独选址建设用地项目，涉及林地手续已办理。"),
                new ReportSection("二、申请用地现状", "项目总用地面积0.3800公顷，全部为农用地，其中耕地0公顷、永久基本农田0公顷。集体土地0.0236公顷，国有土地0.3564公顷，权属清楚无争议。"),
                new ReportSection("三、农用地转用情况", "本次申请将农用地0.3800公顷转为建设用地。项目符合南宁市国土空间总体规划，不位于自然保护区和生态保护红线范围内，计划指标由自治区核销。"),
                new ReportSection("四、补充耕地情况", "项目不涉及占用耕地，无补充耕地任务。"),
                new ReportSection("五、土地征收情况", "项目需征收五塘社区集体农用地0.0236公顷，符合公共利益情形，征地前期程序、补偿安置协议和社保费用已落实。"),
                new ReportSection("六、土地利用与供应情况", "项目符合国家产业政策和供地政策，拟以出让方式供地，开发用途为工业用地。风电机组和箱变用地均符合电力工程项目建设用地指标。"),
                new ReportSection("七、地灾压矿情况", "项目位于地质灾害易发区，已由甲级资质单位完成一级地灾评估并通过专家审查；项目不压覆重要矿产资源。"),
                new ReportSection("八、信访与违法用地处理", "项目不存在信访问题。项目违法用地0.0588公顷，不涉及生态保护红线或自然保护区，行政处罚和整改已执行到位。")
        );
    }
}