# Contributing to Mini Numbers

Thanks for helping. Mini Numbers is Kotlin + Ktor on JDK 21 with a vanilla-JS dashboard (no build step).

## Set up

```bash
git clone https://github.com/OneManStudioDotSe/mini-numbers.git
cd mini-numbers
./gradlew run            # http://localhost:8080 → setup wizard (set PORT=8090 to use another port)
```

## Check your change

```bash
./gradlew test detekt    # JVM tests + static analysis (detekt must report zero issues)
./gradlew buildFatJar    # build/libs/mini-numbers-all.jar
cd e2e && npm ci && npx playwright test   # browser tests against the JAR (needs Chrome)
```

UI changes: check light and dark themes. The browser tests start the JAR on port 8080; if that port is busy,
run them with `E2E_PORT=8096 npx playwright test`. `tests/screenshots.spec.js` rewrites the README screenshots;
skip it unless you mean to update them.

## Where things are

The technical reference — layout, database schema, settings, endpoints, and the definition of done — is
[_docs/ARCHITECTURE.md](_docs/ARCHITECTURE.md). Plans and open gaps are in [_docs/ROADMAP.md](_docs/ROADMAP.md).

## Style

- **Kotlin**: [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html), enforced by
  detekt; `kotlinx.serialization` for JSON; `object` for singletons such as `ServiceManager` and `ConfigLoader`.
- **Frontend**: vanilla JS in namespaced objects (`Dashboard`, `ChartManager`, `Utils`…); colours, spacing and
  fonts only through the CSS variables in `variables.css`; escape anything user-entered with
  `Utils.escapeHtml()`.
- **Tests**: descriptive names (`` `init with valid salt succeeds` ``), Ktor's `testApplication` for HTTP
  routes; test databases go to `test-dbs/` (gitignored).

## Pull requests

Branch from `main`, add tests for new behaviour, run the checks above, and open a PR with a title under 70
characters that says *why*, plus screenshots for UI changes. Reviews look at correctness, security, privacy
(nothing new stored about visitors without a good reason) and simplicity.

Bugs and feature ideas: use the issue templates. Security problems: see [SECURITY.md](SECURITY.md).

By contributing you agree that your work is licensed under the [MIT License](LICENSE).
