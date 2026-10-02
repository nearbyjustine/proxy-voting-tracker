---
name: Proxy Voting
description: Proxy season as an election results desk. Every meeting is a race on deadline, the policy calls each proposal, the votes are the count.
colors:
  broadcast-navy: "#0e1526"
  broadcast-navy-night: "#050913"
  broadcast-muted: "#98a4b9"
  studio-ground: "#edf0f5"
  board-white: "#ffffff"
  board-recess: "#f5f7fa"
  ink: "#0e1526"
  ink-secondary: "#4a5568"
  rule: "#d6dbe3"
  for-blue: "#1f4fcc"
  for-wash: "#e3eafc"
  against-red: "#c4351f"
  against-wash: "#fbe5e1"
  abstain-grey: "#5c6575"
  abstain-wash: "#e9ecf1"
  desk-lamp: "#ffcf3a"
  desk-lamp-wash: "#fff4cc"
  desk-lamp-ink: "#4a3600"
  desk-lamp-on-lit: "#2b2000"
  night-ground: "#080d19"
  night-board: "#0f1729"
  night-recess: "#142039"
  night-ink: "#e8edf6"
  night-ink-secondary: "#98a4b9"
  night-rule: "#22304b"
  night-for: "#6d97ff"
  night-for-wash: "#16244a"
  night-against: "#ff7a66"
  night-against-wash: "#3a1a17"
  night-abstain: "#a3acbb"
  night-abstain-wash: "#1d2638"
  night-lamp-wash: "#2f2708"
  night-lamp-ink: "#ffe38a"
typography:
  display:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "4.5rem"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.025em"
    fontVariation: "'wdth' 68"
  headline:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "clamp(2.25rem, 5vw, 3rem)"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.025em"
    fontVariation: "'wdth' 68"
  clock:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1
    letterSpacing: "-0.01em"
    fontFeature: "'tnum' 1"
    fontVariation: "'wdth' 68"
  title:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 600
    lineHeight: 1.375
  body:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
    fontFeature: "'tnum' 1"
  label:
    fontFamily: "Archivo Variable, ui-sans-serif, system-ui, sans-serif"
    fontSize: "0.7rem"
    fontWeight: 650
    letterSpacing: "0.09em"
    fontVariation: "'wdth' 85"
rounded:
  segment: "3px"
  chip: "4px"
  mark: "5px"
  control: "7px"
  inset: "8px"
  board: "10px"
spacing:
  segment-gap: "3px"
  xs: "6px"
  sm: "12px"
  md: "16px"
  board-pad: "20px"
  board-pad-wide: "24px"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.board-white}"
    rounded: "{rounded.control}"
    padding: "8px 15px"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "8px 15px"
  button-ghost-hover:
    backgroundColor: "{colors.board-recess}"
  input:
    backgroundColor: "{colors.board-white}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "8px 10px"
  board:
    backgroundColor: "{colors.board-white}"
    rounded: "{rounded.board}"
  board-row-closing-soon:
    backgroundColor: "{colors.desk-lamp-wash}"
  status-open:
    backgroundColor: "{colors.for-wash}"
    textColor: "{colors.for-blue}"
    rounded: "{rounded.chip}"
    padding: "2px 8px"
  status-closing-soon:
    backgroundColor: "{colors.desk-lamp}"
    textColor: "{colors.desk-lamp-on-lit}"
    rounded: "{rounded.chip}"
    padding: "2px 8px"
  status-closed:
    backgroundColor: "{colors.board-recess}"
    textColor: "{colors.ink-secondary}"
    rounded: "{rounded.chip}"
    padding: "2px 8px"
  decision-for:
    backgroundColor: "{colors.for-wash}"
    textColor: "{colors.for-blue}"
    rounded: "{rounded.mark}"
    padding: "2px 6px"
  decision-against:
    backgroundColor: "{colors.against-wash}"
    textColor: "{colors.against-red}"
    rounded: "{rounded.mark}"
    padding: "2px 6px"
  decision-abstain:
    backgroundColor: "{colors.abstain-wash}"
    textColor: "{colors.abstain-grey}"
    rounded: "{rounded.mark}"
    padding: "2px 6px"
  vote-option-selected-for:
    backgroundColor: "{colors.for-blue}"
    textColor: "{colors.board-white}"
    rounded: "6px"
    padding: "8px"
  broadcast-bar:
    backgroundColor: "{colors.broadcast-navy}"
    textColor: "{colors.night-ink}"
---

# Design System: Proxy Voting

## Overview

**Creative North Star: "The Results Desk"**

Proxy season is run like election night. Each shareholder meeting is a race with a deadline; the organisation's voting policy "calls" each proposal; the votes the organisation casts are the count. The interface borrows the material of a broadcast results desk: a navy broadcast bar with a yellow desk clock running in the corner, a cool studio-grey ground, white results boards laid on it, condensed broadcast numerals for tickers and clocks, segmented result bars that fill as votes come in, and rows that light amber when a race is about to close.

The system is dense and operational, built for daily desktop use by analysts and voters, but it must read instantly in a screen share. Hierarchy comes from type width and weight (the condensed cut of one family against its normal width), not from colour blocks or stat cards. Colour is reserved for meaning: three decision colours, one lamp colour for urgency, and nothing decorative. The world explicitly refuses the stat-card fintech dashboard: no KPI tiles, no gradient hero numbers, no chart-for-the-sake-of-a-chart.

Light and dark are both first-class. Dark is "the desk at night": the same roles on a deep navy ground, with decision colours lifted for contrast and the lamp yellow unchanged.

**Key Characteristics:**
- One variable family (Archivo), two widths: condensed (68%) for numerals and headlines, normal for reading.
- Tabular numerals everywhere, so clocks and counts never jitter.
- FOR blue / AGAINST red / ABSTAIN grey, always with a check / x / minus glyph and the word.
- Amber "lit" rows and chips mean closing within 48 hours; nothing else uses the lamp except the desk clock and active-nav underline.
- Segmented result bars, one segment per proposal, filling left-to-right as votes are cast.
- Live countdown clocks (days + HH:MM, seconds demoted) next to exact local and market times.

## Colors

A cool, near-neutral studio palette carrying exactly three decision hues and one lamp yellow; every chromatic colour encodes a fact.

### Primary
- **Broadcast Navy** (broadcast-navy): the broadcast bar across the top of every screen, and the ink of primary buttons. Deepens to **Broadcast Navy Night** (broadcast-navy-night) in dark mode so the bar still sits above the night ground. Secondary text on the bar uses **Broadcast Muted** (broadcast-muted).
- **Desk Lamp** (desk-lamp): the desk clock in the bar, the 3px underline under the active nav item, the CLOSING SOON chip, the ring around a closing-soon race header, and the "closing soon" count highlight on Home. Text on a solid lamp fill is **Lamp Ink on Lit** (desk-lamp-on-lit). Rows that are closing soon take **Desk Lamp Wash** (desk-lamp-wash), with **Desk Lamp Ink** (desk-lamp-ink) for text that sits on the wash (policy events in the audit log).

### Secondary: the three decisions
- **For Blue** (for-blue) with **For Wash** (for-wash): FOR decisions, the OPEN status chip, focus rings, text selection, caret, success notices and vote-related audit events. Doubles as the system's interactive accent because "for" is the affirmative.
- **Against Red** (against-red) with **Against Wash** (against-wash): AGAINST decisions, error banners, "voted against policy" flags, destructive hover on remove.
- **Abstain Grey** (abstain-grey) with **Abstain Wash** (abstain-wash): ABSTAIN decisions only.

### Neutral
- **Studio Ground** (studio-ground): the page background behind the boards.
- **Board White** (board-white): results boards, inputs, unselected vote options.
- **Board Recess** (board-recess): table heads, column header strips, the count strip under a race header, rule rows in the policy editor, summaries and notes, CLOSED chips, row hover.
- **Ink** (ink) and **Secondary Ink** (ink-secondary, 7.5:1 on white): primary and supporting text; secondary ink also carries field labels, demoted seconds on clocks, and proposal sequence numbers.
- **Rule** (rule): every 1px divider and board border, and the outline of an uncast result segment.
- Dark mode maps each role one-for-one onto the `night-*` tokens; components never pick a theme-specific value except the dark-mode text on solid decision fills (deep navy/oxblood instead of white).

### Named Rules
**The Never-Colour-Alone Rule.** A decision is always a glyph + word + colour: check for FOR, x for AGAINST, minus for ABSTAIN. A coloured swatch, dot or bar segment never stands in for a decision without its count label or mark nearby.

**The Lamp Means Deadline Rule.** Desk Lamp yellow is reserved for time pressure and "now": the desk clock, the active tab, closing-soon rows, chips and rings. It never decorates, and it is never used for a decision.

**The Only-Cast-Votes-Colour Rule.** On the Meetings board, result segments are neutral (outlined in rule) until a vote is cast, then fill with the decision colour. Nothing reads as a result before it is one. On a race (meeting detail), the segment outline may carry the policy's call, and the fill is still only the cast vote.

## Typography

**Display Font:** Archivo Variable, condensed (font-stretch 68%) (with ui-sans-serif, system-ui)
**Body Font:** Archivo Variable, normal width (same fallbacks)
**Label Font:** Archivo Variable at 85% width, uppercase

**Character:** One family doing two jobs, like a broadcast graphics package: the condensed cut gives tall, tight numerals for tickers, clocks and headlines; the normal cut keeps company names, rationales and descriptions comfortable to read. Tabular numerals are on globally.

### Hierarchy
- **Display** (700, condensed, 3.75rem to 4.5rem, line-height 1): the ticker in a race header, and the large countdown (3rem to 3.75rem). Uppercase.
- **Headline** (700, condensed, 2.25rem rising to 3rem at md, tight tracking): one per page, uppercase, the page name (MEETINGS, VOTING POLICY, AUDIT LOG, IMPORT). Followed by a one-line lead in secondary ink.
- **Clock / Ticker** (700, condensed, 1.5rem to 1.7rem, line-height 1): board tickers and row countdowns. Seconds drop to 0.55em in secondary ink; the day unit to 0.45em.
- **Title** (600, normal width, 1.25rem, snug): proposal titles; company names on rows at body size, semibold.
- **Body** (400, 0.875rem to 1rem, line-height 1.5): descriptions and rationales, capped at 68ch.
- **Label** (650, 0.7rem, 85% width, 0.09em tracking, uppercase, secondary ink): table and column heads, and field labels (RECOMMENDATION, YOUR VOTE, WHEN, CALL, BECAUSE).
- **Chip text** (600 to 700, condensed, 0.78rem to 0.8rem, uppercase, wide tracking): status chips, decision marks, category chips, audit action tags, nav items (0.95rem).

### Named Rules
**The Two-Widths Rule.** Numbers, tickers, headlines and chips use the condensed cut; anything a person reads as a sentence uses the normal cut. Do not introduce a second family.

**The Chip-After-Heading Rule.** Categories and classifications sit as inline chips after or beside the thing they classify (a category chip leads the proposal description, the status chip sits beside the meeting meta). There are no eyebrow lines above headings.

## Layout

A single centred column, max 1240px, with 16px side gutters on phones and 24px from sm. Pages stack: condensed headline and lead, then one or more boards with 16px between them. The Meetings board is a CSS grid table at lg and above (company, market, deadline, status, count, chevron), with a recessed header strip of labels; below lg each row collapses to two lines (ticker + company with status chip on the right, then market and deadline sharing a line, then the full-width count bar). Race (meeting detail) proposals use a three-column grid at md: a 3.5rem sequence numeral, the proposal body, and a 260 to 320px decision aside separated by a vertical rule (a horizontal rule on phones). Board rows are 20px horizontal by 16px vertical; boards pad 20px, 24px at md, 28px for the race header. The broadcast bar keeps the tenant ("Acting for") and the desk clock visible on phones on their own line, and the nav scrolls horizontally with a right-edge fade.

**The Deadline-First Rule.** Deadlines appear as a live countdown plus the exact local time. On a race the market time is always stated, or "Same as <city> market time" when the zones match; on the board the market line appears whenever it differs from local time, with the market city always in the Market column.

## Elevation & Depth

Mostly flat, with a single soft board shadow that lifts the white boards off the studio ground. Depth inside a board is tonal: recessed strips and rows use Board Recess, separated by 1px rules. No other shadows exist; hover changes tone, not elevation.

### Shadow Vocabulary
- **Board** (`box-shadow: 0 1px 2px rgb(14 21 38 / 0.06), 0 8px 24px -12px rgb(14 21 38 / 0.18)`; dark: `0 1px 2px rgb(0 0 0 / 0.5), 0 12px 28px -14px rgb(0 0 0 / 0.7)`): every board, and only boards.
- **Focus halo** (`0 0 0 3px color-mix(in oklab, For Blue 22%, transparent)`): focused inputs.

### Named Rules
**The One-Lift Rule.** Boards are the only lifted surface. Rows, chips, buttons and notices are flat.

## Shapes

Gently squared corners on a tight scale: 3px result segments, 4px chips, 5px decision marks, 6 to 7px buttons, inputs and vote options, 8px inset notices and rule rows, 10px boards. Everything is bordered with a 1px rule or sits on a tonal wash; the only 2px strokes are the focus outline and the "VOTE IN" stamp. Progress tracks (pay score, board independence) are 6px full-round bars in ink on recess. The result bar is a row of equal segments with 3px gaps, never a single continuous bar.

## Components

### Buttons
Solid, compact, confident.
- **Shape:** softly squared (7px).
- **Primary:** Ink background with Board White text, 600 weight at 0.875rem, 8px by 15px, optional 16px leading icon with a 7px gap. Hover mixes 14% For Blue into the ink; active presses to 0.97 scale (disabled under reduced motion).
- **Ghost:** transparent with an inset 1px rule; hover fills Board Recess. Icon-only ghost buttons are square with 7px padding (reorder, remove, paging).
- **Disabled:** 40% opacity, not-allowed cursor.
- **Sign in on the broadcast bar** uses For Blue fill with white text.

### Vote options (signature)
A three-up radiogroup of condensed uppercase buttons: FOR (check), AGAINST (x), ABSTAIN (minus). Unselected: Board White with a rule border, recess on hover. Selected: solid decision colour with white text (dark mode: deep tinted text). The same control, smaller, sets each policy rule's call. When a vote lands, a "VOTE IN" stamp in the decision colour (2px border, condensed, wide tracking) drops in beside the YOUR VOTE label, scaling from 1.8 and rotating from -14 to -4 degrees over 320ms, and the matching result-bar segment fills. A vote that differs from the policy's call shows an against-red warning line with a triangle glyph.

### Decision Mark
Glyph + word chip on the decision's wash (5px radius, condensed uppercase). Large variant (16px glyph, 1rem text) for the policy recommendation; small for the board recommendation and audit details. A solid variant exists for filled contexts.

### Status chips
Condensed uppercase on a 4px chip: OPEN on For Wash, CLOSING SOON on solid Desk Lamp with a pulsing 6px dot (pulse is motion-safe only), CLOSED on Board Recess in secondary ink. Closed rows on the board drop to 60% opacity.

### Cards / Containers (Boards)
- **Corner Style:** 10px.
- **Background:** Board White on Studio Ground.
- **Shadow Strategy:** the Board shadow; see Elevation.
- **Border:** 1px Rule.
- **Internal Padding:** 20px, 24px at md. Boards hold tables and row lists edge to edge with an internal recessed header strip.

### Result Bar (signature)
One segment per proposal, equal widths, 3px gaps, 3px radius, 10px tall on the board and 16px on a race. Each segment is an inset 1.5px outline that fills from the left (scaleX over 450ms, expo-out) in the cast vote's colour. Always paired with a condensed "n/m IN" count, and exposed to assistive tech as an image with "n of m voted".

### Countdown
Days, then HH:MM in condensed bold, seconds demoted to 0.55em in secondary ink, driven by one shared ticking clock, `role="timer"`. Closed meetings show "n days ago" in secondary ink instead (or CLOSED at large size).

### Inputs / Fields
- **Style:** Board White, 1px rule, 7px radius, 8px by 10px, 0.875rem; selects and textareas share it.
- **Focus:** border turns For Blue with the 3px focus halo.
- **Disabled:** inherits the read-only appearance when the user cannot edit the policy.
- Labels are uppercase Label type above or inline before the field.

### Notices
Inline 8px-radius strips: error banners on Against Wash with a circle-alert glyph and a visible correlation reference; success notices on For Wash with a circle-check glyph; neutral notes on Board Recess. They fade and slide 4px in.

### Navigation
The broadcast bar: Broadcast Navy, check-mark app glyph in a For Blue tile, condensed uppercase "PROXY VOTING", the tenant under an "ACTING FOR" label, and the desk clock in condensed Desk Lamp yellow. Below it, condensed uppercase nav items in Broadcast Muted; hover turns white; active is white with a 3px Desk Lamp underline. Locale (EN/FR), theme cycle (system/dark/light), user name and sign-out sit right-aligned as quiet icon buttons with a translucent white hover.

### Motion
Motion confirms, never decorates: route change fades and rises 6px (180ms); board rows stagger in by 35ms; policy rules animate their reorder with layout transitions (300ms); a vote stamps in and fills its segment. All easing is expo-out `cubic-bezier(0.16, 1, 0.3, 1)`, and motion respects the user's reduced-motion setting.

## Do's and Don'ts

### Do:
- **Do** pair every decision colour with its glyph (check / x / minus) and its word.
- **Do** keep result-bar segments neutral on the board until a vote is cast; only cast votes carry colour.
- **Do** light a row with Desk Lamp Wash (and a race header with a 2px lamp ring) when its deadline is under 48 hours.
- **Do** set tickers, clocks, counts, headlines and chips in the condensed cut with tabular numerals.
- **Do** show the countdown with the exact local deadline, and the market time (or "Same as <city> market time") on every race.
- **Do** place category and status as inline chips after or beside what they classify.
- **Do** keep the tenant ("Acting for") visible in the broadcast bar at every width.

### Don't:
- **Don't** build stat cards, KPI tiles or big-number dashboard summaries; the board rows are the summary.
- **Don't** put eyebrow or kicker lines above headings.
- **Don't** use Desk Lamp yellow for anything that is not time pressure or "now".
- **Don't** convey a decision by colour alone, including in bars, dots or row tints.
- **Don't** add shadows beyond the single board shadow, or lift rows and chips on hover.
- **Don't** introduce a second typeface; use Archivo's width axis instead.
- **Don't** animate for decoration; motion follows a consequential action or a route change.
