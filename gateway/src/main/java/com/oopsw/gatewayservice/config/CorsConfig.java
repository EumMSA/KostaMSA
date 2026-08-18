package com.oopsw.gatewayservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS는 게이트웨이(입구) 한 곳에서만 처리한다.
 * 하위 서비스가 각자 CORS 헤더를 붙이면 Access-Control-Allow-Origin이 중복되어
 * 브라우저가 응답을 거부하므로, 서비스 쪽 CorsFilter는 두지 않는다.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 쿠키(refreshToken)를 주고받으므로 credentials 허용.
        // 이 경우 Allow-Origin에 "*"를 쓸 수 없어 originPattern을 사용한다.
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        // React가 응답에서 직접 읽어야 하는 헤더들.
        // Authorization: 재발급된 액세스 토큰
        // Token-Status: expired / invalid 구분용
        config.setExposedHeaders(List.of("Authorization", "Token-Status"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
