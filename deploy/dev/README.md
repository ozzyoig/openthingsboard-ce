# OpenThingsBoard CE — Docker development environment

This stack is for development on a separate Docker host or in its own Compose
project. It never connects to a production database, runs a remote deployment or
mounts production paths. PostgreSQL has no published port; HTTP and MQTT bind to
loopback on ports 18080 and 11883. Do not reuse production credentials or data.
Keep the project name `openthingsboard-ce-dev` (or choose another unique name
with `docker compose -p ...` consistently for every command).

## Build mechanism

The workflow uses the existing ThingsBoard CE 4.3.1.8 Maven reactor:

```sh
mvn -B -ntp -T 2 -pl msa/tb-node -am package \
  -DskipTests -Dpkg.skip.rpm=true -Dpkg.skip.zip=true
docker build -t openthingsboard-ce:local msa/tb-node/target
```

Use JDK 17, Maven and Docker on Linux amd64. The frontend Maven plugin installs
the repository-pinned Node and Yarn and compiles Angular. `application` packages
the UI, executable boot JAR, SQL schemas and install resources into
`application/target/thingsboard.deb` using the existing Gradle packaging plugin.
`msa/tb-node` filters its upstream Dockerfile/startup script and copies that DEB
into `msa/tb-node/target`. Build **that generated context**, not the unfiltered
source directory. Do not pass `skip.yarn`, `skip.ui.build`, `pkg.skip` or
`pkg.skip.deb` to the image build: they remove required artifacts. RPM and Windows
ZIP are unnecessary for this image. The runtime base comes from `msa/pom.xml`.

The existing `msa/tb` / `tb-postgres` image embeds its own PostgreSQL and is not
appropriate for the separate PostgreSQL 16 service. `tb-node` supports monolith
mode and the upstream `INSTALL_TB` / `UPGRADE_TB` commands. No replacement Java
launcher or Dockerfile is introduced. Existing Apache 2.0 notices are retained.

## First start

Install Docker Engine with Compose v2 supporting `up --wait`. Run all subsequent
commands from this directory:

```sh
cp .env.example .env
# Fill POSTGRES_PASSWORD in .env (e.g. generate with openssl rand -hex 24).
docker compose config --quiet
docker compose pull
docker compose run --rm install
docker compose up -d --wait --wait-timeout 600
docker compose logs -f thingsboard
```

Run `install` exactly once against a new database. It creates the ThingsBoard
schema and system administrator; it does not load demo tenants. Open
http://localhost:18080 and use `sysadmin@thingsboard.org` / `sysadmin`, then change
the password. The application may take several minutes to become healthy.
For a local build set `TB_IMAGE=openthingsboard-ce:local` in `.env` and skip pull
of that image. PostgreSQL still needs to be downloaded.

For a private GHCR package, authenticate using `docker login ghcr.io` with a
credential having `read:packages`. Enter the token using `--password-stdin`;
do not commit it. Public images can be pulled anonymously. `.env` is ignored.

## Updates and persistence

For a build compatible with the existing database schema:

```sh
docker compose pull
docker compose up -d --wait --wait-timeout 600
```

`main` follows successful builds of main. For repeatable deployment, set
`TB_IMAGE` to a published `sha-<full-commit>` tag, release tag such as
`v4.3.1.8`, or image digest before pulling. Workflow dispatch validates a branch
without publishing. Pull requests also build/test without publishing. Pushes to
main and `v*` tags publish **the image that passed the smoke test**, using the
repository's `GITHUB_TOKEN` with `packages:write`; no deployment credentials are
needed. Enable Actions/package creation in repository settings as required.
The initial GHCR package may be private; change its visibility separately if
anonymous pulls are desired. Only linux/amd64 is currently built.

The named `postgres-data` volume persists across pull, container recreation and
`docker compose down`. App data and logs also have separate named volumes.
Never run `docker compose down -v` unless intentionally deleting this dev stack's
data. Changing `POSTGRES_PASSWORD` after initialization does not change an
existing database role's password; rotate it in PostgreSQL and `.env` together.

Back up before any update:

```sh
mkdir -p backups
docker compose exec -T postgres pg_dump -U thingsboard -d thingsboard -Fc > backups/thingsboard.dump
```

Store backups securely outside this checkout. A newer ThingsBoard schema may
require an explicit migration; `pull` / `up` do **not** migrate it automatically.
Follow the release's supported upgrade path, stop the app, and invoke the
upstream migration with the **new** image and the documented source version:

```sh
docker compose pull
docker compose stop thingsboard
FROM_VERSION=<supported-source-version> docker compose run --rm upgrade
docker compose up -d --wait --wait-timeout 600
```

Do not rerun `install` to upgrade. A rollback after migration requires the
matching database backup as well as the old image; simply retagging is unsafe.
Keep PostgreSQL on major version 16; a major database upgrade is a separate task.

## Scope and validation

This single-node development stack uses the in-memory queue and local JavaScript
evaluator, so it needs no Kafka or remote JS executor. Queued messages do not
survive an application restart. It is not a production HA configuration.

CI compiles Java and Angular, runs the common utility tests, builds the upstream
image, installs a fresh PostgreSQL 16 database, checks the login page and actual
administrator authentication, then recreates the stack and verifies database
persistence. Its uniquely named Compose project and volumes are removed at the
end. CI never contacts a production Docker host.
