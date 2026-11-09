# SwiftDrop UI Design System — Brand & Interface Guidelines (Version 1.0)

A practical visual specification for the SwiftDrop parcel delivery platform: customer website, merchant portal, dispatcher/admin dashboard, and courier mobile application.

| Role | HEX |
|---|---|
| Primary | `#F97316` |
| Support | `#FBBF24` |
| Contrast | `#172033` |
| Canvas | `#F8FAFC` |

> **Design principle:** Use orange to make SwiftDrop recognizable and to draw attention to primary actions. Use navy for structure and contrast, white/light backgrounds for long sessions, and yellow only for small highlights. Courier screens must prioritize readability, large touch targets, and safe use when the rider is stopped.

Prepared as an implementation reference for the SwiftDrop React frontend and its role-based interfaces.

---

## 1. Brand foundations

SwiftDrop serves customers, merchants, dispatchers, administrators, and couriers. Keep one consistent brand across all experiences, while adapting the layout to each role and device.

### 1.1 Brand colour palette

| No. | Name | HEX | Use |
|---|---|---|---|
| 01 | Primary Orange | `#F97316` | Main CTA buttons, logo mark, active navigation, links, key actions |
| 02 | Deep Orange | `#EA580C` | Hover/pressed state for orange buttons; strong emphasis |
| 03 | Golden Yellow | `#FBBF24` | Small highlights, attention badges, promotional accents; not body text on white |
| 04 | Dark Navy | `#172033` | Desktop sidebar, header/footer dark areas, headings, navigation |
| 05 | Light Orange | `#FFF7ED` | Soft highlighted panels, selected cards, gentle hero backgrounds |
| 06 | Page Background | `#F8FAFC` | Main page and dashboard canvas |
| 07 | White | `#FFFFFF` | Cards, forms, modal surfaces, tables |
| 08 | Primary Text | `#0F172A` | Main headings and important content |
| 09 | Secondary Text | `#64748B` | Descriptions, helper text, timestamps, labels |
| 10 | Border | `#E2E8F0` | Input borders, card outlines, separators |
| 11 | Muted Surface | `#F1F5F9` | Table headers, disabled surfaces, neutral chips |

Suggested balance for typical desktop pages: approximately 60% light surfaces, 25% navy structure, 10% orange actions, and 5% yellow accents. This is a visual guide, not a strict measurement.

### 1.2 Status and feedback colours

| No. | Status | Text / icon | Soft background | Meaning |
|---|---|---|---|---|
| 12 | Created | `#64748B` | `#F1F5F9` | Neutral / awaiting processing |
| 13 | Picked Up | `#2563EB` | `#EFF6FF` | Blue / parcel collected |
| 14 | In Transit | `#7C3AED` | `#F5F3FF` | Purple / moving through network |
| 15 | Out for Delivery | `#EA580C` | `#FFF7ED` | Orange / courier is completing delivery |
| 16 | Delivered | `#16A34A` | `#F0FDF4` | Green / successful completion |
| 17 | Failed | `#DC2626` | `#FEF2F2` | Red / unsuccessful delivery |

Also reserve semantic colours for interface feedback: success `#16A34A`, warning `#D97706`, error `#DC2626`, and informational `#2563EB`. Do not use brand orange as the only indicator of success or failure.

---

## 2. Global components and interaction states

Use the same component behaviour in every role-based dashboard. A user should not need to relearn how buttons, menus, forms, or status labels work on different pages.

### 2.1 Header / top navigation

| Element | Specification |
|---|---|
| Header background | White `#FFFFFF` for customer pages; Navy `#172033` for dark/compact variants. |
| Logo | Orange symbol `#F97316` with navy wordmark `#172033` on white; white wordmark on navy. |
| Header height | Desktop 64–72 px; mobile 56–64 px. |
| Navigation text | Default `#334155`; active `#F97316` or a navy label with orange underline. |
| Hover effect | Text changes to `#EA580C`; optional soft orange surface `#FFF7ED`; transition 150–200 ms ease. |
| Active state | Orange underline (2–3 px) or orange left/bottom indicator; do not rely on colour alone. |
| Dropdown | White surface, 10–12 px radius, subtle shadow, border `#E2E8F0`; hovered item `#FFF7ED`. |
| Sticky header | Optional on customer site; add a subtle shadow after scroll. Keep it compact. |
| Mobile menu | Hamburger opens a full-height or anchored panel; large 44–48 px menu rows. |

### 2.2 Sidebar navigation (merchant/admin/dispatcher)

| Element | Specification |
|---|---|
| Sidebar background | Navy `#172033`. |
| Default menu item | Text/icon `#CBD5E1`; transparent background. |
| Hover | Background `#263449`; text/icon `#FFFFFF`; 150 ms transition. |
| Active item | Background `#F97316`; text/icon `#FFFFFF`; radius 8–10 px. |
| Section label | Muted `#94A3B8`, uppercase, 11–12 px, medium weight. |
| Width | Expanded 240–260 px; collapsed 72–80 px; mobile becomes a drawer. |
| Icons | Consistent line icons, 18–20 px; pair with labels on desktop. |

### 2.3 Buttons

| Variant | Style and states |
|---|---|
| Primary | Orange `#F97316`, white text. Hover `#EA580C`; pressed `#C2410C`; focus ring 3 px `rgba(249,115,22,.25)`. |
| Secondary | White or transparent surface, navy text, border `#CBD5E1`. Hover `#F1F5F9`. |
| Dark | Navy `#172033` with white text. Hover `#263449`. |
| Destructive | Red `#DC2626` with white text. Hover `#B91C1C`. Use only for delete/cancel-critical actions. |
| Disabled | Background `#E2E8F0`, text `#94A3B8`; no hover; cursor not-allowed. |
| Sizing | Primary desktop 44–48 px high; mobile key actions 48–56 px high; horizontal padding 16–24 px. |
| Shape | 8–12 px radius; use pill buttons only for chips or a deliberately rounded visual style. |
| Motion | 150–200 ms background/border transition; avoid bouncing or distracting animations. |

---

## 3. Forms, cards, tables, overlays

### 3.1 Inputs and forms

| Element | Specification |
|---|---|
| Label | Above field; `#334155`; 13–14 px; medium weight. Always provide a visible label, not placeholder-only. |
| Default input | White background, `#CBD5E1` border, `#0F172A` text, 10 px radius; 44–48 px high. |
| Hover | Border becomes `#94A3B8`. |
| Focus | Border `#F97316` plus 3 px soft orange focus ring; keep keyboard focus visible. |
| Placeholder | `#94A3B8`; use concise examples. |
| Error | Border `#DC2626`; error text `#B91C1C`; show a clear message below the field and an icon where useful. |
| Success | Optional green validation message; do not show green until validation succeeds. |
| Helper text | `#64748B`, 12–13 px; explain format or constraints. |
| Required fields | Use a visible asterisk and explain required fields near the form. |
| Spacing | 8 px label-to-input; 16–20 px between fields; group related fields. |

### 3.2 Cards and dashboard widgets

| Element | Specification |
|---|---|
| Card surface | White `#FFFFFF` on page background `#F8FAFC`. |
| Border | 1 px `#E2E8F0`; use subtle shadow only when the card is elevated or clickable. |
| Radius | 12–16 px; keep consistent across cards and panels. |
| Padding | 16–24 px desktop; 14–16 px mobile. |
| Hover (clickable cards) | Border `#FDBA74` or subtle shadow; `translateY(-1 to -2 px)` only if it does not cause layout shift. |
| Metric number | Navy `#172033`, 24–32 px bold; use orange for one key metric, not every number. |
| Metric label | Muted `#64748B`, 12–14 px. |
| Charts | Navy/blue/teal/green/orange series; use labels and legends, not colour alone. |

### 3.3 Tables and data lists

| Element | Specification |
|---|---|
| Header row | Background `#F1F5F9`; text `#475569`; 12–13 px semibold. |
| Body row | White; text `#334155`; 13–14 px. |
| Row hover | `#FFF7ED` or `#F8FAFC`; transition 120–150 ms. |
| Dividers | 1 px `#E2E8F0`. |
| Actions | Use compact text buttons or icon buttons with accessible labels; keep destructive actions separated. |
| Responsive | On narrow screens, allow horizontal scroll or convert rows into stacked cards; do not squash critical columns. |

### 3.4 Badges, alerts, modals, tooltips

| Component | Specification |
|---|---|
| Status badge | Use matching soft background + darker status text; pill radius; 11–12 px semibold. |
| Info alert | Blue `#EFF6FF` background, `#1D4ED8` text. |
| Success alert | Green `#F0FDF4` background, `#166534` text. |
| Warning alert | Yellow/amber `#FFFBEB` background, `#92400E` text. |
| Error alert | Red `#FEF2F2` background, `#991B1B` text. |
| Modal | White surface, 14–16 px radius, dark overlay `rgba(15,23,42,.55)`; clear title and close control. |
| Tooltip | Navy background, white text; small, brief; appear on hover/focus, not hover-only for essential info. |

---

## 4. Role-based screen guidance

The brand remains the same, but the information hierarchy changes according to each user's task.

### 4.1 Customer website

- Header: white background, orange logo, navy navigation; orange hover text and underline.
- Hero: white or light-orange background, large navy headline, orange emphasis, one primary orange CTA.
- Tracking form: white card, clear tracking-number field, orange Track button.
- Tracking timeline: use status colours 12–17; show labels, dates and times beside icons.
- Delivery history: white cards/table, readable dates, parcel reference, status badge and receipt download action.
- Mobile: stack sections, keep Track and Create Delivery actions prominent.

### 4.2 Merchant portal

- Desktop shell: navy sidebar, white top bar and `#F8FAFC` content canvas.
- Overview cards: total orders, in transit, delivered and failed; use neutral numbers with status accents.
- Orders table: status badge, order reference, parcel details, created date, ETA and action menu.
- Create order form: grouped sender/recipient, pickup/drop-off, parcel size/weight and quote summary.
- API keys and webhook settings: mask secrets by default; provide copy/rotate actions and clear last-delivery status.
- Use orange for Create Order and Save actions; use red only for revoke/delete.

### 4.3 Dispatcher / admin dashboard

- Use navy sidebar with orange active item; keep search, notifications and account menu in the top bar.
- Top metrics: total deliveries, unassigned parcels, active couriers, success rate.
- Map and assignment panel: distinguish courier markers and parcel pins with icons and labels, not colour alone.
- Courier workload: show active delivery counts and capacity indicators; use amber for nearing capacity.
- Reports: use restrained chart colours; orange may highlight the selected series or key trend.
- Administrative actions: confirmation modal for assigning, suspending, deleting or changing pricing.

### 4.4 Courier mobile application (outdoor-first)

- Use a light surface for sunlight readability; navy text and strong orange primary actions.
- Show today's delivery count and next assigned delivery near the top.
- Display address, recipient/contact action, parcel reference and delivery notes in large readable blocks.
- Primary action button: at least 48–56 px high; examples: Start Delivery, Picked Up, Arrived.
- Delivered action: green confirmation state; Failed action: red, followed by a required reason selection.
- Navigation action can use navy or blue; do not make the whole screen orange.
- Use large touch targets (ideally 48 × 48 px or larger), generous spacing and clear icon labels.
- Avoid tiny text, low-contrast yellow-on-white, dense tables and gestures that require precision.
- Do not encourage interaction while the vehicle is moving; the driver should stop safely before updating a delivery.

### 4.5 Physical brand application

For courier uniforms and delivery boxes, use orange as the main brand colour, navy for logo/text contrast, and certified reflective high-visibility material for road safety. The UI palette is not a substitute for road-safety standards or required protective equipment.

---

## 5. Hover, focus, pressed and motion specification

Define interaction states centrally so buttons and links behave consistently. Hover states are for mouse/trackpad users; keyboard focus and touch feedback must also be supported.

| Component | Default | Hover | Pressed | Focus / disabled |
|---|---|---|---|---|
| Primary button | `#F97316` / white | `#EA580C` | `#C2410C` | Focus: 3 px orange ring; Disabled `#E2E8F0` |
| Text link | `#2563EB` or `#EA580C` | Underline + darker tone | Active darker tone | Visible focus outline; visited state optional |
| Header link | `#334155` | `#EA580C` + underline | Active orange underline | Focus outline, no colour-only indication |
| Sidebar item | Transparent / `#CBD5E1` | `#263449` / white | `#334155` | Active `#F97316` / white |
| Secondary button | White / navy / border | `#F1F5F9` | `#E2E8F0` | Navy/orange focus ring |
| Input | White / `#CBD5E1` | Border `#94A3B8` | — | Orange border + soft focus ring |
| Clickable card | White / subtle border | Soft shadow or `#FFF7ED` border | Slightly lower elevation | Focus ring if keyboard actionable |
| Table row | White | `#FFF7ED` or `#F8FAFC` | — | Focus style when row is actionable |
| Icon button | Transparent / navy icon | Soft `#FFF7ED` or `#F1F5F9` | Slightly darker surface | Tooltip + accessible name |

### Motion rules

| Item | Rule |
|---|---|
| Standard transition | 150–200 ms ease for colour, border and shadow. |
| Dropdown/menu | 120–180 ms fade/slide; avoid large movement. |
| Modal | 160–220 ms fade; respect reduced-motion preference. |
| Loading | Use a subtle spinner or skeleton; do not flash orange rapidly. |
| Reduced motion | Respect `prefers-reduced-motion` and remove non-essential transitions. |
| No hover-only information | Essential actions, labels and help must remain available on touch and keyboard. |

### Accessibility and contrast

- Use dark navy or near-black text on white/light-orange surfaces. White text is suitable on primary orange for large/bold button labels, but verify small text contrast before release.
- Do not use golden yellow `#FBBF24` as small text on white. Pair it with navy/dark text or use it as a decorative background.
- Never communicate parcel state by colour alone: always include a text label and, where helpful, an icon.
- Provide keyboard focus indicators, descriptive form errors, accessible names for icon-only buttons and sufficient touch target sizes.
- Test the courier app outdoors in bright daylight and at the actual phone sizes used by drivers.

---

## 6. Typography, spacing and responsive layout

### Typography

| Type | Guideline |
|---|---|
| Recommended font | Inter. Alternative: Plus Jakarta Sans. Use a locally bundled font or a trusted font provider. |
| Page title | 28–36 px desktop; 24–30 px mobile; bold. |
| Section heading | 18–24 px; semibold/bold. |
| Body | 14–16 px; line height 1.45–1.6. |
| Labels | 12–14 px; medium/semibold. |
| Metadata | 12–13 px; muted colour, but remain readable. |
| Numbers / metrics | 24–32 px; bold; tabular numerals preferred. |

### Spacing and shape

| Item | Guideline |
|---|---|
| Base spacing scale | 4, 8, 12, 16, 20, 24, 32, 40, 48 px. |
| Content width | Use a readable max-width for customer pages; dashboards may use full available width. |
| Grid gap | 16–24 px desktop; 12–16 px mobile. |
| Card radius | 12–16 px. |
| Input/button radius | 8–12 px. |
| Page padding | 24–32 px desktop; 16 px mobile. |
| Sidebar width | 240–260 px expanded; 72–80 px collapsed. |

### Responsive breakpoints (suggested)

| Device | Width | Layout |
|---|---|---|
| Mobile | ≤ 639 px | Single column; sidebar becomes drawer; tables become cards or scroll. |
| Tablet | 640–1023 px | Two-column where useful; collapsible sidebar; simplified top navigation. |
| Desktop | ≥ 1024 px | Full dashboard layout; persistent sidebar; multi-column cards and tables. |

---

## 7. Suggested CSS tokens

Use shared tokens rather than hard-coding colours in every component. These CSS variables can be placed in the frontend's global stylesheet.

```css
:root {
  /* Brand */
  --sd-orange: #F97316;
  --sd-orange-hover: #EA580C;
  --sd-yellow: #FBBF24;
  --sd-navy: #172033;
  --sd-orange-soft: #FFF7ED;

  /* Surfaces and text */
  --sd-page: #F8FAFC;
  --sd-surface: #FFFFFF;
  --sd-text: #0F172A;
  --sd-text-muted: #64748B;
  --sd-border: #E2E8F0;
  --sd-muted-surface: #F1F5F9;

  /* Parcel states */
  --sd-created: #64748B;
  --sd-picked-up: #2563EB;
  --sd-in-transit: #7C3AED;
  --sd-out-for-delivery: #EA580C;
  --sd-delivered: #16A34A;
  --sd-failed: #DC2626;

  /* Interaction */
  --sd-focus-ring: rgba(249, 115, 22, 0.28);
  --sd-radius-card: 14px;
  --sd-radius-control: 10px;
  --sd-transition: 180ms ease;
}

.button-primary {
  background: var(--sd-orange);
  color: #fff;
  transition: background var(--sd-transition);
}

.button-primary:hover { background: var(--sd-orange-hover); }

.button-primary:focus-visible {
  outline: 3px solid var(--sd-focus-ring);
  outline-offset: 2px;
}
```

---

## 8. Final implementation checklist

- [ ] All four role interfaces use the same logo, brand colours, typography and status mapping.
- [ ] Header, sidebar, buttons, links, inputs, cards, tables, badges, modals and alerts have defined default/hover/focus/disabled states.
- [ ] Primary actions are orange; yellow is used sparingly and never for low-contrast small text.
- [ ] Every parcel status includes a text label and a consistent colour/icon.
- [ ] Courier screens are mobile-first, readable outdoors, and have large touch targets.
- [ ] Forms have visible labels, validation messages, required-field indicators and keyboard focus states.
- [ ] Responsive layouts are tested at mobile, tablet and desktop widths.
- [ ] Accessibility checks include contrast, keyboard navigation, focus visibility and reduced motion.
- [ ] Branding on uniforms/vehicles uses suitable reflective safety equipment; UI colours are not a safety substitute.

**SwiftDrop design direction:** energetic orange identity, dependable navy structure, clean light surfaces, and a high-visibility, task-focused courier experience.
