# db/init

이 디렉터리의 `*.sql` 파일은 **DB 컨테이너를 처음 만들 때 파일명 순서대로 한 번만** 실행된다.
(이미 데이터가 있는 볼륨에는 실행되지 않는다. 다시 돌리려면 `docker compose down -v`)

## 방침

- JPA 엔티티가 있는 테이블은 `ddl-auto: update`가 자동 생성하므로 여기에 DDL을 두지 않는다.
- 여기에는 **샘플 데이터(seed)** 만 넣는다.
- 서비스를 분리하는 각 단계에서, 그 서비스가 쓰는 테이블의 엔티티와 샘플 데이터를 함께 추가한다.

## 파일명 규칙

```
10-auth-seed.sql          1단계   account, refresh_token
20-statistics-seed.sql    2단계
30-notice-seed.sql        3단계
40-inventory-seed.sql     4단계   foodm, foodc, used
40-sales-seed.sql         4단계   menus, menuc, sales, revenue
```

주의: seed는 테이블이 만들어진 뒤에 실행되어야 한다. JPA가 테이블을 만들기 전에
init 스크립트가 먼저 도는 순서 문제가 있으므로, seed는 컨테이너 init이 아니라
서비스 기동 후 수동 실행하거나 `spring.sql.init` 을 쓰는 편이 안전하다.
각 단계에서 실제로 붙여보고 방식을 확정한다.
