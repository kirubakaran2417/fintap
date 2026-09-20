# Digi Kadai (FinTap)

Bank white-label merchant app from the BFS Business AI Hackathon brief: **SoftPOS + ONDC + AI** on Flutter, with a Spring Boot API.

The screens follow the product wireframes: welcome, shop onboarding, merchant home, accept payment (card tap / UPI), ONDC store, digital khata, and vernacular AI insights.

## What this MVP includes

| Layer | What you can demo |
| --- | --- |
| Merchant app | Login, shop setup, today's revenue, customers, ONDC GMV, khata |
| SoftPOS / UPI | Amount keypad, AI rail nudge (card vs UPI), simulated tap / QR collect |
| ONDC | Catalogue from barcode, one-tap publish, buyer-app orders |
| Khata | Give credit and collect repayment |
| Intelligence | Hindi / Tamil / Telugu / English insights, tokenised card customer profiles |

Visa/Mastercard **Tap on Phone NFC** still needs the official CPoC/MPoC SDK on Android. This repo now talks to the **real sandbox HTTP APIs**: ONDC Beckn (mock/pre-prod) and Mastercard Gateway + Developers OAuth1. Without credentials the merchant app keeps the local demo path.

## ONDC + Mastercard sandbox

1. Generate signing keys (while logged in as the demo merchant):

```powershell
# after login, use the Bearer token
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/integrations/ondc/keys -Headers @{ Authorization = "Bearer TOKEN" }
```

2. Register as a seller NP on [ONDC pre-prod registry](https://preprod.registry.ondc.org/ondc/subscribe). Host this API on a public HTTPS URL. Point `subscriber_url` at `/protocol/v1`. Put the public key in `ondc-site-verification.html` (already served).

3. Create a [Mastercard Developers](https://developer.mastercard.com) project and/or MPGS test merchant from your acquirer. Put keys in environment variables (see `backend/env.example`).

4. Restart the API with:

```powershell
$env:ONDC_ENABLED="true"
$env:MC_GATEWAY_ENABLED="true"
# plus the keys from env.example
cd backend
mvn spring-boot:run
```

5. Check wiring:

- `GET /api/integrations/status`
- `POST /api/integrations/ondc/ping` — signed `/search` to `https://mock.ondc.org/api/b2b/bpp`
- Card collect in the app creates an MPGS `CREATE_CHECKOUT_SESSION` when gateway credentials are set

Beckn inbound endpoints (no merchant JWT; ONDC network calls these):

- `POST /protocol/v1/search|select|init|confirm|status|cancel`
- `POST /protocol/v1/on_subscribe`
- `GET /ondc-site-verification.html`

Mastercard:

- Card accept → MPGS checkout session + `checkoutUrl`
- `GET /api/payments/mastercard/orders/{orderId}` retrieves the sandbox order
- NFC tap payload from the Tap on Phone SDK is accepted as `sourceOfFunds.provided.card.devicePayment` (not raw PAN)

## Demo login

- Mobile: `9876543210`
- PIN: `1234`
- Shop: Lakshmi Kirana

## Backend (Spring Boot 3.3, Java 21)

```powershell
cd backend
mvn spring-boot:run
```

API: `http://localhost:8080`  
H2 console: `http://localhost:8080/h2` (JDBC URL `jdbc:h2:mem:digikadai`)

Quick check:

```powershell
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"mobile\":\"9876543210\",\"pin\":\"1234\"}"
```

Then call `/api/home` with `Authorization: Bearer <token>`.

## Flutter app

Flutter SDK is not required to run the API. To run the merchant UI:

1. Install Flutter: https://docs.flutter.dev/get-started/install/windows
2. From `mobile/`:

```powershell
flutter create . --project-name digi_kadai --org com.fintap
flutter pub get
flutter run
```

`flutter create .` adds Android / iOS / Windows / Web runners without overwriting `lib/`.

Android emulator talks to the API at `http://10.0.2.2:8080`. Chrome / Windows use `http://localhost:8080`.

## API map

- `POST /api/auth/login`
- `GET /api/merchants/me`
- `POST /api/merchants/onboard`
- `GET /api/home`
- `GET /api/payments/routing?amount=`
- `POST /api/payments/accept`
- `GET /api/payments/mastercard/orders/{orderId}`
- `GET /api/integrations/status`
- `POST /api/integrations/ondc/keys|ping|lookup`
- `GET|POST /api/catalog`, `POST /api/catalog/generate`, `POST /api/catalog/{id}/publish`
- `GET /api/ondc/orders`, `POST /api/ondc/orders/{id}/status`
- `GET|POST /api/khata`
- `GET /api/insights?lang=hi|ta|te|en`
- `GET /api/customers`

## Next (pilot)

- Drop in Mastercard Tap on Phone Android SDK and POST the device payment blob
- Complete ONDC `/on_search` async callback to the BAP after ACK
- PCI-CPoC / MPoC with the partner bank
