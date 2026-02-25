# 4DrumTrainer

A rudimentary Android drum-learning app that listens through the device microphone and evaluates whether the student is playing in rhythm and striking the correct drum or cymbal for the current lesson.

---

## Table of Contents

1. [App Concept](#app-concept)
2. [Technical Architecture](#technical-architecture)
3. [Acoustic Analysis Pipeline](#acoustic-analysis-pipeline)
4. [Curriculum Structure](#curriculum-structure)
5. [Age-Adaptive Learning](#age-adaptive-learning)
6. [Progress Model](#progress-model)
7. [Project Setup](#project-setup)

---

## App Concept

4DrumTrainer guides students aged **7 years and above** from absolute beginner to confident drummer through a structured, progressive curriculum.  The app supports visual beat grids, a visual metronome, and real-time acoustic feedback via the built-in microphone.

Key features:
- **Student profile** with name and date-of-birth; minimum age enforced (7 years).
- **Age-adaptive curriculum**: tempo, repetitions, and pattern complexity are scaled automatically to the student's age group.
- **Acoustic hit detection**: the microphone signal is analysed to detect when a drum is struck and which drum/cymbal it most likely was.
- **Rhythm scoring**: each session computes a *rhythm score* (timing accuracy) and a *pitch/instrument score* (correct drum part), combined into an *overall score*.
- **Lesson progression**: a lesson is unlocked when the student passes the previous one with an overall score ≥ the lesson's threshold (typically 65–70 %).
- **Encouragement messages** adapt to the student's score so young learners always receive positive reinforcement.

---

## Technical Architecture

```
app/src/main/java/com/drumtrainer/
├── model/
│   ├── AgeGroup.kt          – CHILD / YOUNG / TEEN_AND_ABOVE tiers
│   ├── BeatEvent.kt         – Single beat position + drum part
│   ├── Curriculum.kt        – Ordered level list + next-lesson logic
│   ├── CurriculumLevel.kt   – Group of related lessons
│   ├── DrumPart.kt          – Drum/cymbal enum with frequency ranges
│   ├── Lesson.kt            – Full lesson definition (BPM, pattern, threshold)
│   ├── LessonProgress.kt    – One attempt record (scores, pass/fail)
│   └── Student.kt           – Profile (name, birth date → age group)
├── audio/
│   ├── AudioProcessor.kt    – AudioRecord session manager + timestamp builder
│   ├── DrumHitClassifier.kt – Frequency-band energy → DrumPart classification
│   ├── HighPassFilter.kt    – DC-removal IIR filter
│   ├── OnsetDetector.kt     – RMS energy onset / hit detection
│   └── RhythmEvaluator.kt  – Compare expected vs. detected hits → scores
├── data/
│   ├── CurriculumProvider.kt – Static curriculum with all 8 levels / 14 lessons
│   ├── DatabaseHelper.kt    – SQLite (students + progress tables)
│   └── PreferencesManager.kt– SharedPreferences wrapper
├── MainActivity.kt          – Dashboard (welcome, progress summary, start)
├── ProfileActivity.kt       – Create / edit student profile (name + DOB)
├── LessonActivity.kt        – Practice screen (metronome, beat grid, recording)
├── ResultActivity.kt        – Score display + navigation
└── PatternGridRenderer.kt   – Builds coloured beat grid into a LinearLayout
```

---

## Acoustic Analysis Pipeline

```
Microphone (PCM 16-bit, 44 100 Hz, mono)
        │
        ▼
HighPassFilter          remove DC offset (α = 0.95)
        │
        ▼
OnsetDetector           sliding RMS energy, threshold = 3× rolling background
        │                min gap between onsets = 80 ms (suppresses double-triggers)
        ▼
DrumHitClassifier       DFT band-energy across each DrumPart's characteristic
        │                frequency range → highest energy band wins
        ▼
(timestampMs, DrumPart?)
        │
        ▼
RhythmEvaluator         compare detected hits to expected pattern timestamps
                         within a ±150 ms window
                         → rhythmScore (0–100)  pitchScore (0–100)
                         → overallScore = 0.6 × rhythm + 0.4 × pitch
```

**Instrument frequency ranges used for classification:**

| Drum part     | Low (Hz) | High (Hz) |
|---------------|----------|-----------|
| Bass drum     |   50     |    200    |
| Snare         |  150     |    800    |
| Hi-hat closed | 3 000    |  8 000    |
| Hi-hat open   | 2 000    |  8 000    |
| Ride          |  300     |  5 000    |
| Crash         |  200     | 10 000    |
| Tom           |   80     |    500    |

---

## Curriculum Structure

8 progressive levels (≈ 14 lessons total):

| Level | Title                    | BPM range  | New skills                               | Min age |
|-------|--------------------------|------------|------------------------------------------|---------|
|   1   | Getting Started          |  60 – 75   | Steady quarter-note hi-hat               |   7     |
|   2   | The Basic Beat           |  65 – 85   | Bass on 1 & 3, snare on 2 & 4           |   7     |
|   3   | Eighth-Note Hi-Hat       |  70 – 90   | Hi-hat on every eighth note              |   8     |
|   4   | Open Hi-Hat              |  75 – 90   | Open hi-hat on beats 2 & 4              |   9     |
|   5   | Tom Fills                |  80 – 95   | Single-stroke fill at end of bar         |  10     |
|   6   | Crash & Ride             |  85 – 100  | Crash accent on beat 1, ride pattern     |  11     |
|   7   | Sixteenth Notes          |  90 – 110  | Sixteenth-note hi-hat, ghost notes       |  12     |
|   8   | Advanced Fills           |  95 – 120  | Multi-tom fills, syncopation             |  13     |

Each lesson requires a minimum overall score of **65–70 %** to pass and unlock the next.

### Recommended session schedule

| Student age | Sessions per week | Minutes per session | Expected level-up time |
|-------------|-------------------|---------------------|------------------------|
| 7–9         | 2–3               | 10–15               | 3–5 weeks per level    |
| 10–12       | 3–4               | 15–20               | 2–3 weeks per level    |
| 13+         | 4–5               | 20–30               | 1–2 weeks per level    |

---

## Age-Adaptive Learning

The `AgeGroup` enum (derived from the student's date of birth at runtime) controls:

| Parameter                    | CHILD (7–9) | YOUNG (10–12) | TEEN_AND_ABOVE (13+) |
|------------------------------|-------------|----------------|----------------------|
| Suggested BPM start          | 60          | 70             | 80                   |
| Practice repetitions         | 4 bars      | 6 bars         | 8 bars               |
| Max simultaneous instructions| 1           | 2              | 3                    |

Young students see the *minimum* BPM for each lesson; older students see a higher tempo.  The visual beat grid is designed with large, high-contrast cells so a 7-year-old can easily read it.  Encouragement messages remain positive regardless of score.

---

## Progress Model

`LessonProgress` records each attempt:

```
overallScore = round(rhythmScore × 0.6 + pitchScore × 0.4)
```

A lesson is marked **passed** when `overallScore ≥ lesson.passThresholdPct`.  The student's current level index is promoted automatically when they complete all lessons in a level.

---

## Project Setup

### Requirements

- Android Studio Hedgehog (2023.1) or newer
- Android SDK 34 (compile target), minimum SDK 24 (Android 7)
- A physical device or emulator with microphone support
- `android.permission.RECORD_AUDIO` granted at runtime (prompted automatically)

### Build & Run

```bash
./gradlew assembleDebug          # build debug APK
./gradlew test                   # run JVM unit tests
./gradlew connectedAndroidTest   # run instrumented tests on device/emulator
```

### Run unit tests only

```bash
./gradlew :app:testDebugUnitTest
```

---

## CI/CD — Automatic APK Releases

Every push to `main` triggers the **Build and Release APK** GitHub Actions workflow
(`.github/workflows/build-release.yml`).  The workflow:

1. Builds a **debug APK** signed with a **consistent keystore** stored in GitHub Secrets.
2. Creates a **GitHub Release** (tagged `v<versionName>-build<run_number>`) and attaches
   the APK so users can download and sideload it directly.

Because the same keystore is used for every build, a newly released APK can be installed
over a previously installed version without uninstalling first (standard Android update flow).

### One-time keystore setup (repository maintainer)

Run the following commands once to create the signing keystore and upload its contents as
GitHub Secrets:

```bash
# 1. Generate a keystore (keep the generated file somewhere safe)
keytool -genkeypair \
  -v \
  -keystore debug-release.keystore \
  -alias drumtrainer \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass <STORE_PASSWORD> \
  -keypass  <KEY_PASSWORD> \
  -dname "CN=4DrumTrainer, O=4DrumTrainer, C=DE"

# 2. Encode the keystore as base64
base64 -w 0 debug-release.keystore > debug-release.keystore.b64
```

Then add the following **GitHub Actions secrets** under
*Settings → Secrets and variables → Actions*:

| Secret name        | Value                                        |
|--------------------|----------------------------------------------|
| `KEYSTORE_BASE64`  | Contents of `debug-release.keystore.b64`     |
| `KEYSTORE_PASSWORD`| `<STORE_PASSWORD>` chosen above              |
| `KEY_ALIAS`        | `drumtrainer`                                |
| `KEY_PASSWORD`     | `<KEY_PASSWORD>` chosen above                |

> **Important:** keep the `.keystore` file and passwords in a safe place.
> Losing them means future builds cannot update existing installations.
