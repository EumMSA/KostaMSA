package com.oopsw.gatewayservice.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 검증은 게이트웨이에서만 한다. 검증에 성공하면 사용자 정보를
 * X-User-Id / X-User-Role 헤더로 바꿔 하위 서비스에 넘긴다.
 * 하위 서비스는 JWT를 몰라도 되고 이 헤더만 신뢰하면 된다.
 *
 * 주의: X-User-Id에 담기는 username은 이 프로젝트에서 사업자등록번호(bId)다.
 *       도메인 서비스들이 데이터를 스코프하는 키이므로 반드시 전달되어야 한다.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    /**
     * 토큰 없이 통과시킬 경로.
     * 새 공개 엔드포인트를 추가할 때마다 여기에 넣어야 한다.
     * (1.5단계에서 /api/auth/register, /api/auth/phone/** 추가 예정)
     */
    private static final List<String> WHITELIST = List.of(
            "/api/auth/login",
            "/api/auth/reissue",
            "/api/auth/logout"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final Algorithm algorithm;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        this.algorithm = Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // CORS 프리플라이트(OPTIONS)에는 Authorization 헤더가 실리지 않는다.
        // 여기서 막으면 브라우저의 모든 요청이 401이 되므로 그냥 통과시킨다.
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        String path = request.getURI().getPath();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String header = request.getHeaders().getFirst(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return unauthorized(exchange, null);
        }

        String token = header.substring(PREFIX.length());

        try {
            DecodedJWT decoded = JWT.require(algorithm).build().verify(token);
            String username = decoded.getSubject();
            String role = decoded.getClaim("role").asString();

            ServerHttpRequest mutatedRequest = request.mutate()
                    // 클라이언트가 위조해 보낸 값이 섞이지 않도록 먼저 지우고 다시 넣는다.
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.remove("X-User-Role");
                    })
                    .header("X-User-Id", username)
                    .header("X-User-Role", role)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (TokenExpiredException e) {
            return unauthorized(exchange, "expired");
        } catch (JWTVerificationException e) {
            return unauthorized(exchange, "invalid");
        }
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String tokenStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        if (tokenStatus != null) {
            response.getHeaders().add("Token-Status", tokenStatus);
        }
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
