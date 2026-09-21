# FinTap End-to-End System Project Flow Specification

This specification breaks down the end-to-end operational flow of **FinTap**, detailing how data moves seamlessly from the **Flutter Client UI**, through the **Spring Boot REST Controllers & Middleware**, into the **Core Domain Logic & JPA Persistence**, and out to **External Protocol Engines (ONDC, Mastercard MPGS, Dunzo)**.

---

## 1. Architectural Layer Breakdown

FinTap is constructed using a strict 5-Tier Layered Architecture:

```
┌────────────────────────────────────────────────────────────────────────┐
│ 1. FRONTEND PRESENTATION LAYER (Flutter 3.x Mobile & Web)              │
│    Screens (Pay, ONDC, Khata, Insights) -> ApiClient HTTP Client        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP REST / Bearer JWT
┌───────────────────────────────────▼────────────────────────────────────┐
│ 2. CONTROLLER & GATEWAY LAYER (Spring Boot REST Controllers)           │
│    AuthAdvice (JWT Auth) -> CommerceController, PaymentController      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Injected Beans & DTO Objects
┌───────────────────────────────────▼────────────────────────────────────┐
│ 3. BUSINESS LOGIC & SERVICE LAYER (Spring @Service Beans)              │
│    CommerceService, PaymentService, OndcNetworkService, Mastercard     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Spring Data JPA Methods
┌───────────────────────────────────▼────────────────────────────────────┐
│ 4. PERSISTENCE & DATA LAYER (JPA Repositories & H2 Database)           │
│    MerchantRepository, OndcOrderRepository, PaymentRepository          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Network Protocols & REST
┌───────────────────────────────────▼────────────────────────────────────┐
│ 5. EXTERNAL PROTOCOL & LOGISTICS INTEGRATIONS                          │
│    ONDC Beckn Registry, Mastercard MPGS Gateway, Dunzo Delivery Fleet   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Comprehensive End-to-End Data Flow Sequences

### Flow A: ONDC Order Reception & Automated Dunzo Dispatch

```mermaid
sequenceDiagram
    autonumber
    actor BuyerApp as Buyer App (Paytm / PhonePe)
    participant OndcNet as ONDC Beckn Gateway
    participant OndcService as OndcNetworkService
    participant CommService as CommerceService
    participant OrderRepo as OndcOrderRepository
    participant AppUI as Flutter OndcScreen
    participant DunzoFleet as Dunzo Logistics API

    %% 1. Inbound Order
    BuyerApp->>OndcNet: 1. Customer places order on ONDC Buyer App
    OndcNet->>OndcService: 2. POST /protocol/v1/confirm (Beckn payload)
    OndcService->>OrderRepo: 3. Save new OndcOrder (status: NEW)
    OrderRepo-->>OndcService: Order Saved with ID #1042

    %% 2. App Real-time Refresh
    AppUI->>CommService: 4. Periodic Poll / Trigger GET /api/ondc/orders
    CommService->>OrderRepo: 5. findByMerchantOrderByCreatedAtDesc(merchant)
    OrderRepo-->>CommService: List<OndcOrder>
    CommService-->>AppUI: List<OndcOrderDto>
    AppUI->>AppUI: Render Order Card with Live 3-Min Pickup Timer

    %% 3. Dispatch Trigger
    alt User Taps "Dispatch with DUNZO"
        AppUI->>CommService: 6a. POST /api/ondc/orders/1042/status {status: "DUNZO_PICKUP"}
    else Timer Expires (00:00)
        AppUI->>CommService: 6b. Auto-Trigger Microtask updateOrder(1042, "DUNZO_PICKUP")
    end

    CommService->>CommService: 7. parseStatus("DUNZO_PICKUP") -> OndcOrderStatus.DUNZO_PICKUP
    CommService->>OrderRepo: 8. Update status & setUpdatedAt(Now)
    CommService->>DunzoFleet: 9. Dispatch Delivery Request to Dunzo Fleet
    DunzoFleet-->>CommService: Delivery Partner Assigned
    CommService-->>AppUI: Updated OndcOrderDto (Status: DUNZO_PICKUP)
    AppUI->>AppUI: Update Card Badge ("Dunzo Partner Assigned")
```

---

### Flow B: Mastercard Hosted Checkout (MPGS) Payment Processing

```mermaid
sequenceDiagram
    autonumber
    actor Merchant as Kirana Merchant
    participant PayScreen as Flutter PayScreen
    participant ApiClient as ApiClient (Dart)
    participant AuthAdvice as AuthAdvice (JWT Interceptor)
    participant PayCtrl as PaymentController
    participant PayService as PaymentService
    participant MCGateway as MastercardGatewayService
    participant MCModal as Hosted Checkout Gateway UI

    Merchant->>PayScreen: 1. Enter ₹500 & Select "Mastercard Hosted Checkout"
    PayScreen->>ApiClient: 2. acceptPayment(500.0, "CARD", provider: "MASTERCARD")
    ApiClient->>AuthAdvice: 3. POST /api/payments/accept (Bearer Token)
    AuthAdvice->>AuthAdvice: 4. Extract Merchant from Authorization Header
    AuthAdvice->>PayCtrl: 5. Pass resolved Merchant & AcceptPaymentRequest
    PayCtrl->>PayService: 6. accept(merchant, request)

    PayService->>MCGateway: 7. createCheckout(amount, orderRef)
    alt Live Mastercard Keys Configured
        MCGateway-->>PayService: MPGS Session ID & Checkout URL
    else Sandbox / Mock Mode
        MCGateway-->>PayService: Mock Session ID & /pay/mastercard/{id} URL
    end

    PayService-->>ApiClient: 8. Return PaymentDto (status: PENDING, checkoutUrl: ...)
    ApiClient-->>PayScreen: 9. Return Response Map
    PayScreen->>MCModal: 10. Launch WebView / Modal to checkoutUrl

    MCModal->>MCGateway: 11. Enter Test Card & Submit 3DS OTP
    MCGateway->>PayService: 12. Reconcile Payment Status (SUCCESS)
    PayService-->>PayScreen: 13. Payment Completed Alert
    PayScreen->>Merchant: 14. Voice Prompt ("₹500 received via Mastercard")
```

---

## 3. Data Transformation Pipeline

Data transitions across layers using strict Type Mappings:

```
[Flutter JSON Map]  <--->  [Spring DTO Record]  <--->  [JPA Entity Domain]  <--->  [SQL Database Row]
 {                          record OndcOrderDto(         @Entity                   ONDC_ORDERS Table
   "id": 1042,                Long id,                   public class OndcOrder {   ID BIGINT PRIMARY KEY,
   "orderRef": "ONDC-4418",   String orderRef,             private Long id;         ORDER_REF VARCHAR(64),
   "amount": "281.00",        BigDecimal amount,           private BigDecimal amt;  AMOUNT DECIMAL(10,2),
   "status": "ACCEPTED"       OndcOrderStatus status       private Enum status;     STATUS VARCHAR(32)
 }                          )                            }                         }
```

---

## 4. Key Security & Resilience Controls

1. **Authentication Token Lifecycle**:
   - Flutter stores token in `SharedPreferences`.
   - Included in HTTP headers: `Authorization: Bearer <jwt-token>`.
   - Injected into Spring Controllers seamlessly by [`AuthAdvice.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/web/AuthAdvice.java).

2. **Fault-Tolerant Status Parsing**:
   - `CommerceService.parseStatus()` normalizes raw string inputs (case-insensitive, whitespace-trimmed) to prevent `IllegalArgumentException` and 500 errors.

3. **Global Exception Containment**:
   - [`ApiExceptionHandler.java`](file:///e:/GIRI/Workspaces/FinTap/backend/src/main/java/com/fintap/digikadai/web/ApiExceptionHandler.java) intercepts `ResponseStatusException`, `IllegalArgumentException`, and uncaught errors, standardizing HTTP error payloads as `{ "error": "<clean_reason>" }`.
