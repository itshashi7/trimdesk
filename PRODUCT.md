# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Primary: the outlet cashier.** Standing at a busy grooming-salon counter on a 1366×768 laptop in Chrome. Job: look up the customer by phone, add services, assign barbers, take payment and print a receipt, using the keyboard alone, in under 20 seconds. Today they do this in Excel.
- **Audience for this build: the owner of a multi-outlet salon or grooming chain** watching a 2-minute demo video and trying a live link. Success is that they believe it could run billing and reporting across all their outlets.
- **Other roles:** outlet manager (outlet report, barbers, billing), franchise owner (reports for their outlets), HQ admin (all-outlet dashboard, royalty report, service menu). Manager/owner/HQ screens must work on a phone.

## Product Purpose

TrimDesk is a pitch prototype that shows three things: keyboard-fast billing at the counter; a customer's history following them to any outlet; and HQ seeing every outlet live with franchise royalties calculated automatically. It is a convincing demo built in a weekend, not production software.

## Positioning

One shared customer record across every outlet of a franchise chain, plus royalty calculation built into the billing data, replacing per-outlet Excel sheets.

## Operating Context

- Counter billing: phone lookup → customer card (last visits at any outlet, preferred barber, notes) → services via shortcut keys 1–9 → barber per item → discount → pay with F2/F3/F4 (cash/UPI/card) → 80mm thermal receipt and optional WhatsApp bill link.
- Payment is recorded only (no gateway). WhatsApp is a wa.me link, not the Business API.
- Money in Indian rupees with Indian digit grouping (₹1,23,456). Times shown in IST.
- Demo data: 4 franchises, 10 outlets (8 Chennai, Coimbatore, Madurai), 30 barbers, 16 services, ~15k customers, ~39k bills over 90 days.

## Capabilities and Constraints

- Stack (decided in docs/SPEC.md): React 18 + Vite + TypeScript, Tailwind CSS, React Router, TanStack Query, Recharts; Spring Boot API under `/api` with JWT auth.
- Roles are enforced by the API; the UI mirrors them. Demo login uses a role switcher with seeded users (password `demo123`).
- Tax rate is a placeholder (18%) until the client's accountant confirms it.
- Language: English only, UI and receipt.
- Out of scope: real payments, WhatsApp API, offline sync, inventory, appointments, memberships, GST e-invoicing.

## Brand Commitments

- Product name "TrimDesk" (renamed from the working name "GroomDesk"; internal code identifiers keep the old name).
- Generic prototype: no real chain's name, logo or brand assets. The login screen carries a small "Prototype · demo data only" caption.
- White-label: HQ can switch the accent colour (presets "Neutral" and "Brand", the client's own colour); text on the accent must meet WCAG 4.5:1.

## Evidence on Hand

- Seeded demo data only. No real customers, testimonials, outlet photos or client metrics exist; do not fabricate any.
- Real service rate card not yet provided (`backend/src/main/resources/seed/services.json` is a placeholder).

## Product Principles

1. The counter is the hero: every billing action has a key, and the cashier never needs the mouse.
2. The server decides money: prices, tax and commission come from the API; the UI only displays them.
3. Show cross-outlet history at the moment it matters, in the customer card at billing time.
4. Calm and credible over flashy: it must look like software a chain with 100+ outlets could run on.

## Accessibility & Inclusion

WCAG AA: 4.5:1 text contrast (including text on the accent), visible keyboard focus, labelled controls for screen readers.
