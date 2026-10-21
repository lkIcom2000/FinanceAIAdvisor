---
name: Fine by Atruvia
description: Visual identity of "Fine", the hybrid financial advice agent for C-class retail customers, operated by Atruvia. Angular Material (M3) on Angular 22; this DESIGN.md specifies the Atruvia brand-layer delta on top of M3 system tokens.
status: final
created: 2026-10-21
updated: 2026-10-21
sources:
  - ../../prds/prd-FinanceTool-2026-10-20/prd.md
  - ../../architecture/architecture-FinanceTool-2026-10-20/ARCHITECTURE-SPINE.md
  - https://atruvia.de/ (corporate styling, extracted 2026-10-21)
companion: EXPERIENCE.md
colors:
  # Brand palette taken from atruvia.de (main CSS, 2026-10-21).
  # Mapped onto M3 system roles (--mat-sys-*). Unlisted M3 roles are generated
  # by the M3 theme from primary/secondary/tertiary/error and stay as generated.
  brand-navy: '#000064'
  brand-blue: '#0A5AE1'
  brand-cyan-light: '#D4F2F5'
  brand-ice: '#F2FAFC'
  brand-cyan: '#00BBFF'
  brand-coral: '#FF5A46'
  brand-red: '#EB1700'
  brand-orange: '#FA9B50'
  brand-yellow: '#EDC739'
  brand-grey: '#767676'
  # M3 role overrides
  primary: '{colors.brand-blue}'
  on-primary: '#FFFFFF'
  primary-container: '{colors.brand-cyan-light}'
  on-primary-container: '{colors.brand-navy}'
  secondary: '{colors.brand-navy}'
  on-secondary: '#FFFFFF'
  surface: '#FFFFFF'
  on-surface: '{colors.brand-navy}'
  on-surface-variant: '#3D3D6B'
  surface-container-low: '{colors.brand-ice}'
  surface-container: '#E8F6F9'
  outline: '{colors.brand-grey}'
  outline-variant: '#C9DDE3'
  error: '{colors.brand-red}'
  on-error: '#FFFFFF'
  # Semantic tints (derived from brand hues; used as fills with navy text only)
  risk-container: '#FFE3DF'
  attention-container: '#FDEBDC'
  premise-container: '#FBF3D4'
  # Data visualisation
  chart-median: '{colors.brand-navy}'
  chart-band: '{colors.brand-cyan-light}'
  chart-band-edge: '{colors.brand-blue}'
  chart-actual: '{colors.brand-blue}'
  chart-risk: '{colors.brand-coral}'
  highlight-ring: '{colors.brand-cyan}'
typography:
  # The ATRUVIA typeface is proprietary. The prototype uses Inter
  # (open licence) as the closest neutral substitute; swap to ATRUVIA only if licensed.
  display:
    fontFamily: 'Inter, Helvetica, Arial, sans-serif'
    fontSize: 32px
    fontWeight: '700'
    lineHeight: '40px'
    letterSpacing: -0.01em
  headline:
    fontFamily: 'Inter, Helvetica, Arial, sans-serif'
    fontSize: 24px
    fontWeight: '700'
    lineHeight: '32px'
  title:
    fontFamily: 'Inter, Helvetica, Arial, sans-serif'
    fontSize: 18px
    fontWeight: '600'
    lineHeight: '26px'
  body:
    fontFamily: 'Inter, Helvetica, Arial, sans-serif'
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '24px'
  body-sm:
    fontFamily: 'Inter, Helvetica, Arial, sans-serif'
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '20px'
  label:
    fontFamily: 'Inter, Helvetica, Arial, sans-serif'
    fontSize: 14px
    fontWeight: '600'
    lineHeight: '20px'
  caption:
    fontFamily: 'Inter, Helvetica, Arial, sans-serif'
    fontSize: 12px
    fontWeight: '400'
    lineHeight: '16px'
  figure-lg:
    fontFamily: 'Inter, Helvetica, Arial, sans-serif'
    fontSize: 28px
    fontWeight: '700'
    lineHeight: '36px'
    fontFeature: 'tnum'
  figure:
    fontFamily: 'Inter, Helvetica, Arial, sans-serif'
    fontSize: 16px
    fontWeight: '600'
    lineHeight: '24px'
    fontFeature: 'tnum'
rounded:
  sm: 4px
  md: 8px
  lg: 20px
  full: 9999px
spacing:
  '1': 4px
  '2': 8px
  '3': 12px
  '4': 16px
  '6': 24px
  '8': 32px
  '12': 48px
  header-height: 64px
  chat-width: 440px
  chat-width-wide: 500px
  overview-min-width: 560px
components:
  button-primary:
    background: '{colors.primary}'
    foreground: '{colors.on-primary}'
    radius: '{rounded.full}'
    height: 44px
    typography: '{typography.label}'
  button-secondary:
    background: 'transparent'
    foreground: '{colors.primary}'
    border: '2px solid {colors.primary}'
    radius: '{rounded.full}'
    height: 44px
  button-text:
    foreground: '{colors.primary}'
    typography: '{typography.label}'
  app-header:
    background: '{colors.brand-navy}'
    foreground: '#FFFFFF'
    height: '{spacing.header-height}'
  overview-card:
    background: '{colors.surface}'
    border: '1px solid {colors.outline-variant}'
    radius: '{rounded.lg}'
    padding: '{spacing.6}'
  figure-value:
    typography: '{typography.figure}'
    foreground: '{colors.on-surface}'
  figure-chip:
    background: '{colors.surface-container-low}'
    foreground: '{colors.on-surface}'
    border: '1px solid {colors.outline-variant}'
    radius: '{rounded.sm}'
    typography: '{typography.figure}'
  why-link:
    foreground: '{colors.primary}'
    typography: '{typography.label}'
  agent-message:
    background: '{colors.surface}'
    foreground: '{colors.on-surface}'
    border: '1px solid {colors.outline-variant}'
    radius: '{rounded.md}'
  user-message:
    background: '{colors.brand-navy}'
    foreground: '#FFFFFF'
    radius: '{rounded.md}'
  human-advisor-message:
    background: '{colors.primary-container}'
    foreground: '{colors.on-primary-container}'
    border-left: '4px solid {colors.brand-navy}'
    radius: '{rounded.md}'
  outcome-label:
    typography: '{typography.label}'
    foreground: '{colors.on-surface-variant}'
  check-badge:
    background: '{colors.surface-container-low}'
    foreground: '{colors.on-surface}'
    icon-color: '{colors.primary}'
    radius: '{rounded.full}'
    typography: '{typography.caption}'
  waiting-card:
    background: '{colors.surface-container-low}'
    step-active: '{colors.primary}'
    step-done: '{colors.brand-navy}'
    step-pending: '{colors.outline}'
    radius: '{rounded.md}'
  status-chip-informed:
    background: '{colors.surface-container-low}'
    foreground: '{colors.on-surface}'
    radius: '{rounded.full}'
  status-chip-recommended:
    background: '{colors.primary-container}'
    foreground: '{colors.on-primary-container}'
    radius: '{rounded.full}'
  status-chip-awaiting:
    background: '{colors.attention-container}'
    foreground: '{colors.brand-navy}'
    radius: '{rounded.full}'
  status-chip-executed:
    background: '{colors.primary}'
    foreground: '{colors.on-primary}'
    radius: '{rounded.full}'
  status-chip-undone:
    background: 'transparent'
    foreground: '{colors.on-surface-variant}'
    border: '1px solid {colors.outline}'
    radius: '{rounded.full}'
  status-chip-escalated:
    background: '{colors.brand-navy}'
    foreground: '#FFFFFF'
    radius: '{rounded.full}'
  risk-callout:
    background: '{colors.risk-container}'
    foreground: '{colors.brand-navy}'
    accent: '{colors.brand-coral}'
    radius: '{rounded.md}'
  premise-callout:
    background: '{colors.premise-container}'
    foreground: '{colors.brand-navy}'
    accent: '{colors.brand-yellow}'
    radius: '{rounded.md}'
  highlight-ring:
    outline: '3px solid {colors.highlight-ring}'
    outline-offset: 2px
    radius: inherit
  range-chart:
    median: '{colors.chart-median}'
    band: '{colors.chart-band}'
    band-edge: '{colors.chart-band-edge}'
    risk-marker: '{colors.chart-risk}'
---

## Brand & Style

Fine is the advice agent that Atruvia, the technology partner of the cooperative banking group (Volksbanken Raiffeisenbanken), operates for mass-market customers. It borrows Atruvia's corporate look so that study participants meet something that feels like a real bank product. The looks are deep navy, a confident action blue, airy ice and cyan surfaces, pill-shaped buttons, and generous white space.

The posture is **calm, plain and on your side**. Atruvia's site speaks in a friendly, direct voice. Fine keeps that warmth but drops the playfulness wherever money is at stake. Fine never looks like a trading app: no tickers, no green and red price colours, no dark "pro" mode. The screen should feel like a well-kept paper folder of your finances, with a helper beside it.

Two ideas shape every visual decision:

1. **Proof is visible.** Every figure can be traced. "Why?" links and the "Checked" badge are first-class elements, not footnotes.
2. **Nothing alarms without reason.** Coral, the brand's signal colour, is reserved for real risk to the user's money, such as a forecast shortfall. Refusals, escalations and questions about the profile are never coloured as errors.

Fine inherits Angular Material (M3). This file sets only the brand layer: colour roles, typography, shapes and a small set of product-specific components. Every M3 component not listed under Components (form fields, dialogs, menus, tabs, snackbars, tooltips, progress indicators, steppers) keeps its M3 visuals and only picks up the theme tokens.

> **Operator assumption.** Atruvia is an IT provider, not a licensed bank. Under the PRD's variant B, the licence holder is assumed to be a cooperative bank on the Atruvia platform. The brand shown is Atruvia plus the product name Fine.

## Colors

The palette comes straight from atruvia.de. Three colours carry the interface, and the rest are signals with narrow jobs.

- **Navy `{colors.brand-navy}` (#000064)** is the text and structure colour. It is used for body text, headings, the app header, the user's own chat messages and the "passed to advisor" status. It replaces black everywhere: Fine has no pure-black text.
- **Action blue `{colors.primary}` (#0A5AE1)** is the M3 primary colour. It is used for primary buttons, links, "Why?" links, active tabs, the focused step in the waiting card, and the "Done for you" status. Blue means *you can act here* or *Fine acted here*. It reaches 5.9:1 on white.
- **Light cyan `{colors.primary-container}` (#D4F2F5) and ice `{colors.surface-container-low}` (#F2FAFC)** are the quiet surfaces. Ice is the chat background, the background of figure chips and the waiting card. Light cyan marks recommendations, the forecast band in range charts, and human-advisor messages.
- **Cyan `{colors.brand-cyan}` (#00BBFF)** is used only for the highlight ring (FR-8). It has too little contrast for text and must never carry text or meaning on its own.
- **Coral `{colors.brand-coral}` (#FF5A46)** marks **risk to money**: a forecast shortfall, a negative forecast balance, an emergency fund below target. It appears as an accent bar, icon or chart marker on a `{colors.risk-container}` tint with navy text. Coral fails 4.5:1 for text on white, so text that must be red uses **`{colors.error}` (#EB1700, 4.5:1)**, and only for negative balances and form errors.
- **Orange `{colors.brand-orange}`** appears only as the `{colors.attention-container}` tint behind "Needs your confirmation". It means *waiting for you*, not *danger*.
- **Yellow `{colors.brand-yellow}`** appears only as the `{colors.premise-container}` tint and accent for `DETECT_TRAP` ("Something in the question isn't right").
- **Grey `{colors.outline}` (#767676)** is used for outlines, pending steps and the "Undone" status. It reaches 4.54:1, so it can also carry secondary text.

Avoid: green and red as gain and loss colours, gradients, colour as the only carrier of meaning (every status also has an icon and a word), and more than one signal colour in a single card.

## Typography

Inter stands in for the proprietary ATRUVIA typeface. The ramp is deliberately large: users have low financial literacy and some may have weak eyesight, so body text is 16px and nothing that matters is smaller than 14px. Captions at 12px are used only for timestamps and validity dates.

- **`{typography.display}`** is used once per Overview tab, for the headline figure, e.g. the current account balance.
- **`{typography.headline}`** is used for tab and page titles.
- **`{typography.title}`** is used for card titles, the outcome label of a response card, and dialog titles.
- **`{typography.body}`** is used for chat text and explanations.
- **`{typography.body-sm}`** is used for assumptions, sources and secondary lines.
- **`{typography.label}`** is used for buttons, chips and tabs.
- **`{typography.figure}` and `{typography.figure-lg}`** are used for every amount, percentage and duration. They use tabular numerals so that columns line up and figures don't jitter when they update.

Number format (English UI): `1,240.00 EUR` in tables and confirmations, `1,240 EUR` in running text, a real minus sign for negatives (`−90 EUR`), and ranges written out (`between 41,000 and 68,000 EUR`). Figures are never set in light weights.

## Layout & Spacing

The spacing scale is 4-based (`{spacing.1}` to `{spacing.12}`). Cards use `{spacing.6}` padding, and items inside a card are spaced by `{spacing.4}`.

The workspace is a fixed two-column shell below a `{spacing.header-height}` navy header:

- **Left: Financial Overview.** The main surface, flexible width, at least `{spacing.overview-min-width}`. A tab bar sits at the top and the tab content scrolls independently.
- **Right: Chat.** A fixed-width assistant column: `{spacing.chat-width}` up to 1439px viewport width, `{spacing.chat-width-wide}` from 1440px. Its composer is pinned to the bottom, and the message list scrolls independently.

The target screen is a laptop at 1366×768. At 1280px and up, both columns are fully visible. Narrower layouts are not supported (scope reduction 2026-10-21).

There is no Advisor View or Research Console UI; advisor and research functions are used through Swagger UI (scope reduction 2026-10-21).

## Elevation & Depth

The interface is flat. Hierarchy comes from tone (white cards on ice, ice on white) and 1px `{colors.outline-variant}` borders, not shadows.

- **Level 0:** cards, chat messages, chips.
- **Level 1 (M3):** the sticky tab bar and the chat composer while content scrolls under them.
- **Level 3 (M3):** dialogs (L2 confirmation, Mandate creation, "Why?" provenance) and menus.

## Shapes

The shapes follow Atruvia's language: **pill-shaped buttons and chips** (`{rounded.full}`), **generously rounded cards** (`{rounded.lg}`, 20px), and **small radii** for dense elements: `{rounded.md}` (8px) for chat messages, callouts and inputs, `{rounded.sm}` (4px) for figure chips. Circles are reserved for avatars, step dots in the waiting card and the status icon in notifications, echoing the circle motifs on atruvia.de.

## Components

These M3 components keep their M3 visuals and pick up only the theme tokens: `mat-tab-group`, `mat-form-field`, `mat-select`, `mat-slider`, `mat-dialog`, `mat-stepper`, `mat-snack-bar`, `mat-tooltip`, `mat-menu`, `mat-progress-bar`, `mat-list`, `mat-expansion-panel`, `mat-datepicker`.

Brand-layer and product-specific components:

- **Primary button.** `{components.button-primary}`: blue pill, white label, 44px high. There is at most one per card or dialog. Its label names the exact action, e.g. "Confirm transfer of 200.00 EUR".
- **Secondary button.** `{components.button-secondary}`: 2px blue outline pill, as on atruvia.de. Used for the alternative action ("Change values", "Not now").
- **App header.** Navy bar. From left to right: the Atruvia wordmark (Decided 2026-10-21: text wordmark unless logo use is cleared), a divider, the product name "Fine", the simulated date chip "Today: 15 Oct 2026" (research profile only), the logged-in name, and Log out.
- **Overview card.** White, `{rounded.lg}`, 1px outline. Its title is in `{typography.title}`. The main figure is in `{typography.figure-lg}` with a "Why?" link right next to it.
- **Figure chip.** An inline, clickable amount inside chat text. Ice background, `{rounded.sm}`, tabular figures, and a small link icon when the figure also exists in the Overview. Clicking it opens "Why?". Hovering or focusing it shows the matching Overview element's highlight.
- **Highlight ring.** `{components.highlight-ring}`: a 3px cyan outline around the matching Overview element. A steady outline, with no pulsing colour.
- **Agent message.** White card on the ice chat background, 8px radius. Every agent message is a *response card* (see below).
- **Response card anatomy (all outcomes).** From top to bottom:
  1. the outcome label row (icon and plain words in `{typography.label}`, `{colors.on-surface-variant}`);
  2. the answer body;
  3. optional blocks (sources, Suitability Statement, chart, form);
  4. the footer: the check badge on the left, and "Why?" or "Show calculation" on the right.

  Outcome icons and labels:

  | Outcome | Icon (Material Symbols) | Label | Accent |
  |---|---|---|---|
  | `INFORM` | `menu_book` | Explanation | none |
  | `COMPUTE` | `calculate` | Calculated for you | none |
  | `RECOMMEND_CATEGORY` | `recommend` | Recommendation | light cyan left bar |
  | `ASK_PROFILE` | `assignment_ind` | A few questions first | none |
  | `REFUSE_OUT_OF_SCOPE` | `do_not_disturb_on` | Not part of this service | none |
  | `DETECT_TRAP` | `report` | Let's check the question | `{components.premise-callout}` |
  | `ESCALATE` | `support_agent` | Passed to a human advisor | navy left bar |
- **User message.** A navy bubble with white text, right-aligned, `{rounded.md}`.
- **Human-advisor message.** A light-cyan bubble with a 4px navy left bar. The header row shows the advisor avatar circle, "Kaya · Human advisor" and the `person` icon. It must never be confused with Fine's cards, so it never carries a check badge.
- **Check badge.** A pill in ice with a blue `verified` icon: "Checked · 8 rules passed". Clicking it opens "Why?" on the Check Chain section.
- **Waiting card.** An ice card in the agent's position. It has four steps as dots joined by a line: done steps are navy filled, the active step is a blue ring with an M3 indeterminate spinner inside, pending steps are grey. Each dot has a label in `{typography.body-sm}`.
- **Status chips.** These are the notification and action states (AD-10), always icon plus word:

  | State | Token | Icon | Word |
  |---|---|---|---|
  | `INFORMED` | `{components.status-chip-informed}` | `info` | For your information |
  | `RECOMMENDED` | `{components.status-chip-recommended}` | `lightbulb` | Recommendation |
  | `AWAITING_CONFIRMATION` | `{components.status-chip-awaiting}` | `pending_actions` | Needs your OK |
  | `EXECUTED` | `{components.status-chip-executed}` | `task_alt` | Done for you |
  | `UNDONE` | `{components.status-chip-undone}` | `undo` | Undone |
  | `ESCALATED` | `{components.status-chip-escalated}` | `support_agent` | With an advisor |
- **Risk callout.** A coral-tint box with a 4px coral left bar, navy text and the `warning` icon. It is used only for a shortfall or an emergency fund below its floor.
- **Range chart (ngx-echarts).** The p10–p90 band is a light-cyan area with thin blue edges, the median is a 2px navy line, and the axes are grey. The legend is spelled out ("Middle outcome", "1 in 10 chance it's lower", "1 in 10 chance it's higher"). A coral dashed line appears only for a shortfall threshold in the Cash-Flow Forecast. Chart typography uses `{typography.caption}` and `{typography.body-sm}`, never smaller.
- **Suggestion chip.** An M3 assist chip with a 1px `{colors.outline-variant}` outline, `{rounded.full}` and `{colors.primary}` label text, with a leading `chat` icon.
- **Notification item.** An M3 list item on white. The leading slot holds a 40px circle in the status chip's background colour with the state icon. Unread titles use `{typography.title}` bold, read titles regular weight. The expanded area sits on `{colors.surface-container-low}` with `{spacing.4}` padding.
- **Assessment card and parameter proposal card.** White cards inside the agent message, with `{rounded.md}` and a 1px outline. Rows are label (left, `{typography.body-sm}`) and value (right, `{typography.figure}`). The source tag ("From your account data", "From your question") is a small ice pill in `{typography.caption}`. The progress bar is M3 linear in `{colors.primary}`.
- **Mandate card.** A white card titled with the plain-language summary sentence, then a bounds list (label and figure pairs), then the status line ("Active since 3 Oct") and the Revoke text button in `{colors.error}`.

Visual reference for these components in context: [`mockups/workspace-lena.html`](mockups/workspace-lena.html). DESIGN.md wins on conflict.

## Do's and Don'ts

| Do | Don't |
|---|---|
| Use navy for all text and structure | Use pure black, or grey for body text |
| Keep coral for real money risk, always on a tint with an icon | Colour refusals, profile questions or escalations red |
| Pair every status with an icon and a word | Use colour as the only signal |
| Use pill buttons whose labels name the exact action and amount | Write generic labels like "OK", "Submit" or "Proceed" |
| Use tabular numerals for every figure; keep "Why?" next to every figure | Show a figure without a way to trace it |
| Show ranges as bands with a spelled-out legend | Show a single forecast line or a "you will have X" number |
| Use flat cards with tonal separation | Use heavy shadows, gradients or glassmorphism |
| Inherit M3 components untouched outside this list | Restyle M3 form fields, dialogs or tabs one by one |
| Use green only if M3 needs it for system feedback | Use green and red for gains and losses (Fine is not a trading app) |
