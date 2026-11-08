# Security policy

## Reporting a vulnerability

Do not publish exploit details, private media, personal information, access tokens or sensitive logs in a public issue or pull request.

GitHub private vulnerability reporting is not currently enabled for this repository. To arrange a private reporting channel, open an issue containing only a request to contact the maintainers about a security matter. Share the technical details only after a private channel has been agreed. No dedicated security mailbox or response-time commitment is currently published.

A useful private report includes the affected commit/version, deployment environment, minimal reproduction, expected impact and any proposed mitigation. Use synthetic files and redact credentials and private paths.

## Supported scope

Security fixes are currently maintained on the default branch on a best-effort basis. There is no published long-term support matrix or guaranteed security support period for tagged releases. Include the exact commit or artifact version when reporting a problem.

## Deployment model

The application currently has no login or upload/edit/delete API. Anyone who can reach it can browse and play supported files under the configured media root. Signed playback parameters validate a prepared stream; they are not user authentication.

Mount media read-only, keep the writable data root separate, run as a non-root user and apply appropriate access control, TLS and network restrictions in your deployment. The [Docker guide](docs/docker.md) and [architecture](docs/architecture.md) describe filesystem boundaries and resource limits. Treat input media and storage as external dependencies; validate the chosen filesystem, codecs and workload.
