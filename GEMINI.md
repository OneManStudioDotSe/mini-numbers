# Gemini CLI - Mini Numbers Mandates

This document provides project-specific instructions and context for Gemini CLI when working on the Mini Numbers project.

## Project Context
Mini Numbers is a privacy-first, self-hosted web analytics platform built with Kotlin and Ktor.

## Core Mandates
- **Privacy First:** Never introduce features that compromise visitor privacy (e.g., persistent cookies, PII storage). Adhere to the three privacy modes: STANDARD, STRICT, and PARANOID.
- **Lightweight:** Keep the client-side `tracker.js` as small as possible (aim for < 2KB).
- **Service Management:** Use `ServiceManager` for all lifecycle-related operations. Ensure new services are properly integrated into the `initialize` and `reload` flows.
- **Database Integrity:** Use Exposed ORM for all database interactions. Ensure migrations or schema changes are reflected in `DatabaseFactory` and individual table definitions in `src/main/kotlin/db/`.
- **Testing:** Maintain high test coverage. Always add tests for new features or bug fixes in `src/test/kotlin/`. Integration tests should use the `test-dbs/` directory.

## Development Workflows

### Build & Run
- `brew install openjdk@21` (if not installed)
- `./gradlew run`: Start the server in development mode.
- `./gradlew buildFatJar`: Create a production-ready JAR.
- `./gradlew minifyTracker`: Manually trigger tracker minification.

### Testing
- `./gradlew test`: Run the full test suite.
- Tests use system properties for configuration (see `build.gradle.kts`).

### Static Analysis
- `./gradlew detekt`: Run static analysis. Follow existing rules in `config/detekt/detekt.yml`.

## Architectural Guidelines
- **Unified Entry Point:** `Application.kt` is the main entry point.
- **Routing:** API endpoints are defined in `Routing.kt` and `WidgetRouting.kt`. Admin endpoints are session-auth protected.
- **Caching:** Use Caffeine for query and GeoIP caching to maintain performance.
- **Validation:** Use `InputValidator.kt` for all incoming data to the `/collect` and admin endpoints.

## Tech Stack Reminders
- Kotlin 2.3.0, Ktor 3.4.0
- Exposed ORM (Postgres/SQLite)
- Caffeine Cache
- MaxMind GeoIP2
- Vanilla JS/CSS for frontend
