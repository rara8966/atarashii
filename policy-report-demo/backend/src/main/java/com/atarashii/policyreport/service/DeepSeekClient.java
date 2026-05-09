package com.atarashii.policyreport.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekClient {
    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public GenerationResult generate(String prompt, String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            return new GenerationResult(false, modelName(model), "未配置 DeepSeek API Key。");
        }
        try {
            Map<String, Object> payload = Map.of(
                    "model", modelName(model),
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content", "你是建设用地报批审查报告助手，只输出中文，建议要具体、可执行。"),
                            Map.of("role", "user", "content", prompt)
                    )
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new GenerationResult(false, modelName(model), "DeepSeek 返回错误：HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return new GenerationResult(false, modelName(model), "DeepSeek 未返回有效文本。");
            }
            return new GenerationResult(true, modelName(model), content);
        } catch (Exception ex) {
            return new GenerationResult(false, modelName(model), "DeepSeek 调用失败：" + ex.getMessage());
        }
    }

    private String modelName(String model) {
        return model == null || model.isBlank() ? "deepseek-chat" : model.trim();
    }

    public record GenerationResult(boolean usedModel, String model, String text) {
    }
}