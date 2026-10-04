# Persistent HTTPS deployment

The APK includes native fusion, map tiles, the GraphHopper graph, TFLite inference and local drive files. Tracking and recording work without this server. Uploads, shared history, model updates and reports use the Express/MongoDB service below; MongoDB is not embedded in Android.

This deployment serves one trusted team workspace. The phone key grants access to that workspace's drives. It is not a public multi-user service with isolated user accounts.

1. On a Linux host with Docker Compose, point a domain's DNS to the host and allow inbound TCP 80/443.
2. Copy `deploy/.env.example` to `deploy/.env`. Set your domain and email. Generate three different hexadecimal secrets with `openssl rand -hex 32`; use them for MongoDB, phone workspace access and publishing. Hex avoids URI-escaping issues in the database connection string.
3. From the repository root run `docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --build`.
4. Verify `https://YOUR_DOMAIN/api/health` returns `{"status":"ready"}`. Enter that HTTPS origin and the phone `API_KEY` in Android Settings. The publisher key stays on the server/operator's computer.
5. Publish bundled models with `API_URL=https://YOUR_DOMAIN ADMIN_API_KEY=YOUR_PUBLISHER_KEY node scripts/seed-models.mjs` from a secure operator shell. Avoid storing real secrets in shell history; load them from your environment/secret manager.

Caddy provisions HTTPS certificates. MongoDB and the API have no host port exposure. Three persistent volumes retain database records, uploaded binaries and certificates across container recreation. Back up both `mongo-data` and `uploads` together; restoring only one leaves orphaned references. Use a maintenance window or consistent database dump for backups.

Use `docker compose --env-file deploy/.env -f deploy/compose.yaml logs --tail=100 api` for diagnostics. Never use `down -v` on a live deployment unless you intend to delete its data.

Validation in this workspace: API integration tests run against real local MongoDB. Docker is not installed here, so container startup, DNS, certificates and public-host integration still require verification on your deployment host.

Configuration references: [Compose required environment interpolation](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/) and [Caddy request-body limits](https://caddyserver.com/docs/caddyfile/directives/request_body).
