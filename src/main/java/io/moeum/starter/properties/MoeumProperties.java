package io.moeum.starter.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "moeum")
public class MoeumProperties {

    /** 스타터 활성화 여부 (기본값: true) */
    private boolean enabled = true;

    /** DTMS 서버 주소 (예: http://localhost:8080) */
    private String serverUrl;

    /** X-Api-Key 헤더 값 */
    private String apiKey;

    /** DTMS의 Workspace name과 매칭 */
    private String workspaceKey;

    /** DTMS의 Project name과 매칭 */
    private String projectKey;

    /** 애플리케이션 이름 (Project 설명에 사용) */
    private String applicationName;
}
