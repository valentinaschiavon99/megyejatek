# Privacy

**Short version:** Megyejáték stores a username, your scores and anonymous crash reports. No e-mail, no location, no ads, no tracking.

| Data | Where | Why | Who can see it |
|---|---|---|---|
| Username and password | Firebase Authentication (Google, EU project) | To sign in | Password: nobody (Firebase stores only a hash). Username: other signed-in players |
| Scores (mode, points, time, hits, hints) | On your phone; completed rounds also in Firestore | Personal records and the global leaderboard | Other signed-in players see the leaderboard |
| Crash reports (device model, Android version, stack trace) | Firebase Crashlytics | To find and fix crashes | The developer |
| Nickname and Vaktérkép setting | On your phone only | Settings | Only you |

The app does not read your location, contacts or files, and shows no ads. Nothing is backed up to the cloud by Android.

**Deleting your data:** Beállítások → Fiók törlése deletes your account and every score it posted, right away. Scores kept on your phone are removed too.

**Update check:** the app asks GitHub whether a newer version exists. This request carries no personal data.

Sources of the map data are listed in [DATA_SOURCES.md](DATA_SOURCES.md).
