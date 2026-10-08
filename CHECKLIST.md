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

- [x] Nothing is requested at startup. Permissions are only asked when the
      feature is used, and there is a **Settings → Permissions** page that
      shows the current state and jumps to the system page.
      - `READ_MEDIA_VIDEO` (33+) / `READ_EXTERNAL_STORAGE` (≤32) — pick a video.
      - `POST_NOTIFICATIONS` (33+) — offline model download progress.
      - `INTERNET` / `ACCESS_NETWORK_state` — declared, not runtime.
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
- [x] **ML Kit on-device translation** as a separate engine: download, delete,
      progress, and a Wi-Fi hint.
- [x] **Dictionary engine**: word-by-word gloss fallback that works with zero
      network and zero downloaded models.
- [x] Ridiculously defensive lookups: a missing database, a strange schema or
      a corrupt file degrades to "no result", never to a crash.

## 8. Builds cleanly on GitHub Actions

- [x] `.github/workflows/android.yml`: checkout → JDK 17 → Gradle → assemble
      debug → unit tests → upload APK.
- [x] A fresh clone builds with **no secrets**: the release build type falls
      back to the debug signing config unless `PV_KEYSTORE_*` is provided.
- [x] `./gradlew` and `gradle/wrapper/gradle-wrapper.jar` are committed.
- [x] Unit tests cover the parts that are pure Kotlin and easy to get wrong:
      `SrsScheduler`, `TextUtils` + `ColorCodec`, `SubtitleParser`,
      `GameEngine`, `DeckExporter`, `StylePrefs`.

---

## Manual / on-device checks still worth doing

These cannot be proven from the code alone; run them once on a real device.

- [ ] Pick a video + subtitle, confirm cues line up and the delay control works.
- [ ] Tap a word in the subtitle, confirm the popup, save, and see it in the
      archive.
- [ ] Download an ML Kit model on Wi-Fi and translate a line offline.
- [ ] Import a large `.sqlite` dictionary and search in both directions.
- [ ] Switch the app language to Persian and check every screen for clipped
      text and correct mirroring.
- [ ] Import a custom font and confirm it is applied only to the chosen area.
- [ ] Play each of the six games with a small deck (< 4 words) — the round
      builder pads options instead of crashing.
