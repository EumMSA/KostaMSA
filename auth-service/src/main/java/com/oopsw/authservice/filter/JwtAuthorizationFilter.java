package com.oopsw.authservice.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.oopsw.authservice.repository.entity.Account;
import com.oopsw.authservice.support.JwtProvider;
import com.oopsw.authservice.userdetails.AccountDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
public class JwtAuthorizationFilter extends BasicAuthenticationFilter {
    private final JwtProvider jwtProvider;

    public JwtAuthorizationFilter(AuthenticationManager authenticationManager, JwtProvider jwtProvider) {
        super(authenticationManager);
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String header = request.getHeader(JwtProvider.HEADER);

        if (header == null || !header.startsWith(JwtProvider.PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(JwtProvider.PREFIX.length());

        try {
            DecodedJWT decodedToken = jwtProvider.verify(token);
            String username = decodedToken.getSubject();
            String role = decodedToken.getClaim("role").asString();
            Account account = new Account();
            account.setUsername(username);
            account.setRole(role);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            new AccountDetails(account), null, List.of(new SimpleGrantedAuthority(role)));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (TokenExpiredException e) {
            SecurityContextHolder.clearContext();
            response.setHeader("Token-Status", "expired");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            log.error("[JwtAuthorizationFilter] TokenExpiredException : 토큰만료");
        } catch (JWTVerificationException e) {
            SecurityContextHolder.clearContext();
            response.setHeader("Token-Status", "invalid");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            log.error("[JwtAuthorizationFilter] JWTVerificationException : 토큰검증X");
        }

        chain.doFilter(request, response);
    }
}
