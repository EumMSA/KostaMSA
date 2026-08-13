package com.oopsw.authservice.support;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {
    public static final String HEADER = "Authorization";
    public static final String PREFIX = "Bearer ";

    private final Algorithm algorithm;
    private final long accessExpMillis;
    private final long refreshExpMillis;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration}") Duration accessExp,
            @Value("${jwt.refresh-expiration}") Duration refreshExp) {
        this.algorithm = Algorithm.HMAC256(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpMillis = accessExp.toMillis();
        this.refreshExpMillis = refreshExp.toMillis();
    }

    public String createAccessToken(String username, String role) {
        long now = System.currentTimeMillis();

        log.info("[JwtProvider] createAccessToken : username={}, role={}", username, role);
        return JWT.create()
                .withSubject(username)
                .withClaim("role", role)
                .withIssuedAt(new Date(now))
                .withExpiresAt(new Date(now + accessExpMillis))
                .sign(algorithm);
    }

    public String createRefreshToken(String username) {
        long now = System.currentTimeMillis();

        log.info("[JwtProvider] createRefreshToken : username={}", username);
        return JWT.create()
                .withSubject(username)
                .withIssuedAt(new Date(now))
                .withExpiresAt(new Date(now + refreshExpMillis))
                .sign(algorithm);
    }

    public DecodedJWT verify(String token) {
        return JWT.require(algorithm)
                .build()
                .verify(token);
    }

    public String getUserNameWithoutVerify(String token) {
        return JWT.decode(token).getSubject();
    }

    public String getUsername(String token) {
        return verify(token).getSubject();
    }

    public long getRefreshExpMillis() {
        return refreshExpMillis;
    }
}
