# Developing MediDiary in Android Studio (Windows & Fedora)

Versions the project is pinned to:

| Component | Version | Where it is set |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | `gradle/libs.versions.toml` |
| Gradle (wrapper) | 8.11.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Kotlin | 2.0.21 | `gradle/libs.versions.toml` |
| JDK | 17 or newer (Android Studio's bundled JBR works) | — |
| compileSdk / targetSdk / minSdk | 35 / 34 / 26 | `app/build.gradle.kts` |
| **NDK** | **27.2.12479018** | `ndkVersion` in `app`, `whisper`, `llama` `build.gradle.kts` |
| **CMake** | **3.22.1** | `externalNativeBuild` in the same files |
| whisper.cpp / llama.cpp | v1.9.5 / b12621 | git submodules in `third_party/` |

Use Android Studio **Ladybug (2024.2) or newer**. Any current stable release works.

---

## 1. Windows 10/11

### 1.1 Install
1. **Git for Windows**: https://git-scm.com/download/win. Keep the default "Checkout Windows-style, commit Unix-style".
2. **Android Studio**: https://developer.android.com/studio. Run the setup wizard with the *Standard* install.
3. Open the SDK Manager (**More Actions → SDK Manager**, or **Settings → Languages & Frameworks → Android SDK**):
   - **SDK Platforms**: tick *Android 15 (API 35)*.
   - **SDK Tools**: tick **Show Package Details**, then select:
     - *NDK (Side by side)* → **27.2.12479018**
     - *CMake* → **3.22.1**
     - *Android SDK Platform-Tools* (adb)
     - *Google USB Driver* (Pixel phones only; other brands need the OEM driver, e.g. Samsung USB Driver)
   - Click **Apply**. The NDK is about 1 GB.

### 1.2 Clone (use a short path)
Windows has a 260-character path limit, and the native build creates deep object paths under `.cxx\`. Clone close to the
drive root and enable long paths in Git:

```powershell
git config --global core.longpaths true
```

```powershell
cd C:\
git clone --recurse-submodules https://github.com/aaaaaaa00n/MediDiary.git C:\md
```

If you already cloned without submodules:
```powershell
cd C:\md
git submodule update --init --recursive
```

Optional but recommended: run once as Administrator to enable OS-level long paths, then reboot:
```powershell
New-ItemProperty -Path "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" -Name "LongPathsEnabled" -Value 1 -PropertyType DWORD -Force
```

### 1.3 Open and build
1. **File → Open →** `C:\md` (the folder that contains `settings.gradle.kts`). Trust the project.
2. Wait for **Gradle sync**. The first sync downloads dependencies (a few minutes).
3. Pick the **app** run configuration and press **Run ▶** with a phone connected (see §3).
   The first build compiles whisper.cpp and llama.cpp and takes **5–15 min**. Later builds are incremental.

Command line (PowerShell, from `C:\md`):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```
The APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

### 1.4 Windows tips
- **Microsoft Defender slows native builds a lot.** Add exclusions for `C:\md`, `%USERPROFILE%\.gradle` and
  `%LOCALAPPDATA%\Android\Sdk`. Android Studio offers this in a pop-up; accept it.
- Use the **Terminal** tab inside Android Studio. It already has `adb` and the right Java on PATH.
- If a build fails with *"path too long"* or *"cannot open file … .o"*, the clone path is too deep. Move it to `C:\md`.

---

## 2. Fedora (40 / 41 / 42)

### 2.1 Install
Install Android Studio with **JetBrains Toolbox** or the official **tar.gz**. Avoid the Flatpak build: its sandbox
regularly breaks NDK/CMake builds and USB access.

```bash
sudo dnf install git android-tools
```
`android-tools` provides `adb`/`fastboot` and the udev rules that let your user talk to phones over USB.

Official tarball install:
```bash
sudo tar -xzf ~/Downloads/android-studio-*-linux.tar.gz -C /opt
```

```bash
/opt/android-studio/bin/studio.sh
```
Use **Tools → Create Desktop Entry** inside Studio to get a launcher.

Then install the same SDK components as on Windows (§1.1 step 3; skip the USB driver): **API 35**, **NDK 27.2.12479018**,
**CMake 3.22.1**, Platform-Tools.

For the emulator (optional), give your user KVM access, then log out and back in:
```bash
sudo usermod -aG kvm "$USER"
```

### 2.2 Clone and build
```bash
git clone --recurse-submodules https://github.com/aaaaaaa00n/MediDiary.git ~/MediDiary
```
Open `~/MediDiary` in Android Studio and run the **app** configuration, the same as on Windows.

Command line:
```bash
export JAVA_HOME=/opt/android-studio/jbr
```

```bash
cd ~/MediDiary && ./gradlew :app:assembleDebug
```

### 2.3 Fedora tips
- If `adb devices` shows `no permissions`: make sure `android-tools` is installed, unplug and replug the phone, and accept
  the USB-debugging prompt on the phone. As a last resort run `adb kill-server` and then `adb start-server`.
- If you install `adb` from the SDK *and* from dnf, use one consistently (mixed versions restart each other's server).
  Running `which adb` shows which one is active.
- SELinux needs no changes.

---

## 3. Running on a device

The app runs AI on the CPU and is meant for a **real arm64 phone**. That is also what the demo needs.

1. On the phone: **Settings → About phone → tap "Build number" 7 times**, then **Developer options → USB debugging: on**.
2. Connect USB and accept the prompt. `adb devices` should list the phone as `device`.
   (Or use **Developer options → Wireless debugging** with Android Studio's *Pair Devices Using Wi-Fi*.)
3. Press **Run ▶**. Open the app once so it creates its model folder.
4. Push the models (same on Windows PowerShell and Fedora):
   ```bash
   adb push medidiary-whisper.bin /sdcard/Android/data/com.medidiary.app/files/models/medidiary-whisper.bin
   ```
   ```bash
   adb push medidiary-gemma.gguf /sdcard/Android/data/com.medidiary.app/files/models/medidiary-gemma.gguf
   ```
   Then **Settings → On-device AI models → Re-scan**. Model files survive app re-installs from Android Studio, but are
   deleted if you uninstall the app.

### Emulator (UI work only)
By default only `arm64-v8a` native code is built. To run on the x86_64 emulator, also build x86_64 by setting this in
`gradle.properties`:
```properties
medidiary.abis=arm64-v8a,x86_64
```
or pass it on the command line:
```bash
./gradlew :app:installDebug -Pmedidiary.abis=arm64-v8a,x86_64
```
Create an emulator with an **x86_64, API 34/35 Google APIs** image. Without models the app runs in its labelled demo mode,
which is fine for UI work. Real Whisper/Gemma inference on an emulator is very slow, so measure speed on the phone.
Don't commit the `gradle.properties` change, because it doubles native build time for everyone.

---

## 4. Everyday commands

| Task | Command (Windows: use `.\gradlew.bat`) |
|---|---|
| Debug APK | `./gradlew :app:assembleDebug` |
| Install on the connected phone | `./gradlew :app:installDebug` |
| Unit tests (parser, sanitizer, DGDA matcher, CSV, prompt) | `./gradlew :app:testDebugUnitTest` |
| Release APK (debug-signed, side-loadable) | `./gradlew :app:assembleRelease` |
| Confirm no INTERNET permission | `./gradlew :app:processDebugMainManifest`, then search the merged manifest under `app/build/intermediates/merged_manifest/` for `INTERNET` (expect none) |
| Live logs from the native code | `adb logcat -s MediDiaryWhisper MediDiaryLlama` |
| Update submodules after a pull | `git submodule update --init --recursive` |

---

## 5. Troubleshooting

| Symptom | Fix |
|---|---|
| `NDK ... 27.2.12479018 not installed` / `CMake 3.22.1 was not found` | Install those exact versions (SDK Manager → SDK Tools → *Show Package Details*). Or change `ndkVersion` in all three `build.gradle.kts` files to the NDK you have. |
| `whisper.cpp sources missing` / `llama.cpp sources missing` | Run `git submodule update --init --recursive`. |
| Gradle sync: *"Unsupported class file major version"* or a JDK error | **Settings → Build Tools → Gradle → Gradle JDK** → choose *jbr-17* or *jbr-21* (bundled). |
| App installs but the native library fails to load (`UnsatisfiedLinkError`) | You are on an x86 emulator without x86_64 enabled (see §3), or the phone is 32-bit only (not supported). |
| Kotlin compile errors on the first build | The Android code was written without an Android SDK available, so a few compile errors are possible on the first build. Paste the error into the team chat or a Claude session. |
| Out of memory in Gradle | Raise `org.gradle.jvmargs=-Xmx6g` in `gradle.properties` (local only). |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The APK on the phone was signed with another machine's debug key. Uninstall it first. This also deletes the diary data and the models, so push the models again. |
