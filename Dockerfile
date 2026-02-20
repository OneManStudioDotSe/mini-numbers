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

# Stage 2: Runtime (minimal Alpine image)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install wget for health checks
RUN apk add --no-cache wget

# Create non-root user for security
RUN addgroup -S analytics && adduser -S analytics -G analytics

# Copy the fat JAR from build stage
COPY --from=build /app/build/libs/*-all.jar app.jar

# Create data and backup directories with proper ownership
RUN mkdir -p /app/data /app/backups && chown -R analytics:analytics /app

# Switch to non-root user
USER analytics

# Expose application port
EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Health check using the /health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
