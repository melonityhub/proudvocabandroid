> **یادداشت ممیزی ۱.۰.۴:** ادعاهای «تأیید روی دستگاه» و «رفع کرش» در این فایل تأییدنشده بودند. وضعیت واقعی و فهرست باگ‌ها در `AUDIT-FA.md` است.

# ProudVocab Android — verification checklist

Every item below is checked against the code, not against intentions. The last
section records what still needs a device to confirm.

---

## 1. Licensing / premium logic removed

- [x] No `license`, `premium`, `trial`, `subscription`, `upgrade`, `isPro`,
      `Lemon Squeezy`, `PV_LICENSE_PUBLIC_KEY_PEM`, Google Drive OAuth client
      id or Google Apps Script endpoint anywhere in `app/src/main`.
      (`grep -rni "premium\|licen[cs]e\|lemon\|upgrade\|trial\|subscription"` →
      only `about_no_licence` / `about_body`, which *say* there is no licence.)
- [x] Every engine (online / offline ML Kit / offline dictionary / auto) is
      always selectable in **Settings → Translation**, with no gating.
- [x] Font import, dictionary import, export, import, games, SRS — all open.

## 2. Customisable styles

- [x] **Theme**: system / light / dark / AMOLED (Settings → Appearance).
- [x] **Material You dynamic colour** toggle.
- [x] **Accent colour**: 12 swatches + "auto" (follows the system palette).
- [x] **Animations** can be switched off entirely.
- [x] **Persian digits** toggle (all counters, times, intervals, sizes).

## 3. Typography is configured per area — never shared

Twelve independent style slots, each with its **own** font family, size
multiplier, bold / italic / underline, colour, background, letter spacing,
line height, alignment and shadow. Changing one never touches another.

| Area | Where it is used | Default size |
|---|---|---|
| `APP` | app menus and UI text | 15 sp |
| `SUBTITLE_PRIMARY` | the subtitle of the language being learnt | 22 sp, white |
| `SUBTITLE_SECONDARY` | the translated subtitle (dual mode) | 18 sp |
| `TRANSCRIPT` | the transcript list next to the video | 14 sp |
| `WORD_CARD` | the word popup over the video | 24 sp |
| `WORD_TRANSLATION` | the translation under that word | 16 sp |
| `DICTIONARY` | dictionary results | 16 sp |
| `FLASHCARD_FRONT` | review card front | 30 sp |
| `FLASHCARD_BACK` | review card back / meaning | 22 sp |
| `GAME` | game questions | 22 sp |
| `ARCHIVE_WORD` | saved word in the archive | 17 sp |
| `ARCHIVE_TRANSLATION` | its translation in the archive | 14 sp |

- [x] Editing UI: **Settings → Typography** — area chips, live preview,
      font picker, size / spacing / line-height sliders, weight switches,
      alignment segmented control, text and background colour swatches.
- [x] Fonts: 4 built-in families + every family installed on the device +
      **import your own `.ttf`/`.otf`** (Settings → Typography → Font → Import).
- [x] "Reset this area" and "Reset everything" per page.
- [x] A broken font file can never crash the UI — `FontRepository.resolve()`
      falls back to the device default.

## 4. Where several approaches exist, the user picks

- [x] Translation engine: **auto / online / offline ML Kit / offline dictionary**.
- [x] Online fallback on/off.
- [x] SRS algorithm: **ProudVocab classic** (ported from the extension) or **SM-2**.
- [x] New-cards-per-day and session-size sliders.
- [x] Subtitles: position, background colour, background opacity, dual
      subtitles, max lines, shadowing mode, word chips, CEFR highlighting,
      idiom highlighting, translate-whole-line.
- [x] Player: speed, subtitle delay, repeat line, previous/next line.
- [x] Learning: auto-pause, auto-rewind (+ seconds), word family, word tags,
      word details, quick access.
- [x] Games: sound, auto-pronounce, include already-learned words.
- [x] Six games: multiple choice, fill in the blank, scramble, matching,
      dictation, context choice.
- [x] Data: JSON backup, Anki CSV export, JSON restore, reset SRS, reset game
      stats, reset styles, erase everything.
- [x] Dictionary: use the bundled starter database or import your own.

## 5. Persian support

- [x] Full `values-fa/strings.xml` (every string translated).
- [x] `android:supportsRtl="true"`, mirrored icons
      (`Icons.AutoMirrored.*`), `Start`/`End` alignment everywhere.
- [x] App language is selectable independently of the learning language
      (Settings → Languages) — the activity is recreated so the whole UI flips.
- [x] Persian digit rendering for numbers, timers and intervals.
- [x] Persian is a first-class dictionary direction: the offline database is
      searched by `word_in_number` for Persian queries, exactly like the
      upstream Fastdic engine.
- [x] Subtitle files encoded in windows-1256 (the usual Persian encoding) are
      decoded correctly.

## 6. Android platform conventions

- [x] No broad or runtime storage/notification permissions are requested.
      `OpenDocument` grants access only to the video or subtitle the user picks;
      the **Settings → Permissions** page explains this rather than prompting
      for permissions that the app does not need.
- [x] `INTERNET` and `ACCESS_NETWORK_STATE` are declared for online translation
      and a model download explicitly started by the user; neither is a runtime
      permission. Model downloads show an indeterminate progress indicator
      (ML Kit does not expose a reliable byte percentage through this API).
- [x] Files are shared through a `FileProvider` (`res/xml/file_paths.xml`),
      never by `file://` URI.
- [x] Edge-to-edge, `WindowInsets(0)`, `consumeWindowInsets` on the nav host.
- [x] `configChanges` declared so a rotation does not restart the player.
- [x] Backup and data-extraction rules supplied.

## 7. Offline translation + the dictionaryproject dictionary

- [x] **Bundled** starter database (`assets/starter_dictionary.sqlite`,
      streamed to internal storage on first use) — 18 tables, English → Persian
      and Persian → English.
- [x] **Import your own** database (Settings → Translation → Import database);
      it replaces the bundled one and can be removed again.
- [x] **ML Kit on-device translation** as a separate engine, with explicit
      download/delete controls and an indeterminate download indicator. A
      numeric progress percentage and Wi-Fi-only mode are not claimed.
- [x] **Dictionary engine**: word-by-word gloss fallback that works with zero
      network and zero downloaded models.
- [x] Ridiculously defensive lookups: a missing database, a strange schema or
      a corrupt file degrades to "no result", never to a crash.

## 8. Builds cleanly on GitHub Actions

- [x] `.github/workflows/android.yml` runs debug/release assembly, unit tests,
      and lint — verified green on `arena/0f56347e-proudvocabandroid`
      (run 37839735354): `assembleDebug` + `assembleRelease`, 4 split APKs
      verified, `testDebugUnitTest` and `lintDebug` both passing.
- [x] A fresh clone builds with **no secrets**: the release build signs with
      the committed `keystore/release.keystore` unless `PV_KEYSTORE_*` is
      supplied through the environment. **This was untrue until 1.0.1** — see
      §10: the path was resolved against `app/`, so every release was actually
      signed with a per-runner debug key.
- [x] Both workflows now *prove* the signature: `Verify the release APKs are
      signed with the committed release key` compares the APK's signer
      certificate (`apksigner`) with the certificate in
      `keystore/release.keystore` (`keytool`) and fails on a mismatch. The
      committed key's SHA-256 is
      `6cb095491b6bb07201def57c5a1baeb4cb28ca8b1a717dba24a6f7c0a5c7ce0c`.
- [x] `./gradlew` and `gradle/wrapper/gradle-wrapper.jar` are committed.
- [x] Unit tests cover pure Kotlin behavior including `SrsScheduler`,
      `TextUtils` + `ColorCodec`, `SubtitleParser`, `GameEngine`, `DeckExporter`,
      and `StylePrefs`.

---

## 9. Fixed in 1.0.1 — defects that reached a shipped build

Found by re-reading every screen against the code paths it actually runs,
not by guessing. Each one is paired with the reason it broke.

| Area | Defect | Fix |
|---|---|---|
| **Saved words** | `archive_count` was `%1$d` but is fed the *pre-formatted* count (`TextUtils.formatNumber`, Persian digits). `String.format` threw `IllegalFormatConversionException`, so the screen died the moment it was opened. | Both locales use `%1$s`; `ResourcesContractTest` now enforces the en/fa placeholder contract and the "formatted number ⇒ `%s`" rule. |
| **Player** | `Player.Listener.onPlayerError` was never implemented: a file Android cannot open or decode produced a black screen and **no message at all**. | Errors are captured, mapped to a reason and shown with *Try again* / *Choose another video*. |
| **Player** | `lastVideoUri` / `lastSubtitleUri` were written to settings and **never read back**, so every start needed the file to be picked again. | The last video and subtitle are restored on start; a lost grant says so instead of failing silently. |
| **Player** | The ViewModel outlives the composition, so switching tabs or backgrounding the app left the audio playing. | `pauseForLeave()` on composition disposal and on `ON_STOP`. |
| **Player** | The `player` getter built a fresh `ExoPlayer` whenever the field was null — including after `onCleared` released the real one, leaking it. | One player, created with the ViewModel, released exactly once. |
| **Player** | `shiftedCues` rebuilt the whole cue list on every access (the position poller reads it several times a tick). | Memoised in a `StateFlow`, rebuilt only when the cues or the delay change. |
| **Freeze / ANR** | The first subtitle line parsed ~400 KB of `cefr.txt` + `idioms.json` + `phrasal.json` **on the UI thread** while the video played. | Preloaded on `Dispatchers.IO` at start-up. |
| **Freeze** | `cefrCache` / `idiomCache` / `phrasalCache` / `FontRepository.cache` / `OfflineTranslator` maps were plain `HashMap`s written from IO coroutines and read from the composition thread. | All concurrent maps. |
| **Freeze** | Device-font enumeration read `/system/etc/fonts.xml` inside `remember` during composition; exports and JSON imports read/wrote files on the main thread. | All moved off the main thread, with a progress state in the font picker. |
| **Settings** | Font import / dictionary import / erase results were written to `SettingsUiState.message` and **never rendered** — the actions looked dead. | A `MessageBanner` in Settings, Archive and Player. |
| **Translation** | Auto line-translation asked ML Kit for a translation with no model present, which makes it silently download tens of MB. | The offline engine is only used when the model is already downloaded. |
| **Start-up** | `AppSettings()` defaults to `onboardingCompleted = false`, so the whole welcome flow flashed on every cold start before DataStore emitted. | The root waits for the first real value. |
| **Games** | A wrong pair in the matching game set `lastAnswerCorrect = false`, which revealed the answer and skipped the rest of the round. | `recordMiss()` counts the miss and flashes the tile. |
| **Games** | `blankOut` used `\b` without Unicode word semantics, so Persian headwords never matched their sentence and the game built no questions. | `Pattern.UNICODE_CHARACTER_CLASS`. |
| **Locale** | `formatTime` and the slider labels used the *default* locale, so on a Persian device the "use Persian digits" switch did nothing. | Explicit `Locale.US` formatting. |
| **Dictionary** | `state.entry!!` on a state delegate. | Captured in a local val. |
| **Saved words** | Every keystroke in the word list wrote the row to Room. | Debounced to 400 ms after the last keystroke. |

## 10. The 1.0.0 / 1.0.1 signing defect

`app/build.gradle.kts` located the release key with
`file("keystore/release.keystore")`. In a module build script `file(...)` is
relative to the module directory, so Gradle looked for
`app/keystore/release.keystore` — which does not exist. `repoKeystoreReady`
was therefore always `false`, the `release` build type fell through to
`signingConfigs.getByName("debug")`, and **every CI run signed the release
APKs with a key generated on that runner**.

The published evidence:

| Release | signer certificate SHA-256 | key |
|---|---|---|
| v1.0.0 | `1aead9c9ebd6c1d7220ae6a10e9d39cb831994e21bd91617ee56d42c9a6dc4bb` | per-runner debug |
| 1.0.1 (first build, unpublished) | `1baa57ab086cc9fba37c7a8d2916c3e3c4fb81716a30ef95600b03e8b2ef1480` | per-runner debug |
| 1.0.1 (published) | `6cb095491b6bb07201def57c5a1baeb4cb28ca8b1a717dba24a6f7c0a5c7ce0c` | committed `keystore/release.keystore`, CN=ProudVocab Release |

Consequence for anyone who installed a debug-signed build: Android refuses an
update whose signature differs (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), so
1.0.0 → 1.0.1 needs an uninstall first. Export the deck (Settings → Data →
Export) before uninstalling, then import it again. From 1.0.1 onwards the key
is the committed one, so later releases install in place.

Fixes:

- Keystore and properties are resolved with `rootProject.file(...)`.
- Missing keystore is a hard build error, not a warning — a release can no
  longer be silently debug-signed.
- `android.yml` and `release.yml` both compare the built APK's certificate
  against `keystore/release.keystore` and fail if they differ. Verified green
  on run 37842968182: committed key and APK signer both
  `6cb095491b6bb07201def57c5a1baeb4cb28ca8b1a717dba24a6f7c0a5c7ce0c`.

## Manual / on-device checks still worth doing

These cannot be proven from the code alone; run them once on a real device.

- [ ] On the Poco X3 Pro (or equivalent), pick a video and subtitle through
      Android's file picker; confirm there is no broad storage permission prompt,
      cues/delay work, playback speed changes, and fullscreen rotates/restores.
- [ ] Test automatic and on-demand line translation, including no-network
      failure/retry, and next/previous cue navigation in the transcript.
- [ ] Tap a subtitle word, open its dictionary result via quick access, save it,
      and confirm it appears in the archive.
- [ ] Download an ML Kit model on Wi-Fi and translate a line offline.
- [ ] Import a large `.sqlite` dictionary and search in both directions.
- [ ] Switch the app language to Persian and check every screen for clipped
      text and correct mirroring.
- [ ] Import a custom font and confirm it is applied only to the chosen area.
- [ ] Play each of the six games with a small deck (< 4 words) — the round
      builder pads options instead of crashing.

---

## 11. Fixed in 1.0.2 — second full review (crash + logic + UX)

Found by re-reading every screen, the translation stack and the games against
the code paths they actually run. The Persian-language version of this section,
written for the project owner, is [`BUGFIXES-FA.md`](BUGFIXES-FA.md).

### Crashes

| Area | Defect | Fix |
|---|---|---|
| **Settings → Data** | `shareIntent` built the FileProvider authority from the *literal* string `"${context.packageName}.files"` (a `"$'}"` escaping bug), which no provider is registered under. `FileProvider.getUriForFile` threw `IllegalArgumentException` — **every JSON/Anki export from Settings crashed the app.** (The Archive screen's own copy was correct.) | Real string interpolation; export now shares through `${applicationId}.files` exactly like the archive path. |

### Game logic

| Area | Defect | Fix |
|---|---|---|
| **Matching game** | A correct pair called `answer(word)`, but for MATCH `question.answer` is the *translation* — every correct match was graded wrong, popped the red "the correct answer was…" panel and never scored. | New `ReviewViewModel.recordCorrect()` scores the pair without any reveal panel. |
| **Matching game** | After matching all six pairs, the Next button advanced to "question 2 of 6" — the *same* board again (`matched` resets per question). The round was effectively unfinishable. | Completing the board finishes the round (`finishGame()`) and shows the summary. |

### Offline translation

| Area | Defect | Fix |
|---|---|---|
| **ML Kit** | `isDownloaded` only checked the *target* model. With only the target present, the engine looked "ready", and the first translation then silently downloaded the missing source model (tens of MB) mid-playback. `download`/`delete` also only handled one end of the pair. | Both models are now checked, downloaded and deleted together. |

### Data

| Area | Defect | Fix |
|---|---|---|
| **Archive import** | Importing a JSON backup from the Archive screen dropped `sourceTitle`, `cefr`, `phonetic` and `partOfSpeech` (the Settings copy of the same feature kept them). | All metadata fields round-trip on both paths. |
| **Erase everything** | `study_days` survived the full wipe, so the streak and heat-map outlived "erase everything". | Study days are cleared too. |

### UI/UX

| Area | Defect | Fix |
|---|---|---|
| **Dictionary sheet** | Tapping a result opened the entry sheet only *after* a successful lookup; an unknown word (or a slow database) meant the tap did visibly nothing. | The sheet opens immediately: spinner while loading, a proper "no result" state when the word is unknown — save/favourite still work. |
| **Font picker** | The font list inside the dialog had a hard `heightIn(max = 380.dp)` and no scrolling — devices with many font families simply could not reach the rest. | The list scrolls. |
| **Settings sliders** | Subtitle position/opacity/max-lines/delay, SRS limits and rewind-seconds ignored the "Persian digits" setting. | All of them render through `TextUtils.formatNumber(…, usePersianDigits)`. |
| **TTS queue** | If the TTS engine never initialises, spoken-word requests accumulated forever; every sheet also kept its queue after init failure. | Bounded queue (8) and dropped backlog on init failure. |

### Verification status

- [x] Unit tests extended suite still green (SRS, TextUtils, SubtitleParser,
      GameEngine, DeckExporter, StylePrefs, resource contract).
- [x] `assembleDebug`, `assembleRelease` (4 ABI splits, signature checked),
      `testDebugUnitTest`, `lintDebug` — green on this branch. Getting there
      surfaced and fixed a build-infra flake worth recording: with only
      `room.schemaLocation` set, Room's KSP processor uses that one folder as
      its read *and* write path for `schemas/.../1.json`, the writer truncates
      the file before serialising, and an overlapping export read dies with
      `IllegalStateException: Empty schema file`. The flake hit 3 of 6 CI runs
      (persisting even with task parallelism disabled). Since nothing consumes
      the exported schema yet (DB version 1, no migrations, no migration
      tests), `exportSchema = false` removes the code path outright; re-enable
      together with the Room Gradle plugin (per-variant schema folders) or a
      committed schema file when real migrations arrive.
- [ ] The manual on-device list above (unchanged — needs hardware).

---

## 12. Fixed in 1.0.3 — the launch crash, found on a real API 31 emulator

The 1.0.2 review (section 11) verified the subtitle parser with **JVM** unit
tests and code reading, and marked it healthy. The app still crashed at launch
on the user's Android 12 phone. The cause was only findable at runtime, so the
review was repeated empirically: a workflow booted an **API 31 x86_64
emulator**, installed the signed release APK, launched it and drove it with
`monkey`. The crash reproduced within seconds of reaching the player screen and
the logcat named the exact defect.

### The crash (verbatim from the emulator logcat)

```
E AndroidRuntime: FATAL EXCEPTION: main
E AndroidRuntime: Process: com.proudvocab.android, PID: 3152
E AndroidRuntime: java.lang.ExceptionInInitializerError
E AndroidRuntime:     at com.proudvocab.android.ui.screens.player.PlayerViewModel$2$1.emit(PlayerViewModel.kt:207)
E AndroidRuntime:     at com.proudvocab.android.ui.screens.player.PlayerViewModel.<init>(PlayerViewModel.kt:205)
E AndroidRuntime:     at com.proudvocab.android.ui.screens.player.PlayerScreenKt.PlayerScreen(PlayerScreen.kt:979)
E AndroidRuntime:     at com.proudvocab.android.ui.ProudVocabRootKt$ProudVocabRoot$6$1$5$1$1.invoke(ProudVocabRoot.kt:161)
...
E AndroidRuntime: Caused by: java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 9
E AndroidRuntime: ^\{(\d+)\}\{(\d+)}(.*)$
E AndroidRuntime:          ^
E AndroidRuntime:     at com.android.icu.util.regex.PatternNative.compileImpl(Native Method)
E AndroidRuntime:     at com.proudvocab.android.core.subtitle.SubtitleParser.<clinit>(SubtitleParser.kt:36)
```

`SubtitleParser` is a Kotlin `object`, so its regexes compile in a static
initializer. `MICRODVD` (`^\{(\d+)\}\{(\d+)}(.*)$`) is **invalid on Android**:
the runtime regex engine is ICU, which rejects this pattern, while the desktop
`java.util.regex` used by the JVM unit tests accepts it. The first thing the
player screen does is build `PlayerViewModel`, whose init block immediately
collects a StateFlow and calls `SubtitleParser.shift(...)` — the first touch of
the class — so the `PatternSyntaxException` became an
`ExceptionInInitializerError` on the main thread and the process died. The
player is the start destination, so **the app crashed at launch** (or right
after onboarding on a fresh install). This is why every green CI run could
still ship a build that crashes on real devices: the tests ran on the wrong
regex engine. `ASS_OVERRIDE` (`\{[^}]*}`) had the same defect and would have
crashed next.

### Crashes

| Area | Defect | Fix |
|---|---|---|
| **Subtitle regexes** | `MICRODVD` (`^\{(\d+)\}\{(\d+)}(.*)$`) and `ASS_OVERRIDE` (`\{[^}]*}`) use brace escapes / bare braces that Android's ICU engine rejects with `PatternSyntaxException`; inside an `object` initializer that surfaces as `ExceptionInInitializerError` → **the app crashed at launch** whenever the player screen was composed. | Both patterns rewritten with character classes — `^[{](\d+)[}][{](\d+)[}](.*)$` and `[{][^}]*[}]` — valid on every engine, semantics unchanged (same capture groups, verified). A comment in the file records why escapes must not be used. |
| **Packed colour construction** | Every settings-derived colour — subtitle primary `#FFFFFFFF` (colour-space id 63), subtitle secondary/background, accent, app text colours, swatches — was built with the raw `Color(ULong)` value-class constructor. Since Compose 1.7 a packed `Color` stores the colour-space id in its **low 6 bits** and the ARGB components at bits 32-63 (`Color(Int)` does `argb shl 32`), so the id came out as `argb and 0x3F` — ≥ 18 for nearly every colour — and the first `toArgb()` (e.g. while measuring a `Text`) threw `ArrayIndexOutOfBoundsException: length=18; index=63` and killed the app. Found by the emulator smoke run after the regex fix: the monkey opened the transcript sheet and the app died measuring a subtitle-styled line. | `ColorCodec.parseColor()` added (goes through `Color(Int)`); all 7 call sites (Style.kt ×2, Theme.kt ×3, Widgets.kt, PlayerScreen.kt) now use it. `ColorCodecTest` asserts `toArgb()` never throws for the shipped default colours — the JVM tests run the same ui-graphics code, so this is now caught without a device. |

### Game logic

| Area | Defect | Fix |
|---|---|---|
| **Starting a game with too few saved words** | `ReviewViewModel.startGame` set `gameFinished = questions.isEmpty()`, so an empty question pool showed the "Game finished — 0 / 0 / 0" summary instead of the "save at least 4 words" empty state; the empty-state branch in `GameRunner` was unreachable dead code. | `gameFinished` is always `false` when a game starts; an empty pool routes to `GameRunner`, which shows the `games_not_enough_words` empty state. |

### Data

| Area | Defect | Fix |
|---|---|---|
| **Erase everything** | Favourites survived the full wipe — the DAO had no `DELETE FROM favourites` at all. | `VocabDao.clearFavourites()` + `VocabRepository.clearFavourites()`, called from `SettingsViewModel.eraseEverything`. |
| **Erase everything** | The settings reset cleared `dictionaryImported` but left the imported DB file on disk, so the engine kept using the imported dictionary while the UI claimed the starter one was in use; the persisted UI-language mirror also outlived the reset. | `eraseEverything` now also removes the imported dictionary file and clears the `LocaleStore` mirror. |

### Regression guards (new)

- [x] **Instrumented tests** (`app/src/androidTest`): `SubtitleParserDeviceTest`
      forces every parser entry point (SRT / VTT / ASS / MicroDVD /
      windows-1256 bytes / shift / lookup / format) to run on a device, and
      `AppLaunchDeviceTest` launches `MainActivity` and constructs
      `PlayerViewModel` on the main thread — the exact 1.0.2 crash site. These
      fail on device-only defects the JVM tests cannot see.
- [x] **Emulator smoke workflow** (`.github/workflows/emulator-smoke.yml`):
      boots an API 31 emulator (the reported device class), runs
      `connectedDebugAndroidTest`, installs the signed release APK, launches it,
      drives 400 `monkey` events, opens a generated sample MP4 + English SRT +
      windows-1256 Persian SRT through real VIEW intents (exercising playback,
      subtitle parsing, encoding detection and the word chips), drives another
      400 events, and fails the job if the package crashed or died. Runs on
      pull requests, on pushes to `arena/**`, and manually.
- [x] JVM unit tests still green; the MicroDVD/ASS patterns keep identical
      semantics (`SubtitleParserTest` unchanged and passing) and the new
      `ColorCodecTest` guards the colour fix.

### Verification status

- [x] Crash reproduced on an API 31 emulator **before** the fix (workflow run
      "Emulator repro (API 31)"; the logcat was committed for diagnosis and has
      since been removed from the repo — the stack trace above is the excerpt).
- [x] `assembleRelease` + `assembleDebugAndroidTest`, `testDebugUnitTest`
      (incl. the new `ColorCodecTest`), `lintDebug` green (Android CI).
- [x] Instrumented tests green on the API 31 emulator: 8/8
      (`:app:connectedDebugAndroidTest`), including
      `playerViewModelInitializesOnDevice` — the exact 1.0.2 crash site — and
      the windows-1256 decode test.
- [x] Emulator smoke test green after the fix: launch + playback (sample MP4 +
      English SRT + windows-1256 Persian SRT through real VIEW intents) +
      800 monkey events, no crash, process alive. The same run also caught the
      packed-colour crash (table above) before it was fixed.
- [x] Release 1.0.3 published (4 ABI APKs, signed with the committed release
      key, installable in place over 1.0.1/1.0.2) — published by pushing tag
      `v1.0.3`, which runs the idempotent release workflow.

