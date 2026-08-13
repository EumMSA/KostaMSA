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
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String WHITELIST_PREFIX = "/api/auth";

    private final Algorithm algorithm;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        this.algorithm = Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith(WHITELIST_PREFIX)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return unauthorized(exchange, null);
        }

        String token = header.substring(PREFIX.length());

        try {
            DecodedJWT decoded = JWT.require(algorithm).build().verify(token);
            String username = decoded.getSubject();
            String role = decoded.getClaim("role").asString();

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
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
