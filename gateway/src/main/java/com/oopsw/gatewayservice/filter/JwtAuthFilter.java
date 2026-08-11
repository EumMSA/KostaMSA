package com.oopsw.gatewayservice.filter;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import reactor.core.publisher.Mono;

// 모든 요청이 지나가는 전역 필터.
// 0단계에서는 통과만 시킨다. 1단계(Auth 분리)에서 이 안에
// "토큰 검증 → 사용자 정보(bId 등)를 헤더에 실어 하위 서비스로 전달"을 채운다.
//
// 여기로 JWT 검증을 끌어올리면 5개 서비스가 각자 검증할 필요가 없어진다.
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // TODO(1단계): Authorization 헤더에서 토큰 추출 → 검증 →
        //   실패 시 401 응답, 성공 시 exchange.mutate()로 X-User-Id 등 헤더 주입.
        //   로그인/회원가입 등 인증 예외 경로는 화이트리스트로 통과.
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 낮을수록 먼저 실행. 라우팅보다 앞에서 검증하도록 음수로 둔다.
        return -1;
    }
}
