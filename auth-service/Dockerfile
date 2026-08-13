FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle :auth-service:bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/auth-service/build/libs/*.jar app.jar
EXPOSE 9001
ENTRYPOINT ["java", "-jar", "app.jar"]
