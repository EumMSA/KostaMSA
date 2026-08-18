# KostaMSA

기존 모놀리식([KostaErpServer](https://github.com/EumMSA/KostaErpServer))을 MSA로 전환하는 새 모노레포.
Gradle 멀티 모듈 구조이며, 각 서비스는 독립 실행되는 Spring Boot 앱이다.

전환 배경과 단계별 계획은 [docs/MSA-전환-정리.md](docs/MSA-전환-정리.md) 참고.

## 현재 상태

| 모듈 | 포트 | 상태 |
|---|---|---|
| `discovery` (Eureka) | 8761 | 완료 |
| `gateway` (Spring Cloud Gateway) | 9000 | 완료 — 라우팅 + JWT 검증 + CORS |
| `auth-service` | 9001 | 로그인/토큰 발급 완료, 회원가입은 1.5단계 |
| `statistics-service` | 9005 | 예정 (2단계) |
| `notice-service` | 9004 | 예정 (3단계) |
| `inventory-service` | 9002 | 예정 (4단계) |
| `sales-service` | 9003 | 예정 (4단계) |

버전: Spring Boot 3.5.4 / Spring Cloud 2025.0.0 (Northfields) / Java 17 / Postgres 16

## 처음 한 번: 환경변수 설정

```
cp .env.example .env
```

`.env`의 `JWT_SECRET`을 채운다. **gateway와 auth-service가 반드시 같은 값을 써야 한다** —
다르면 로그인은 되는데 이후 모든 요청이 401이 된다.

```
openssl rand -hex 32
```

## 로컬 실행

### 방법 A: gradle로 개별 실행 (개발 중 권장)

DB를 먼저 띄우고:

```
docker compose up -d db
```

각 모듈을 순서대로 (별도 터미널에서):

```
./gradlew :discovery:bootRun
./gradlew :gateway:bootRun
./gradlew :auth-service:bootRun
```

`bootRun`은 `.env`를 자동으로 읽지 않으므로 셸에서 환경변수를 넘겨야 한다.

### 방법 B: docker compose로 한 번에

```
docker compose up --build
```

필요한 것만 골라 띄우기:

```
docker compose up db discovery gateway auth-service
```

DB를 완전히 초기화하려면 (샘플 데이터 재적재):

```
docker compose down -v
```

## 확인

- Eureka 대시보드: http://localhost:8761 → gateway와 auth-service가 등록되어 있으면 정상
- 게이트웨이 라우팅 목록: http://localhost:9000/actuator/gateway/routes

## 구조 규칙

- **JWT 검증은 게이트웨이에서만.** 서비스는 게이트웨이가 넣어준
  `X-User-Id` / `X-User-Role` 헤더를 신뢰한다. 서비스에서 JWT를 다시 검증하지 않는다.
- **`X-User-Id`는 사업자등록번호(bId)다.** 모놀리식에서 `username == bId` 규약을 쓰고 있어
  모든 도메인 서비스가 이 값으로 데이터를 스코프한다.
- **CORS는 게이트웨이에서만.** 서비스가 각자 CORS 헤더를 붙이면 중복되어 브라우저가 거부한다.
- **DB는 물리적으로 1개를 공유**하되, 각 서비스는 자기 테이블만 직접 접근한다.
  남의 데이터가 필요하면 API로 조회한다.
- 서비스 포트(9001 등)는 배포 시 외부에 노출하지 않는다. 게이트웨이만 공개한다.
  (서비스는 헤더를 무조건 신뢰하므로 직접 접근 시 인증 우회가 가능하다)
