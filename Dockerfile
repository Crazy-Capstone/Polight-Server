# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

# Wrapper first: the Gradle distribution download is cached apart from sources
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --version --no-daemon

# Dependency layer: only invalidated when the build scripts change
COPY settings.gradle build.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY src ./src
RUN ./gradlew bootJar --no-daemon

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=builder /workspace/build/libs/*.jar app.jar
RUN chown spring:spring app.jar

USER spring
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
