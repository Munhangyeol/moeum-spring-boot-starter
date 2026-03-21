package io.moeum.starter.config;

import io.moeum.starter.client.MoeumClient;
import io.moeum.starter.properties.MoeumProperties;
import io.moeum.starter.service.MoeumRegistrationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(DataSource.class)
@EnableConfigurationProperties(MoeumProperties.class)
@ConditionalOnProperty(prefix = "moeum", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MoeumAutoConfiguration {

    @Bean
    public MoeumClient moeumClient(MoeumProperties properties) {
        return new MoeumClient(properties.getServerUrl(), properties.getApiKey());
    }

    @Bean
    public MoeumRegistrationService moeumRegistrationService(MoeumProperties properties,
                                                              MoeumClient moeumClient,
                                                              DataSource dataSource) {
        return new MoeumRegistrationService(properties, moeumClient, dataSource);
    }
}
