package io.moeum.starter.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

@Slf4j
public class MoeumClient {

    private final RestClient restClient;
    private final String apiKey;

    public MoeumClient(String serverUrl, String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(serverUrl)
                .build();
        this.apiKey = apiKey;
    }

    public void register(RegisterPayload payload) {
        restClient.post()
                .uri("/api/integrations/register")
                .header("X-Api-Key", apiKey)
                .header("Content-Type", "application/json")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        log.info("[Moeum] 등록 성공: projectKey={}", payload.getProjectKey());
    }
}
