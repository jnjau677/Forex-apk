# Release Checklist

## Before tagging

- [ ] All CI checks green on the release commit.
- [ ] Version bumped in `app/build.gradle.kts`: `versionCode` +1 and a new `versionName`.
- [ ] Release signing preflight passes in CI (keystore and passwords configured as GitHub secrets).
- [ ] R8 mapping file (`mapping.txt`) archived together with the APK artifact, so obfuscated crash stacks can be deobfuscated.
- [ ] Manual smoke test: sign in, market data loads, Diagnostics tab shows healthy state, background scan produces a notification.

## Quota and cost review

- **Alpha Vantage free tier allows 25 requests per day.** A full scan of the ~6 known symbols consumes 6 requests, so the free tier supports only ~4 full scans per day. Alpha Vantage must stay a low-frequency fallback, not the primary provider; otherwise upgrade the plan or drop it.
- The 15-minute periodic scan of ~6 symbols is ~576 symbol fetches per day if every scan hits the network. Keep FCS as the primary provider (it self-throttles) and rely on local caching so most scans are served from Room without network calls.
- **OpenRouter is paid per request.** Before release, confirm the model, max token limit, and an estimate of requests per user per day. If costs become material, move to the proxy in `docs/PROXY.md` so the key cannot be abused from outside the app.

## Release

- [ ] Tag the release commit: `git tag vX.Y.Z && git push origin vX.Y.Z`. CI builds the signed APK and attaches it to the GitHub release.
- [ ] Verify the APK installs over the previous version: `versionCode` greater, signature identical, Room data preserved.

## Rollback plan

- Play Store: halt the staged rollout, fix forward with a higher `versionCode`, then resume. For a critical defect, stop rollout entirely; already-updated users keep the bad build until the fix ships, so keep staged rollout percentages conservative.
- GitHub releases are additive: keep the previous tag intact, do not move tags.
- There is no backend, so there is nothing to roll back server-side.

## After release

- [ ] Watch provider consoles for unexpected quota usage (key abuse) for the first 48 hours.
- [ ] File any hotfix as a new PR; do not amend the tagged commit.
## CI scanners

What runs today and what was removed:

- **CodeQL** (`.github/workflows/codeql.yml`) is the security scanner for this repo and stays enabled. Keep it green.
- Removed unconfigured starter workflows: `apisec-scan.yml` (needs a paid APIsec account, secrets and a registered API project), `codescan.yml` (Salesforce/Apex scanner, not applicable), `eslint.yml` (no JS/TS in this repo), `sonarcloud.yml` (empty project keys, no SONAR_TOKEN), `detekt.yml` (v1.15.0 CLI from 2020 cannot parse Kotlin 2.2), and `Codeql.yml` (case-only duplicate of `codeql.yml`).

To re-add a scanner properly:

- **SonarCloud:** create the project on sonarcloud.io, set the `SONAR_TOKEN` secret, fill in `projectKey` and `organization`, then re-add the workflow.
- **Detekt:** add it as a Gradle plugin (`dev.detekt`, see https://detekt.dev) with a checked-in config and baseline, and run `gradle detekt` in the Build & Test job - do not use the detached 2020 CLI starter.
- **Secrets-based scanners** (APIsec, CodeScan): only after the account exists, the secrets are set, and there is something that tool can actually scan.
