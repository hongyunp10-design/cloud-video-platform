
# Stage 1: Build the Spring Boot application.
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B -ntp -DskipTests package \
    && cp target/*.jar /tmp/app.jar


# Stage 2: Run with a smaller JRE-only image.
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /tmp/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
