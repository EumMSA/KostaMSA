package com.oopsw.authservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oopsw.authservice.dto.TokenResponse;
import com.oopsw.authservice.dto.UserLoginRequest;
import com.oopsw.authservice.service.AuthService;
import com.oopsw.authservice.support.CookieUtil;
import com.oopsw.authservice.support.JwtProvider;
import com.oopsw.authservice.userdetails.AccountDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final JwtProvider jwtProvider;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager,
                                   JwtProvider jwtProvider,
                                   AuthService authService,
                                   ObjectMapper objectMapper) {
        super(authenticationManager);
        this.jwtProvider = jwtProvider;
        this.authService = authService;
        this.objectMapper = objectMapper;
        super.setFilterProcessesUrl("/api/auth/login");
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        try {
            UserLoginRequest userLoginRequest = objectMapper.readValue(request.getInputStream(), UserLoginRequest.class);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userLoginRequest.getUsername(), userLoginRequest.getPassword());
            return getAuthenticationManager().authenticate(authToken);
        } catch (IOException e) {
            throw new RuntimeException("로그인 요청 JSON 파싱 실패", e);
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        AccountDetails principal = (AccountDetails) authResult.getPrincipal();
        String username = principal.getUsername();
        String role = principal.getAccount().getRole();

        TokenResponse tokens = authService.issueToken(username, role);

        response.setHeader(JwtProvider.HEADER, JwtProvider.PREFIX + tokens.getAccessToken());
        response.addHeader(HttpHeaders.SET_COOKIE,
                CookieUtil.refreshCookie(tokens.getRefreshToken(), jwtProvider.getRefreshExpMillis()).toString());

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
    }
}
