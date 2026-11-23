# MediDiary — Developer Guide (Android Studio)

This guide covers setting up Android Studio on **Windows** or **Fedora**, building and running the app, finding your way
around the code, changing the **Catppuccin Mocha + neumorphism** UI, working on the on-device AI, and shipping the demo APK.

---

## 0. Quick start (if you already have Android Studio)

```bash
git clone --recurse-submodules https://github.com/aaaaaaa00n/MediDiary.git
```
1. Open the folder in Android Studio and let Gradle sync finish.
2. In **SDK Manager → SDK Tools → Show Package Details**, install **NDK 27.2.12479018** and **CMake 3.22.1**.
3. Plug in an arm64 Android phone with USB debugging on and press **Run ▶**. The first build takes 5–15 min while it compiles whisper.cpp and llama.cpp.
4. Push the two model files (§4.3) and tap **Settings → Load model → Re-scan**.

---

## 1. Pinned versions

| Component | Version | Where it is set |
|---|---|---|
| Android Studio | Ladybug (2024.2) or newer | — |
| Android Gradle Plugin | 8.7.3 | `gradle/libs.versions.toml` |
| Gradle (wrapper) | 8.11.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin / Compose compiler | 2.0.21 | `gradle/libs.versions.toml` |
| Compose BOM (Material 3 1.3) | 2024.12.01 | `gradle/libs.versions.toml` |
| JDK | 17+ (Android Studio's bundled JBR is fine) | Settings → Build Tools → Gradle → Gradle JDK |
| compileSdk / targetSdk / minSdk | 35 / 34 / 26 | `app/build.gradle.kts` |
| **NDK** | **27.2.12479018** | `ndkVersion` in `app`, `whisper`, `llama` `build.gradle.kts` |
| **CMake** | **3.22.1** | `externalNativeBuild` in the same three files |
| whisper.cpp / llama.cpp | v1.9.5 / b12621 | git submodules in `third_party/` |

---

## 2. Setup on Windows 10/11

### 2.1 Install
1. Install **Git for Windows** (https://git-scm.com/download/win) with the default "Checkout Windows-style, commit Unix-style". The repo's `.gitattributes` keeps `gradlew` working everywhere.
2. Install **Android Studio** (https://developer.android.com/studio) and run the setup wizard with the *Standard* install.
3. Open **More Actions → SDK Manager** (or *Settings → Languages & Frameworks → Android SDK*):
   - **SDK Platforms:** Android 15 (API 35).
   - **SDK Tools** (tick *Show Package Details*): **NDK (Side by side) 27.2.12479018**, **CMake 3.22.1**, Android SDK Platform-Tools, and *Google USB Driver* (Pixel phones only; Samsung, Xiaomi and others need the vendor's USB driver).

### 2.2 Clone to a short path
Windows limits paths to 260 characters, and the native build creates deep object paths. Clone close to the drive root:

```powershell
git config --global core.longpaths true
```

```powershell
git clone --recurse-submodules https://github.com/aaaaaaa00n/MediDiary.git C:\md
```

Optional, recommended: enable OS long paths once in an **Administrator** PowerShell, then reboot.
```powershell
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force
```

### 2.3 Windows tips
- **Exclude the project from Microsoft Defender** (`C:\md`, `%USERPROFILE%\.gradle`, `%LOCALAPPDATA%\Android\Sdk`). Native builds can be 2–3× slower without it. Android Studio offers this in a pop-up; accept it.
- Use Android Studio's **Terminal** tab. It already has `adb` and the right Java on `PATH`.
- Command-line builds use `.\gradlew.bat`. If Gradle can't find Java:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  ```

---

## 3. Setup on Fedora (40 / 41 / 42)

### 3.1 Install
Install Android Studio from **JetBrains Toolbox** or the **official tar.gz**. Avoid the Flatpak build: its sandbox regularly breaks NDK/CMake builds and USB access.

```bash
sudo dnf install git android-tools
```
`android-tools` gives you `adb` plus udev rules that let your user access phones over USB.

```bash
sudo tar -xzf ~/Downloads/android-studio-*-linux.tar.gz -C /opt
```

```bash
/opt/android-studio/bin/studio.sh
```
Use **Tools → Create Desktop Entry** for a launcher. Then install the same SDK components as on Windows (§2.1 step 3, without the USB driver).

For the emulator (optional), give your user KVM access, then log out and back in:
```bash
sudo usermod -aG kvm "$USER"
```

### 3.2 Clone
```bash
git clone --recurse-submodules https://github.com/aaaaaaa00n/MediDiary.git ~/MediDiary
```

### 3.3 Fedora tips
- If `adb devices` says `no permissions`, check that `android-tools` is installed, re-plug the phone and accept the prompt. As a last resort run `adb kill-server` and then `adb devices`.
- Don't mix the SDK's `adb` with dnf's `adb`; `which adb` shows which one is active. Mixed versions keep restarting each other's server.
- For command-line builds, set Java first:
  ```bash
  export JAVA_HOME=/opt/android-studio/jbr
  ```
- SELinux needs no changes.

---

## 4. First build and run

### 4.1 Open the project
**File → Open →** the folder containing `settings.gradle.kts`, then **Trust project**. Wait for *Gradle sync* (bottom status bar).
Sync errors about the NDK or CMake mean step 2.1.3 / 3.1 isn't done. Missing `third_party` sources mean you need:
```bash
git submodule update --init --recursive
```

### 4.2 Run on a phone
1. On the phone: **Settings → About phone → tap Build number 7×**, then **Developer options → USB debugging**.
   (Or use **Wireless debugging** with Android Studio's *Device Manager → Pair Devices Using Wi-Fi*.)
2. Select the phone in the device dropdown, choose the **app** configuration and press **Run ▶**.
3. The first build compiles whisper.cpp and llama.cpp (5–15 min). Incremental builds after that take seconds to a minute.

### 4.3 Install the AI models
The models are never in git or the APK. Open the app once so it creates its folder, then:

```bash
adb push medidiary-whisper.bin /sdcard/Android/data/com.medidiary.app/files/models/medidiary-whisper.bin
```

```bash
adb push medidiary-gemma.gguf /sdcard/Android/data/com.medidiary.app/files/models/medidiary-gemma.gguf
```

Then tap **Settings → Load model → Re-scan**. You can also copy the files to the phone and use **Load model → Import…**.

| File | Source |
|---|---|
| `medidiary-whisper.bin` | `ggml-model-q6_k.bin` (or `q4_0`) from SayedShaun/bengali-whisper-medium-ggml; see `training/whisper_bengali_setup.ipynb` |
| `medidiary-gemma.gguf` | Your fine-tune, from `training/gemma3_medidiary_v2.ipynb` |

Models survive re-installs from Android Studio but are deleted when the app is **uninstalled**. Without models the app runs in a labelled **demo mode** with scripted AI output, which is fine for UI work.

---

## 5. Project tour

### 5.1 Modules
```
app/       Kotlin + Jetpack Compose app (com.medidiary.app)
whisper/   JNI wrapper → libmedidiary_whisper.so   (whisper.cpp, its own static ggml)
llama/     JNI wrapper → libmedidiary_llama.so     (llama.cpp,   its own static ggml)
third_party/whisper.cpp, third_party/llama.cpp     pinned git submodules — never edit, only bump
training/  Colab notebooks (Gemma fine-tune v2, Whisper setup/benchmark)
docs/      this guide
```

### 5.2 Package map (`app/src/main/java/com/medidiary/app/`)
| Package | What lives there |
|---|---|
| `MediDiaryApp.kt` | `AppContainer` (manual DI): DB, repositories, model manager, pipeline, exports. Seeds the DGDA table on start. |
| `ui/home/HomeScreen.kt` | **The main screen**: ☰ Calendar, ⚙ Settings, EN/BN toggle, VOICE MODEL card, TRANSCRIPTION AND EDITS card, review cards |
| `ui/diary/` | `CalendarPanel` (month grid + DATES list + edits), `DiaryViewModel`, `DateUtils` |
| `ui/settings/` | Settings window: User details, Load model, Retention 30/60/Custom, Export CSV/PDF, Advanced, Privacy |
| `ui/checkin/CheckInViewModel.kt` | Record → transcribe → extract → review → save state machine (the human-in-the-loop logic) |
| `ui/components/` | Neumorphic kit (`NeuCard`, `NeuButton`, `NeuIconButton`, `NeuSegmented`, `NeuTextField`, `SectionLabel`), banners, edit dialogs, kit previews |
| `ui/home/HomePreviews.kt` | Sample data + previews of the real Home, Calendar and Settings screens |
| `ui/theme/` | `Theme.kt` (Catppuccin Mocha palette + Material 3 scheme), `Neumorphism.kt` (`Modifier.neumorphic`) |
| `audio/` | 16 kHz mono PCM WAV recorder |
| `ai/` | `InferencePipeline` (one-model-at-a-time mutex), native/mock engines, `ModelManager` |
| `domain/` | `ModelResponseParser`, `ExtractionSanitizer`, the exact system prompt |
| `data/` | Room entities/DAOs, `DiaryRepository`, `SettingsStore`, DGDA seeder + matcher |
| `export/` | `PdfReportGenerator`, `CsvExporter`, FileProvider sharing |
| `work/` | `RetentionWorker` (daily 30/60/custom-day cleanup) |

### 5.3 How a check-in flows
```
[Mic] ─► AudioRecorder (WAV in cache) ─► InferencePipeline.transcribe ─► WhisperNative (load→run→free)
      ─► transcript in TRANSCRIPTION AND EDITS (user edits) ─► [Confirmation]
      ─► InferencePipeline.extract ─► LlamaNative (load→run→free) ─► ModelResponseParser ─► ExtractionSanitizer
      ─► DgdaMatcher (generic names, deterministic) ─► review cards (user ticks / edits / adds)
      ─► [Confirm & Save] ─► DiaryRepository.saveVerified (Room) ─► CALENDAR panel updates live
```
The WAV is deleted right after transcription. Nothing touches Room until **Confirm & Save**.

---

## 6. Working on the UI

### 6.1 Design tokens: Catppuccin Mocha
All colours come from `ui/theme/Theme.kt`. Use the tokens and never hard-code hex values in screens.

| Token | Use |
|---|---|
| `Mocha.Base` | Screen background **and** raised neumorphic surfaces |
| `Mocha.Mantle` | Inset wells (pressed buttons, text fields), drawer background |
| `Mocha.Surface0/1/2` | Pills, banners, dividers |
| `Mocha.Text / Subtext1 / Subtext0 / Overlay*` | Text, from primary down to hints |
| `Mocha.Blue` (`MedicineAccent`) | Primary actions, medicines, selected states |
| `Mocha.Peach` (`SymptomAccent`) | Symptoms |
| `Mocha.Mauve` | Settings, model actions |
| `Mocha.Green` / `Yellow` / `Red` | Success · DGDA match / warnings · fuzzy match / danger · recording · delete |
| `Mocha.Lavender` | Section labels |

The app is **always dark** (Mocha) and ignores the system light/dark setting. The PDF export stays black-on-white for printing.

### 6.2 Neumorphism kit
`Modifier.neumorphic(cornerRadius, elevation, inset)` draws the soft-UI effect: the same colour as the background,
a light shadow up-left and a dark shadow down-right. `inset = true` gives the pressed or selected look.

| Component | Use it for |
|---|---|
| `NeuCard { … }` | A section (VOICE MODEL, TRANSCRIPTION AND EDITS, review). Column with 12 dp spacing. |
| `NeuButton(onClick, selected = …, accent = …, alwaysAccent = …) { Icon(); Text() }` | Any button. Inset while pressed or when `selected`. |
| `NeuIconButton(icon, description, onClick, tint)` | Round icon buttons (☰, ⚙, ←) |
| `NeuSegmented(options, selected, onSelect)` | Toggles like EN/BN, All/Medicines/Symptoms, export ranges |
| `NeuTextField(value, onValueChange, …)` | Text input in an inset well |
| `SectionLabel("Voice model")` | Upper-case section headings |
| `MessageBanner(text, BannerKind.X)` | Info, success, error and demo-mode notices |

Rules that keep it looking right:
1. **Leave 12–20 dp between neumorphic elements and around their parent's edges.** The shadows draw outside the element's bounds and get cut off if siblings touch or a parent clips.
2. Raised elements must sit on `Mocha.Base`. Neumorphism only works when the element and background share a colour.
3. Use colour for meaning (accent text or icons), not big coloured fills.
4. Blurred shadows need **Android 9+**. On Android 8.x the same components render flat with a subtle edge, which is expected.

### 6.3 Iterate without a phone: Compose Previews
Every screen is split into a **stateful wrapper** (collects ViewModel state, handles permissions and intents) and a
**stateless content composable** (state in, callbacks out). Because of that, the *real* screens render in Preview with
sample data:

| File | Previews |
|---|---|
| `ui/home/HomePreviews.kt` | Home (phone, with review cards) · Home while recording · Home on a tablet with the permanent Calendar · Settings |
| `ui/components/NeuPreviews.kt` | The neumorphic component kit on its own |

Open either file and switch the editor to **Split** or **Design** (top-right).
- **Interactive mode** (the ▶ icon on a preview) lets you press buttons and flip toggles.
- Edit the sample data in `PreviewData` (Bangla text, more cards, error banners) to check edge cases.
- To preview a new screen, follow the same pattern: a `XxxUi` data class + `XxxActions` interface + a stateless
  `XxxContent(ui, actions)`, then a `@Preview` that passes sample data and a no-op actions object.

The images in `docs/screenshots/` were rendered from these same preview functions.

### 6.4 Iterate on a device
- **Live Edit** (*Settings → Editor → Live Edit*) pushes Compose changes to a running app on Android 11+ without reinstalling.
- **Layout Inspector** (*Tools → Layout Inspector*) shows the live Compose tree and recomposition counts. Use it when shadows look clipped.
- Test on a **small phone (360 dp wide)** and in **landscape or tablet**. At 840 dp and wider the Calendar becomes a permanent left panel, as in the wireframe.
- Bangla text renders with the system Noto Sans Bengali font. Check long Bangla words in the review cards and DATES list.

### 6.5 Adding a screen
1. Create `ui/<feature>/<Feature>Screen.kt` (+ `<Feature>ViewModel.kt` extending `AndroidViewModel`; get dependencies from `(app as MediDiaryApp).container`). Keep the layout in a stateless `<Feature>Content(ui, actions)` so it can be previewed (§6.3).
2. Add a route in `ui/MediDiaryNavHost.kt`, e.g. `composable("feature") { FeatureScreen(viewModel()) }`.
3. Start the screen with `Column(Modifier.fillMaxSize().background(Mocha.Base).safeDrawingPadding()…)`. The app draws edge-to-edge, so this keeps content out from under the status and navigation bars.

---

## 7. Working on AI and native code

| Task | Where |
|---|---|
| Change Whisper decoding (language, threads, prompt) | `ai/NativeEngines.kt`, `whisper/src/main/cpp/whisper_jni.cpp` |
| Change Gemma prompt wrapping / stopping | `llama/src/main/cpp/llama_jni.cpp` (`build_prompt`, `JsonEndDetector`) |
| The system prompt | `domain/Prompts.kt`. It **must match training**, and `PromptsTest` fails if it changes. Retrain if you edit it. |
| Reject bad model output | `domain/ExtractionSanitizer.kt` and `domain/ModelResponseParser.kt` (add unit tests alongside) |
| Brand → generic | `assets/dgda/*.csv`; bump `DgdaSeeder.SEED_VERSION` after editing |

- **Logs:** in the **Logcat** tool window, filter by `tag:MediDiaryWhisper | tag:MediDiaryLlama`, or run:
  ```bash
  adb logcat -s MediDiaryWhisper MediDiaryLlama
  ```
- **Timings:** the transcript and review sections show "Transcribed/Extracted on-device in X s". Use these numbers in the evaluation write-up.
- **Memory:** in **View → Tool Windows → Profiler**, attach to the app and watch *Memory* while you record and press Confirmation. Whisper and Gemma never load together; the peak should be about the larger model plus buffers.
- **Native debugging:** select the **app** run configuration → *Edit Configurations → Debugger → Debug type: Dual (Java + Native)* to step into the JNI C++.
- **Never edit `third_party/`.** To update a library, change the submodule commit and fix any JNI API changes in `*_jni.cpp`.

---

## 8. Data

- **Inspect the database live:** open **App Inspection → Database Inspector** while the app runs. You can see `diary_sessions`, `diary_records`, `dgda_medicines` and `dgda_aliases`, and run SQL.
- **Changing a Room entity** (`data/db/Entities.kt`) changes the schema. There are no migrations during the hackathon, so **uninstall the app** after a schema change (this also deletes the models; push them again) or bump `version` in `AppDatabase`. Without one of those, the app crashes on start with *"Room cannot verify the data integrity"*.
- **DGDA reference list:** edit `app/src/main/assets/dgda/dgda_medicines.csv` / `dgda_aliases.csv`, bump `DgdaSeeder.SEED_VERSION`, and run the unit tests (`DgdaMatcherTest` checks the CSVs).
- **Retention:** 30 / 60 / custom days (Settings). It runs at app start and daily via `RetentionWorker`.

---

## 9. Testing and verification

| Check | How |
|---|---|
| Unit tests (parser, sanitizer, DGDA matcher + CSVs, CSV export, prompt pin, calendar dates) | Right-click `app/src/test` → **Run 'Tests in …'**, or `./gradlew :app:testDebugUnitTest` |
| No network permission | `./gradlew :app:processDebugMainManifest`, then open the merged manifest (*Build → Analyze APK…* or `app/build/intermediates/merged_manifest/`) and confirm there is **no** `INTERNET` |
| End-to-end demo flow | Use the checklist below, **in airplane mode** |

**Airplane-mode smoke test (run before every demo build):**
1. Airplane mode on. Open the app; the Settings screen shows *Whisper ready* and *Gemma ready*.
2. Toggle **BN**, tap the mic and say a Bangla sentence with one medicine and two symptoms. Stop.
3. The transcript appears; fix one word.
4. Tap **Confirmation**. The review cards show the medicine (with DGDA match pill) and both symptoms.
5. Untick one card, edit another, then **Confirm & Save**. You get a green "Saved" banner.
6. Open ☰ **Calendar**: today has dots and the DATES list shows the entries. Edit one, then delete one.
7. In **Settings → Export to → PDF**, the share sheet opens and the PDF shows the disclaimer and table. Do the same for **CSV**.
8. Set retention to **Custom 1 day** and confirm older test entries disappear. Set it back to 30/60.

---

## 10. Team Git workflow

- **One branch per task**, then a pull request into `main`:
  ```bash
  git switch -c ui/settings-polish
  ```
- **Pull with submodules:**
  ```bash
  git pull --recurse-submodules
  ```
  If `third_party` changed, also run:
  ```bash
  git submodule update --init --recursive
  ```
- **Never commit model files** (`*.gguf`, `*.bin` are git-ignored), `local.properties`, or `build/` folders.
- **Line endings:** `.gitattributes` keeps `gradlew` LF and `*.bat` CRLF. Don't override it.
- **Before pushing:** run the unit tests and make sure the app builds.
- **Avoid two people editing `HomeScreen.kt` at once.** It's the biggest UI file. Split work by card (Voice model, Transcription, Review) or move a card into its own file first.

---

## 11. Release and demo build

- **APK for the demo:** **Build → Build App Bundle(s) / APK(s) → Build APK(s)**, or:
  ```bash
  ./gradlew :app:assembleRelease
  ```
  The output is `app/build/outputs/apk/release/app-release.apk`. Release builds are signed with the debug key so they can be side-loaded.
- **Direct APK link for judges:** create a **GitHub Release** and attach the APK. Models aren't in the APK, so the README explains how to load them.
- **Same signing key across team machines:** an APK built on one laptop can't update an install from another laptop (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). For the demo phone, always install from one machine, or uninstall first.
- **CI builds (GitHub Actions)** are described in §11.1.

### 11.1 GitHub Actions: `.github/workflows/android-apk.yml`

| Trigger | What happens |
|---|---|
| Push to `main` (or a `claude/**` branch), any pull request, or *Run workflow* | Unit tests → release APK → checks → APK uploaded to the run |
| Push a tag `v*` (e.g. `v1.0.0`) | All of the above **plus a GitHub Release** with the APK attached (the direct link for judges) |

Every build checks three things and **fails if any is missing or wrong**:
1. The APK contains **no** `INTERNET` / `ACCESS_NETWORK_STATE` permission (core rule #1).
2. It contains `libmedidiary_whisper.so` and `libmedidiary_llama.so` for arm64.
3. It is properly signed.

The run's **Summary** page shows the APK size, SHA-256, version and signing key.

**Download a build:** *Actions → Android APK → pick a run → Artifacts → `MediDiary-<version>.apk`* (a plain APK, not a zip;
requires being logged in to GitHub). Artifacts are kept for 30 days.

**Publish a release for the demo / judges:**
```bash
git tag v1.0.0
```

```bash
git push origin v1.0.0
```
About 15–25 minutes later, *Releases* has `MediDiary-1.0.0.apk` with a public download link.

**Build time:** the first run compiles whisper.cpp and llama.cpp, about 15–25 min. Later runs reuse the Gradle and native
(`.cxx`) caches when `third_party/` and the C++ code are unchanged.

**Versioning:** `versionCode` = the workflow run number, so each CI build is "newer" and installs over the previous one.
`versionName` is the tag (`v1.2.0` → `1.2.0`) or `1.0.0-ci.<run>`.

#### Fixed signing key (recommended before the demo)
Without a key, each CI run signs with a throw-away debug key. Installing a newer CI build then fails with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` until you uninstall, and uninstalling **deletes the diary and the models**.
To avoid that, give CI one permanent key:

1. Create a keystore once and keep it somewhere safe (**never commit it**; `*.jks` is git-ignored):
   ```bash
   keytool -genkeypair -v -keystore medidiary-release.jks -alias medidiary -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Base64-encode it. On Linux:
   ```bash
   base64 -w0 medidiary-release.jks > keystore.b64
   ```
   On Windows PowerShell:
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("medidiary-release.jks")) | Out-File -Encoding ascii keystore.b64
   ```
3. In GitHub, go to **Settings → Secrets and variables → Actions → New repository secret** and add:

   | Secret | Value |
   |---|---|
   | `MEDIDIARY_KEYSTORE_BASE64` | contents of `keystore.b64` |
   | `MEDIDIARY_KEYSTORE_PASSWORD` | the keystore password |
   | `MEDIDIARY_KEY_ALIAS` | `medidiary` |
   | `MEDIDIARY_KEY_PASSWORD` | the key password (same as the keystore password if you pressed Enter) |

4. Delete `keystore.b64`. The next run's summary should say *Signing: release key (secrets)*.

To build locally with the same key, export the same four `MEDIDIARY_*` variables, with `MEDIDIARY_KEYSTORE_FILE` set to the
`.jks` path. `app/build.gradle.kts` picks them up automatically.

Pull requests from forks don't receive secrets, so they build with the debug key. That's expected.
---

## 12. Emulator (UI work only)

Only `arm64-v8a` native code is built by default. To run on an **x86_64** emulator, build that ABI too, using either a local edit to `gradle.properties`:
```properties
medidiary.abis=arm64-v8a,x86_64
```
or a one-off command:
```bash
./gradlew :app:installDebug -Pmedidiary.abis=arm64-v8a,x86_64
```
Create an emulator with an **x86_64 API 34/35 Google APIs** image in *Device Manager*. Use demo mode (no models); real inference
on an emulator is very slow. **Don't commit** the `gradle.properties` change, because it doubles native build time for everyone.

---

## 13. Troubleshooting

| Symptom | Fix |
|---|---|
| `NDK … 27.2.12479018 not installed` / `CMake 3.22.1 not found` | Install those exact versions (SDK Manager → SDK Tools → *Show Package Details*), or change `ndkVersion` in all three `build.gradle.kts` files to the NDK you have. |
| `whisper.cpp sources missing` / `llama.cpp sources missing` | Run `git submodule update --init --recursive`. |
| Gradle sync: *Unsupported class file major version* / JDK errors | *Settings → Build Tools → Gradle → Gradle JDK* → choose **jbr-17** or **jbr-21**. |
| Windows: *path too long*, `cannot open … .o` | Re-clone to `C:\md` and enable long paths (§2.2). |
| `UnsatisfiedLinkError: libmedidiary_*.so` | You're on an x86 emulator without x86_64 enabled (§12), or a 32-bit-only phone (not supported). |
| App crashes on start after pulling | Someone changed a Room entity. Uninstall and reinstall (§8). |
| Shadows look clipped or flat | Add more spacing around the element (§6.2 rule 1). A flat look on Android 8.x is expected. |
| "Demo mode" banner | Models aren't found. Check the exact file names, then **Settings → Load model → Re-scan**. |
| Transcription very slow | Try the `q4_0` Whisper file, and make sure nothing else heavy is running. See `training/whisper_bengali_setup.ipynb`. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The installed APK came from another machine's debug key. Uninstall first (this deletes diary data and models). |
| Gradle out of memory | Raise `org.gradle.jvmargs=-Xmx6g` locally in `gradle.properties`. |
| `adb: no permissions` (Fedora) | Install `android-tools`, re-plug, and accept the USB-debugging prompt (§3.3). |
