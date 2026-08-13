package com.oopsw.authservice.support;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public class CookieUtil {
    public static ResponseCookie refreshCookie(String token, long maxAgeMillis) {
        return ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(false)
                .path("/api/auth")
                .maxAge(Duration.ofMillis(maxAgeMillis))
                .sameSite("Lax")
                .build();
    }

    public static ResponseCookie deleteRefreshCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false)
                .path("/api/auth")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }
}
