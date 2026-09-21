# FinTap (Digi Kadai) — Comprehensive Project Specification

**FinTap** (internally *Digi Kadai*) is an all-in-one digital operating system and fintech platform specifically engineered for Indian **Kirana stores, small retailers, and MSMEs**. 

It seamlessly bridges traditional offline shopkeeping (cash sales, paper Khata ledgers, local card taps) with modern digital commerce—allowing small merchants to sell on **ONDC (Open Network for Digital Commerce)**, accept payments via **Mastercard Hosted Checkout**, fulfill orders via **Dunzo logistics**, and access **pre-approved micro-loans**.

---

## 1. Executive Summary & Core Value Proposition

Small retail merchants in India face three major challenges:
1. **High Aggregator Commissions (20–30%)** on platforms like Swiggy/Zomato/Blinkit.
2. **Complex Digital Inventory Setup** that takes hours or days to catalogue.
3. **Fragmented Operations** across payment collection, customer credit ledgers, and logistics.

**FinTap solves this in a single mobile application**:
- **Instant Product Digitization**: AI barcode scanning catalogues products in under 5 seconds.
- **Zero Commission e-Commerce**: Acts as an **ONDC Seller Node (BPP)**, listing store inventory across all major buyer apps (**Paytm, PhonePe Pincode, Magicpin, Mystore**) without aggregator cuts.
- **Universal Payments**: Integrated with **Mastercard Hosted Checkout (MPGS)**, UPI, and Card Taps with real-time audio payment alerts.
- **Built-in Fulfillment**: Direct integration with **Dunzo delivery fleet** for automated last-mile fulfillment.
- **Credit & Financial Inclusion**: Integrated digital **Khata ledger** and a pre-approved **₹1,00,000 (₹1 Lakh) e-Mudra micro-loan**.

---

## 2. Platform Modules & Key Features

```
                                  ┌───────────────────────────┐
                                  │       FinTap Engine       │
                                  └─────────────┬─────────────┘
                                                │
         ┌───────────────────┬──────────────────┼───────────────────┬───────────────────┐
         │                   │                  │                   │                   │
  ┌──────▼──────┐     ┌──────▼──────┐    ┌──────▼──────┐     ┌──────▼──────┐     ┌──────▼──────┐
  │ ONDC Online │     │ Mastercard  │    │   Digital   │     │  Business   │     │   Dunzo     │
  │    Store    │     │ MPGS Gateway│    │    Khata    │     │  Insights   │     │ Fulfillment │
  └─────────────┘     └─────────────┘    └─────────────┘     └─────────────┘     └─────────────┘
```

### Module 1: ONDC Online Store (BPP / Seller NP Node)
- **Beckn Protocol v1.0.0 Compliance**: Fully implements the open protocol APIs (`/search`, `/select`, `/init`, `/confirm`, `/status`, `/cancel`).
- **1-Click Publishing**: Merchants toggle products live on ONDC. The engine automatically sets selling prices 6% below MRP to maximize customer conversion.
- **Silent & Live Order Receiver**: Ingests real-time orders from any ONDC buyer app.
- **Cryptographic Security & Diagnostics**:
  - Generates **Ed25519** signing keys and **X25519** encryption keys.
  - Built-in diagnostic ping to `mock.ondc.org` and network registry lookup via `preprod.registry.ondc.org`.

### Module 2: Universal Payment Acceptance & Mastercard MPGS
- **Mastercard Hosted Checkout (MPGS)**: Allows merchants to accept card payments via an authentic, interactive Mastercard Hosted Checkout experience featuring 3D-Secure OTP verification.
- **Smart Payment Router**: Dynamically routes transactions to the optimal rail (UPI, Mastercard MPGS, Local Card Terminal) based on transaction size and merchant settings.
- **Instant Voice Prompts**: Triggers clear audio alerts ("*₹500 received via Mastercard*") upon payment reconciliation.
- **Automated Customer CRM**: Automatically creates customer profiles and tracks visit counts and lifetime spend using anonymized card tokens.

### Module 3: Digital Khata & Customer Ledger
- **Udhar (Credit) / Jama (Debit) Tracking**: Digitizes paper notebooks into an auditable ledger.
- **WhatsApp Payment Reminders**: Sends direct payment links and account statements to customers with one tap.

### Module 4: Business Insights & e-Mudra Micro-Loans
- **Revenue Analytics**: Real-time tracking of daily sales, weekly trends, and outstanding credit balances.
- **Pre-Approved e-Mudra Loan**: Provides instant access to a pre-approved **₹1,00,000 (1 Lakh)** business loan at 11.5% p.a., powered by digital sales history.

### Module 5: Dunzo Logistics Fulfillment
- **Automated Pickup Timer**: Displays a 3-minute live countdown window upon order receipt.
- **Auto-Dispatch**: Automatically or manually triggers Dunzo delivery partner assignment and tracks status updates (`ASSIGNED_DUNZO`, `DUNZO_PICKUP`, `DISPATCHED`, `DELIVERED`).

---

## 3. Technology Stack & Framework Architecture

### Frontend (Mobile & Web App)
- **Framework**: Flutter 3.x / Dart 3.x (Cross-platform: Android, iOS, Web).
- **UI Architecture**: Material Design 3 with custom HSL dark theme (`FtColors`).
- **Client Networking**: `ApiClient` with asynchronous HTTP REST pipeline and persistent JWT authentication.

### Backend Core
- **Language & Runtime**: Java 17, Spring Boot 3.x.
- **Persistence Layer**: Spring Data JPA with an embedded H2 Relational Database.
- **Security**: JWT Bearer token authentication handled globally via [`AuthAdvice.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/web/AuthAdvice.java) controller advice.
- **Fault-Tolerant Exception Handling**: [`ApiExceptionHandler.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/web/ApiExceptionHandler.java) catches invalid inputs and returns standardized JSON error responses.

---

## 4. Summary of Key Files in Codebase

| Path | Purpose |
| :--- | :--- |
| **Backend Java** | |
| [`OndcNetworkService.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/integration/ondc/OndcNetworkService.java) | Beckn v1.0.0 protocol engine, keypair generator & registry lookup |
| [`CommerceService.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/service/CommerceService.java) | Catalogue management, status normalizer & order lifecycle engine |
| [`CommerceController.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/web/CommerceController.java) | REST endpoints for catalogue, ONDC orders, and Dunzo updates |
| [`PaymentService.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/service/PaymentService.java) | Payment routing, card tokenization, and customer CRM profiling |
| [`MastercardGatewayService.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/integration/mastercard/MastercardGatewayService.java) | Mastercard MPGS API client & Hosted Checkout Simulator |
| [`MastercardCheckoutController.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/web/MastercardCheckoutController.java) | Renders the interactive 3DS Mastercard Hosted Checkout UI |
| [`NudgeService.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/service/NudgeService.java) | WhatsApp customer nudge & template generator |
| [`NudgeController.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/web/NudgeController.java) | REST API `POST /api/nudge/whatsapp` |
| **Flutter Dart** | |
| [`whatsapp_service.dart`](file:///e:/GIRI/Workspaces/FinTap/mobile/lib/services/whatsapp_service.dart) | Deep-link launcher (`wa.me`) & phone number formatter |
| [`ondc_screen.dart`](file:///e:/GIRI/Workspaces/FinTap/mobile/lib/screens/ondc_screen.dart) | ONDC store management, barcode scanner, order cards & live timer |
| [`pay_screen.dart`](file:///e:/GIRI/Workspaces/FinTap/mobile/lib/screens/pay_screen.dart) | Payment collection interface, card tap, and Mastercard modal |
| [`insights_screen.dart`](file:///e:/GIRI/Workspaces/FinTap/mobile/lib/screens/insights_screen.dart) | Sales analytics & ₹1 Lakh e-Mudra loan application UI |
| [`api_client.dart`](file:///e:/GIRI/Workspaces/FinTap/mobile/lib/api/api_client.dart) | Frontend HTTP REST client with JWT header management |
