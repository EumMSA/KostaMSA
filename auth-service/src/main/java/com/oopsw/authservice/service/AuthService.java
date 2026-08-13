package com.oopsw.authservice.service;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.oopsw.authservice.dto.TokenResponse;
import com.oopsw.authservice.repository.AccountRepository;
import com.oopsw.authservice.repository.RefreshTokenRepository;
import com.oopsw.authservice.repository.entity.Account;
import com.oopsw.authservice.repository.entity.RefreshToken;
import com.oopsw.authservice.support.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AuthService {
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;

    public void createUserAccount(String username, String password, String name, String email) {
        if (username == null || accountRepository.existsByUsername(username)) {
            return;
        }
        accountRepository.save(Account.builder()
                .username(username)
                .password(password)
                .role("ROLE_USER")
                .email(email)
                .name(name)
                .build());
    }

    @Transactional
    public TokenResponse issueToken(String username, String role) {
        String accesssToken = jwtProvider.createAccessToken(username, role);
        String refreshToken = jwtProvider.createRefreshToken(username);
        long expiryDate = System.currentTimeMillis() + jwtProvider.getRefreshExpMillis();

        saveRT(username, refreshToken, expiryDate);

        return TokenResponse.builder()
                .accessToken(accesssToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (refreshToken == null) throw new JWTVerificationException("RT 없음");

        String username;
        try {
            username = jwtProvider.getUsername(refreshToken);
        } catch (Exception e) {
            throw new JWTVerificationException("RT 유효하지 않음");
        }

        RefreshToken saved = refreshTokenRepository.findByUsername(username)
                .orElseThrow(() -> new JWTVerificationException("저장된 RT 없음(로그아웃 상태)"));

        if (!saved.getToken().equals(refreshToken)) {
            refreshTokenRepository.deleteByUsername(username);
            throw new JWTVerificationException("RT 불일치 (탈취 의심)");
        }

        if (saved.getExpiryDate() < System.currentTimeMillis()) {
            refreshTokenRepository.deleteByUsername(username);
            throw new JWTVerificationException("RT 만료");
        }

        Account account = accountRepository.findByUsername(username);

        if (account == null) {
            refreshTokenRepository.deleteByUsername(username);
            throw new JWTVerificationException("계정 없음(삭제/비활성)");
        }

        return issueToken(username, account.getRole());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;

        String username;
        try {
            username = jwtProvider.getUsername(refreshToken);
        } catch (TokenExpiredException e) {
            username = jwtProvider.getUserNameWithoutVerify(refreshToken);
        } catch (JWTVerificationException e) {
            return;
        }
        refreshTokenRepository.deleteByUsername(username);
    }

    @Transactional
    public void saveRT(String username, String refreshToken, long expiryDate) {
        refreshTokenRepository.save(
                refreshTokenRepository.findByUsername(username)
                        .map(rt -> {
                            rt.setToken(refreshToken);
                            rt.setExpiryDate(expiryDate);
                            return rt;
                        })
                        .orElseGet(() -> RefreshToken.builder()
                                .username(username)
                                .token(refreshToken)
                                .expiryDate(expiryDate)
                                .build())
        );
    }
}
