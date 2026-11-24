# Family sync setup (family flavor)

The app has two flavors:

| Flavor | APK | Network | What it is |
|---|---|---|---|
| `offline` (default) | `MediDiary-<v>.apk` | **none** (CI fails if any network permission appears) | the original zero-cloud app |
| `family` | `MediDiary-<v>-family.apk` | `INTERNET` + `ACCESS_NETWORK_STATE` only | same app + opt-in, end-to-end-encrypted family sharing |

Speech recognition and extraction are on-device in both. With family sharing on, **only the
medicine/symptom entries the user confirmed** are sent, AES-256-GCM encrypted with a key that
exists only on the family's phones. Audio and transcripts never leave the phone.

## One-time Firebase setup (≈10 min)

1. <https://console.firebase.google.com> → **Add project** (Analytics not needed).
2. **Build → Authentication → Get started → Sign-in method → Anonymous → Enable.**
3. **Build → Firestore Database → Create database** (production mode, a region near Bangladesh,
   e.g. `asia-south1`).
4. Firestore → **Rules** → paste [`firebase/firestore.rules`](../firebase/firestore.rules) → **Publish**.
5. Project settings → **Add app → Android**, package name **`com.medidiary.app.family`** →
   download `google-services.json` → put it at **`app/google-services.json`** (it is git-ignored).
   The build reads the API key, app id and project id from it. The Google Services Gradle plugin
   is not needed.
6. Build: `./gradlew :app:assembleFamilyDebug` (or pick *familyDebug* under Build Variants).

### CI
Add repository secrets `MEDIDIARY_FIREBASE_API_KEY` (`current_key`), `MEDIDIARY_FIREBASE_APP_ID`
(`mobilesdk_app_id`) and `MEDIDIARY_FIREBASE_PROJECT_ID` (`project_id`), all from that JSON. Without them
the family APK still builds and Settings shows *"no sync service configured"*.

## Using it

1. **Parent's phone** (or yours, during a visit): Settings → **Family sharing** → your name →
   *Create shared diary*. Everything already in the diary is uploaded.
2. Tap **Invite family** → a QR code appears.
3. **Caregiver's phone**: Settings → Family sharing → your name → **Scan QR** (or *Share link* over
   WhatsApp and paste it) → **Join**.
4. Entries saved on either phone appear on the other within seconds while both are online. Edits and
   deletes sync too. Phones that were offline catch up when they reconnect (background sync every
   ~15 min, or *Sync now*). Entries from the other phone show **"by <name>"** in the calendar.

**Treat the QR or link like a password.** Anyone holding it can read and add to the diary. To
cut someone off, everyone taps *Leave* and one phone creates a new diary.

## How it works

```
Confirm & Save / edit / delete ──► Room row (syncState = PENDING, updatedAt, updatedBy)
        └─► encrypt(record JSON, key, aad = diaryId/uuid) ──► diaries/{id}/records/{uuid} {v,u,dev,c}
Firestore listener / 15-min worker ──► decrypt ──► last-writer-wins merge (updatedAt, then device id)
```

* Deletes are tombstones (`deleted = 1`), so they reach the other phone.
* Retention (30/60/custom days) runs on each phone and is never synced. Entries older than this phone's
  window are not re-imported.
* *Delete all diary data* first leaves the shared diary, so the other phone can't refill it.
* Room schema v1 → v2 migration adds `uuid`, `updatedAt`, `updatedBy`, `deleted`, `authorName`,
  `authorDevice`, `syncState`. Existing diaries keep their entries.

Code: `app/src/main/java/com/medidiary/app/sync/` (crypto, invite, merge rules: JVM-tested in
`SyncCoreTest`) and `app/src/family/java/com/medidiary/app/sync/FirestoreFamilySync.kt`.
