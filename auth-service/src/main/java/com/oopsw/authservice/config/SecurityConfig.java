package com.oopsw.authservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oopsw.authservice.filter.GatewayAuthenticationFilter;
import com.oopsw.authservice.filter.JwtAuthenticationFilter;
import com.oopsw.authservice.service.AuthService;
import com.oopsw.authservice.support.JwtProvider;
import com.oopsw.authservice.userdetails.AccountDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * auth-service는 토큰 "발급"만 담당한다.
 * 토큰 "검증"은 게이트웨이(JwtAuthFilter)가 하고, 그 결과를
 * X-User-Id / X-User-Role 헤더로 받아 GatewayAuthenticationFilter가 인증을 세운다.
 *
 * CORS도 게이트웨이가 처리하므로 여기서는 설정하지 않는다.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtProvider jwtProvider;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final AccountDetailsService accountDetailsService;

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(accountDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())

            // 게이트웨이가 넘긴 헤더로 인증을 세운다 (로그인 요청에는 헤더가 없으므로 그냥 통과).
            .addFilterBefore(new GatewayAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            // 로그인(/api/auth/login)을 처리하고 토큰을 발급한다.
            .addFilter(new JwtAuthenticationFilter(authenticationManager, jwtProvider, authService, objectMapper))

            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/api/auth/login",
                            "/api/auth/reissue",
                            "/api/auth/logout"
                    ).permitAll()
                    // 컨테이너 헬스체크용
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                    (request, response, authException) -> {
                        log.error("[SecurityConfig] .exceptionHandling : " + authException.getMessage());
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.getWriter().write("{\"message\":\"authentication required\"}");
                    }));

        return http.build();
    }
}
