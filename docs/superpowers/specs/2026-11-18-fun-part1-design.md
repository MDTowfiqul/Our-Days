# Fun tab, part 1 — romantic flows (design, agreed 2026-11-18)

## Intent
The Fun tab stops saying "coming soon" and becomes the romantic heart of the
app: the things Towfiqul already built as web pages for Ainon (For-Ainon,
Again_for_Ainon, Date_with_Ainon, i_miss_mynon), rebuilt natively in the app,
in the i_miss_mynon style. For the couple first, for any relationship second.

## What part 1 is
1. **Miss-you card** — live timer since you last met, 1–5 "how much do you
   miss me" slider, kiss button with counter and a confetti burst, love notes
   that change on tap. Per relationship, like photos and slides.
2. **Plan a date** — 4-stage wizard: ask (with the dodging No button) →
   date + time → food → summary, shared as text (share sheet).
3. **Plan a trip** — 4-stage wizard: ask → destination (Maldives,
   Switzerland, Bali, Paris, or a place in Bangladesh) → date → summary,
   shared as text. Keeps the honest note: abroad is a future promise for now.
4. **Send a surprise** — compose (occasion: proposal, birthday, Eid,
   anniversary, just because + a message). It travels to the partner inside
   the sync file; on their phone it appears in the Fun tab and opens with
   the playful reveal (dodging No, then YES → message + confetti).

## What part 1 is not
- No Would You Rather / games (part 2), no focus-together timer (part 3),
  no daily question / date ideas (part 4).
- No password gates. Charming once on a link, annoying every day in an app.
- No new server, no new account, no new dependency. Surprises ride the
  existing encrypted sync file; the wizards share by the system share sheet.
- No remote photos. The card uses the relationship photo the app already has.

## Style (from i_miss_mynon)
Dark navy card over the blurred relationship photo, pink / purple / gold
accents, floating hearts, gentle motion (600 ms crossfade), confetti on
meaningful taps only. First-person, pet-name, emoji-sprinkled copy.
English + Bengali, like the rest of the app.

## Approaches considered
- **A. One phone + share sheet only** (no sync changes). Simpler, but a
  surprise would never arrive inside the partner's app — only as a text.
  Rejected for the surprise; kept for the wizards, where a text is the point.
- **B. Sync-file delivery (chosen).** The surprise is data in the sync file,
  like a moment. Union merge by id, revealed = either side revealed. No new
  infrastructure, works offline, fits how the app already pairs and syncs.
- **C. Firebase live delivery.** Rejected for part 1 — that is the part 2/3
  path, and part 1 must work with no connection at all.

## Data
- `LoveSettings` gains per-relationship maps `lastMet` (epoch day),
  `kisses` (count), `missLevels` (1–5), and a `surprises` list — same
  DataStore-key + codec pattern as `photos` / `slidesByRelationship`.
- `Surprise`: id, relationshipId, occasion, message, from (person id),
  createdAt, revealed. `SurpriseCodec` (JSON array).
- `SyncData.surprises` (optional, old files decode empty). Merge: union by
  id, revealed OR. Export includes the relationship's surprises only when
  moments are included ("names & date only" must not smuggle a surprise).
- Backup keeps all of it, like everything else in settings.

## Decisions the user made
- Order: romantic flows first, then games, then focus, then daily
  question / date ideas. One part at a time; he directs the next step
  after testing each build.
- Foods and destinations are copied from his own pages (Kacchi Biryani,
  Bhuna Khichuri, Fuchka/Chotpoti …; Maldives, Switzerland, Bali, Paris).
- Miss-you default: no date set until he picks one — no invented number.

## Left to the builder
Exact wording of the 5 slider levels and the 8 love notes (EN+BN, wholesome,
in the pages' voice). The dodging-No pleading lines, generic (no names).
The confetti burst is hand-rolled (the existing `Confetti()` fires once per
launch, so it cannot back the kiss button). Screenshot tests for the Fun tab
in EN and BN.
