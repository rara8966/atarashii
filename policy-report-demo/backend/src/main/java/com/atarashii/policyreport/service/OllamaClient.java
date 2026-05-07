package com.atarashii.policyreport.service;

import com.atarashii.policyreport.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OllamaClient {
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaClient(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(3, properties.getOllamaTimeoutSeconds())))
                .build();
    }

    public List<String> installedModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getOllamaBaseUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode models = objectMapper.readTree(response.body()).path("models");
            List<String> names = new ArrayList<>();
            for (JsonNode model : models) {
                names.add(model.path("name").asText());
            }
            return names;
        } catch (Exception ex) {
            return List.of();
        }
    }

    public boolean reachable() {
        return !installedModels().isEmpty();
    }

    public GenerationResult generate(String prompt) {
        if (!properties.isOllamaEnabled()) {
            return new GenerationResult(false, "", "Ollama 已在配置中关闭");
        }
        GenerationResult primary = generateWithModel(properties.getOllamaModel(), prompt);
        if (primary.usedOllama()) {
            return primary;
        }
        if (!properties.getOllamaFallbackModel().equals(properties.getOllamaModel())) {
            return generateWithModel(properties.getOllamaFallbackModel(), prompt);
        }
        return primary;
    }

    private GenerationResult generateWithModel(String model, String prompt) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("prompt", prompt);
            payload.put("stream", false);
            payload.put("options", Map.of("temperature", 0.15, "num_ctx", 8192));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getOllamaBaseUrl() + "/api/generate"))
                    .timeout(Duration.ofSeconds(Math.max(10, properties.getOllamaTimeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new GenerationResult(false, model, "Ollama 返回状态码 " + response.statusCode());
            }
            String text = objectMapper.readTree(response.body()).path("response").asText().trim();
            if (text.isBlank()) {
                return new GenerationResult(false, model, "Ollama 返回内容为空");
            }
            return new GenerationResult(true, model, text);
        } catch (Exception ex) {
            return new GenerationResult(false, model, ex.getMessage());
        }
    }

    public record GenerationResult(boolean usedOllama, String model, String text) {
    }
}