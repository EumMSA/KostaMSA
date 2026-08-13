package com.oopsw.authservice.controller;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.oopsw.authservice.dto.TokenResponse;
import com.oopsw.authservice.service.AuthService;
import com.oopsw.authservice.support.CookieUtil;
import com.oopsw.authservice.support.JwtProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@RestController
public class JwtAuthRestController {

    private final JwtProvider jwtProvider;
    private final AuthService authService;

    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            TokenResponse tokens = authService.reissue(refreshToken);

            response.setHeader(JwtProvider.HEADER, JwtProvider.PREFIX + tokens.getAccessToken());
            response.addHeader(HttpHeaders.SET_COOKIE, CookieUtil.refreshCookie(tokens.getRefreshToken(), jwtProvider.getRefreshExpMillis()).toString());
            return ResponseEntity.status(HttpStatus.OK).build();

        } catch (JWTVerificationException e) {
            log.error("[JwtAuthRestController] reissue : {} 재발급 오류", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, CookieUtil.deleteRefreshCookie().toString());
        return ResponseEntity.ok("로그아웃 성공");
    }
}
