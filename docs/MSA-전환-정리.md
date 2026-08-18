# KostaERP → MSA 전환 정리

기존 모놀리식 프로젝트(1~3차)를 MSA로 전환하기 위한 논의와 결정 사항 정리.
학습(MSA 개념 이해)이 주목적.

---

## 1. MSA 기본 개념 정리

**Q. MSA는 서비스 단위로 분리하는 것인가?**
맞다. 정확히는 비즈니스 기능(도메인) 단위로 분리한다. 너무 잘게 쪼개면(과분할)
관리 포인트만 늘어나므로, 이 프로젝트 규모에서는 5개 논리 서비스가 적당하다.

**Q. 서비스와 연관된 DB도 함께 분리되나?**
이론상 "Database per Service"가 원칙이지만, 이 프로젝트에서는 전부 분리하지 않는다.
- Auth는 조인 의존이 적어 분리 가능
- Inventory·Sales·Notice·Statistics는 조인·트랜잭션으로 강하게 엮여 있어
  물리 분리 시 분산 트랜잭션(Saga)·분산 조회를 직접 구현해야 함 → 팀 프로젝트 범위 초과

**Q. 분리된 서비스는 독립 실행 가능한 모듈이어야 하나?**
그렇다. 각 서비스는 자체 프로세스로 단독 기동되고 자체 포트를 연다.
단, "독립 실행"과 "런타임 의존 없음"은 다르다. 실행은 독립적이어도,
기능 수행 시 다른 서비스의 API를 호출하거나 이벤트를 구독하는 것은 정상이다.

---

## 2. 핵심 결정

| 항목 | 결정 | 이유 |
|------|------|------|
| 분리 범위 | **서비스만 논리 분리** (DB는 물리적으로 1개 공유) | 학습 목적 · 분산 트랜잭션/조회 난이도 회피 |
| DB 규칙 | 물리 1개, **각 서비스는 자기 테이블만 직접 접근** · 남의 데이터는 API로 조회 | 공유해도 MSA 감각 유지 |
| 저장소 | **새 레포 `KostaMSA`** (모노레포) | 기존 히스토리와 분리 · 원본은 참조용 보존 |
| 빌드 | Gradle 멀티 모듈 | 클론 한 번 · 서비스 경계 넘는 변경도 한 PR |
| 배포 | **AWS** (Docker 이미지, 서비스별) | EC2+compose(간단) 또는 ECS/EKS(정석) |

### 원본 분석 후 추가된 결정 (2026-08-18)

| # | 항목 | 결정 | 비고 |
|---|------|------|------|
| 1 | **DB 종류** | 원본 MariaDB → **Postgres 유지** | 매퍼 SQL 27곳 방언 변환 필요 (4절) |
| 2 | **계정 정본** | **`account` 테이블** (JPA) | `USERINFO`는 사업장 정보용으로만. `ErpUserDetailsService`·`JsonLoginFilter`는 잔재로 폐기 |
| 3 | **`used` / `disposals` 소유** | **inventory-service** | sales·notice는 API로 조회 |
| 4 | **회원가입/OCR/BRN** | **1.5단계로 분리** | 외부 서비스 2개 의존 → 로그인 검증과 섞지 않음 |
| 5 | **기존 데이터** | **버리고 샘플 데이터로 시작** | 새 환경에서 작업. MariaDB 덤프 불필요 |
| 6 | **영속성 스택** | **부분 전환** (전면 JPA 전환 안 함) | 5절 |
| 7 | **DB 작업 시점** | **서비스 분리와 같은 단계에서** | 별도 0.5단계 두지 않음 |

---

## 3. 서비스 구성 (논리 5개 + 인프라 2)

| 서비스 | 포트 | 역할 | 소유 테이블 |
|--------|------|------|-----------------|
| discovery (Eureka) | 8761 | 서비스 등록·발견 | - |
| gateway | 9000 | 라우팅 + JWT 검증 + CORS | - |
| auth-service | 9001 | 로그인·토큰 (OCR·BRN 연동) | account, refresh_token, userinfo, admin_user |
| inventory-service | 9002 | 식자재·재고·폐기 | foodm, foodc, **used**, **disposals**, reason |
| sales-service | 9003 | 메뉴·매출·매입 | menus, menuc, sales, revenue, purchase |
| notice-service | 9004 | 알림 | exp_notice, out_of_stock_notice, disposal_notice, stock_notice_setting |
| statistics-service | 9005 | 읽기 전용 집계 | (읽기만) |

**전체 흐름**: React → Gateway(JWT 검증·CORS·라우팅) → 각 서비스 → 공유 DB.

### 게이트웨이 ↔ 서비스 규약

게이트웨이가 JWT를 검증하고 아래 헤더로 바꿔 전달한다. 서비스는 JWT를 다시 검증하지 않는다.

| 헤더 | 내용 |
|---|---|
| `X-User-Id` | username = **사업자등록번호(bId)** |
| `X-User-Role` | `ROLE_USER` / `ROLE_MANAGER` |

> **중요**: 원본에서 `bId = accountDetails.getUsername()` 규약을 쓰고 있다(컨트롤러 18곳).
> 분리된 서비스에는 Security 컨텍스트가 없으므로, 각 서비스에 헤더를 읽어
> SecurityContext를 채우는 필터(`GatewayAuthenticationFilter`)를 둔다.
> 그러면 컨트롤러는 기존과 동일하게 `@AuthenticationPrincipal`을 쓸 수 있다.

---

## 4. 원본(KostaErpServer) 분석 결과

Java 218개 파일 / 약 8,600줄. Maven + Spring Boot 3.5.14. **MyBatis(XML) + JPA 혼용.**

### 가져올 것 / 버릴 것

| 가져옴 | 버림 |
|---|---|
| `advice/restcontroller/` (17개) — React가 쓰는 API | `advice/controller/` (11개) — Thymeleaf 화면용 |
| `service/`, `repository/`, `dto/`, `vo/` | `templates/` (18개), `static/` (JS·CSS) |
| **테스트 39개** — 이관 시 안전망으로 함께 이동 | `advice/restcontroller/testmain.java` |
| 매퍼 XML 9개 (101 쿼리 / 1,091줄) | thymeleaf·devtools 의존성 |

### Postgres 방언 변환 목록 (총 27곳)

| 변환 | 위치 | 건수 |
|---|---|---|
| `IFNULL` → `COALESCE` | addfoodmaterial, disposal, notice | 8 |
| `CURDATE()` → `CURRENT_DATE` | disposal, foodmaterial, notice | 8 |
| `DATE_FORMAT` → `TO_CHAR` | statisticsMapper | 4 |
| `STR_TO_DATE` → `TO_DATE` | addfoodmaterial | 4 |
| `LIMIT o, n` → `LIMIT n OFFSET o` | addfoodmaterial, disposal | 3 |
| `DATE_ADD(... INTERVAL n DAY)` → interval 연산 | foodmaterial | 1 |
| `DATEDIFF(a,b)` → 날짜 뺄셈 | notice | 1 |
| **`UPDATE ... JOIN ... SET` → `UPDATE ... SET ... FROM`** | menu.xml (판매 차감) | **1** |

백틱 사용은 0건.

### 식별자 대소문자 정책

MariaDB는 관대하지만 Postgres는 아니다. 원본은 `globally_quoted_identifiers: true` +
대문자 테이블명(`FOODM`, `MENUS`, `USED`)을 쓰는데, JPA는 대문자를 따옴표로 감싸 생성하고
MyBatis SQL은 따옴표가 없어 소문자로 해석된다 → **테이블을 못 찾는다.**

> **결정: 전부 소문자로 통일한다.** `globally_quoted_identifiers`를 끄고,
> `@Table(name="MENUS")` 를 소문자로 바꾼다. 매퍼 SQL은 따옴표가 없으므로 그대로 동작한다.

### 알려진 버그 (이관하며 고칠 것)

- `MenuServiceImpl.saleMenu()` 매출 채번: `RV%03d` 문자열 증가 방식 →
  동시 판매 시 ID 충돌, 1000건 초과 시 포맷 깨짐. **시퀀스로 교체.**

---

## 5. 영속성 스택 방침 (MyBatis vs JPA)

원본 매퍼는 **쿼리 101개 / 1,091줄**, 그런데 **동적 SQL(`<if>`, `<foreach>`)은 0건**.
MyBatis를 "XML에 SQL 담아두는 통"으로만 쓰고 있다.
반면 **절반 가까이가 집계·리포팅 쿼리**(statistics 13개 전부, disposal 대부분)로,
JPA가 가장 취약한 영역이다. 전면 전환해도 결국 native query가 되어 방언 문제가 남는다.

> **결론: 전면 전환하지 않는다. 셋으로 나눈다.**

| 대상 | 방침 |
|---|---|
| **엔티티** | **전부 작성한다.** DDL이 소스에 없는 7개 테이블(foodm, foodc, used, disposals, reason, disposal_notice, userinfo)도 엔티티를 만들면 `ddl-auto`가 스키마를 생성한다 → MariaDB 덤프 불필요 |
| **단순 CRUD 쿼리** | JPA 파생 메서드로 전환. 방언 변환 작업 상당수가 자동 소멸 |
| **집계·리포팅 쿼리** | **손대지 않는다.** MyBatis 유지 또는 native query. `saleMenu`의 차감 쿼리도 집합 연산이므로 그대로 |

**그리고 한꺼번에 하지 않는다.** 서비스 분리와 영속성 재작성을 동시에 하면
장애 원인 구분이 불가능해지고, DAO 테스트 9개(안전망)가 먼저 무너진다.
**각 분리 단계 안에서 그 서비스 몫만** 전환한다.

---

## 6. 레포 구성

- `EUM` — 프론트(React), 그대로
- `OCR` — FastAPI, 그대로 (`:8000`)
- `BRN` — NestJS, 그대로 (`:3000`)
- `KostaErpServer` — 기존 모놀리식, **참조용 보존** (더는 커밋 안 함)
- `KostaMSA` — 새 모노레포 ← 앞으로 여기서 작업

## 7. 패키지 구조

```
KostaMSA/
├── settings.gradle        (서브프로젝트 등록)
├── build.gradle           (공통 의존성·플러그인)
├── docker-compose.yml     (로컬: DB + discovery + gateway + 서비스)
├── .env.example           (JWT_SECRET 등)
├── db/init/               (샘플 데이터 seed)
├── docs/                  (이 문서)
├── discovery/             com/oopsw/discoveryserver/
├── gateway/               com/oopsw/gatewayservice/{config, filter}
├── auth-service/          com/oopsw/authservice/{controller,service,repository,...}
├── inventory-service/     com/oopsw/inventoryservice/{... , client}
├── sales-service/         com/oopsw/salesservice/
├── notice-service/        com/oopsw/noticeservice/
├── statistics-service/    com/oopsw/statisticsservice/
└── common/ (선택)         com/oopsw/common/
```

- 최상위 패키지는 **`com.oopsw`** (원본과 동일). Gradle `group`도 `com.oopsw`로 맞출 것
- 각 도메인 서비스 내부는 기존 모놀리식 계층 구조 그대로
  (controller → service → repository → entity/dto)
- 달라지는 점: 남의 서비스를 HTTP로 부르는 `client/` 패키지가 추가됨
- `common`은 진짜 공유 불가피한 최소한만. 처음엔 없이 시작하고 중복이 쌓이면 그때 생성
  (`GatewayAuthenticationFilter`가 2단계에서 중복되면 첫 후보)
- 주의: 현재 루트 `build.gradle`이 모든 서브프로젝트에 Spring Boot 플러그인을 적용한다.
  `common`을 만들 때는 `bootJar`만 생성되어 라이브러리로 못 쓰므로 조정이 필요하다.

---

## 8. 작업 순서

**원칙**: 인프라 뼈대 먼저 → 리스크 낮은 서비스부터 하나씩. 한 번에 다 쪼개지 않는다.

- [x] **0단계 인프라 뼈대** — discovery + gateway + 공유 DB + docker-compose
- [x] **0단계 정리** — gitignore, .env, 게이트웨이 CORS, JWT 검증 일원화
- [ ] **1단계 Auth 분리** — 로그인/토큰 발급. React 로그인 화면으로 확인 ← **현재**
- [ ] **1.5단계 회원가입·OCR·BRN** — RegistrationService, PythonOcrClient, BusinessRegistrationClient,
      PhoneVerification, 관리자 승인(`/api/manager/**` → `MANAGER` 역할 인가)
- [ ] **2단계 Statistics 분리** — 쓰기 없음. 방언 변환 4곳 + 코드 이동
- [ ] **3단계 Notice 분리** — 첫 서비스 간 통신. `disposals`는 inventory 소유이므로 API 조회
- [ ] **4단계 Inventory + Sales 분리** — 가장 까다로움.
      `used` 소유권, `saleMenu` 트랜잭션 경계, 매출 채번 버그 수정

**전 과정 규칙**
- 각 단계에서 그 서비스가 쓰는 **엔티티 + 샘플 데이터 + 방언 변환**을 함께 처리
- 컨트롤러 이관 시 `@AuthenticationPrincipal` 패턴 유지 (게이트웨이 헤더 필터 덕분)
- 새 공개 엔드포인트를 추가하면 게이트웨이 `JwtAuthFilter.WHITELIST`에도 반영
- 원본 테스트를 함께 옮겨 자동 검증 → 그 다음 React 화면으로 확인
- 문제 생기면 그 단계만 롤백

---

## 9. 로컬 실행 방식

실행 순서: **DB → discovery → gateway → (그 기능에 필요한 서비스만)**

- 로그인 확인 → db + discovery + gateway + auth-service
- 판매 확인 → db + discovery + gateway + sales + inventory
- 통계 확인 → db + discovery + gateway + statistics

```
docker compose up db discovery gateway auth-service
```

DB 초기화(샘플 데이터 재적재): `docker compose down -v`

**Eureka 필요성 참고**: EC2에 compose로 올려 컨테이너 이름으로 서로를 찾으면 Eureka 없이도 가능.
Eureka는 다중 인스턴스 확장이나 순수 학습 목적일 때 의미가 커진다.

---

## 10. 기술 버전

- Spring Boot 3.5.4 (원본은 3.5.14 — 이관 시 맞출지 검토)
- Spring Cloud 2025.0.0 (Northfields)
- Java 17 (원본 dockerfile은 21이었으나 17로 통일)
- Postgres 16 (공유 DB, 컨테이너 1개)
