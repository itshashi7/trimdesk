---
version: 1
slug: "frontend-src-pages-billingpage-tsx"
primary_target: "frontend/src/pages/BillingPage.tsx"
related_targets: ["frontend/src/pages/LoginPage.tsx","frontend/src/components/Receipt.tsx"]
---

# Cashier billing surface (+ login, app shell, receipt)

Mode: Operate. Build path: code-led (Operate surface; no image generation).

Audience/job: outlet cashier at a busy counter, 1366×768 Chrome, keyboard only, bill in under 20 s. Demo audience: chain owner.

Constraints: docs/SPEC.md design rules are pinned (neutral base + one accent used only on primary buttons, active nav, chart lines, bill total; theme tokens in theme.css; system font stack; tabular numbers). Prices/tax/commission come from the API.

## Direction contract

THESIS: The counter is a keyboard instrument. Refuses the category-default POS of big touch tiles and a cart sidebar of equal weight; here the live bill is a calm receipt column and every action wears its key.

OWN-WORLD: White/graphite neutrals, hairline borders, soft 12px cards, one accent only on the total, primary buttons and active nav. Keycap badges (small bordered monospace-digit chips) on every actionable thing.

STORY: Cashier types a phone, sees the customer's cross-outlet history, taps digits to add services, pays with F-keys, prints. Owner sees speed and history.

FIRST VIEWPORT: Top bar (outlet, cashier, nav). Left 58%: phone field (autofocus) above customer card, then a 3-column service grid with keycaps. Right 42%: receipt column — items with barber select, discount, tax, then the total large in accent, then F2/F3/F4 pay keys. Shortcut legend pinned at bottom.

FORM: Pinned by brief (minimal Apple-like, Firecorp reference), position 1 of 1. seed: none (unavailable). Signature move: keycaps everywhere; pressing a key flashes its tile/control so keyboard use is visible on video.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
