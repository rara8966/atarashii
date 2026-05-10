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
public class DoubaoClient {
    private static final String BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public GenerationResult analyzeImage(String endpoint, String apiKey, String base64Jpeg, String prompt) {
        if (apiKey == null || apiKey.isBlank() || endpoint == null || endpoint.isBlank()) {
            return new GenerationResult(false, endpoint != null ? endpoint : "doubao", "未配置豆包 API Key 或 Endpoint。");
        }
        try {
            List<Map<String, Object>> content = List.of(
                    Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64," + base64Jpeg)),
                    Map.of("type", "text", "text", prompt)
            );
            Map<String, Object> payload = Map.of(
                    "model", endpoint.trim(),
                    "max_tokens", 600,
                    "messages", List.of(Map.of("role", "user", "content", content))
            );
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = response.body();
                return new GenerationResult(false, endpoint, "豆包返回错误：HTTP " + response.statusCode() + " " + body.substring(0, Math.min(200, body.length())));
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) {
                return new GenerationResult(false, endpoint, "豆包未返回有效文本。");
            }
            return new GenerationResult(true, endpoint, text.trim());
        } catch (Exception ex) {
            return new GenerationResult(false, endpoint, "豆包调用失败：" + ex.getMessage());
        }
    }

    public record GenerationResult(boolean usedModel, String model, String text) {}
}
