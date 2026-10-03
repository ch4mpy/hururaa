# Demo deployment

Everything needed to run the demonstrator on a single Linux host with Docker Compose, deployed
continuously to `https://hururaa.c4-soft.com` by
[`.github/workflows/deploy-demo.yml`](../.github/workflows/deploy-demo.yml) on every push to
`master`. Sized for a demo (a few users, a few requests per minute), not for production.

## How it works

- One public origin, `https://${DEMO_HOST}` (`hururaa.c4-soft.com` unless the `demo` environment
  defines a `DEMO_HOST` variable), served by **Caddy** with an automatic Let's Encrypt certificate.
  Caddy routes by path prefix exactly like `nginx-reverse-proxy` does in dev: `/auth/` → Keycloak,
  `/gateway/` → the BFF, `/fr/` and `/en/` → the Angular build (`/` redirects to `/fr/`),
  `/assets/` → pf-ui's images (loaded with absolute paths), `/mailpit/` (basic-auth) → Mailpit,
  `/grafana/` → Grafana LGTM.
- TLS stops at Caddy: inside the Docker network every hop is plain HTTP and services are reached
  by their compose service name. The Spring apps run with the `demo` profile (last document of
  each `application.yml`), which only overrides URLs, hosts and secrets.
- The images are built and pushed to GHCR by the workflow (`hururaa-keycloak` from
  [`keycloak/Dockerfile`](../keycloak/Dockerfile), `hururaa-gateway` and `hururaa-api` from
  [`backend/Dockerfile`](backend/Dockerfile), `hururaa-frontend` from
  [`frontend/Dockerfile`](frontend/Dockerfile)). Nothing is built on the host.
- The `public-facing` realm is the dev export of
  [`keycloak/import/`](../keycloak/import/public-facing-realm.json) rendered by
  [`keycloak/render-realms.sh`](keycloak/render-realms.sh) at deploy time: public host name,
  Mailpit as SMTP server, the `hururaa-bff` and `hururaa-api` secrets replaced by real ones and
  every other client secret (the sample applications', which nothing runs in the demo) by a random
  one. The realm is only imported when absent, so Keycloak data (including users, organizations
  and the clients Hurura'a provisions during the demo) survives redeploys; to apply new secrets, a
  changed realm setting or to start over, `docker compose down -v` on the host and redeploy (take
  a backup first, see below).
- The API starts with the Liquibase `dev` context: the dev applications, whose administrators and
  managers are the members of the realm's `hururaa.admins` and `hururaa.<prefix>.product-owners` groups.
  A Keycloak volume created before administrators and managers moved to these groups lacks them: `docker compose
  down -v` and redeploy. The demo users deliberately keep
  their public password, `secret` (in clear in the realm export, which the rendering leaves
  alone): anyone can sign in as any of them, Hurura'a administrators included. A deployment
  beyond the demo must replace them.
- Every service has a healthcheck when the image allows it (the Spring apps answer on
  `/actuator/health/readiness`, on their management port), and `docker compose up --wait` in the
  workflow fails the deployment unless they all report healthy. Container logs rotate (10 MB × 3
  per service).

## One-time setup

1. Order the VPS (Ubuntu LTS, 8 GB RAM is comfortable with Grafana LGTM, 4 GB is too tight) and
   note its IP. Caddy publishes ports 80 and 443: the host cannot share them with another
   deployment of the same kind.
2. DNS: an `A` record for `hururaa.c4-soft.com` pointing at that IP (Caddy needs it to get its
   certificate).
3. Generate an SSH key pair dedicated to the deployment (`ssh-keygen -t ed25519 -f hururaa-demo`)
   and prepare the host, as root:
   ```bash
   ssh root@<vps-ip> 'bash -s' < deploy/vps-bootstrap.sh "$(cat hururaa-demo.pub)"
   ```
   Some images (OVH among them) disable SSH for root and give a `ubuntu` sudoer instead. Piping
   the script on stdin then leaves sudo no terminal to ask for a password, so copy it over first:
   ```bash
   scp deploy/vps-bootstrap.sh ubuntu@<vps-ip>:/tmp/
   ssh -t ubuntu@<vps-ip> "sudo bash /tmp/vps-bootstrap.sh '$(cat hururaa-demo.pub)'"
   ```
4. In the GitHub repository, create the `demo` environment with:

   | Kind | Name | Value |
   |---|---|---|
   | variable | `DEMO_HOST` | optional, defaults to `hururaa.c4-soft.com` |
   | variable | `KC_VERSION` | optional, defaults to `26.7` |
   | variable | `KC_ADMIN_USERNAME` | optional, defaults to `admin` |
   | secret | `VPS_HOST` | the VPS IP or host name |
   | secret | `VPS_SSH_KEY` | the private key generated above |
   | secret | `VPS_KNOWN_HOSTS` | optional but recommended: the VPS host key line(s), `ssh-keyscan -H <vps-ip>` run from a machine you trust. Without it the workflow trusts whatever key the host presents on every run |
   | secret | `KC_ADMIN_PASSWORD` | Keycloak admin console (`/auth/admin/`) |
   | secret | `HURURAA_BFF_CLIENT_SECRET` | `hururaa-bff` client of the `public-facing` realm |
   | secret | `HURURAA_API_CLIENT_SECRET` | `hururaa-api` client of the `public-facing` realm |
   | secret | `POSTGRES_PASSWORD` | `hururaa-api` database |
   | secret | `RABBITMQ_PASSWORD` | broker user `hururaa-api` |
   | secret | `MAILPIT_SMTP_PASSWORD` | SMTP password of user `mailpit`, used by the realm |
   | secret | `MAILPIT_UI_PASSWORD_HASH` | bcrypt hash protecting `/mailpit/` (user `demo`): `docker run --rm caddy:2-alpine caddy hash-password --plaintext '<password>'` |
   | secret | `GRAFANA_ADMIN_PASSWORD` | Grafana `admin` user |

   Generate the passwords and secrets, e.g. `openssl rand -base64 32`.
5. Push to `master` (or run the workflow manually). The first `docker compose up` takes a few
   minutes to converge: Let's Encrypt, then Keycloak, then the Spring apps, which restart until
   the issuer answers.

## Day to day

On the host, as `hururaa`, from `~/hururaa/deploy`:

```bash
docker compose ps                   # health of every service
docker compose logs -f gateway      # or hururaa-api, keycloak, caddy...
docker compose down -v              # wipe everything (Keycloak users, applications, mails)
```

At the end of the demo: `docker compose down -v`, then cancel the VPS.

## Rollback

Every deployment is immutable: the four images are tagged with the commit SHA and `IMAGE_TAG` in
`~/hururaa/deploy/.env` is what the stack runs. `docker image prune -f` only removes untagged
layers, so the previous images are still on the host (and in GHCR in any case). To go back to a
known-good commit, on the host as `hururaa`:

```bash
cd ~/hururaa/deploy
sed -i 's/^IMAGE_TAG=.*/IMAGE_TAG=<sha of the good commit>/' .env
docker compose pull --quiet && docker compose up -d --wait
```

The next push to `master` rewrites `.env` and deploys again: a rollback buys time, the fix goes
through `master` like everything else. A change to the Liquibase changelog or the realm export is
not rolled back by this (the database schema and Keycloak keep what the newer version did).

## Backups

The workflow installs [`backup.sh`](backup.sh) in the `hururaa` user's crontab, nightly at 03:15
(host time). It writes to `~/backups/` and keeps 7 days:

- `rest-api-db-<stamp>.dump`: the REST API database (applications, permission journal),
  `pg_dump` custom format, taken hot;
- `keycloak-h2-<stamp>.tgz`: Keycloak's embedded H2 files. H2 has no hot backup, so Keycloak is
  stopped for the few seconds the copy takes (the Spring apps fail their Keycloak calls meanwhile
  and recover on their own). Users, groups (Hurura'a's reserved ones included), role mappings and provisioned
  clients are in there.

The two must be restored together: the applications reference Keycloak clients and reserved
groups, the journal Keycloak user ids. The backups stay on the host: copying them elsewhere
is up to you.

To restore, from `~/hururaa/deploy`:

```bash
# the REST API database (the API is stopped so that nothing writes meanwhile)
docker compose stop hururaa-api
docker compose exec -T rest-api-db pg_restore -U rest-api -d rest_api --clean --if-exists \
  < ~/backups/rest-api-db-<stamp>.dump
docker compose start hururaa-api

# Keycloak (the volume is emptied, then the archive unpacked in its place)
docker compose stop keycloak
kc_volume=$(docker volume ls -q -f label=com.docker.compose.project=hururaa-demo -f label=com.docker.compose.volume=keycloak_data)
docker run --rm -v "$kc_volume:/data" -v ~/backups:/backup:ro alpine:3 \
  sh -c 'rm -rf /data/* && tar xzf /backup/keycloak-h2-<stamp>.tgz -C /data'
docker compose start keycloak
```
