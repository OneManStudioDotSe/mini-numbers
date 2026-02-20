# ==============================================================
# Mini Numbers - Multi-stage Docker Build
# ==============================================================
# Build:  docker build -t mini-numbers .
# Run:    docker run -p 8080:8080 mini-numbers
# ==============================================================

# Stage 1: Build the fat JAR
FROM gradle:8-jdk21 AS build
WORKDIR /app

# Copy Gradle configuration first for dependency caching
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle

# Download dependencies (cached unless build files change)
RUN gradle dependencies --no-daemon || true

# Copy source code and build
COPY src ./src
RUN gradle buildFatJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install wget for health checks
RUN apk add --no-cache wget

# Create non-root user
RUN addgroup -S analytics && adduser -S analytics -G analytics

# Copy the fat JAR from build stage
COPY --from=build /app/build/libs/*-all.jar app.jar

# Create data and backup directories
RUN mkdir -p /app/data /app/backups && chown -R analytics:analytics /app

USER analytics

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
