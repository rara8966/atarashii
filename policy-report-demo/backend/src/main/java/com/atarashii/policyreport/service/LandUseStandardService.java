package com.atarashii.policyreport.service;

import com.atarashii.policyreport.model.DemoModels.LandUseStandardDto;
import com.atarashii.policyreport.model.DemoModels.StandardSummary;
import com.atarashii.policyreport.persistence.LandUseStandardEntity;
import com.atarashii.policyreport.persistence.LandUseStandardRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LandUseStandardService {
    private final LandUseStandardRepository repository;
    private final TikaDocumentParser parser;
    private final ProjectTypeCatalog projectTypeCatalog;

    public LandUseStandardService(LandUseStandardRepository repository,
                                  TikaDocumentParser parser,
                                  ProjectTypeCatalog projectTypeCatalog) {
        this.repository = repository;
        this.parser = parser;
        this.projectTypeCatalog = projectTypeCatalog;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfEmpty() {
        if (repository.count() > 0) {
            return;
        }
        List<LandUseStandardEntity> standards = loadFromResources();
        if (!standards.isEmpty()) {
            repository.saveAll(standards);
        }
    }

    public List<LandUseStandardDto> search(String projectType, String query, int limit) {
        int size = Math.max(1, Math.min(limit <= 0 ? 30 : limit, 100));
        return repository.search(safe(projectType), safe(query), PageRequest.of(0, size)).stream().map(this::toDto).toList();
    }

    public List<StandardSummary> summaries() {
        Map<String, Long> counts = new LinkedHashMap<>();
        repository.findAll().forEach(item -> counts.merge(item.getProjectType(), 1L, Long::sum));
        return counts.entrySet().stream()
                .map(entry -> new StandardSummary(entry.getKey(), projectTypeCatalog.labelOf(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparing(StandardSummary::projectTypeLabel))
                .toList();
    }

    public LandUseStandardEntity getEntity(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("标准条目不存在: " + id));
    }

    public LandUseStandardDto toDto(LandUseStandardEntity entity) {
        return new LandUseStandardDto(
                entity.getId(),
                entity.getProjectType(),
                entity.getProjectTypeLabel(),
                entity.getSourceFile(),
                entity.getChapterTitle(),
                entity.getContent(),
                entity.getKeywords()
        );
    }

    private List<LandUseStandardEntity> loadFromResources() {
        List<LandUseStandardEntity> entities = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath*:/land-standards/source/**/*.docx");
            int order = 0;
            for (Resource resource : resources) {
                String sourceName = resource.getFilename() == null ? "未命名标准.docx" : resource.getFilename();
                String path = resource.getURL().toString();
                String projectType = projectTypeCatalog.classify(path + " " + sourceName);
                String label = projectTypeCatalog.labelOf(projectType);
                try (InputStream inputStream = resource.getInputStream()) {
                    String text = normalize(parser.parse(inputStream, sourceName, "application/vnd.openxmlformats-officedocument.wordprocessingml.document").text());
                    for (StandardChunk chunk : split(sourceName, text)) {
                        LandUseStandardEntity entity = new LandUseStandardEntity();
                        entity.setProjectType(projectType);
                        entity.setProjectTypeLabel(label);
                        entity.setSourceFile(sourceName);
                        entity.setChapterTitle(chunk.title());
                        entity.setContent(chunk.content());
                        entity.setKeywords(buildKeywords(label, chunk.title(), chunk.content()));
                        entity.setSortOrder(order++);
                        entities.add(entity);
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("导入用地标准库失败: " + ex.getMessage(), ex);
        }
        return entities;
    }

    private List<StandardChunk> split(String sourceName, String text) {
        List<StandardChunk> chunks = new ArrayList<>();
        String title = stripExtension(sourceName);
        StringBuilder buffer = new StringBuilder();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            boolean heading = isHeading(line);
            if (heading && buffer.length() > 240) {
                chunks.add(new StandardChunk(title, trimContent(buffer.toString())));
                buffer.setLength(0);
                title = line;
            } else if (heading) {
                title = line;
            }
            buffer.append(line).append('\n');
            if (buffer.length() > 1800) {
                chunks.add(new StandardChunk(title, trimContent(buffer.toString())));
                buffer.setLength(0);
            }
        }
        if (buffer.length() > 0) {
            chunks.add(new StandardChunk(title, trimContent(buffer.toString())));
        }
        return chunks.stream().filter(chunk -> chunk.content().length() > 40).toList();
    }

    private boolean isHeading(String line) {
        return line.matches("^第[一二三四五六七八九十0-9]+[篇章节].*") || line.matches("^表\\d+(?:\\.\\d+)*(?:-\\d+)?\\s+.*") || line.contains("建设用地指标");
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace('\u0000', ' ').replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private String trimContent(String content) {
        String normalized = content.replaceAll("[ \\t]+", " ").trim();
        return normalized.length() > 2200 ? normalized.substring(0, 2200) : normalized;
    }

    private String buildKeywords(String label, String title, String content) {
        String sample = (label + " " + title + " " + content).replaceAll("[^0-9A-Za-z\\u4e00-\\u9fa5]+", " ").trim();
        if (sample.length() > 260) {
            sample = sample.substring(0, 260);
        }
        return sample.toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record StandardChunk(String title, String content) {
    }
}