# CARDS distribution and Docker image

The `slingfeature` submodule aggregates all the features making up the CARDS platform into
the three deployable feature models (`core_tar` for the TAR segment store, `core_mongo` for
MongoDB persistence, `core_rdb` for a relational database), and, with `-Pdocker`, the `docker`
submodule builds the `cards/cards` Docker image.

One image definition serves two flavors, differing only in how much of the artifact
repository is baked in:

```
mvn clean install -Pdocker               # developer flavor: small and fast
mvn clean install -Pdocker,production    # production flavor: fully self-contained
```

## How the image works

The container starts the Sling Feature Launcher on
`mvn:io.uhndata.cards/cards/<version>/slingosgifeature/core_<storage>` (one of `tar`, `mongo`
or `rdb`, named by the `OAK_STORAGE` environment variable — without it, `tar` when
`OAK_FILESYSTEM` is set and `mongo` otherwise), resolving artifacts from, in order:

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

**Relational persistence**: with `OAK_STORAGE=rdb` the repository lives in a relational
database, which must be reachable and must hold a database the connecting user may create
tables in — Oak creates its own tables on first start. Only the PostgreSQL JDBC driver is
bundled in the image; `RDB_DRIVER` exists for deployments that add another vendor's driver
through `ADDITIONAL_SLING_FEATURES`:

```
docker run --rm -e OAK_STORAGE=rdb -e EXTERNAL_RDB_URI=jdbc:postgresql://db:5432/cards \
  -e RDB_USER=cards -e RDB_PASSWORD=secret -p 8080:8080 -it cards/cards
```

**PostgreSQL collation — required**: the database **must** be created with `C` (or `POSIX`)
collation. Oak's `RDBDocumentStore` orders the node `id` column by Unicode code point (its queries
use `ORDER BY` on `id`, and the primary-key index ordering must match); a locale collation such as
`en_US.utf8` — the default the official `postgres` image initializes — orders those ids differently,
so Oak mis-resolves already-persisted data. The symptom is insidious: the *first* start succeeds
(the database is empty, nothing to read back), but *every restart* then fails during activation with
`ClusterRepositoryInfo getOrCreateId: both setting and then reading of /:clusterConfig/:clusterId
failed` and the instance hangs. Create the database accordingly:

```
CREATE DATABASE cards OWNER cards TEMPLATE template0 ENCODING 'UTF8' LC_COLLATE 'C' LC_CTYPE 'C';
```

For the official `postgres` image, set `POSTGRES_INITDB_ARGS=--encoding=UTF8 --lc-collate=C --lc-ctype=C`
on a **fresh** data volume (collation is fixed when the database is created and cannot be changed
afterwards). The entrypoint verifies this on start and refuses to launch against a non-`C` database.
See the [Oak RDB DocumentStore documentation](https://jackrabbit.apache.org/oak/docs/nodestore/document/rdb-document-store.html).

**Cluster node identity**: both document-store back-ends, `mongo` and `rdb`, record a *cluster
node* in the database, identified by the hardware address and the working directory, and reclaim
it on the next start. A container is given a fresh MAC address every run, so without help a
restart that happens before the previous lease expires — two minutes — cannot reclaim or wait for
the old cluster node. It takes a new cluster id instead and leaves the old entry behind, marked
active forever and therefore never recovered. Setting `OAK_MACHINE_ID` to any stable value pins the
address, so that a restarted container reclaims its own cluster node.

It is **opt-in, with no default, on purpose**. Several CARDS containers routinely share one
database, and they distinguish themselves precisely by having different hardware addresses; a
shared default would collapse them all onto a single cluster node, which corrupts the repository
rather than merely failing. Set it only for a single-instance deployment, or give every instance
its own distinct value.

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
- `core_tar.json` / `core_mongo.json` / `core_rdb.json` — the aggregated feature models, the
  complete versioned inventory of every Java artifact in the deployment;
- `yarn.lock` — the complete inventory of the frontend JavaScript dependencies;
- `logo.png` — the platform logo shipped with this build.

The runtime environment variables understood by the container are documented in
[environment.md](../environment.md).
