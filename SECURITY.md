# Security

## Reporting a vulnerability

Please **do not open a public issue**. Use GitHub's private reporting instead:
**Security → Report a vulnerability** on this repository. I'll answer within a week.

## What the app does

The game works offline. Network is used only for accounts and the global leaderboard (Firebase).
Without `google-services.json` the app is built offline-only, with no accounts at all.

| Area | Measure |
|---|---|
| Accounts | Username + password on Firebase Authentication. No e-mail is collected: the username maps to an address under the reserved `.invalid` domain. Usernames are unique case-insensitively. |
| Passwords | At least 10 characters with lower and upper case, digit and symbol; must not contain the username or common words (`CredentialPolicy`). Enforce the same in Firebase → Authentication → Password policy. Passwords are only handed to Firebase, never stored or logged, and cleared from memory after each attempt. |
| Login errors | "Wrong username or password" for both unknown users and wrong passwords, so usernames can't be probed. Firebase throttles repeated attempts. |
| Database | Firestore rules (`firebase/firestore.rules`): read only when signed in; a player can create their own profile once and add scores only under their own uid and username; scores are range-checked against the scoring limits; nothing can be edited or deleted; everything else is closed. |
| Permissions | `INTERNET` and `VIBRATE` only. The only exported component is the launcher activity. |
| Stored data | App-private storage only. Cloud backup and device-to-device transfer are disabled, so sessions and scores are never copied off the phone. |
| Network | HTTPS only, system CAs only (no user-installed certificates). |
| Input | Nicknames are cleaned of control, zero-width and bidi-override characters and length-capped (`InputSanitizer`). |
| Task hijacking | The launcher activity has an empty `taskAffinity`. |
| Release builds | R8 shrinking and obfuscation, not debuggable. The signing key is read from environment variables, never from the repo. |
| Debug builds | Signed with the **public** debug key in `app/debug.keystore`, only so CI builds install over each other. Never trust an APK only because of this signature. |
| Secrets | `google-services.json` is git-ignored and injected in CI from the `GOOGLE_SERVICES_JSON_B64` secret. |
| Crash reports | Firebase Crashlytics, without user ids or usernames. |

## Supply chain and health checks

- **CI** (`android.yml`) builds, compiles the UI tests and runs the unit tests on every push and PR, plus every Monday even without changes.
- **Android lint** runs in CI with security checks promoted to errors; results appear under **Security → Code scanning**.
- **CodeQL** (`security-extended` queries) scans the Kotlin code on every push and weekly.
- **Dependabot** opens weekly update PRs for Gradle libraries, GitHub Actions and the Python pipeline. Dependabot alerts flag libraries with known vulnerabilities.
- All GitHub Actions are **pinned to commit SHAs**, workflows run with read-only permissions by default, and checkout does not keep the token.
- The Gradle wrapper checksum is validated on every CI run.
- Every published APK comes with a `.sha256` file to check the download.

## Known limits

- **App Check is not enforced.** Play Integrity only vouches for apps installed from Google Play, and this APK is distributed through GitHub. The Firestore rules are the line of defence; a determined player could still post a plausible fake score through the API.
- **No password reset**, because there is no e-mail address. Players should use a password manager.
