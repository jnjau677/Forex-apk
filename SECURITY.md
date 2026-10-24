# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| development (`main`) | yes |
| latest tagged release | yes |
| older releases | no |

## Reporting a Vulnerability

Please do not open public issues for security problems.

Report privately by email to the address listed on the profile of the repository owner. Include:

- affected component (local auth, CI, networking, storage)
- steps to reproduce or a proof of concept
- potential impact

We aim to acknowledge reports within 72 hours. Please allow up to 14 days for a fix before public disclosure.

## Threat Model and Architecture Notes

- **Client-held API keys are extractable.** Any key compiled into the APK (FCS, Alpha Vantage, OpenRouter) can be recovered by anyone who obtains the APK. Treat them as public identifiers protected by provider-side quotas and restrictions, not as secrets. For privileged or paid providers, route calls through a backend proxy (see `docs/PROXY.md`).
- **Local authentication** uses PBKDF2-HMAC-SHA256 with 120,000 iterations and a per-user random salt. The Room database stores only salted hashes; plaintext passwords are never persisted. Legacy SHA-256 hashes are transparently upgraded to PBKDF2 on the next successful sign-in.
- **Device backups** exclude the Room database and secure shared preferences (see `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml`), so credential material does not leave the device via Android Auto Backup or device transfer.

## Secret Handling

- Never commit secrets to source control, including in test scripts. Use GitHub repository secrets for CI and a git-ignored local secrets file for build injection.
- **Rotation procedure**:
  1. Revoke the exposed key at the provider console.
  2. Issue a new key, applying quota caps and (where supported) Android app or referrer restrictions.
  3. Update GitHub repository secrets and any local secret files.
  4. Cut a release so shipped builds stop using the revoked key.

If a secret was committed to git history, rotation alone is not enough: purge the history (for example with `git filter-repo`) because old commits remain cloneable.