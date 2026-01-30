# Git Workflow

## Branches

- `dev` - Main development branch (PR target)
- `master` - Production releases
- Feature branches from `dev`

## Commit Messages

Use conventional format:
```
[CARDS-XXXX] Short description

Longer explanation if needed.
```

## Pull Requests

1. Create feature branch from `dev`
2. Make changes with clear commits
3. Open PR targeting `dev`
4. Ensure CI passes
5. Get review approval
6. Squash and merge

## Release Process

Managed via Maven Release Plugin:
```bash
mvn release:prepare
mvn release:perform
```

Creates tags like `cards-0.9.36`.

## CI Checks

PRs must pass:
- Compilation
- Unit tests
- Checkstyle
- Integration tests (when applicable)

## Common Operations

```bash
# Update from dev
git fetch origin
git rebase origin/dev

# Create feature branch
git checkout -b CARDS-1234-feature-name origin/dev

# Amend last commit (before push)
git commit --amend
```
