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

Visa/Mastercard **Tap on Phone NFC** still needs the official CPoC/MPoC SDK on Android. Card collect can go live with **Razorpay test keys**. ONDC and Mastercard use official sandbox hosts when the network and credentials allow; otherwise the app stays on the local demo path.

## Run after clone (another computer)

Do this on a **new machine** after `git clone`. Two terminals: API first, then the Flutter app.

### What you need

| Tool | Why |
| --- | --- |
| Git | Clone the repo |
| **JDK 21** | Spring Boot API |
| **Maven 3.9+** | `mvn spring-boot:run` |
| **Flutter** (stable) | Merchant UI |
| Chrome | Easiest demo (`flutter run -d chrome`) |

Install Flutter from [docs.flutter.dev](https://docs.flutter.dev/get-started/install). Confirm:

```powershell
java -version
mvn -version
flutter doctor
```

`flutter doctor` can warn about Android Studio / Visual Studio. Those are optional if you only run **Chrome**.

### 1. Clone

```powershell
git clone https://github.com/kirubakaran2417/fintap.git
cd fintap
```

The GitHub repo is **private**. The other machine must be logged in to GitHub with access (or use a PAT / SSH key).

### 2. Start the API (terminal 1)

```powershell
cd backend
mvn spring-boot:run
```

Wait until the log says `Started DigiKadaiApplication`.

- API: http://localhost:8080  
- H2 console: http://localhost:8080/h2  
- JDBC URL: `jdbc:h2:file:./data/fintap` (user `sa`, empty password)

Leave this terminal running. First start creates `backend/data/` (local DB; not in git).

Quick check:

```powershell
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"mobile\":\"9876543210\",\"pin\":\"1234\"}"
```

### 3. Start the merchant app (terminal 2)

Always run from **`mobile/`**, not the repo root (there is no `pubspec.yaml` at the root).

```powershell
cd mobile
flutter pub get
flutter run -d chrome
```

| Device | API base the app uses |
| --- | --- |
| Chrome / Windows / macOS | `http://localhost:8080` |
| Android emulator | `http://10.0.2.2:8080` |

If web/Android folders are missing on a sparse clone:

```powershell
cd mobile
flutter create . --project-name digi_kadai --org com.fintap
flutter pub get
flutter run -d chrome
```

### 4. Use the app

1. Welcome → **Create merchant account** (your mobile + 4-digit PIN) **or** **I already have an account**.
2. Demo shop (seeded on first API start): mobile `9876543210`, PIN `1234`, shop Lakshmi Kirana.
3. Complete **store registration** if you created a new account.
4. **Dashboard** — today / week / month, 7-day bars, ONDC, khata.
5. **Pay** — amount keypad, Card or UPI. Card uses Razorpay checkout when test keys work; otherwise local SoftPOS.
6. **ONDC** — catalogue, publish, ping sandbox (needs reachability to `mock.ondc.org`).
7. **Khata** — give credit / collect.
8. **AI** — insights in EN / हिन्दी / தமிழ் / తెలుగు.
9. **Demo evidence** — checklist icon on Dashboard (or Network status card). Shows last ONDC / Razorpay / Mastercard calls.

Stop: `Ctrl+C` in each terminal. Flutter Chrome: press `q` in the Flutter terminal.

### 5. Optional: live Razorpay (card)

Do **not** commit keys. On the new PC:

1. Create test keys in [Razorpay Dashboard → API Keys](https://dashboard.razorpay.com/app/keys) (`rzp_test_...`).
2. In the app: Demo evidence → paste Key ID and Key Secret → **Connect Razorpay**.  
   Or create gitignored `backend/data/razorpay.env`:

```
RAZORPAY_KEY_ID=rzp_test_xxxxxxxx
RAZORPAY_KEY_SECRET=your_secret
```

3. Restart the API, then **Pay** → Card → open checkout. Use [Razorpay test cards](https://razorpay.com/docs/payments/payments/test-card-details/).

Mastercard MPGS still needs merchant ID + API password from Merchant Manager (see `backend/env.example`). ONDC mock ping needs outbound HTTPS to `mock.ondc.org`; a connect timeout means this network is blocking that host.

### Typical problems

| Symptom | Fix |
| --- | --- |
| `No pubspec.yaml file found` | `cd mobile` before `flutter run` |
| `flutter` not recognized | Add Flutter `bin` to PATH, open a new terminal |
| App cannot login / Network error | Start the API first; confirm http://localhost:8080 |
| Android login fails | Emulator uses `10.0.2.2:8080`; API must be running on the PC |
| Spring Boot fails to start | Use **Java 21** |
| Empty dashboard on a new account | Expected until you collect a payment; demo login has seed data |
| Port 8080 in use | Stop the other Java process, or change `server.port` in `backend/src/main/resources/application.yml` |

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
- `GET /api/integrations/evidence` — last ping URL, HTTP status, truncated signature, last MPGS session id
- `POST /api/integrations/ondc/ping` — signed `/search` to `https://mock.ondc.org/api/b2b/bpp`
- Card collect in the app creates an MPGS `CREATE_CHECKOUT_SESSION` when gateway credentials are set

### How to show Demo Evidence to judges

1. Start the API (`cd backend; mvn spring-boot:run`) and the app (`cd mobile; flutter run -d chrome`).
2. Login as `9876543210` / `1234`.
3. On Home, tap the checklist icon (or **Open demo evidence** on the dark Network status card).
4. Tap **Ping mock.ondc.org**. If keys are set, the URL host is `mock.ondc.org`, `signed` is true, and `signatureHint` is the first characters of the Beckn `Authorization` signature. Tap a field to copy it.
5. Go to **Pay**, enter ₹250+, accept **Card**. Return to Demo Evidence. With MPGS keys, `url` is on `test-gateway.mastercard.com` and `sessionId` is the gateway session. Without keys, the card says **Not live yet** — that is the honest local SoftPOS path.
6. Optional: keep DevTools Network open so they can also see the outbound HTTPS hosts.

Do not claim production ONDC NP status or Tap on Phone NFC unless those SDKs and certificates are actually in place.

Beckn inbound endpoints (no merchant JWT; ONDC network calls these):

- `POST /protocol/v1/search|select|init|confirm|status|cancel`
- `POST /protocol/v1/on_subscribe`
- `GET /ondc-site-verification.html`

Mastercard:

- Card accept → MPGS checkout session + `checkoutUrl`
- `GET /api/payments/mastercard/orders/{orderId}` retrieves the sandbox order
- NFC tap payload from the Tap on Phone SDK is accepted as `sourceOfFunds.provided.card.devicePayment` (not raw PAN)

## Demo login

See **Run after clone** above. Shortcut: `9876543210` / `1234` (Lakshmi Kirana).

## Backend and Flutter

Full start steps are in **Run after clone**. Summary: `cd backend` → `mvn spring-boot:run`, then `cd mobile` → `flutter pub get` → `flutter run -d chrome`.

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
