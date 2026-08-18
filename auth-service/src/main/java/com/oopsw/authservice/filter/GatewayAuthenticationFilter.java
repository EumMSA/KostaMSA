package com.oopsw.authservice.filter;

import com.oopsw.authservice.repository.entity.Account;
import com.oopsw.authservice.userdetails.AccountDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 게이트웨이가 JWT를 검증한 뒤 넘겨준 X-User-Id / X-User-Role 헤더를 읽어
 * SecurityContext를 채운다. 서비스는 JWT를 직접 검증하지 않는다(게이트웨이 책임).
 *
 * 덕분에 컨트롤러에서 기존 모놀리식과 동일하게 쓸 수 있다:
 *     @AuthenticationPrincipal AccountDetails accountDetails
 *     String bId = accountDetails.getUsername();
 *
 * 주의: 이 필터는 헤더를 무조건 신뢰한다. 따라서 서비스 포트(9001)가
 *       외부에 직접 노출되면 인증을 우회할 수 있다.
 *       배포 시에는 게이트웨이만 공개하고 서비스 포트는 내부망에 둔다.
 */
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String username = request.getHeader(USER_ID_HEADER);
        String role = request.getHeader(USER_ROLE_HEADER);

        if (username != null && !username.isBlank()
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            Account account = new Account();
            account.setUsername(username);
            account.setRole(role);

            List<SimpleGrantedAuthority> authorities =
                    (role == null || role.isBlank())
                            ? List.of()
                            : List.of(new SimpleGrantedAuthority(role));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new AccountDetails(account), null, authorities));
        }

        chain.doFilter(request, response);
    }
}
