# Design system

AfyaClaim's look is derived from eight health and wellness case studies on Behance, chosen as
references for the back office and the POS app:

| Reference | What we took |
|---|---|
| Senso — skincare tracking | Lime accent on soft off-white, rounded day-chips, generous card radius |
| Rythea — heart monitoring platform | Dot-matrix numerals for headline metrics, ink-black data cards beside light ones |
| Elli — aging care, mood & meds | Calm lavender info chips, gentle hierarchy for non-technical users |
| Biorg — wellness app & site | Palette anchors `#BCC388` sage and `#43511C` moss, Inter typography |
| Fitness coach management | Dense-but-calm list rows for staff workflows |
| OTO — hearing dose monitor | Dot-matrix lime numbers on near-black for the "one number that matters" |
| PulseFit | Chartreuse + black brand pairing, rounded-cap iconography |
| Airofit | Pill controls and segmented toggles |

## Tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `--bg` | `#F3F4EF` | `#0B0C0A` | app background |
| `--surface` | `#FFFFFF` | `#16181A` | cards |
| `--ink` | `#0E0F0C` | `#F2F3EE` | text, hero cards, sidebar |
| `--lime` | `#C8F169` | `#C8F169` | primary action, active nav, hero numbers |
| `--moss` | `#43511C` | `#C8F169` | text on lime, success |
| `--sage` | `#BCC388` | `#43511C` | soft fills, low-risk meter |
| `--coral` | `#E5484D` | `#FF7A76` | flags, rejection |
| `--amber` | `#B7791F` | `#F2B33D` | under review, warnings |
| `--lavender` | `#E7E6F7` | `#2A2840` | submitted, info |

Type: **Inter** for UI, **JetBrains Mono** for tabular money, **Doto** (dot-matrix) for the one hero
number on a screen. Radii: 24 cards · 16 inner blocks · 14 inputs · full pills.

## Rules

- One hero number per screen, in Doto on an ink card. Everything else is Inter/Mono.
- Status is never colour-only: every pill has an icon and a label.
- Exactly one lime primary action per view.
- Touch targets ≥ 44px (web) / 48dp (Android). Motion 150–250 ms, respects reduced motion.
- No emoji as icons — Lucide on web, Material Symbols on Android.
