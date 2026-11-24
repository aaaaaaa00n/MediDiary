# Hackathon Workflow: Caregiver Sync · Bad-Name Stop · 10 s Silence Gate

Status: **Decisions agreed (see below). All three tasks implemented on branch `claude/peaceful-fermat-d9syz2`.**

## Team decisions (2026-11-24)

| Question | Decision |
|---|---|
| Task 1 backend | ✅ Opt-in, E2E-encrypted **Firebase** sync in a separate `family` flavor; the `offline` flavor stays zero-network |
| Task 1 what syncs | ✅ **Only medicine and symptom records**, never transcripts or audio |
| Task 2 "stop" | ✅ Halt **before Review/Save** (no mid-recording detection; too memory-heavy). The user can still cut a recording short with the stop button |
| Task 2 override | ✅ Keep **"Keep as typed"** |
| Task 3 60 s window | ✅ Counts from the **mic tap**, as before |
| Task 3 trailing silence | ✅ No auto-stop on trailing silence. The **user taps stop** (or 60 s cap) |

## Implementation status

| Task | Where | Tests |
|---|---|---|
| 3 · silence gate | `audio/SpeechGate.kt` (VAD + gate), `audio/AudioRecorder.kt`, `CheckInViewModel.startRecording` | `SpeechGateTest` (9) |
| 2 · name gate | `data/dgda/MedicineNameGate.kt`, `data/dgda/Phonetic.kt`, `CheckInViewModel.extract`, `HomeContent.RejectedNamesCard` | `MedicineNameGateTest` (7) |
| 1 · family sync | `sync/SyncCore.kt` (crypto, invite, merge), `data/DiaryRepository.kt` (merge, tombstones), Room v2 migration, `src/family/.../FirestoreFamilySync.kt`, `ui/settings/FamilySharingCard.kt`, `firebase/firestore.rules` | `SyncCoreTest` (7) |

Setup and usage for sync: [`FAMILY_SYNC_SETUP.md`](FAMILY_SYNC_SETUP.md) · step-by-step two-phone guide: [`MediDiary_Family_Sync_Setup.pdf`](MediDiary_Family_Sync_Setup.pdf).

> The rest of this document is the original plan. Where it differs from the decisions above, the decisions win
> (e.g. Task 3's 60 s now counts from the tap, and there is no trailing-silence stop).
Base: `main` @ `f8e5fad` (pulled 2026-11-24).

---

## 0. Where the current app stands (what we build on)

```
Mic tap ─► AudioRecorder.record()          (16 kHz WAV, hard cap 60 s, stops only on tap / cap)
        ─► CheckInViewModel.stopAndTranscribe()
        ─► InferencePipeline.transcribe()  (whisper.cpp, runs AFTER recording, not streaming)
        ─► user edits transcript
        ─► InferencePipeline.extract()     (Gemma → JSON)
        ─► ModelResponseParser → ExtractionSanitizer → DgdaMatcher (EXACT / FUZZY / UNMATCHED)
        ─► Review cards ─► "Confirm & Save" ─► DiaryRepository.saveVerified() ─► Room (local only)
```

Key files: `audio/AudioRecorder.kt`, `ui/checkin/CheckInViewModel.kt`, `ui/home/HomeScreen.kt`,
`data/dgda/DgdaMatcher.kt`, `data/db/{Entities,Daos,AppDatabase}.kt`, `data/DiaryRepository.kt`.

> ⚠️ **Blocking conflict for Task 1.** `claude.md` Rule #1 and the manifest strip `INTERNET`, and the CI
> step *"Verify APK"* **fails the build** if a network permission appears. You can't sync with a parent
> in another city unless one of these changes. **The team has to decide this before Task 1 starts** (see §1.1).

---

## Recommended order and split (3 people)

| Order | Task | Size | Owner | Depends on |
|---|---|---|---|---|
| 1 | **Task 3**: 10 s silence gate + 60 s window | S (~2 h) | Dev A | none |
| 2 | **Task 2**: stop on wrong / badly mispronounced medicine | M (~3 h) | Dev B | none (uses history table from Task 1 if ready, else DGDA only) |
| 3 | **Task 1**: shared caregiver diary + sync | L (~5–6 h) | Dev C (+A after Task 3) | Decision §1.1 |
| 4 | Integration, demo script, CI update | S | everyone | 1–3 |

Tasks 2 and 3 are independent and fully offline, so start them right away. Task 1 waits for the §1.1 decision.

---

## Task 3: 10-second silence gate, then 60 s recording

### Behaviour
```
tap mic
  │
  ▼
[LISTENING]  up to 10 s, nothing written to disk, last 0.5 s kept in a ring buffer (pre-roll)
  │                                     │
  │ speech detected                     │ 10 s pass with no speech
  ▼                                     ▼
[RECORDING]  writes pre-roll + audio    [NO_SPEECH] stop, delete file, no Whisper call,
  │          until 60 s after the TAP          show "Didn't hear anything, tap to try again"
  │ 60 s reached OR user taps stop
  ▼
auto-transcribe (the silent lead-in was never recorded, so Whisper gets only real audio)
```
- The "blank 10 seconds are removed" because **we never write them**. Only ~0.5 s of pre-roll before
  speech onset is kept so the first syllable isn't clipped.
- Max total time on the mic is 10 s + 60 s. Max audio Whisper sees is 60.5 s.
- Side benefit: Whisper stops hallucinating on silence (the "Thank you for watching" problem).

### Speech detection (VAD). Energy-based, no new model.
- Frame = 30 ms (480 samples). Compute RMS → dBFS.
- Calibrate a noise floor from the first ~300 ms (median frame energy).
- Frame is "voiced" if `dBFS > max(noiseFloor + 12 dB, -45 dBFS)`.
- **Onset** = ≥ 8 voiced frames within the last 10 (~240 ms), which ignores taps and clicks.
- Constants live in one `VadConfig` so we can tune them on the demo phone.
- Stretch option: whisper.cpp v1.9.5 has a built-in Silero VAD, but that needs another model file. Skip it unless energy VAD fails on the demo phone.

### Code changes
| File | Change |
|---|---|
| `audio/Vad.kt` (new) | Pure-Kotlin `EnergyVad` (frame → voiced?, onset detector). JVM-testable. |
| `audio/AudioRecorder.kt` | State machine `LISTENING → RECORDING`. Ring-buffer pre-roll. `record()` returns `sealed RecordingResult { Speech(file, ms), NoSpeech }`. New `StateFlow<RecPhase>` and `listenRemainingMs`. `MAX_SECONDS` counts from onset. Add `SILENCE_TIMEOUT_MS = 10_000`. |
| `ui/checkin/CheckInState.kt` | `Busy.LISTENING` (before speech) as well as `RECORDING`. |
| `ui/checkin/CheckInViewModel.kt` | When `record()` returns on its own (60 s cap / NoSpeech), **auto-continue**: Speech → transcribe, NoSpeech → delete + info message. Remove the 700 ms "too short" check (VAD covers it). |
| `ui/home/HomeScreen.kt` | Remove the `LaunchedEffect` that stops at 60 s (the recorder owns it now). |
| `ui/home/HomeContent.kt` | LISTENING: "Start speaking… 7 s" countdown ring. RECORDING: existing 0:00 → 1:00 timer. |
| `test/.../VadTest.kt` | Synthetic PCM: pure silence → NoSpeech. Noise only → NoSpeech. Tone at 3 s → onset ≈3 s. Tone at 11 s → NoSpeech. Single click → no onset. 90 s of tone → file is 60 s + pre-roll. |

---

## Task 2: Stop when the medicine name is wrong or badly mispronounced

### Interpretation (please confirm, see Open Questions)
Whisper runs **after** recording, so we can't judge names while the mic is open. "Stop recording" here means:
**halt the check-in before anything reaches the Review/Save step**, discard the proposals, keep the
transcript, and ask the user to say the name again or type it. Nothing bad gets into the diary.

### Three-way verdict per extracted medicine
Compare each name against **DGDA brands + generics + aliases** ∪ **every name previously saved in this
diary** (the user's personal vocabulary).

| Verdict | Rule (tunable) | Result |
|---|---|---|
| **KNOWN** | existing `DgdaMatcher` EXACT/FUZZY, or exact hit in history | continue as today |
| **CLOSE** (mild mispronunciation) | best similarity ≥ 0.60 **or** phonetic key match | continue, card shows "Did you mean **Seclo**?" chip |
| **REJECT** (wrong / horribly mispronounced) | best similarity < 0.60 **and** no phonetic match | **stop the flow**: no review cards, banner "We couldn't recognise 'xyz'. Say it again or type it.", buttons *Re-record* / *Edit transcript* / *Keep as typed* |

- `similarity = 1 − levenshtein(a, b) / max(len a, len b)` on `DgdaMatcher.normalize()` output.
- **Phonetic key** (handles Bangla-script ↔ Latin and vowel slop): transliterate Bangla to rough Latin
  (`নাপা→napa`, `সেকলো→seklo`), lowercase, `c/k/q→k`, `ph/f→f`, `z/j→j`, drop vowels after the first
  letter, collapse repeats. `"seklo"`, `"secklow"` → `skl`.
- **Symptoms are never gated**. Only `type == medicine`.
- **"Keep as typed"** (human override) saves as `MANUAL` and adds the name to history, so it's KNOWN next
  time. We need this because the DGDA table only has **80 brands**. Without it, real medicines would be blocked.

### Code changes
| File | Change |
|---|---|
| `data/dgda/MedicineNameGate.kt` (new) | `verdict(name, dgdaKeys, historyNames): Verdict{KNOWN, CLOSE(suggestion), REJECT(best, score)}`. Pure Kotlin. |
| `data/dgda/Phonetic.kt` (new) | Bangla→Latin transliteration and phonetic key. |
| `data/db/Daos.kt` | `SELECT DISTINCT originalName FROM diary_records WHERE type='medicine'` |
| `ui/checkin/CheckInViewModel.kt` | In `extract()` after matching: if any REJECT → `busy=NONE`, `proposals=[]`, `rejected=[…]`, error banner. Add `keepAsTyped(name)`. |
| `ui/checkin/CheckInState.kt` | `rejectedNames: List<RejectedName>` |
| `ui/home/HomeContent.kt`, `components/Banners.kt` | Reject banner with 3 actions. "Did you mean" chip on CLOSE cards. |
| `test/.../MedicineNameGateTest.kt` | Table test: `napa/নাপা/nappa → KNOWN`, `seklow → CLOSE(Seclo)`, `xyzzor/banana → REJECT`, a history-only name → KNOWN, an empty list → no crash. |

**Prep for the demo:** record ~15 real utterances (good, slightly off, garbage) on the demo phone and tune
the 0.60 threshold against them.

---

## Task 1: One shared diary, parent's phone ⇄ caregiver's phone, auto-sync

### 1.1 Decision needed first: how do two phones in different places talk?

| Option | Works across cities? | Keeps "zero cloud"? | Hackathon effort |
|---|---|---|---|
| **A. Opt-in E2E-encrypted cloud sync (Firebase Firestore)** ✅ *recommended* | Yes, realtime | Inference stays 100 % on-device. Only **encrypted, already-verified** records leave the phone, and only after the user turns on Family Sharing. Firebase sees ciphertext. | Medium |
| B. Supabase / own tiny server | Yes | Same as A | Medium-high (have to host it) |
| C. Bluetooth / Wi-Fi Direct / Nearby | **No**, phones must be in the same room | Yes | Medium |
| D. Manual: export encrypted `.mdiary` file → WhatsApp → import | Yes, but not automatic | Yes | Low (fallback only) |

**Proposal:** Option A. Reword Rule #1 to *"Zero cloud **inference**. Network is used only for optional,
end-to-end-encrypted family sync of user-verified records."* Then:
- Manifest: keep the `tools:node="remove"` lines in `src/offline/` and add `INTERNET` +
  `ACCESS_NETWORK_STATE` only in a `family` **product flavor**, so the pure-offline build still exists.
- CI: the "no network permission" check runs on `offlineRelease`. `familyRelease` gets its own check that
  allows only `INTERNET` + `ACCESS_NETWORK_STATE`.
- README / claude.md / privacy screen updated to match.

### 1.2 Pairing (no accounts, no email)
```
Parent phone                                   Caregiver phone
Settings → Family sharing → "Create shared diary"
  • diaryId = random UUID
  • key     = 256-bit AES key (Android Keystore-wrapped)
  • shows QR: medidiary://join?d=<diaryId>&k=<base64key>&n=<"Ammu">
                                               Settings → Family sharing → "Join (scan QR)"
                                                 • stores diaryId + key, display name
both: Firebase Anonymous Auth → uid added to /diaries/{diaryId}/members/{uid}
```
- The QR is shown on the parent's screen. For a remote parent, *Share invite* sends the same link over
  WhatsApp. The link contains the key, so it carries a "treat like a password" warning.
- Either side can create the diary. Usually the caregiver sets it up on the parent's phone during a visit.
- Security rules: only members can read or write `/diaries/{diaryId}/**`. Docs contain only ciphertext plus `updatedAt`.

### 1.3 Data model changes (Room v1 → v2, a real `Migration`, not destructive)
`DiarySession` and `DiaryRecord` gain:
| Column | Purpose |
|---|---|
| `uuid TEXT UNIQUE` | global ID across phones (backfilled for existing rows) |
| `updatedAt INTEGER` | last-writer-wins clock |
| `deleted INTEGER` | tombstone, so deletes sync |
| `authorDeviceId TEXT`, `authorName TEXT` | "Added by Ammu / by you" badge |
| `syncState TEXT` | `LOCAL_ONLY · PENDING · SYNCED` |

New table `sync_meta(diaryId, lastPulledAt, deviceId, displayName)`.
All DAO reads gain `AND deleted = 0`. `DiaryRepository.update/delete` set `updatedAt`, `PENDING`, and a tombstone instead of a hard delete.

### 1.4 Sync engine
```
local write (Confirm & Save / edit / delete)
   └─► row.syncState = PENDING ─► SyncWorker (WorkManager, NetworkType.CONNECTED, expedited)
                                     ├─ push: encrypt(record JSON) → Firestore doc /diaries/{d}/records/{uuid}
                                     └─ pull: Firestore snapshot listener (while app open) + worker (background)
remote change ─► decrypt ─► merge: if remote.updatedAt > local.updatedAt (tie → deviceId) then upsert
```
- **Only verified records sync** (they're the only thing in Room anyway), so Rule #2 still holds.
- Raw audio and transcripts with audio never leave the phone. Decide whether `rawTranscript` syncs (suggest **no**, records only).
- Retention stays **local per device**: purge local rows, don't push tombstones for retention deletes.
- Offline-first: works without network and catches up later (Firestore offline cache + our outbox).

### 1.5 Code changes
| File | Change |
|---|---|
| `app/build.gradle.kts`, `libs.versions.toml` | `offline` / `family` flavors. `family` gets firebase-bom (firestore, auth), ZXing/ML Kit barcode scanning + QR generation. `google-services.json` for the family flavor only. |
| `src/family/AndroidManifest.xml` | `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA` (QR scan) |
| `data/db/Entities.kt`, `AppDatabase.kt` | new columns, `MIGRATION_1_2`, `exportSchema = true` |
| `data/db/Daos.kt` | pending/changed-since queries, upsert-by-uuid, `deleted = 0` filters |
| `data/DiaryRepository.kt` | stamps `uuid/updatedAt/author`, tombstones, enqueue sync |
| `sync/` (new) | `SyncCrypto` (AES-GCM), `PairingManager` (QR payload), `SyncRepository` (interface, with a no-op impl in `offline`), `FirestoreSync` (`family`), `SyncWorker`, `MergePolicy` (pure LWW, unit-tested) |
| `ui/settings/` | "Family sharing" section: Create / Show QR / Join / Leave / last synced time |
| `ui/diary/CalendarPanelContent.kt` | author badge per entry, sync status dot |
| `.github/workflows/android-apk.yml` | build and verify both flavors (see §1.1) |
| tests | `MergePolicyTest` (LWW, tombstone wins, tie-break), `SyncCryptoTest` (round-trip, wrong key fails), `PairingPayloadTest`, Room `MigrationTest` |

---

## End-to-end demo script (for the judges)
1. **Silence gate:** tap mic, stay quiet → after 10 s it stops by itself, "Didn't hear anything", no transcription.
2. **Speech:** tap mic, wait 3 s, say "সকালে নাপা খেয়েছি, মাথা ব্যথা" → recording starts at the first word, timer shows up to 1:00, auto-transcribes. The audio Whisper got has no 3 s lead-in.
3. **Mispronounced:** say "seklow" → card shows "Did you mean Seclo?".
4. **Garbage:** say "blorvatrex" → flow stops with the reject banner, nothing reaches Save. Show *Keep as typed*.
5. **Sync:** parent phone saves "Napa", and within seconds it appears on the caregiver phone with an "Added by Ammu" badge. Caregiver deletes an entry → it disappears on the parent's phone. Turn on airplane mode, add an entry, turn it off → it catches up.

## Open questions for the team
1. **Task 1:** Is Option A (opt-in, E2E-encrypted Firebase sync, `family` flavor) OK, given that it relaxes Rule #1 for sync only?
2. **Task 1:** Should the transcript text sync too, or only the medicine/symptom records?
3. **Task 2:** OK with "stop" meaning *halt before Review/Save* rather than cutting the mic mid-sentence? Live mid-recording detection would need chunked streaming Whisper. It's possible, but CPU-heavy and against the one-model-at-a-time memory rule.
4. **Task 2:** Keep the *Keep as typed* override? (Recommended, since DGDA only has 80 brands.)
5. **Task 3:** After speech starts, also auto-stop on ~3 s of trailing silence, or always run the full 60 s unless the user taps stop?
6. **Task 3:** Does the 60 s start at the first sound (proposed) or at the mic tap?
