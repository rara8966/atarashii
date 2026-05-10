package com.atarashii.policyreport.service;

import com.atarashii.policyreport.config.AppProperties;
import com.atarashii.policyreport.model.DemoModels.LandUseStandardDto;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PolicyKnowledgeService {
    private static final int MAX_POLICY_TEXT = 120_000;

    private final AppProperties properties;
    private final TikaDocumentParser parser;
    private final LandUseStandardService landUseStandardService;
    private final Path persistedPolicyCard = Path.of("data", "policy-card.txt");
    private String policyCardText;

    public PolicyKnowledgeService(AppProperties properties, TikaDocumentParser parser, LandUseStandardService landUseStandardService) {
        this.properties = properties;
        this.parser = parser;
        this.landUseStandardService = landUseStandardService;
    }

    public String buildKnowledgeFor(String targetStep, String documentText) {
        return buildKnowledgeFor(targetStep, documentText, null);
    }

    public String buildKnowledgeFor(String targetStep, String documentText, String projectType) {
        List<String> snippets = new ArrayList<>();
        snippets.add("明白卡核心口径: 先看项目类型、选址合规、预审有效性、农转用与征收材料是否齐全、计划指标和补充耕地是否落实。");
        snippets.add("1009号模板核心口径: 审查报告按八段写清项目基本情况、申请用地现状、农用地转用、补充耕地、土地征收、土地利用、地灾压矿、信访违法处理；每段都要有材料依据和明确结论。");
        snippets.add(snippet(loadPolicyCardText(), targetStep, documentText));
        if (projectType != null && !projectType.isBlank() && !"general".equals(projectType)) {
            String standardSnippet = buildStandardKnowledge(projectType, targetStep);
            if (!standardSnippet.isBlank()) {
                snippets.add(standardSnippet);
            }
        }
        return String.join("\n", snippets);
    }

    public boolean hasPolicyCardLoaded() {
        return !loadPolicyCardText().isBlank();
    }

    public TikaDocumentParser.ParsedDocument replacePolicyCard(MultipartFile file) {
        TikaDocumentParser.ParsedDocument parsed = parser.parse(file);
        String text = parsed.text() == null ? "" : parsed.text();
        policyCardText = text.length() > MAX_POLICY_TEXT ? text.substring(0, MAX_POLICY_TEXT) : text;
        try {
            Files.createDirectories(persistedPolicyCard.getParent());
            Files.writeString(persistedPolicyCard, policyCardText, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
        return parsed;
    }

    private String buildStandardKnowledge(String projectType, String targetStep) {
        List<LandUseStandardDto> chunks = landUseStandardService.search(projectType, "", 6);
        if (chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("【内置用地指标标准（").append(projectType).append("）】\n");
        sb.append("以下为系统内置该项目类型的建设用地指标条文，作为第六步用地标准核对的基准依据：\n");
        for (LandUseStandardDto chunk : chunks) {
            sb.append("▶ ").append(chunk.chapterTitle()).append("\n");
            String content = chunk.content();
            sb.append(content, 0, Math.min(content.length(), 500)).append("\n\n");
        }
        return sb.toString().trim();
    }

    private String loadPolicyCardText() {
        if (policyCardText == null) {
            if (Files.isRegularFile(persistedPolicyCard)) {
                try {
                    policyCardText = Files.readString(persistedPolicyCard, StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    policyCardText = "";
                }
            }
            if (policyCardText == null || policyCardText.isBlank()) {
                policyCardText = loadTextFromCandidates(properties.getPolicyCardPath(), "2025重大项目用地政策明白卡.pdf");
            }
        }
        return policyCardText;
    }

    private String loadTextFromCandidates(String configuredPath, String fileName) {
        List<Path> candidates = List.of(
                Path.of(configuredPath),
                Path.of("..", "..", fileName),
                Path.of("..", fileName),
                Path.of(fileName)
        );
        for (Path candidate : candidates) {
            Path absolutePath = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(absolutePath)) {
                String text = parser.parse(absolutePath).text();
                return text.length() > MAX_POLICY_TEXT ? text.substring(0, MAX_POLICY_TEXT) : text;
            }
        }
        return "";
    }

    private String snippet(String sourceText, String targetStep, String documentText) {
        if (sourceText.isBlank()) {
            return "未读取到本地政策文件，当前仅使用系统内置规则。";
        }
        List<String> keywords = keywordsFor(targetStep, documentText);
        for (String keyword : keywords) {
            int index = sourceText.indexOf(keyword);
            if (index >= 0) {
                int start = Math.max(0, index - 450);
                int end = Math.min(sourceText.length(), index + 950);
                return sourceText.substring(start, end).replaceAll("\\s+", " ");
            }
        }
        return sourceText.substring(0, Math.min(900, sourceText.length())).replaceAll("\\s+", " ");
    }

    private List<String> keywordsFor(String targetStep, String documentText) {
        String normalizedStep = targetStep == null ? "" : targetStep.toLowerCase(Locale.ROOT);
        List<String> keywords = new ArrayList<>();
        if (normalizedStep.contains("1") || normalizedStep.contains("项目基本")) {
            keywords.addAll(List.of("用地预审", "项目立项", "初步设计"));
        } else if (normalizedStep.contains("2") || normalizedStep.contains("现状")) {
            keywords.addAll(List.of("勘测定界", "权属", "国土变更调查"));
        } else if (normalizedStep.contains("3") || normalizedStep.contains("农用地")) {
            keywords.addAll(List.of("生态保护红线", "永久基本农田", "土地利用计划"));
        } else if (normalizedStep.contains("4") || normalizedStep.contains("补充耕地")) {
            keywords.addAll(List.of("补充耕地", "占补平衡", "水田"));
        } else if (normalizedStep.contains("5") || normalizedStep.contains("征收")) {
            keywords.addAll(List.of("土地征收", "补偿安置", "公共利益"));
        } else if (normalizedStep.contains("6") || normalizedStep.contains("土地利用")) {
            keywords.addAll(List.of("节约集约", "供地", "土地有偿使用费"));
        } else if (normalizedStep.contains("7") || normalizedStep.contains("地质")) {
            keywords.addAll(List.of("地质灾害", "压覆矿产"));
        } else if (normalizedStep.contains("8") || normalizedStep.contains("违法")) {
            keywords.addAll(List.of("违法用地", "信访", "查处"));
        }
        if (documentText != null && documentText.contains("生态保护红线")) {
            keywords.add(0, "生态保护红线");
        }
        if (documentText != null && documentText.contains("违法用地")) {
            keywords.add(0, "违法用地");
        }
        return keywords.isEmpty() ? List.of("建设用地") : keywords;
    }
}