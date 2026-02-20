# Contributing to Mini Numbers

Thank you for your interest in contributing to Mini Numbers! This guide will help you get started.

## Development Setup

### Prerequisites

- **JDK 21** (Temurin, Corretto, or similar)
- **Gradle** (included via wrapper)
- **Git**

### Getting Started

```bash
# Clone the repository
git clone https://github.com/your-username/mini-numbers.git
cd mini-numbers

# Run the application
./gradlew run

# Visit http://localhost:8080 to complete the setup wizard
```

### Running Tests

```bash
./gradlew test          # Run all tests
./gradlew detekt        # Run static analysis
./gradlew buildFatJar   # Build executable JAR
```

---

## Project Structure

See [CLAUDE.md](CLAUDE.md) for a full technical architecture reference. Key directories:

```
src/main/kotlin/se/onemanstudio/
├── api/models/         # API request/response models
├── config/             # Configuration system
├── core/               # Core domain logic (security, services)
├── db/                 # Database layer (Exposed ORM)
├── middleware/          # Input validation, rate limiting
├── services/           # External integrations (GeoIP, UA parsing)
└── setup/              # Setup wizard

src/main/resources/
├── static/             # Admin dashboard (HTML, CSS, JS)
└── setup/              # Setup wizard frontend

src/test/kotlin/        # Test suite (166 tests)
```

---

## Code Style

### Kotlin

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Static analysis enforced via [Detekt](https://detekt.dev/) (`./gradlew detekt`)
- Use `@Volatile` and `@Synchronized` for thread-safe singletons
- Prefer `object` declarations for singletons (e.g., `ServiceManager`, `ConfigLoader`)
- Use `kotlinx.serialization` for JSON models

### Frontend (Vanilla JavaScript)

- No frameworks - vanilla JS with namespaced objects
- Follow existing patterns: `Dashboard`, `ChartManager`, `Utils`, `SegmentsManager`
- Use CSS custom properties from `variables.css` for all colors, spacing, fonts
- BEM-like class naming: `.component__element--modifier`
- Escape user-generated content with `Utils.escapeHtml()`

---

## Testing

### Test Organization

Tests are organized by package mirroring the source structure:

| Directory | What it tests |
|-----------|--------------|
| `core/` | Security, hashing, service lifecycle |
| `middleware/` | Input validation, sanitization |
| `services/` | User agent parsing |
| `analytics/` | Analytics calculations |
| `integration/` | HTTP endpoints, end-to-end flows |

### Writing Tests

- Use descriptive test names: `` `init with valid salt succeeds` ``
- Use Ktor's `testApplication` for integration tests
- Keep tests isolated and deterministic
- Test databases are written to `test-dbs/` (gitignored)

---

## Pull Request Process

1. **Fork** the repository and create a feature branch from `main`
2. **Write clear commit messages** - focus on the "why", not the "what"
3. **Add tests** for new functionality
4. **Run the full test suite** before submitting: `./gradlew test detekt`
5. **Open a PR** with:
   - A clear title (under 70 characters)
   - A description explaining what changed and why
   - Any relevant screenshots for UI changes

### PR Checklist

- [ ] Tests pass (`./gradlew test`)
- [ ] Detekt passes (`./gradlew detekt`)
- [ ] No new security vulnerabilities introduced
- [ ] UI changes tested in both light and dark themes
- [ ] Documentation updated if applicable

---

## Reporting Issues

### Bug Reports

Please include:
- Steps to reproduce
- Expected vs. actual behavior
- Environment details (OS, JDK version, browser)
- Relevant log output

### Feature Requests

Please include:
- Description of the feature
- Use case / motivation
- Any relevant examples from other tools

---

## Code Review

Reviewers look for:
- **Correctness** - Does the code do what it claims?
- **Security** - Any new attack vectors? Input validation?
- **Privacy** - Does it maintain the privacy-first design?
- **Performance** - Any unnecessary database queries or DOM operations?
- **Simplicity** - Is the solution the simplest one that works?

---

## License

By contributing to Mini Numbers, you agree that your contributions will be licensed under the [MIT License](LICENSE).
