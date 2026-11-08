# Maintainer procedures

[한국어](ko/maintainers.md) · [Documentation index](../README.md#documentation)

Contributors use the normal fork, branch, test and PR workflow in [CONTRIBUTING](../CONTRIBUTING.md). Maintainer-only credentials and local automation are not contributor setup prerequisites.

## Current repository policy

The canonical repository currently restricts the PR author/assignee through `.github/codex-governance.json`. Human accounts own commits, pushes, PR integration and merges; a GitHub App submits formal review for the exact current head. Required checks are `quality` and `app-review`. Every new push requires a fresh current-head approval. Only APPROVED passes the review gate.

That account-bound author restriction is a current project exception and prevents direct integration of some external fork PRs. Maintainers may carry an accepted external change into an integration PR while linking the original PR and preserving attribution. Broader direct integration needs a separately reviewed policy/tooling change; do not weaken protection or publish App credentials to work around it.

Keep protected/default branches separate from the reviewed head. Use verified-head merging and remove the merged head's remote, local and tracking references after integration. Do not delete unrelated contributor branches. New policy changes require their own review; everyday application/docs changes should not silently change the approval rules.

## Release checklist

1. Review the complete current head, run the [development checks](development.md) and inspect the resulting JAR/data exclusions.
2. Update [CHANGELOG](../CHANGELOG.md) from actual changes. Keep source/build version and any published tag/artifact version consistent.
3. Use a candidate with a separate data root/port. Verify startup settings, static resources, representative browsing, byte ranges and native playback on the intended targets.
4. Record checks and their limits in [verification](verification.md). Avoid replacing historical evidence with a claim that unrun tests passed.
5. Complete the repository's required review/checks before merging. Publish a release or registry artifact only when the actual artifact exists and has been verified; the README must not invent a download or pull command.
6. Replace the running container using the same external mounts/configuration, retain a rollback image/configuration and verify the live candidate. Do not run two instances against one data root.

The current Dockerfile builds from source. A dedicated image-registry/release-publishing pipeline is not established by these documentation files. Container/media credentials and private incident-report channels require explicit operational setup.

## Documentation ownership and history

English is canonical. The root README is the single document index, detailed configuration belongs in its guide, and Korean counterparts are updated in the same PR. Add material where users look for it instead of duplicating whole guides.

The retained [v8 platform specification](spec-platform.json) is historical metadata for its recorded artifact versions, not a fresh attestation of current files. Earlier design-specific reports remain in Git history. The current Gradle/documentation acceptance evidence is summarized in the verification report; private media identities, host data and task artifacts remain untracked.
