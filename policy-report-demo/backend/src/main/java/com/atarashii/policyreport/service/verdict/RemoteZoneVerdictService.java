package com.atarashii.policyreport.service.verdict;

import com.atarashii.policyreport.config.CloudClientProperties;
import com.atarashii.policyreport.security.MachineFingerprint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/**
 * 功能区合规判定的“客户端实现”：本地不含算法，HTTP 调用云端服务器。
 * <p>
 * 仅在 client profile 下作为 {@link ZoneVerdictApi} 的 Bean 生效；
 * 服务器/本地构建用真算法 {@code FunctionalZoneVerdictService}。
 * <p>
 * 每次请求都带授权头 {@code X-License-Key} / {@code X-Machine-Id}，
 * 服务器的 LicenseFilter 校验不通过会返回 403，这里转成可读的报错。
 */
@Service
@Profile("client")
// 仅当显式开启 app.zone-verdict.remote=true 时才走云端。默认（false/缺失）由本地
// FunctionalZoneVerdictService 接管，二者互斥，避免同时存在两个 ZoneVerdictApi Bean。
@ConditionalOnProperty(name = "app.zone-verdict.remote", havingValue = "true")
public class RemoteZoneVerdictService implements ZoneVerdictApi {

    private static final ParameterizedTypeReference<List<ZoneVerdict>> ZONE_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final CloudClientProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient;
    private final String machineId;

    public RemoteZoneVerdictService(CloudClientProperties properties) {
        this.properties = properties;
        this.machineId = properties.getMachineId().isBlank()
                ? MachineFingerprint.current()
                : properties.getMachineId();

        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public List<ZoneVerdict> verdictForProject(String projectId) {
        if (properties.getBaseUrl().isBlank()) {
            throw new IllegalStateException("未配置云端服务器地址（app.cloud-client.base-url）");
        }
        if (properties.getLicenseKey().isBlank()) {
            throw new IllegalStateException("软件尚未激活：缺少授权码");
        }
        String url = properties.getBaseUrl() + "/api/v2/projects/" + projectId + "/functional-zone-verdict";
        try {
            List<ZoneVerdict> body = restClient.get()
                    .uri(url)
                    .header("X-License-Key", properties.getLicenseKey())
                    .header("X-Machine-Id", machineId)
                    .retrieve()
                    .body(ZONE_LIST);
            return body == null ? List.of() : body;
        } catch (RestClientResponseException e) {
            // 服务器明确回了 HTTP 状态码：优先把它的报错文案透出来
            throw new IllegalStateException(extractServerMessage(e), e);
        } catch (RestClientException e) {
            // 连不上 / 超时
            throw new IllegalStateException("无法连接授权服务器，请检查网络后重试", e);
        }
    }

    /** 从服务器返回体里取 error 字段；取不到则按状态码给默认文案。 */
    private String extractServerMessage(RestClientResponseException e) {
        String fallback = e.getStatusCode().value() == 403
                ? "授权校验未通过，请联系供应方"
                : "云端判定服务返回错误（HTTP " + e.getStatusCode().value() + "）";
        try {
            var node = objectMapper.readTree(e.getResponseBodyAsString());
            String error = node.path("error").asText("");
            return error.isBlank() ? fallback : error;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
