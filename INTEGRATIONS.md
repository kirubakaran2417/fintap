# ONDC and Mastercard sandbox setup

The Flutter app calls only the Spring Boot API. ONDC signing keys, Mastercard credentials, and payment reconciliation stay in the backend.

## ONDC prototype BPP

Use a stable public HTTPS deployment and configure:

```text
PUBLIC_BASE_URL=https://your-public-host
ONDC_SUBSCRIBER_ID=your-subscriber-id
ONDC_SUBSCRIBER_URL=https://your-public-host/protocol/v1
ONDC_BPP_ID=your-subscriber-id
ONDC_BPP_URI=https://your-public-host/protocol/v1
ONDC_SIGNING_PRIVATE_KEY=...
ONDC_SIGNING_PUBLIC_KEY=...
ONDC_ENCRYPTION_PRIVATE_KEY=...
ONDC_ENCRYPTION_PUBLIC_KEY=...
ONDC_REGISTRY_ENCRYPTION_PUBLIC_KEY=...
ONDC_SITE_VERIFICATION_TOKEN=...
ONDC_VERIFY_INCOMING=true
```

`POST /api/integrations/ondc/keys` creates signing and encryption key pairs for initial setup. Store the private keys in a secret manager; the application does not persist newly generated private keys.

Incoming `search`, `select`, `init`, `confirm`, `status`, and `cancel` requests are validated and ACKed immediately. Their signed `on_*` callbacks run asynchronously. Transient callback failures are retried up to three times; 4xx responses fail immediately. The latest result appears in `GET /api/integrations/evidence` as `ondcCallback`.

For local protocol testing only, set `ONDC_VERIFY_INCOMING=false` and `ONDC_ALLOW_HTTP_CALLBACKS=true`. Never use those values on a public deployment.

## Mastercard MPGS

Obtain a test merchant from Mastercard Payment Gateway Services or an acquiring bank. A generic Mastercard Developers project does not provide MPGS merchant credentials.

```text
PUBLIC_BASE_URL=https://your-public-host
MC_GATEWAY_ENABLED=true
MC_GATEWAY_URL=https://test-gateway.mastercard.com
MC_MERCHANT_ID=...
MC_API_PASSWORD=...
MC_API_VERSION=100
MC_CHECKOUT_SCRIPT_URL=https://test-gateway.mastercard.com/static/checkout/checkout.min.js
```

Card collection creates an MPGS checkout session and returns a FinTap-hosted checkout URL. The return endpoint retrieves the order from MPGS before changing the local payment status. Flutter can also refresh it through `GET /api/payments/{id}/status?refresh=true`.

Failure rules:

- Explicit `provider: MASTERCARD` with missing credentials returns a persisted `FAILED` payment and a `failureReason`.
- `provider: AUTO` tries Mastercard first when configured, then Razorpay if configured; if neither is configured it uses the clearly labelled local simulation.
- A transient MPGS status lookup leaves the payment `PENDING` and records the refresh error instead of reporting a false failure or success.

## Tap on Phone (SoftPOS NFC)

FinTap includes a dual-tier Contactless Tap on Phone (SoftPOS) architecture:

1. **Integrated Visa & Mastercard SoftPOS (NFC)**:
   - **In-App SoftPOS Sheet**: When a merchant accepts card payments on the mobile app, FinTap displays an interactive Contactless NFC Tap Sheet prompting the customer to hold their Visa, Mastercard, or NFC phone near the device.
   - **Web NFC Hardware Scanning**: The checkout terminal (`/pay/mastercard/{orderId}`) utilizes the modern **Web NFC API** (`window.NDEFReader`). On NFC-enabled smartphones (such as Android running Chrome), it activates the phone's native NFC chip to detect live physical contactless cards and smartphones.
   - **Synthesized Audio & Haptics**: Plays the authentic dual-tone EMV terminal chime via Web Audio API (`880Hz` $\rightarrow$ `1760Hz`) and triggers haptic vibration.
   - **EMVCo Level 2 Telemetry**: Reads card scheme (`VISA qVSDC` / `Mastercard M/Chip`), Contactless AID (`A0000000031010` / `A0000000041010`), and verifies the ARQC cryptogram without requiring a PIN (below the ₹5,000 contactless limit).
   - **Endpoint**: `POST /api/payments/{id}/nfc-tap` with `{ "brand": "VISA" | "MASTERCARD", "panLast4": "..." }`.

2. **Certified Acquirer CPoC/MPoC SDK**:
   - The Flutter `TapOnPhoneAdapter` and backend device-payment endpoint (`POST /api/payments/{id}/mastercard/device`) are pre-wired for an acquirer-certified CPoC/MPoC Android SDK for production card reader certification. Raw PAN or CVV is never stored.

## Secrets

Runtime credential entry is disabled by default. Use environment variables or a secret manager. `ALLOW_RUNTIME_CREDENTIALS=true` exists only for isolated local demonstrations and must not be enabled in a shared or public environment.
