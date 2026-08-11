# KostaMSA

기존 모놀리식(KostaErpServer)을 MSA로 전환하는 새 모노레포.
Gradle 멀티 모듈 구조이며, 각 서비스는 독립 실행되는 Spring Boot 앱이다.

## 현재 상태: 0단계 (인프라 뼈대)

- `discovery` — Eureka 서버 (:8761), 서비스 등록·발견
- `gateway` — Spring Cloud Gateway (:9000), 라우팅 + JWT 검증 자리
- 공유 DB — Postgres 컨테이너 1개 (5개 서비스가 논리적으로 나눠 씀)

버전: Spring Boot 3.5.4 / Spring Cloud 2025.0.0 (Northfields) / Java 17

## 처음 한 번: Gradle Wrapper 생성

이 레포에는 wrapper가 포함돼 있지 않으니, 로컬에 gradle이 있으면 한 번 실행:

```
gradle wrapper --gradle-version 8.10
```

이후로는 `./gradlew` 로 빌드한다.

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
```

### 방법 B: docker compose로 한 번에

```
docker compose up --build
```

필요한 것만 골라 띄우기 (서비스가 늘어난 뒤):

```
docker compose up db discovery gateway auth-service
```

## 확인

- Eureka 대시보드: http://localhost:8761
  → gateway가 등록되어 있으면 정상
- 게이트웨이 라우팅 목록: http://localhost:9000/actuator/gateway/routes

## 다음 단계

1. **1단계 Auth 분리**: `auth-service` 모듈 추가 → `settings.gradle`에 include,
   `docker-compose.yml`·gateway `application.yml`의 주석 라우팅을 푼다.
   JWT 검증 로직을 `gateway/filter/JwtAuthFilter.java`에 채운다.
2. 2단계 Statistics → 3단계 Notice → 4단계 Inventory+Sales 순으로 진행.

각 단계 끝에 반드시 프론트(EUM) 화면으로 실제 동작을 확인한 뒤 다음으로.
