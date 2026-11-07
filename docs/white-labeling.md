# White Labeling v1

OpenThingsBoard CE 4.3.1.8 supports branding stored in PostgreSQL. Changing
branding does not rebuild the Angular bundle, replace the Docker image, restart
the server or require an asset volume.

## Global appearance

Sign in as System Administrator and open **Settings → Appearance / White
Labeling** (`/settings/appearance`). Set the application name, interface logo,
login logo, favicon and optional accent color, then select **Save**. Uploaded
images have a preview but are not persisted until Save. The global name defaults
to **OpenThingsBoard CE**. With no image override the original CE assets are used;
original authorship and Apache 2.0 notices remain intact.

Global branding is intentionally public: it is returned by
`GET /api/noauth/branding` and used by the login screen. Never include private
information in it. Login uses the dedicated login logo if set, otherwise the
global interface logo. There is no tenant selection by URL, query parameter,
domain or email before authentication.

## Tenant appearance and inheritance

A Tenant Administrator opens the same page to edit only their own tenant's
application name, interface logo, favicon and accent color. The authenticated
tenant is derived on the server from the session, never from a submitted tenant
ID. Customer users see their tenant's effective branding but cannot edit it.

Inheritance is per field: an empty field inherits the global value; an empty
global field uses the built-in default. Clearing an image removes that tenant's
override and restores the inherited image. **Reset all overrides** deletes the
scope's branding configuration. For the system scope this restores the defaults;
for a tenant it restores current global settings. Updating a global setting also
updates tenants that do not override that field.

The public login screen always uses global branding. After authentication the
application switches to the current tenant's branding; logout restores global
branding. Refresh an already open tab to see changes saved elsewhere. Saving in
the editor also refreshes the current tab. Requests are not cached, so changing
settings does not require a restart or waiting for cache expiry.

## Images and color

- Logos: PNG, JPEG, WebP. Favicon: PNG only.
- Each image: 1 byte–256 KiB; dimensions 1–2048 pixels on each axis.
- Uploads and direct API submissions are validated on the server. Filenames and
  submitted MIME types are not trusted; raster signatures and parsed dimensions
  are checked. SVG, HTML, remote URLs and arbitrary data URIs are rejected.
- Images are stored as bounded raster data URIs inside the existing tenant-scoped
  administration settings document. No filesystem paths or external asset hosts
  are configured, and no untrusted HTML is rendered.
- An optional `#RRGGBB` accent changes primary toolbars and primary raised/filled
  buttons at runtime. Contrasting black/white foreground text is chosen from
  luminance. This is deliberately not a replacement for Angular Material's
  compiled palette: light/dark surfaces, semantic error/warning colors, charts
  and dashboard/widget-specific colors retain their existing behavior.

## Storage, API and upgrade

No schema migration is required. Settings use the existing `admin_settings`
table with key `whiteLabelingV1` and the system or authenticated tenant ID.
Existing CE 4.3.1.8 installations need no new tables or columns. Normal database
backups include branding and its images. Tenant deletion uses existing settings
cleanup. Do not edit settings rows or copy private tenant images into global
branding manually.

API endpoints:

| Endpoint | Access / purpose |
| --- | --- |
| `GET /api/noauth/branding` | Public, effective global configuration only |
| `GET /api/branding/effective` | Authenticated, current tenant plus global defaults |
| `GET /api/branding` | System/tenant administrator, own raw overrides |
| `POST /api/branding` | System/tenant administrator, replace own overrides |
| `DELETE /api/branding` | System/tenant administrator, reset own overrides |
| `POST /api/branding/image?favicon=false` | Administrator, validate multipart `file`; returns `{data: ...}` to save with settings |

The configuration fields are `appName`, `logo`, `loginLogo`, `favicon` and
`primaryColor`. Omitted/null fields inherit. Only the system administrator can
set `loginLogo`. There is no API for selecting another tenant's private scope.

For an existing Docker installation, back up the PostgreSQL database and record
the current image tag/digest before updating. After this feature is merged and a
successful trusted workflow publishes it:

```sh
# Run in the directory of your existing Compose project, preserving its .env,
# project name, database credentials and named volumes.
docker compose exec -T postgres pg_dump -U thingsboard -d thingsboard -Fc > thingsboard-before-branding.dump
# Set TB_IMAGE=ozzysin/openthingsboard-ce:dev, or pin the published sha-<commit>.
docker compose pull
docker compose up -d --wait --wait-timeout 600
```

Do not run first-time `install` on an existing database. No `upgrade` invocation
is needed for this feature on CE 4.3.1.8. If updating from an older CE version,
follow that version's supported schema upgrade path separately. Never use
`down -v` during an update. The prepared `deploy/dev` project is isolated from
production; see [Docker installation instructions](../deploy/dev/README.md).

## Validation and next stages

`BrandingServiceTest` covers inheritance and input/image validation.
`BrandingControllerTest` exercises persistence through a new service instance,
global/tenant access, two-tenant isolation, customer/anonymous authorization,
multipart uploads and resets against PostgreSQL 16 using existing test fixtures.
The Docker workflow runs these tests before building/publishing its image; no
registry publication is enabled for pull requests.
Its application smoke test also saves global branding through the authenticated
API, recreates both containers and checks the persisted public branding after
restart. This must pass before publication.

To run the feature tests from the repository root:

```sh
mvn -B -ntp -T 2 -pl application -am test \
  -Dtest=BrandingServiceTest,BrandingControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dskip.ui.build=true -Dpkg.skip=true
cd ui-ngx
NG_BUILD_MAX_WORKERS=2 node --max_old_space_size=8048 node_modules/@angular/cli/bin/ng build --configuration development
cd ..
CHROMIUM_PATH=/usr/bin/chromium python3 ui-ngx/tests/branding_smoke.py
```

The browser test requires Python Playwright and Chromium (or Playwright's own
Chromium when `CHROMIUM_PATH` is omitted). It serves the compiled frontend on a
temporary loopback port and uses mocked API responses. It checks login branding,
favicon, system/tenant editors, upload preview, color validation, save, refresh,
reset and logout navigation. It does not validate Java authorization or real
database persistence. Do not run the mocked browser test against a live server.

Backend tests and the complete application/container restart test must pass in CI
before this feature is considered release-ready.

Domain-based tenant login branding, full runtime Material palettes, separate
light/dark logo variants and translation of the editor into languages other than
English and Polish are outside v1.
