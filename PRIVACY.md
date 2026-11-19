# Privacy

**Short version:** Megyejáték stores a username, your scores and anonymous crash reports. No e-mail, no location, no ads, no tracking.

| Data | Where | Why | Who can see it |
|---|---|---|---|
| Username and password | Firebase Authentication (Google, EU project) | To sign in | Password: nobody (Firebase stores only a hash). Username: other signed-in players |
| Scores (mode, points, time, hits, hints) | On your phone; completed rounds also in Firestore | Personal records and the global leaderboard | Other signed-in players see the leaderboard |
| Crash reports (device model, Android version, stack trace) | Firebase Crashlytics | To find and fix crashes | The developer |
| Nickname and Vaktérkép setting | On your phone only | Settings | Only you |

The app does not read your location, contacts or files, and shows no ads. Nothing is backed up to the cloud by Android.

**Deleting your data:** open an issue on GitHub or contact the developer through her GitHub profile, and the account and its scores will be deleted. An in-app "delete account" button is planned.

Sources of the map data are listed in [DATA_SOURCES.md](DATA_SOURCES.md).
