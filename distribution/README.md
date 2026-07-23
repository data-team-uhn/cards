# CARDS distribution and Docker image

This module aggregates all the features making up the CARDS platform into the two deployable
feature models (`core_tar` for the TAR segment store, `core_mongo` for MongoDB persistence),
and, with `-Pdocker`, builds the `cards/cards` Docker image.

One image definition serves two flavors, differing only in how much of the artifact
repository is baked in:

```
mvn install -Pdocker                            # developer flavor: small and fast
mvn install -Pdocker,docker-production          # production flavor: fully self-contained
```

## How the image works

The container starts the Sling Feature Launcher on
`mvn:io.uhndata.cards/cards/<version>/slingosgifeature/core_<storage>` (`tar` or `mongo`,
chosen by the `OAK_FILESYSTEM` environment variable), resolving artifacts from, in order:

1. `/opt/cards/mvnrepo` — the project's own artifacts, including **every feature file
   produced by the repository**, copied from the build's `.mvnrepo`;
2. `/opt/cards/artifacts` — the third-party artifact repository; empty in the developer
   flavor, complete in the production flavor;
3. `/root/.m2/repository` — a volume-mountable host Maven repository;
4. `/root/.cards-generic-m2/repository` — an extra repository that deployment tooling may
   inject artifacts into;
5. the remote repositories, as a last resort.

**Developer flavor**: mount your local repository for instant, offline starts right after a
host build:

```
docker run --rm --volume ~/.m2:/root/.m2 -e OAK_FILESYSTEM=true -p 8080:8080 -it cards/cards
```

Without the mount, third-party artifacts are downloaded on first start and cached in the
`/opt/cards/.cards-data` volume.

**Production flavor**: the build harvests every feature file built by the reactor and
materializes all their referenced artifacts (via the `slingfeature-maven-plugin` `repository`
goal) into `/opt/cards/artifacts`, one deduplicated Maven-layout repository. The image needs
no network access and no mounts, and — because *all* repository features are embedded, not
only the ones aggregated into the core feature — the optional features toggled by the
runtime environment variables (`DEV` for Composum, `DEMO`, `SAML_AUTH_ENABLED`,
`ADDITIONAL_SLING_FEATURES`, per-project distributions...) work without network access. For
completeness, build from a full reactor build (`mvn install -Pdocker` from the repository
root), since the harvest covers the features produced by the current build.

## The metadata layer

`/metadata` inside the image supports security audits of deployments, and is always present
and current:

- `build-info.txt` — version, git commit, and build timestamp;
- `core_tar.json` / `core_mongo.json` — the aggregated feature models, the complete versioned
  inventory of every Java artifact in the deployment;
- `yarn.lock` — the complete inventory of the frontend JavaScript dependencies;
- `logo.png` — the platform logo shipped with this build.

The runtime environment variables understood by the container are documented in
[environment.md](../environment.md).
