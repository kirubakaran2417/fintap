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

Incoming `search`, `select`, `init`, `confirm`, `status`, and `cancel` are BPP actions. Incoming `on_search`, `on_select`, `on_init`, `on_confirm`, `on_status`, and `on_cancel` are BAP callbacks used by the FinTap buyer app.

Local development defaults (`ONDC_VERIFY_INCOMING=false`, `ONDC_ALLOW_HTTP_CALLBACKS=true`) post `/search` to `https://preprod.gateway.ondc.org/search` first, then fall back to `http://localhost:8080/protocol/v1/search` so published FinTap SKUs still appear in the buyer app. Never use those flags on a public deployment; set them to `true` / `false` after you have a public HTTPS subscriber URL.

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

## Tap on Phone

The Flutter `TapOnPhoneAdapter` and backend device-payment endpoint are ready for an acquirer-approved CPoC/MPoC Android SDK. Until that SDK supplies a tokenized `devicePayment` object, the method channel reports unavailable. Raw PAN or CVV must never be passed through Flutter or this API.

## Secrets

Runtime credential entry is disabled by default. Use environment variables or a secret manager. `ALLOW_RUNTIME_CREDENTIALS=true` exists only for isolated local demonstrations and must not be enabled in a shared or public environment.
