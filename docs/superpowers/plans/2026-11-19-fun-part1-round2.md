# Fun part 1, round 2 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Our Days Fun part 1 round 2: plans become a hosted link, "Who is this for?" step, bigger ask dialog, free time picker, custom menu, BD trip destinations without the budget note, custom surprise occasion, and two new Moments views.

**Architecture:** Pure Kotlin link builder in `Fun.kt` (unit-tested, no Android deps); Compose changes in `FunScreen.kt` / `MomentsScreen.kt` following part-1 patterns; one static HTML page `docs/plan/index.html` (i_miss_mynon style, query params, no backend) published on the Our-Days Pages site.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Robolectric unit tests, GitHub Pages static HTML/JS.

**Spec:** User-approved shape, 2026-11-18 19:39 message + "Sounds good" 19:50 (hosted-link decision included). Design doc: `docs/superpowers/specs/2026-11-18-fun-part1-design.md`. Task body: scheduled job `our-days-fun-round2-build`.

## Global Constraints

- Work on branch `per-connection` only (app code). The plan page ALSO goes live on `main` (`docs/plan/index.html`, identical file) because Pages serves `main/docs` and the APK's shared link must work when the user taps it — see Ruling 1 in the ledger.
- Plan page base URL: `https://mdtowfiqul.github.io/Our-Days/plan/` — stable, public, no backend, no accounts.
- Reply in English; respect Islamic principles; never tell the user to check things himself.
- Hard lessons from part 1: `Modifier.matchParentSize()` only inside a BoxScope; every new test class touching org.json needs `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [35])`; in codec decode, check `o.isNull("from")` before `optString`.
- Push with `/opt/hatch/bin/github push-files` (manifest lists changed paths explicitly). Never push under `.github/workflows/` (403). Deletions: `delete_file` via `github call-tool`, one file per call, sequential.
- CI: single `build` job; Tests step (`./gradlew testGithubDebugUnitTest`) runs first. Check via unauthenticated GitHub API; logs need a signed-in cloud browser task. Fix and re-push until green.
- Verify the finished APK itself (hash, contents, screenshots) before delivery; download BOTH artifacts (APK zip `since-us-debug-apk` + screenshots zip) via cloud browser task and verify zips against the API artifacts digests with sha256sum.
- Every new string gets EN (`values/strings.xml`) + BN (`values-bn/strings.xml`).
- No new dependency. No password gates. One part at a time — do NOT start part 2.

## Review Focus

- A plan link with names in Bengali or with `&`/`?` in a custom menu must still open and render — URL-encoding must cover every param value.
- A shared link opened with missing/empty params must show a friendly incomplete-link message, never a blank page or a JS error.
- A custom surprise occasion must survive a sync-file round trip (encode → decode → encode) instead of collapsing to "just because".
- The Moments photo calendar with moments that have no photos must still render (numbers, no crash on missing files).
- The 4-segment Moments switcher must still fit on a narrow phone and the existing `momentsCalendar` screenshot test (clicks "ক্যালেন্ডার") must still pass.

## Task 1: Plan link builder (pure Kotlin) + page contract

**Files:**
- Modify: `app/src/main/java/app/ourdays/data/Fun.kt`
- Test: `app/src/test/java/app/ourdays/data/FunTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `const val PLAN_PAGE_BASE_URL = "https://mdtowfiqul.github.io/Our-Days/plan/"`; `fun buildPlanLink(kind: String, names: String, date: LocalDate?, time: LocalTime?, menu: String?, destination: String?, message: String? = null): String` — params `kind=date|trip`, `names`, `date` (ISO `yyyy-MM-dd`), `time` (`HH:mm`), `menu` (date) or `dest` (trip), `msg` optional; blank values omitted; every value `URLEncoder.encode(v, "UTF-8")`.

- [ ] **Step 1: Write the failing tests** in `FunTest.kt`: `planLinkDateEncodesAllParams` (kind=date, names "Towfiqul & Ainon", date 2026-11-21, time 19:30, menu "Kacchi Biryani, Falooda" → URL starts with base, contains `kind=date`, `names=Towfiqul+%26+Ainon`, `date=2026-11-21`, `time=19%3A30`, `menu=Kacchi+Biryani%2C+Falooda`, no `dest`); `planLinkTripOmitsEmptyMenu` (kind=trip, dest "Cox's Bazar" → contains `dest=Cox%27s+Bazar`, no `menu`, no `time` when time is null); `planLinkOmitsBlankNames`.
- [ ] **Step 2: Run test to verify it fails** — Run: `./gradlew testGithubDebugUnitTest --tests "app.ourdays.data.FunTest"` Expected: FAIL (buildPlanLink unresolved).
- [ ] **Step 3: Implement** `PLAN_PAGE_BASE_URL` + `buildPlanLink` in `Fun.kt` (needs `java.net.URLEncoder`, `java.time.LocalDate`, `java.time.LocalTime`).
- [ ] **Step 4: Run test to verify it passes** — same command. Expected: PASS.
- [ ] **Step 5: Commit** — `git add app/src/main/java/app/ourdays/data/Fun.kt app/src/test/java/app/ourdays/data/FunTest.kt && git commit -m "Fun round 2: plan link builder (URL-encoded, short params)"`.

## Task 2: Hosted plan page (static HTML, i_miss_mynon style)

**Files:**
- Create: `docs/plan/index.html`

**Interfaces:**
- Consumes: Task 1's param contract (`kind`, `names`, `date`, `time`, `menu`, `dest`, `msg`).
- Produces: live page at `https://mdtowfiqul.github.io/Our-Days/plan/?kind=...` rendering: romantic question (date: "Will you go on a date with me? 🌹", trip: "Will you pack your bags and go on a trip with me? 🗺️"), dodging-No game (No dodges from 3rd tap, pleading lines, same 5 lines as the app EN), then plan summary (names, date, time, menu/destination, message if present) + confetti hearts; missing `kind`/`names`/`date` → friendly incomplete-link message; footer "Made with Our Days ♥" linking to `https://mdtowfiqul.github.io/Our-Days/`.

**Style:** dark navy `#0F0C20` background, glass card, pink `#FF4D8D` / purple / gold accents, single file, no external assets, no backend.

- [ ] **Step 1: Write the page** `docs/plan/index.html` — read params with `URLSearchParams`, escape all values before inserting into the DOM (textContent only, never innerHTML with param values), `decodeURIComponent` handled by URLSearchParams.
- [ ] **Step 2: Verify locally** — open the file in a headless check: `python3 -c` parse is not enough; run `node --check` on the extracted JS OR open via `python3 -m http.server` + curl the page and confirm 200 + the question strings present. Then verify the LIVE page after publishing to main (Task 7 step): open `https://mdtowfiqul.github.io/Our-Days/plan/?kind=date&names=Towfiqul+%26+Ainon&date=2026-11-21&time=19%3A30&menu=Kacchi+Biryani` in a browser task and read the rendered question + summary.
- [ ] **Step 3: Commit** — `git add docs/plan/index.html && git commit -m "Fun round 2: hosted plan page (Our-Days/plan, query params, dodging No)"`.

## Task 3: "Who is this for?" + bigger ask dialog + free time picker + custom menu (Date wizard)

**Files:**
- Modify: `app/src/main/java/app/ourdays/ui/FunScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-bn/strings.xml`
- Test: `app/src/test/java/app/ourdays/ui/ScreenshotTest.kt` (existing `funTab`/`funTabBengali` must still pass; no new screenshot needed — wizards open on tap only)

**Interfaces:**
- Consumes: Task 1 `buildPlanLink`; `settings.relationships`, `settings.namesOf(r)` for the picker.
- Produces: `WhoForStep` state shared by all three wizards: category (`partner|family|friends|others`, string resources `fun_who_*`), then relationship picker (shown when `settings.relationships.size > 1`, else auto-selected current relationship); the chosen relationship id + names flow into the wizard and the link.

**New strings (EN / BN):** `fun_who_title` "Who is this for?" / "এটা কার জন্য?", `fun_who_partner` "Partner" / "সঙ্গী", `fun_who_family` "Family members" / "পরিবার", `fun_who_friends` "Friends" / "বন্ধুরা", `fun_who_others` "Others" / "অন্যরা", `fun_who_pick` "Pick who it's for" / "কার জন্য বাছো", `fun_wizard_pick_time` "Pick a time" / "সময় বাছো", `fun_wizard_custom_menu` "Or write your own menu" / "অথবা নিজের মেনু লেখো", `fun_date_share_link` "I made a plan for us! 🌹\n%1$s" / "আমাদের জন্য একটা পরিকল্পনা বানিয়েছি! 🌹\n%1$s", `fun_trip_share_link` "I made a plan for us! 🗺️\n%1$s" / "আমাদের জন্য একটা পরিকল্পনা বানিয়েছি! 🗺️\n%1$s".

**Changes:**
- `DateWizard(settings, onDismiss)`: stage -1 = WhoFor (category chips + relationship list, Next → stage 0). Stage 0 `AskStep`: question text `headlineMedium`, dialog padding 28.dp (WizardDialog), more breathing room.
- Stage 1: replace the six `timeSlots` chips with a "Pick a time" button opening Material3 `TimePicker` in a dialog (`rememberTimePickerState`, confirm → `LocalTime.of(state.hour, state.minute)`); delete `timeSlots`.
- Stage 2: keep preset dish chips, add `OutlinedTextField` for custom menu; `canNext = foods.isNotEmpty() || customMenu.isNotBlank()`; menu string = preset names + custom text, comma-joined.
- Summary stage: build `buildPlanLink("date", names, date, time, menu, null)`, show the link in the summary card, Share button shares `fun_date_share_link` (intro + link) via `shareText`.
- `FunScreen` passes `settings` into `DateWizard`/`TripWizard`/`SurpriseComposer` (they need relationships + names).

- [ ] **Step 1: Write the failing test** — unit test in `FunTest.kt` is already Task 1; for UI, the gate is the existing screenshot suite + compile. Add no new UI test; instead verify by running `./gradlew testGithubDebugUnitTest` after the change (Expected: PASS, incl. `funTab`).
- [ ] **Step 2: Implement** the DateWizard changes + strings (EN + BN).
- [ ] **Step 3: Run tests** — `./gradlew testGithubDebugUnitTest --tests "app.ourdays.data.FunTest" --tests "app.ourdays.ui.ScreenshotTest"`. Expected: PASS.
- [ ] **Step 4: Commit** — `git add` the three modified files + `git commit -m "Fun round 2: who-for step, bigger ask, time picker, custom menu, plan link (date)"`.

## Task 4: Trip wizard — BD destinations, no budget note, plan link

**Files:**
- Modify: `app/src/main/java/app/ourdays/ui/FunScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-bn/strings.xml`

**Interfaces:**
- Consumes: Task 1 `buildPlanLink`, Task 3 `WhoForStep`.
- Produces: trip wizard with WhoFor stage, 10 destination chips, manual field, no budget note, link share.

**New strings (EN / BN):** `fun_dest_coxs` "🏖️ Cox's Bazar" / "🏖️ কক্সবাজার", `fun_dest_sajek` "⛰️ Sajek" / "⛰️ সাজেক", `fun_dest_bandarbans` "🌿 Bandarban" / "🌿 বান্দরবান", `fun_dest_sylhet` "🍃 Sylhet" / "🍃 সিলেট", `fun_dest_saintmartin` "🏝️ Saint Martin's" / "🏝️ সেন্টমার্টিন", `fun_dest_sundarbans` "🐅 Sundarbans" / "🐅 সুন্দরবন", `fun_wizard_anywhere` "Or type any place" / "অথবা যেকোনো জায়গা লেখো". **Delete** `fun_wizard_budget_note` (EN + BN) and its Card.

**Changes:**
- `TripWizard(settings, onDismiss)`: WhoFor stage first (same as Task 3).
- Destination stage: `destinationOptions` gains the 6 BD spots (keep Maldives, Switzerland, Bali, Paris); manual field label becomes `fun_wizard_anywhere` (replaces `fun_wizard_local` — delete that string too if unused elsewhere; check with grep first).
- Summary stage: `buildPlanLink("trip", names, date, null, null, where)`, share `fun_trip_share_link`.

- [ ] **Step 1: Implement** (no new unit test — link builder already tested; UI gate is the suite).
- [ ] **Step 2: Run tests** — `./gradlew testGithubDebugUnitTest`. Expected: PASS.
- [ ] **Step 3: Commit** — `git commit -m "Fun round 2: trip destinations (BD spots), no budget note, plan link"`.

## Task 5: Surprise — custom occasion (kept through sync) + WhoFor

**Files:**
- Modify: `app/src/main/java/app/ourdays/data/Fun.kt`, `app/src/main/java/app/ourdays/ui/FunScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-bn/strings.xml`
- Test: `app/src/test/java/app/ourdays/data/FunTest.kt`

**Interfaces:**
- Consumes: Task 3 `WhoForStep`.
- Produces: `SurpriseCodec.decode` keeps unknown `occasion` values verbatim (custom occasion, trimmed, max 40 chars) instead of mapping to `just_because`; `occasionTitle` shows the raw custom text for unknown occasions; composer has a custom-occasion text field shown when "custom" is picked.

**New strings (EN / BN):** `fun_occasion_custom` "Something else ✏️" / "অন্য কিছু ✏️", `fun_surprise_custom_occasion` "Name your occasion" / "উপলক্ষের নাম লেখো". Composer: when occasion == `custom`, show `OutlinedTextField`; on send, `occasion = customText.trim()` (fallback `just_because` if blank).

**Note:** existing test `surpriseCodecIgnoresGarbage` asserts unknown occasion → `JUST_BECAUSE`; update that assertion to the new behavior (custom kept) — this is the behavior change the task requires, not a regression.

- [ ] **Step 1: Write the failing test** in `FunTest.kt`: `surpriseCodecKeepsCustomOccasion` (encode/decode a surprise with occasion "Graduation" → decoded occasion is "Graduation"); update `surpriseCodecIgnoresGarbage`'s unknown-occasion assertion to expect the custom value kept.
- [ ] **Step 2: Run test to verify it fails** — `./gradlew testGithubDebugUnitTest --tests "app.ourdays.data.FunTest"`. Expected: FAIL (unknown occasion collapses to just_because).
- [ ] **Step 3: Implement** codec change + `occasionTitle` fallback + composer field + WhoFor stage in `SurpriseComposer(settings, ...)` using the chosen relationship id for the `Surprise` and the `isPaired` check.
- [ ] **Step 4: Run test to verify it passes** — same command. Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "Fun round 2: custom surprise occasion (kept through sync) + who-for"`.

## Task 6: Moments — photo calendar + card view

**Files:**
- Modify: `app/src/main/java/app/ourdays/ui/MomentsScreen.kt`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values-bn/strings.xml`
- Test: `app/src/test/java/app/ourdays/ui/ScreenshotTest.kt`

**Interfaces:**
- Consumes: `Moment` (`title`, `date`, `photoPaths`, `note`), `onOpen`, `today`.
- Produces: view switcher with 4 segments — List (`moments_view_list`), Calendar (`moments_view_calendar`, existing month view, unchanged), Photos (`moments_view_photos`), Cards (`moments_view_cards`); `PhotoCalendarView(months, onOpen)`: per month with moments, a 7-column grid; each day cell shows that day's moment photo (`AsyncImage`, `File(photoPaths.first())`, click → `onOpen`) or the day number; `CardsView(entries, today, onOpen)`: big cards (photo 16/10 or gradient placeholder with heart, title, long date + relative, `Day N` = `ChronoUnit.DAYS.between(moment.date, today)`).

**New strings (EN / BN):** `moments_view_photos` "Photos" / "ছবি", `moments_view_cards` "Cards" / "কার্ড", `moments_day_n` "Day %1$d" / "দিন %1$d".

**Screenshot tests:** `momentsPhotos` = `shot("moments_photos", lang = "bn", before = click "ছবি") { MomentsScreen(withMoments, ...) }`; `momentsCards` = `shot("moments_cards", lang = "bn", before = click "কার্ড") { ... }`. (Test moments have no photos — the views must render numbers/placeholders, not crash.)

- [ ] **Step 1: Write the failing screenshot tests** (`momentsPhotos`, `momentsCards`) — they fail at compile (views don't exist).
- [ ] **Step 2: Run to verify failure** — `./gradlew testGithubDebugUnitTest --tests "app.ourdays.ui.ScreenshotTest"`. Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** the two views + 4-segment switcher (keep existing Calendar segment + `momentsCalendar` test passing — it clicks "ক্যালেন্ডার").
- [ ] **Step 4: Run tests** — same command. Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "Fun round 2: Moments photo calendar + card views"`.

## Task 7: Push, CI green, publish plan page, verify live

**Files:** none (push + verify only).

- [ ] **Step 1: Push branch** — `/opt/hatch/bin/github push-files --owner MDTowfiqul --repo Our-Days --branch per-connection --message "Fun part 1 round 2: hosted plan links, who-for, time picker, custom menu/occasion, BD destinations, Moments photo calendar + cards" --source-dir <worktree> --paths-file <manifest>` with every changed path listed explicitly (Fun.kt, FunScreen.kt, MomentsScreen.kt, FunTest.kt, ScreenshotTest.kt, strings.xml x2, docs/plan/index.html).
- [ ] **Step 2: Publish the plan page to main** — same tool, `--branch main`, paths-file containing only `docs/plan/index.html`, message "Our Days plan page (Fun round 2): hosted plan link". (Ruling 1: Pages serves main/docs; without this the shared link is dead until PR merge.)
- [ ] **Step 3: Poll CI** — unauthenticated API `/repos/MDTowfiqul/Our-Days/actions/runs?head_sha=<new head>` until the `build` run concludes; if red, read the log via a signed-in cloud browser task, fix, re-push.
- [ ] **Step 4: Verify the live page** — browser task opens `https://mdtowfiqul.github.io/Our-Days/plan/?kind=date&names=Towfiqul+%26+Ainon&date=2026-11-21&time=19%3A30&menu=Kacchi+Biryani` and `...?kind=trip&names=Towfiqul+%26+Ainon&date=2026-12-25&dest=Cox%27s+Bazar`; confirm the question, the dodging No, and the summary render. Also open with no params and confirm the friendly incomplete message.

## Task 8: Download artifacts, verify APK, stage, deliver

**Files:** stage APK at `~/workspace/your_files/OurDays-PerConnection-PR11-debug.apk` (symlink into the goal's files dir — copying there is the delivery).

- [ ] **Step 1: Download BOTH artifacts** (APK zip `since-us-debug-apk` + screenshots zip) with a cloud browser task; verify the zips against the GitHub API artifacts endpoint digests with `sha256sum` (never trust a transcribed hash).
- [ ] **Step 2: Verify the APK** — unzip; `package app.ourdays`, version 1.0.9; new strings/keys present (`fun_who_title`, `moments_view_photos`, `moments_view_cards`, `PLAN_PAGE_BASE_URL` / plan link); READ the fun_tab screenshots (EN + BN) and the new moments_photos / moments_cards screenshots to confirm the screens render.
- [ ] **Step 3: Stage** the APK at `~/workspace/your_files/OurDays-PerConnection-PR11-debug.apk`.
- [ ] **Step 4: Deliver** in the Our Days side chat: attach the APK, short test notes (link share + opening the link, "Who is this for?", free time picker, custom menu, trip destinations, custom occasion, two Moments views), include the live plan-page link. Then STOP — no part 2, no extra scope.

## Self-review

- Spec coverage: items 1–8 of the task body map to Tasks 1–2 (hosted link), 3–5 (who-for), 3 (ask dialog), 3 (time picker), 3 (custom menu), 4 (budget note out, BD spots + manual), 5 (custom occasion), 6 (Moments views). Build/verify/deliver maps to Tasks 7–8.
- Type consistency: `buildPlanLink` signature is fixed in Task 1 and reused in Tasks 3–4; `WhoForStep` state (category + relationship id) is produced in Task 3 and consumed in Tasks 4–5.
- Proportion: plan is shorter than the code it describes; code bodies are left to the implementer.
