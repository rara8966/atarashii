package com.atarashii.policyreport.service;

import com.atarashii.policyreport.model.DemoModels.ProjectTypeOption;
import com.atarashii.policyreport.persistence.ProjectTypeEntity;
import com.atarashii.policyreport.persistence.ProjectTypeRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 项目类型目录。原本硬编码 9+1，现改为持久化到 project_types 表，
 * 启动时如果表为空则按内置默认列表 seed。可通过"用地标准管理"页停用/上传新类型。
 */
@Component
public class ProjectTypeCatalog {

    /** 内置默认类型，仅在表为空时 seed。 */
    private static final List<ProjectTypeOption> BUILTIN = List.of(
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

    private final ProjectTypeRepository repository;

    public ProjectTypeCatalog(ProjectTypeRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfEmpty() {
        if (repository.count() > 0) return;
        int order = 0;
        for (ProjectTypeOption opt : BUILTIN) {
            ProjectTypeEntity e = new ProjectTypeEntity();
            e.setTypeKey(opt.value());
            e.setLabel(opt.label());
            e.setEnabled(true);
            e.setSortOrder(order++);
            repository.save(e);
        }
    }

    /** 仅返回启用的项目类型（项目创建下拉用）。 */
    public List<ProjectTypeOption> options() {
        return repository.findAllByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(e -> new ProjectTypeOption(e.getTypeKey(), e.getLabel()))
                .toList();
    }

    /** 全量列出（管理页用，含停用项）。 */
    public List<ProjectTypeEntity> allEntities() {
        return repository.findAllByOrderBySortOrderAscIdAsc();
    }

    public String labelOf(String value) {
        if (value == null || value.isBlank()) return "其他建设项目";
        return repository.findByTypeKey(value)
                .map(ProjectTypeEntity::getLabel)
                .orElseGet(() -> BUILTIN.stream()
                        .filter(opt -> opt.value().equals(value))
                        .map(ProjectTypeOption::label)
                        .findFirst()
                        .orElse("其他建设项目"));
    }

    /** 根据文本关键词分类项目类型，启用列表中按命中关键词推断；命中不到回 general。 */
    public String classify(String text) {
        String source = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (source.contains("风电")) return existingOrFallback("wind-power");
        if (source.contains("石油天然气") || source.contains("油气")) return existingOrFallback("oil-gas");
        if (source.contains("煤炭") || source.contains("矿井") || source.contains("选煤")) return existingOrFallback("coal");
        if (source.contains("铁路")) return existingOrFallback("railway");
        if (source.contains("公路")) return existingOrFallback("highway");
        if (source.contains("机场") || source.contains("民用航空")) return existingOrFallback("airport");
        if (source.contains("公共图书馆") || source.contains("文化馆") || source.contains("体育")) return existingOrFallback("public-facility");
        if (source.contains("火电") || source.contains("核电") || source.contains("变电") || source.contains("换流") || source.contains("燃煤") || source.contains("燃气")) return existingOrFallback("power-station");
        return "general";
    }

    private String existingOrFallback(String key) {
        return repository.findByTypeKey(key)
                .filter(ProjectTypeEntity::isEnabled)
                .map(ProjectTypeEntity::getTypeKey)
                .orElse("general");
    }

    public ProjectTypeEntity getOrThrow(String typeKey) {
        return repository.findByTypeKey(typeKey)
                .orElseThrow(() -> new IllegalArgumentException("项目类型不存在: " + typeKey));
    }

    @Transactional
    public ProjectTypeEntity toggleEnabled(String typeKey) {
        ProjectTypeEntity e = getOrThrow(typeKey);
        e.setEnabled(!e.isEnabled());
        return repository.save(e);
    }

    @Transactional
    public ProjectTypeEntity upsert(String typeKey, String label, String sourceFile) {
        if (typeKey == null || typeKey.isBlank()) throw new IllegalArgumentException("类型 key 不能为空");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("类型名称不能为空");
        ProjectTypeEntity entity = repository.findByTypeKey(typeKey).orElseGet(() -> {
            ProjectTypeEntity e = new ProjectTypeEntity();
            e.setTypeKey(typeKey);
            e.setSortOrder((int) repository.count());
            return e;
        });
        entity.setLabel(label);
        if (sourceFile != null && !sourceFile.isBlank()) entity.setSourceFile(sourceFile);
        entity.setEnabled(true);
        return repository.save(entity);
    }
}
