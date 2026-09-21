# FinTap Code Architecture & UML Class Flow Specification

This document provides a detailed software design specification of the **Code Architecture & Class Flow** for the **FinTap** platform. It documents the class relationships, domain models, Spring Boot service beans, JPA repository interfaces, DTO records, and Flutter UI/Client classes.

---

## 1. Class Structure Overview

```mermaid
classDiagram
    %% Domain Entities
    class Merchant {
        +Long id
        +String mobile
        +String shopName
        +String ownerName
        +String pinHash
        +String language
        +Instant createdAt
    }

    class OndcOrder {
        +Long id
        +Merchant merchant
        +String orderRef
        +String transactionId
        +String buyerApp
        +String itemsSummary
        +BigDecimal amount
        +OndcOrderStatus status
        +Instant createdAt
    }

    class CatalogItem {
        +Long id
        +Merchant merchant
        +String name
        +String barcode
        +String category
        +BigDecimal mrp
        +BigDecimal sellingPrice
        +int stock
        +boolean publishedToOndc
    }

    class Payment {
        +Long id
        +Merchant merchant
        +BigDecimal amount
        +PaymentRail rail
        +PaymentStatus status
        +String cardToken
        +String customerLabel
    }

    class OndcOrderStatus {
        <<enumeration>>
        NEW
        ACCEPTED
        PACKED
        ASSIGNED_DUNZO
        DUNZO_PICKUP
        DISPATCHED
        DELIVERED
        CANCELLED
    }

    %% Relationships
    Merchant "1" <-- "*" OndcOrder : owns
    Merchant "1" <-- "*" CatalogItem : catalog
    Merchant "1" <-- "*" Payment : receives
    OndcOrder --> OndcOrderStatus : status
```

---

## 2. Spring Boot Core Service & Controller Architecture

```mermaid
classDiagram
    %% Controllers
    class CommerceController {
        -CommerceService commerce
        +catalog(Merchant) List~CatalogItemDto~
        +add(Merchant, CreateRequest) CatalogItemDto
        +orders(Merchant) List~OndcOrderDto~
        +status(Merchant, Long, Map) OndcOrderDto
        +simulateOrder(Merchant) OndcOrderDto
    }

    class PaymentController {
        -PaymentService payments
        +accept(Merchant, AcceptPaymentRequest) PaymentDto
        +paymentStatus(Merchant, Long, boolean) PaymentDto
    }

    class MastercardCheckoutController {
        -PaymentService paymentService
        +checkoutPage(Long, Model) String
        +processCard(Long, Map) String
    }

    %% Services
    class CommerceService {
        -CatalogItemRepository catalog
        -OndcOrderRepository orders
        -OndcNetworkService ondcNetwork
        +updateOrder(Merchant, Long, String) OndcOrderDto
        +parseStatus(String) OndcOrderStatus
        +publish(Merchant, Long) CatalogItemDto
    }

    class OndcNetworkService {
        -OndcOrderRepository orders
        -CatalogItemRepository catalog
        +simulateIncomingOrder(Merchant) OndcOrder
        +lookupRegistry() Map
        +generateKeys() Map
    }

    class PaymentService {
        -PaymentRepository payments
        -MastercardGatewayService mastercard
        +accept(Merchant, AcceptPaymentRequest) PaymentDto
        +status(Merchant, Long, boolean) PaymentDto
    }

    class MastercardGatewayService {
        +createCheckout(BigDecimal, String) Map
        +gatewayReady() boolean
    }

    %% Dependencies
    CommerceController --> CommerceService
    PaymentController --> PaymentService
    MastercardCheckoutController --> PaymentService
    CommerceService --> OndcNetworkService
    PaymentService --> MastercardGatewayService
```

---

## 3. Data Access & Repository Interfaces

The persistence layer uses Spring Data JPA interfaces extending `JpaRepository`:

```java
public interface OndcOrderRepository extends JpaRepository<OndcOrder, Long> {
    List<OndcOrder> findByMerchantOrderByCreatedAtDesc(Merchant merchant);
    Optional<OndcOrder> findByOrderRef(String orderRef);
    Optional<OndcOrder> findByTransactionId(String transactionId);
}

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {
    List<CatalogItem> findByMerchantOrderByNameAsc(Merchant merchant);
    Optional<CatalogItem> findByMerchantAndBarcode(Merchant merchant, String barcode);
}

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByMerchantOrderByCreatedAtDesc(Merchant merchant);
}
```

---

## 4. Frontend Flutter Class Structure

The mobile app employs a clean modular component structure:

```mermaid
classDiagram
    class ApiClient {
        +String token
        +login(mobile, pin) Future~Map~
        +ondcOrders() Future~List~
        +updateOrder(id, status) Future~Map~
        +acceptPayment(amount, rail, provider) Future~Map~
        +generateOndcKeys() Future~Map~
    }

    class OndcScreen {
        -List orders
        -List catalog
        -Timer _autoRefresher
        -_load() Future~void~
        -_update(action) Future~void~
        -_orderCard(order) Widget
    }

    class PayScreen {
        -TextEditingController amount
        -CardPaymentProvider cardProvider
        -_collectPayment() Future~void~
        -_openMastercardCheckout(url) Future~void~
    }

    class FormatUtils {
        +NumberFormat inr
        +asNum(value) num
        +asInt(value) int
    }

    OndcScreen --> ApiClient : uses
    PayScreen --> ApiClient : uses
    OndcScreen ..> FormatUtils : formats currency & IDs
    PayScreen ..> FormatUtils : formats currency
```

---

## 5. Object Interaction & Execution Trace

When the user taps **"Dispatch with DUNZO"** in the Flutter app:

```
[OndcScreen UI]
   │
   ├─► calls api.updateOrder(orderId, 'DUNZO_PICKUP')
   │      │
   │      ▼
[ApiClient (Dart)]
   │
   ├─► HTTP POST /api/ondc/orders/{id}/status  {"status": "DUNZO_PICKUP"}
   │      │
   │      ▼
[AuthAdvice Interceptor (Java)]
   │
   ├─► Resolves Bearer token -> Merchant entity
   │      │
   │      ▼
[CommerceController.java]
   │
   ├─► status(merchant, id, body)
   │   extracts statusStr = "DUNZO_PICKUP"
   │      │
   │      ▼
[CommerceService.java]
   │
   ├─► updateOrder(merchant, id, statusStr)
   │   ├── parseStatus("DUNZO_PICKUP") -> OndcOrderStatus.DUNZO_PICKUP
   │   ├── orders.findById(id) -> loads OndcOrder
   │   ├── order.setStatus(OndcOrderStatus.DUNZO_PICKUP)
   │   └── orders.save(order) -> persists to Database
   │      │
   │      ▼
[OndcOrderDto Mapper]
   │
   └─► Returns JSON OndcOrderDto to Flutter UI -> Screen updates instantly!
```
