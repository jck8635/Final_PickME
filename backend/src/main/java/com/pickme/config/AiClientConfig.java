package com.pickme.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** FastAPI(AI 서버) 호출용 RestClient. LLM 응답이 느릴 수 있어 읽기 제한시간을 넉넉히 둡니다. */
@Configuration
public class AiClientConfig {

    @Bean
    RestClient aiRestClient(RestClient.Builder builder, PickmeProperties props) {
        // JDK HttpClient 는 기본으로 HTTP/2 업그레이드(h2c)를 시도하는데,
        // uvicorn 이 이를 거부하면서 POST 본문이 사라집니다(422). HTTP/1.1 로 고정합니다.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(90));
        return builder.baseUrl(props.aiServerUrl()).requestFactory(factory).build();
    }
}
