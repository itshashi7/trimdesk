# TrimDesk — project spec (demo for McKingstown pitch)

> Product name: **TrimDesk** (renamed from the working name GroomDesk; shown name lives in
> `frontend/src/lib/brand.ts`, internal identifiers such as `com.groomdesk` keep the old name). This is a **pitch prototype** for a 172-outlet
> men's grooming franchise chain. Goal: a convincing, fast, realistic demo built over one weekend.
> Not production software. Prefer speed and polish of the demo flow over completeness.

## 1. Goal

Show a chain owner, in a 2-minute video and a live link, that:
1. A cashier can finish a bill **with the keyboard alone in under 20 seconds** (vs Excel today).
2. A customer's history follows them across **any outlet**.
3. HQ sees **every outlet live** on one dashboard, and **franchise royalties calculate themselves**.

### Non-goals (phase 2, fake or skip in the demo)
- Real WhatsApp Business API → use a `https://wa.me/91XXXXXXXXXX?text=...` link
- Payment gateway → just record payment mode (CASH / UPI / CARD)
- Offline sync, inventory, appointments, memberships, GST e-invoicing, multi-currency
- Real auth hardening → simple login with seeded demo users

### Design & branding rules
- **Visual reference:** https://dribbble.com/shots/23736346-Firecorp-Finance-Dashboard — minimal,
  Apple-like, generous whitespace, soft cards, thin borders, restrained color.
- **Neutral base + one accent.** Everything is white/greys; the accent appears only on primary
  buttons, active nav item, chart lines and the bill total.
  Charts that compare categories (e.g. the UPI / cash / card split) use the validated series
  colours `--series-1..3` in theme.css, always next to a text label.
- **Theme tokens:** define all colors as CSS variables in `frontend/src/styles/theme.css`
  (`--accent`, `--accent-contrast`, `--bg`, `--surface`, `--border`, `--text`, `--text-muted`) and map
  them in `tailwind.config`. No hard-coded hex values in components.
- **White-label switch:** Settings page (HQ only) with an accent color picker + 2 presets:
  "Neutral" (graphite) and "McKingstown" (brand accent sampled from their site/signage — fill in
  `MCK_ACCENT` in theme.css). Persist per organization. Text on accent must meet WCAG 4.5:1.
- **Font:** system stack `-apple-system, BlinkMacSystemFont, "Segoe UI", Inter, sans-serif`
  (or self-host Inter). Do **not** bundle SF Pro. Use tabular numbers (`font-variant-numeric:
  tabular-nums`) for all amounts.
- Do **not** use the McKingstown logo or brand assets. Show "Prepared for McKingstown" as a small
  caption on the login screen only.

---

## 2. Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Spring Web, Spring Data JPA, Spring Security (JWT), Flyway, Validation |
| DB | PostgreSQL 16 (docker-compose for local) |
| Frontend | React 18 + Vite + TypeScript, Tailwind CSS, React Router, TanStack Query, Recharts |
| Deploy | Backend + Postgres on any cheap host; frontend as static build. One public demo URL. |

### Repo layout
```
/backend        Spring Boot app (package: com.groomdesk)
  /src/main/java/com/groomdesk/{config,auth,franchise,outlet,staff,service,customer,billing,report,seed}
  /src/main/resources/db/migration   Flyway SQL
/frontend       React app
  /src/{api,components,pages,hooks,lib}
docker-compose.yml   postgres only
```

### Conventions
- Money: `NUMERIC(12,2)` in DB, `BigDecimal` in Java, never `double`. Round HALF_UP to 2 decimals.
- Time: store `timestamptz` (UTC); display in `Asia/Kolkata`.
- Never expose JPA entities from controllers — use DTO records.
- Every query that returns outlet data must be scoped by the caller's role (see §4).
- Unit tests required for: bill total calculation, barber commission, franchise royalty.

---

## 3. Data model

```
franchise   id, name, owner_name, owner_phone, gstin, royalty_percent NUMERIC(5,2), created_at
outlet      id, franchise_id FK, code (e.g. "MNP"), name, area, city, tax_rate NUMERIC(5,2), active
app_user    id, username UNIQUE, password_hash, full_name,
            role ENUM(HQ_ADMIN, FRANCHISE_OWNER, OUTLET_MANAGER, CASHIER),
            franchise_id NULL, outlet_id NULL
barber      id, outlet_id FK, name, phone, commission_percent NUMERIC(5,2), active
service     id, code (shortcut key, e.g. "1"), name, category (HAIR/BEARD/FACIAL/COMBO/OTHER),
            price NUMERIC(12,2), duration_min, active          -- central menu set by HQ
customer    id, phone VARCHAR(10) UNIQUE, name, notes, preferred_barber_id NULL,
            first_visit_outlet_id, created_at                  -- shared across ALL outlets
bill        id, bill_no UNIQUE, outlet_id, customer_id, cashier_id,
            subtotal, discount, taxable_amount, tax_rate, tax_amount, total,
            payment_mode ENUM(CASH, UPI, CARD), created_at
bill_item   id, bill_id FK, service_id, barber_id, price, commission_amount
```

Indexes: `bill(outlet_id, created_at)`, `bill(customer_id)`, `bill_item(barber_id)`, `customer(phone)`.

### Business rules
- `subtotal = Σ bill_item.price`
- `discount` is a flat amount or % entered by cashier; cap at subtotal.
- **Menu prices include tax** (tax-inclusive pricing; tax is never added on top).
  `total = subtotal − discount` (what the customer pays);
  `taxable_amount = total × 100 / (100 + outlet.tax_rate)` (HALF_UP to 2 decimals);
  `tax_amount = total − taxable_amount`.
- Tax rate is **configurable per outlet**. Seed a placeholder; real GST rate to be confirmed with
  the client's accountant before any real use.
- `commission_amount = item.price × barber.commission_percent / 100` (on pre-discount price, demo rule).
- **Royalty** per franchise per month = `Σ taxable_amount of its outlets × franchise.royalty_percent / 100`.
- `bill_no` = `{OUTLET_CODE}-{YYYYMMDD}-{4-digit daily sequence}`, e.g. `MNP-20261121-0042`.
- Customer phone: exactly 10 digits, starts with 6–9. Looking up an unknown phone creates a new customer inline.

---

## 4. Roles & data access

| Role | Sees | Can do |
|---|---|---|
| HQ_ADMIN | All franchises & outlets | Dashboard, royalty report, edit service menu |
| FRANCHISE_OWNER | Only their franchise's outlets | Reports for those outlets |
| OUTLET_MANAGER | Only their outlet | Outlet report, barbers, billing |
| CASHIER | Only their outlet | Billing, customer lookup |

Enforce in the service layer (not just UI). Login screen has a **demo role switcher**: buttons
"Login as Cashier / Outlet Manager / Franchise Owner / HQ" using seeded users (password `demo123`).

---

## 5. REST API (prefix `/api`)

```
POST /auth/login                         → JWT + user profile
GET  /services                           → active menu
GET  /outlets/{id}/barbers
GET  /customers/lookup?phone=            → customer + last 5 visits (any outlet) or 404
POST /customers                          → create
GET  /customers/{id}                     → profile + full visit history across outlets
POST /bills                              → create bill (items, discount, paymentMode) → bill DTO
GET  /bills/{id}                         → for receipt print
GET  /reports/outlet/{id}/day?date=      → revenue, bill count, cash/UPI/card split, per-barber earnings
GET  /reports/hq/summary?from=&to=       → totals, outlet ranking, daily revenue trend, footfall
GET  /reports/royalty?month=YYYY-MM      → per franchise: outlets, revenue, royalty %, royalty due
```

---

## 6. Screens

### Cashier — desktop-first (design for 1366×768, Chrome)
**Billing page** — two panels, fully keyboard-driven:
- Left: phone input (auto-focused on load) → `Enter` → customer card (name, last visit, preferred
  barber, notes) or inline "new customer" name field. Below: service grid showing shortcut keys.
- Right: live bill — items with barber dropdown each (defaults to preferred barber), discount,
  tax, **big total**.

Shortcuts:
| Key | Action |
|---|---|
| `/` or `F1` | Focus phone search |
| `1`–`9` | Add service with that shortcut code |
| `B` | Cycle barber on last item |
| `D` | Focus discount |
| `Del` | Remove last item |
| `F2` / `F3` / `F4` | Pay CASH / UPI / CARD → saves bill |
| `Ctrl+P` | Print receipt |
| `W` | Open WhatsApp bill link |
| `Esc` | New bill (clear) |

Show a small shortcut legend at the bottom. Toast on save with bill number.

**Receipt** — print CSS for 80mm thermal paper: outlet name/area, bill no, date-time, items with
barber, subtotal, discount, tax, total, payment mode, "Thank you, visit again".

### Manager / Owner / HQ — responsive (must look good on a phone)
- **Customer profile:** contact, notes, preferred barber, visit timeline across outlets, total spend.
- **Outlet day report:** date picker, KPI cards (revenue, bills, avg bill value), cash/UPI/card
  split, barber table (services, revenue, commission).
- **HQ dashboard:** date range, KPI cards (total revenue, bills, active outlets), 90-day revenue
  line chart, outlet ranking table (sortable), top services bar chart.
- **Royalty report:** month picker, table per franchise → outlets, revenue, royalty %, royalty due,
  grand total. "Export CSV" button.

---

## 7. Seed data (critical — empty dashboards kill the demo)

Implement as a Spring `CommandLineRunner` under profile `seed` (idempotent: skip if data exists).
Use a fixed random seed so data is reproducible.

- **4 franchises**, royalty 6–10%
- **10 outlets** across Chennai areas + 2 Tamil Nadu towns, each with a 3-letter code
- **30 barbers** (3 per outlet), commission 30–45%
- **Service menu** from `backend/src/main/resources/seed/services.json` (placeholder ~15 items;
  will be replaced with the real rate card). Shortcut codes `1`–`9` on the most common ones.
- **~2,000 customers** with realistic Indian names and valid phone numbers; ~40% repeat visitors,
  some visiting 2+ outlets
- **90 days of bills**: 25–60 bills/outlet/day, weekends +40%, evening peak (5–9 pm),
  1–3 items per bill, ~10% bills discounted, payment mix ≈ UPI 65% / cash 30% / card 5%.
  Give outlets different performance levels so the ranking is interesting.
- **Users:** `hq`, `owner1`–`owner4`, `manager_<code>`, `cashier_<code>` — all `demo123`.

---

## 8. Build order (Friday + Saturday milestones)

**Friday morning (before work) — backend foundation**
1. docker-compose Postgres, Spring Boot skeleton, Flyway migrations for §3, entities + repos
2. Seed runner (§7) — verify row counts

**Friday evening (after work) — core APIs**
3. Auth + role scoping, `/services`, `/customers/lookup`, `POST /bills` with tests for §3 rules

**Saturday**
4. React shell, login with role switcher, **billing page + shortcuts + receipt print** (highest priority — polish it)
5. Customer profile + outlet day report
6. HQ dashboard + royalty report (+ CSV export)
7. UI polish: loading states, empty states, number formatting (₹ with Indian grouping: ₹1,23,456)
8. Test billing at 1366×768 and HQ dashboard on a phone
9. Deploy, smoke-test the public URL, record the 2-minute demo video

**If behind schedule, cut in this order:** CSV export → customer profile page (keep history in the
billing customer card) → outlet day report. Never cut: billing + receipt, HQ dashboard, royalty table.

## 9. Definition of done (demo)
- [ ] Keyboard-only bill in < 20 s, receipt prints correctly at 80mm
- [ ] Repeat customer autofills with history from another outlet
- [ ] HQ dashboard loads < 2 s with 90 days of data, charts populated
- [ ] Royalty report matches a manual calculation for one franchise
- [ ] Each role sees only its own data
- [ ] Public demo URL works in a fresh browser
