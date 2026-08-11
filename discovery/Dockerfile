# 빌드 스테이지: 루트에서 이 모듈만 빌드
FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :discovery:bootJar --no-daemon

# 실행 스테이지: 빌드된 jar만 담은 가벼운 이미지
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/discovery/build/libs/*.jar app.jar
EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
