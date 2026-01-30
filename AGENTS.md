# AGENTS.md

CARDS is a Java/React medical data collection platform built on Apache Sling and Jackrabbit Oak.

## Build

```bash
mvn clean install          # Full build
mvn install -Pquick        # Skip tests
./start_cards.sh           # Run locally at :8080
```

## Quick Reference

| Task | Command |
|------|---------|
| Hot deploy | `mvn install -PautoInstallBundle` |
| Frontend only | `cd aggregated-frontend/src/main/frontend && yarn build` |
| Run tests | `mvn test` |
| Integration tests | `mvn verify -PintegrationTests` |
| Docker build | `mvn install -Pdocker` |

## Project Structure

- `modules/` - 48 OSGi bundles (Java backend)
- `aggregated-frontend/src/main/frontend/` - React frontend
- `distribution/` - Sling feature packaging
- `Utilities/` - Scripts and tools

## Detailed Guides

- [Java & Backend Conventions](.claude/docs/java-conventions.md)
- [React & Frontend Conventions](.claude/docs/frontend-conventions.md)
- [Testing Patterns](.claude/docs/testing.md)
- [Module Development](.claude/docs/module-development.md)
- [Git Workflow](.claude/docs/git-workflow.md)
