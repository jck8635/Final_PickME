package com.pickme.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 pickme.* 설정 */
@ConfigurationProperties(prefix = "pickme")
public record PickmeProperties(String aiServerUrl, List<String> corsAllowedOrigins) {
}
