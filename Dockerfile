# ==============================================================
# Mini Numbers - Multi-stage Docker Build
# ==============================================================
# Build:  docker build -t mini-numbers .
# Run:    docker run -p 8080:8080 -v mini-numbers-data:/app/data mini-numbers
#
# Everything the app writes (the setup wizard's .env, the SQLite database) goes to
# /app/data, its working directory, so one volume keeps all of it across upgrades.
# ==============================================================

# Stage 1: Build the fat JAR (Gradle wrapper, so CI, local and Docker use the same version)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Gradle configuration first for dependency caching
COPY build.gradle.kts settings.gradle.kts gradle.properties gradlew ./
COPY gradle ./gradle

# Download dependencies (cached unless build files change)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build
COPY src ./src
RUN ./gradlew buildFatJar --no-daemon

# Stage 2: Runtime (minimal Alpine image)
FROM eclipse-temurin:21-jre-alpine-3.23
WORKDIR /app

# This product includes GeoLite2 data created by MaxMind, available from https://www.maxmind.com
LABEL org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.source="https://github.com/OneManStudioDotSe/mini-numbers" \
      com.maxmind.geolite2.attribution="This product includes GeoLite2 data created by MaxMind, available from https://www.maxmind.com"

# Install wget for health checks
RUN apk add --no-cache wget

# Create non-root user for security
RUN addgroup -S analytics && adduser -S analytics -G analytics

# Copy the fat JAR from build stage (owned by root, read-only for the app user)
COPY --from=build /app/build/libs/*-all.jar /app/app.jar

# Create data and backup directories with proper ownership
RUN mkdir -p /app/data /app/backups && chown analytics:analytics /app/data /app/backups

# The working directory is the data volume, so .env and stats.db persist
WORKDIR /app/data

# Switch to non-root user
USER analytics

# Expose application port. PORT wins over the SERVER_PORT the setup wizard writes to .env, so the
# container keeps listening where the proxy expects it; platforms that inject PORT (Render) override it.
ENV PORT=8080
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Health check using the /health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
