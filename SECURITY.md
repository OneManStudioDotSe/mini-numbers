# Security policy

## Reporting a vulnerability

Please report security problems privately through a
[GitHub security advisory](https://github.com/OneManStudioDotSe/mini-numbers/security/advisories/new),
not in a public issue. Include what you found, how to reproduce it, and the version (`GET /health` shows it).
You'll get a reply within a week.

## Supported versions

Fixes go into the latest release. Upgrade with `docker compose pull && docker compose up -d` or by replacing
the JAR — see the [Install guide](docs/deployment.md#after-installing).

## How Mini Numbers protects data

What it stores about visitors: [docs/privacy.md](docs/privacy.md). How logins, tokens, limits and headers
work: the *Security model* section of [_docs/ARCHITECTURE.md](_docs/ARCHITECTURE.md#security-model).
