package com.atarashii.policyreport.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 八步情形选择目录（与 frontend/src/materialSpecs.ts CASE_GROUPS 同步）。
 * 用于 AI 自动识别情形：让 DeepSeek 基于项目文件内容判断每组应选哪个 option。
 */
@Component
public class SituationCatalog {

    private final Map<Integer, List<CaseGroup>> stepGroups = new LinkedHashMap<>();

    public SituationCatalog() {
        stepGroups.put(1, List.of(
            group("approvalSituation", "可研批复情形选择",
                "预审在可研批复后 已出具检讨", "已超核准有效期 已延期", "可研变更 已批复", "无可研/核准变更"),
            group("designChange", "初步设计变更情形选择",
                "普通变更 已批复", "审批权下放 地方办理", "无初步设计变更"),
            group("projectPhase", "分期/分段报批情形选择",
                "分段报批 多城市", "分期报批 已确定期数"),
            group("landUseType", "单独选址情形选择",
                "完全在规划范围外", "部分在规划范围内", "符合规划范围"),
            group("forestryApproval", "林地审批情形选择",
                "涉及林地 已审批", "不涉及林地"),
            group("constructionStatus", "动工用地情形选择",
                "未动工，不存在违法用地问题",
                "未动工，但项目范围内存在经批准的临时用地",
                "已动工，未超出经批准的先行用地范围",
                "项目主体未动工，但存在非本项目主体的违法用地行为",
                "已动工，超出经批准的先行用地范围",
                "已动工，存在违法用地问题"),
            group("reductionStatus", "核减用地情形选择",
                "未核减用地", "已核减用地")
        ));
        stepGroups.put(2, List.of(
            group("caseInconsistency", "国土变更调查套合情况",
                "与实际申请用地情况一致", "存在无合法来源建设用地", "存在已依法批准建设用地", "其他不一致情况"),
            group("caseNature53", "自然资发〔2023〕53号文",
                "不涉及该文件", "涉及该文件"),
            group("caseFlood", "水利水电项目淹没区",
                "不涉及淹没区", "涉及淹没区")
        ));
        stepGroups.put(3, List.of(
            group("caseNatureReserve", "自然保护区情形选择",
                "不位于自然保护区", "穿越/跨越保护区 不申请用地", "用地位于实验区 已同意"),
            group("caseEcoRedline", "生态保护红线情形选择",
                "不位于生态保护红线", "穿越/跨越红线 不申请用地", "有限人为活动 已认定", "国家重大项目 不可避让"),
            group("casePlan", "计划指标配置情形选择",
                "国家/省级重大项目配置", "使用省级存量处置规模指标"),
            group("caseBasicFarmland", "永久基本农田补划情形选择",
                "占用永农 已补划", "不涉及占用永久基本农田")
        ));
        stepGroups.put(4, List.of(
            group("caseSupplement", "耕地补充情形选择",
                "不涉及占用耕地", "已足额补充耕地", "承诺补充耕地", "无法就地补充耕地"),
            group("casePaddy", "水田补充情形选择",
                "不涉及占用水田", "已足额补充水田", "承诺补充水田", "无法补充水田")
        ));
        stepGroups.put(5, List.of(
            group("casePublicInterest", "征地公共利益情形选择",
                "能源基础设施", "交通基础设施", "水利基础设施", "其他公共利益"),
            group("caseAgreement", "补偿协议签订率情形选择",
                "全部签订", "部分签订 ≥90%", "部分签订 <90%")
        ));
        stepGroups.put(6, List.of(
            group("caseIndustry", "产业政策分类情形选择",
                "鼓励类建设项目", "允许类建设项目", "限制类建设项目"),
            group("caseSupply", "供地方式情形选择",
                "划拨方式供地", "出让方式供地", "租赁方式供地"),
            group("supplyMethod", "有偿使用费情形选择",
                "涉及新增建设用地 已测算费用", "划拨且不涉及新增建设用地")
        ));
        stepGroups.put(7, List.of(
            group("caseGeo", "地质灾害评估情形选择",
                "位于易发区 已评估", "不在易发区"),
            group("caseMineral", "压覆矿产情形选择",
                "不压覆重要矿产", "压覆 已协商补偿", "压覆 已办理审批")
        ));
        stepGroups.put(8, List.of(
            group("petitionType", "信访处理情形选择",
                "无信访事项", "涉及信访 已妥善处理"),
            group("selectedCase", "违法用地情形选择",
                "未动工无违法", "未动工 有临时用地", "范围内他人违法 已处罚",
                "2020年前违法 已处罚承诺履行", "已动工超先行用地 已处罚", "涉及生态红线/保护区 从重处罚")
        ));
    }

    public Map<Integer, List<CaseGroup>> getAllGroups() {
        return stepGroups;
    }

    public List<CaseGroup> flatten() {
        List<CaseGroup> all = new java.util.ArrayList<>();
        stepGroups.values().forEach(all::addAll);
        return all;
    }

    private CaseGroup group(String id, String title, String... labels) {
        List<CaseOption> opts = new java.util.ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            opts.add(new CaseOption(String.valueOf(i + 1), labels[i]));
        }
        return new CaseGroup(id, title, opts);
    }

    public record CaseOption(String value, String label) {}
    public record CaseGroup(String id, String title, List<CaseOption> options) {}
}
