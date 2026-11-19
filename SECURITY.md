# Security

## Reporting a vulnerability

Please **do not open a public issue**. Use GitHub's private reporting instead:
**Security → Report a vulnerability** on this repository. I'll answer within a week.

## What the app does today

Megyejáték is fully offline. It has no `INTERNET` permission, no accounts and no analytics.
The only data it stores is your nickname, the Vaktérkép setting and (soon) local scores.

| Area | Measure |
|---|---|
| Permissions | Only `VIBRATE`. The only exported component is the launcher activity. |
| Stored data | App-private storage only. Cloud backup and device-to-device transfer are disabled. |
| Network | Network security config allows HTTPS only and trusts system CAs only, ready for when the online leaderboard arrives. |
| Input | Nicknames are cleaned of control, zero-width and bidi-override characters and length-capped (`InputSanitizer`). |
| Task hijacking | The launcher activity has an empty `taskAffinity`. |
| Release builds | R8 shrinking and obfuscation, not debuggable. The signing key is read from environment variables, never from the repo. |
| Debug builds | Signed with the **public** debug key in `app/debug.keystore`, only so CI builds install over each other. Never trust an APK only because of this signature. |

## Supply chain and health checks

- **CI** (`android.yml`) builds, compiles the UI tests and runs the unit tests on every push and PR, plus every Monday even without changes.
- **Android lint** runs in CI with security checks promoted to errors; results appear under **Security → Code scanning**.
- **CodeQL** (`security-extended` queries) scans the Kotlin code on every push and weekly.
- **Dependabot** opens weekly update PRs for Gradle libraries, GitHub Actions and the Python pipeline. Dependabot alerts flag libraries with known vulnerabilities.
- All GitHub Actions are **pinned to commit SHAs**, workflows run with read-only permissions by default, and checkout does not keep the token.
- The Gradle wrapper checksum is validated on every CI run.
- Every published APK comes with a `.sha256` file to check the download.

## Planned with the online features (Firebase)

When accounts and global leaderboards are added:

- Firebase Authentication with a strong password policy (minimum 10 characters, upper and lower case, digit and symbol), enforced by the server as well as in the app.
- Firebase App Check with Play Integrity, so only the genuine app can call the backend.
- Firestore security rules: players can only write their own scores; scores are validated (range, time, mode) by the rules.
- No secrets in the repo: `google-services.json` stays out of git and is injected in CI from a secret.
- Crash reporting (Crashlytics) without personal data.
