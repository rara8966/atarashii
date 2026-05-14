package com.atarashii.policyreport.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能区目录：每个项目类型由若干"功能区"构成（一个项目设施的不同构筑物用地组成部分）。
 * 例如风电项目分 5 个功能区：风电机组 / 机组变电站 / 集电线路 / 升压变电站及运行管理中心 / 交通工程。
 *
 * VerdictEngine 会按功能区分别查表、分别判定。
 * standard_tables.annotation_json 里通过 functionalZone 字段标明该表归属哪个功能区。
 * project 抽取的字段也通过 functionalZone 标签标明属于哪个功能区。
 *
 * 注意：与前端 frontend/src/materialSpecs.ts FUNCTIONAL_ZONES 同步。
 */
@Component
public class FunctionalZoneCatalog {

    private final Map<String, List<String>> zonesByType = new LinkedHashMap<>();

    public FunctionalZoneCatalog() {
        // 客户口述的风电分类
        zonesByType.put("wind-power", List.of(
            "风电机组", "机组变电站", "集电线路", "升压变电站及运行管理中心", "交通工程"
        ));
        // 火电/核电/变电站/换流站（参照《电力工程项目建设用地指标》章节结构）
        zonesByType.put("power-station", List.of(
            "厂区/站区主体", "厂前建筑区", "厂外取水建筑物", "工艺管线",
            "运煤皮带廊道", "运输道路", "生活区"
        ));
        zonesByType.put("oil-gas", List.of(
            "采气场站", "处理厂", "集输管线", "输气干线", "压气站", "辅助设施"
        ));
        zonesByType.put("coal", List.of(
            "井口及工业广场", "选煤厂", "矸石场", "运输线路", "辅助生产设施"
        ));
        zonesByType.put("railway", List.of(
            "正线路基", "桥梁工程", "隧道工程", "车站", "区间设施", "运营管理设施"
        ));
        zonesByType.put("highway", List.of(
            "路基工程", "桥梁工程", "隧道工程", "互通立交", "服务区", "管理及养护设施"
        ));
        zonesByType.put("airport", List.of(
            "飞行区", "航站区", "工作区", "生活区", "供油设施", "导航设施"
        ));
        zonesByType.put("public-facility", List.of(
            "主体建筑", "辅助用房", "室外活动场地", "停车与道路"
        ));
        zonesByType.put("general", List.of(
            "主体工程", "辅助工程", "公用工程", "环境保护", "施工临时用地"
        ));
    }

    public List<String> zonesOf(String projectType) {
        return zonesByType.getOrDefault(projectType, List.of());
    }

    public Map<String, List<String>> all() {
        return zonesByType;
    }
}
