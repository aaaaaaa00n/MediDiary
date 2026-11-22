# MediDiary

An offline, bilingual (Bangla + English) Android health diary. Speak a short daily check-in. On-device
Whisper transcribes it, you correct the text, and an on-device fine-tuned Gemma 3 1B proposes medicine and
symptom records. You verify every record before it is saved, then review a timeline and export PDF or CSV
for your clinician.

> **Patient-Reported & AI-Assisted, Not Medically Verified.** MediDiary is a record-keeping aid. It does not
> give diagnoses, dosages or interaction advice.

The full engineering spec is in [`claude.md`](claude.md).

## Guarantees

| Rule | Where it's enforced |
|---|---|
| Zero cloud: no `INTERNET` permission, works in airplane mode | `app/src/main/AndroidManifest.xml` (also strips any merged network permission) |
| Human-in-the-loop: AI never auto-saves | `CheckInViewModel` keeps proposals in memory; `DiaryRepository.saveVerified` is called only from **Confirm & Save** |
| Deterministic generics: the LLM never supplies generic names | `ModelResponseParser` discards `generic_suggestion`; `DgdaMatcher` fills it from the local DGDA table |
| Sequential RAM: Whisper and Gemma are never loaded together | `InferencePipeline` mutex; each JNI call loads, runs and frees its model |
| Raw audio deleted after transcription | `CheckInViewModel.stopAndTranscribe`, plus a startup sweep in `MediDiaryApp` |
| No cloud backup of health data | `allowBackup=false` + `data_extraction_rules.xml` / `backup_rules.xml` |

## Project layout

```
app/                      Kotlin + Jetpack Compose app (com.medidiary.app)
  ai/                     WhisperEngine / GemmaEngine (native + mock), ModelManager, InferencePipeline
  audio/                  16 kHz mono PCM WAV recorder
  data/db/                Room entities + DAOs (diary_sessions, diary_records, dgda_medicines, dgda_aliases)
  data/dgda/              DGDA seeder + deterministic brand→generic matcher
  domain/                 ModelResponseParser, ExtractionSanitizer, system prompt
  export/                 PdfReportGenerator (android.graphics.pdf), CsvExporter, FileProvider sharing
  ui/                     CheckIn → Transcript → Review screens, Timeline, Settings
  work/                   30/60-day retention worker
  src/main/assets/dgda/   DGDA reference CSVs (review before release, see below)
whisper/                  JNI module: whisper.cpp → libmedidiary_whisper.so
llama/                    JNI module: llama.cpp → libmedidiary_llama.so
third_party/              git submodules, pinned: whisper.cpp v1.9.5, llama.cpp b12621
```

Each native module statically links its own copy of ggml and hides those symbols (`--exclude-libs,ALL`), so
whisper.cpp and llama.cpp never clash.

## Build

**Step-by-step setup for Windows and Fedora (Android Studio, emulator, device, troubleshooting): [`docs/DEVELOPING.md`](docs/DEVELOPING.md).**

Requirements: Android Studio (Ladybug or newer), JDK 17, **NDK 27.2.12479018**, CMake 3.22.1 (install both from
SDK Manager → SDK Tools).

```bash
git clone --recurse-submodules <repo-url>
# or, in an existing clone:
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

The first native build compiles whisper.cpp and llama.cpp and takes several minutes. Only `arm64-v8a` is built.

Check that no network permission sneaks in:

```bash
./gradlew :app:processDebugMainManifest && grep -c INTERNET app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

The expected output is `0`.

## Model files (your fine-tuned Whisper + Gemma)

**Do not commit model weights to git.** GitHub rejects files over 100 MB, and `.gitignore` already excludes
`*.gguf` and `*.bin`. Share them with the team through Google Drive or a GitHub Release asset, then put them on
the phone.

The app looks for exactly these two files:

| File name on device | Format | How to produce it |
|---|---|---|
| `medidiary-whisper.bin` | whisper.cpp **ggml** | Hugging Face fine-tune → `python third_party/whisper.cpp/models/convert-h5-to-ggml.py <hf_model_dir> <clone of github.com/openai/whisper> <out_dir>`. Optionally quantise with `whisper-quantize ggml-model.bin out.bin q5_0`. |
| `medidiary-gemma.gguf` | **GGUF**, Q4_K_M | Merge LoRA into the base model if needed, then `python third_party/llama.cpp/convert_hf_to_gguf.py <merged_dir> --outtype f16 --outfile gemma-f16.gguf` and `llama-quantize gemma-f16.gguf gemma-3-1b-it.Q4_K_M.gguf Q4_K_M`. |

You can install them in either of two ways. Neither uses the network.

1. **adb (fastest during development).** Install and launch the app once, then:
   ```bash
   adb push ggml-medidiary-whisper.bin /sdcard/Android/data/com.medidiary.app/files/models/medidiary-whisper.bin
   ```
   ```bash
   adb push gemma-3-1b-it.Q4_K_M.gguf /sdcard/Android/data/com.medidiary.app/files/models/medidiary-gemma.gguf
   ```
   Then go to **Settings → On-device AI models → Re-scan**.
2. **In the app.** Copy the files to the phone (Downloads, USB, SD card), then use **Settings → Import file…**. The
   app checks the file header (GGUF / ggml) before accepting it.

(A third option is to bundle the models in `app/src/main/assets/models/` under the names above, which makes a ~1 GB APK.
Avoid this unless judges must install a single file.)

Until both files are present, the app runs in a clearly labelled **demo mode** with scripted output. You can turn
this off in Settings → Advanced. The proposal requires live AI in the demo, so install the models before recording.

### Matching your fine-tune's prompt format

**Verified against the training notebook (`gemma3.ipynb`):** the model was trained with Unsloth's `gemma-3` chat
template on `system` + `user` + `assistant` messages. That renders as
`<bos><start_of_turn>user\n{SYSTEM_PROMPT}\n\n{transcript}<end_of_turn>\n<start_of_turn>model\n`, which is exactly
what llama.cpp's built-in Gemma handler produces with the default setting (chat template **on**). The system prompt in
`domain/Prompts.kt` is byte-identical to the notebook's `SYSTEM_PROMPT`, and `PromptsTest` keeps it that way.
Training used `max_seq_length = 1024`, so the app's default context is also 1024.

General notes for other fine-tunes:

Extraction sends the exact system prompt from `claude.md` §3.2 (`domain/Prompts.kt`). By default the prompt is wrapped
with the GGUF's embedded chat template (Gemma's `<start_of_turn>user … <start_of_turn>model`; Gemma has no system
role, so the system prompt is merged into the user turn). If your fine-tuning data used a different layout, either:

- turn off **Settings → Advanced → Use GGUF chat template**, which sends `system + "\n\n" + transcript` verbatim, or
- edit `build_prompt()` in `llama/src/main/cpp/llama_jni.cpp` to reproduce your training format exactly.

Decoding is greedy (temperature 0.0) and stops as soon as the top-level JSON object closes. The default context
is 1024 tokens because Bangla transcripts tokenise long. Change it in Settings → Advanced.

### Recommended Whisper model

Use [SayedShaun/bengali-whisper-medium-ggml](https://huggingface.co/SayedShaun/bengali-whisper-medium-ggml). It is already in
whisper.cpp format, so **no conversion is needed**: download one `.bin`, rename it to `medidiary-whisper.bin` and push it.

| file | size | notes |
|---|---|---|
| `ggml-model-q6_k.bin` | 610 MB | best quality per byte; start here |
| `ggml-model-q4_0.bin` | 424 MB | faster and smaller; use it if q6_k is too slow on the demo phone |
| `ggml-model-q2_k.bin` | 254 MB | avoid: the author warns it can hallucinate or drop words |

This is a *medium* model (~769M params), so speed on a phone CPU is the main risk. Time a 20–30 s clip on the demo
phone (the transcript screen shows the on-device time) before choosing between q6_k and q4_0.

### Whisper language

Settings → Speech language: `bn` (default), `en`, or auto-detect for code-switched speech.

## Training notebooks (`training/`)

Both notebooks run in Google Colab.

| Notebook | Purpose |
|---|---|
| `gemma3_medidiary_v2.ipynb` | Re-trains the Gemma 3 1B extractor for better **symptom recall**. It adds about 1,000 synthetic multi-symptom check-ins (labelled so that `name` = the words as spoken), turns packing off, and adds negation and planned-medicine negatives. It reports strict and lenient P/R/F1, re-checks the **quantised GGUF with llama.cpp** using the app's exact prompt, and saves `medidiary-gemma.gguf` to Drive. |
| `whisper_bengali_setup.ipynb` | Sets up the speech model [SayedShaun/bengali-whisper-medium-ggml](https://huggingface.co/SayedShaun/bengali-whisper-medium-ggml), which is already ggml so no conversion is needed. It downloads q6_k and q4_0, checks them with the **same whisper.cpp version as the app** (v1.9.5), compares CER and speed on your own Bangla clips, and saves the chosen file as `medidiary-whisper.bin`. |

## DGDA reference data: please review

`app/src/main/assets/dgda/dgda_medicines.csv` (brand → generic) and `dgda_aliases.csv` (Bangla spellings → brand)
are a hand-made **seed of about 80 common Bangladeshi brands**. **Dev 2 must check every row against the DGDA
registered-drug list before the demo, then extend it.** Edit the CSVs and bump `DgdaSeeder.SEED_VERSION`; no Room
migration is needed. Matching order is: exact brand/alias/generic, then exact after stripping strength/form
("Esonix 20 mg"), then prefix, then a small edit distance. Anything not exact is labelled as a *suggestion* in the UI
and the PDF.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

These cover the parser (fences, chatter, truncated JSON, out-of-schema items), the sanitizer, the DGDA matcher
(against the shipped CSVs) and CSV escaping.

## Demo checklist (airplane mode)

Speak Bangla → edit transcript → **Process & Extract** → verify cards → **Confirm & Save** → Timeline → Export PDF.
The transcript and review screens show on-device timings for the evaluation write-up.

## References & acknowledgements

1. OpenAI Whisper (MIT). Speech model: [SayedShaun/bengali-whisper-medium-ggml](https://huggingface.co/SayedShaun/bengali-whisper-medium-ggml) (Apache-2.0), a whisper.cpp conversion of SayedShaun/bengali-whisper-medium, fine-tuned from openai/whisper-medium.
2. whisper.cpp and its Android example (MIT).
3. Gemma 3 1B. Gemma is provided under and subject to the Gemma Terms of Use (ai.google.dev/gemma/terms).
4. llama.cpp and its Android integration (MIT).
5. Android Developers: Room and app data storage.
6. Bangladesh DGHS, Bangladesh Core FHIR Implementation Guide: DGDA Registered Drugs value set. The brand/generic
   subset is used as a reference for suggestions only.
7. Android Developers: Auto Backup and sensitive data controls.
