package com.atarashii.policyreport.service;

import com.atarashii.policyreport.model.DemoModels.ProjectTypeOption;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ProjectTypeCatalog {
    private final List<ProjectTypeOption> options = List.of(
            new ProjectTypeOption("power-station", "电力工程（火电/核电/变电站/换流站）"),
            new ProjectTypeOption("wind-power", "电力工程（风电场）"),
            new ProjectTypeOption("oil-gas", "石油天然气工程"),
            new ProjectTypeOption("coal", "煤炭工程"),
            new ProjectTypeOption("railway", "新建铁路工程"),
            new ProjectTypeOption("highway", "公路工程"),
            new ProjectTypeOption("airport", "民用航空运输机场工程"),
            new ProjectTypeOption("public-facility", "公共文化体育设施"),
            new ProjectTypeOption("general", "其他建设项目")
    );

    public List<ProjectTypeOption> options() {
        return options;
    }

    public String labelOf(String value) {
        return options.stream()
                .filter(option -> option.value().equals(value))
                .map(ProjectTypeOption::label)
                .findFirst()
                .orElse("其他建设项目");
    }

    public String classify(String text) {
        String source = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (source.contains("风电")) return "wind-power";
        if (source.contains("石油天然气") || source.contains("油气")) return "oil-gas";
        if (source.contains("煤炭") || source.contains("矿井") || source.contains("选煤")) return "coal";
        if (source.contains("铁路")) return "railway";
        if (source.contains("公路")) return "highway";
        if (source.contains("机场") || source.contains("民用航空")) return "airport";
        if (source.contains("公共图书馆") || source.contains("文化馆") || source.contains("体育")) return "public-facility";
        if (source.contains("火电") || source.contains("核电") || source.contains("变电") || source.contains("换流") || source.contains("燃煤") || source.contains("燃气")) return "power-station";
        return "general";
    }
}