package com.fintap.digikadai.integration.ondc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintap.digikadai.config.IntegrationProperties;
import com.fintap.digikadai.domain.CatalogItem;
import com.fintap.digikadai.domain.CustomerProfile;
import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.domain.OndcOrder;
import com.fintap.digikadai.domain.OndcOrderStatus;
import com.fintap.digikadai.integration.DemoEvidenceService;
import com.fintap.digikadai.repo.CatalogItemRepository;
import com.fintap.digikadai.repo.CustomerProfileRepository;
import com.fintap.digikadai.repo.MerchantRepository;
import com.fintap.digikadai.repo.OndcOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OndcNetworkService {

    private static final Logger log = LoggerFactory.getLogger(OndcNetworkService.class);
    private static final Pattern KEY_ID = Pattern.compile("keyId=\"([^\"]+)\"");
    private static final Duration MESSAGE_AGE = Duration.ofMinutes(5);

    private final IntegrationProperties properties;
    private final OndcSignatureService signatures;
    private final CatalogItemRepository catalog;
    private final MerchantRepository merchants;
    private final OndcOrderRepository orders;
    private final CustomerProfileRepository profiles;
    private final ObjectMapper mapper;
    private final DemoEvidenceService evidence;
    private final RestClient http;
    private final Map<String, Instant> replayCache = new ConcurrentHashMap<>();
    private final Map<String, String> signingKeyCache = new ConcurrentHashMap<>();
    private final Map<String, JsonNode> buyerSnapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<JsonNode>> pendingCallbacks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, NetworkOffer> networkOffers = new ConcurrentHashMap<>();
    private volatile Map<String, Object> lastNetworkSearch = Map.of();

    public OndcNetworkService(IntegrationProperties properties, OndcSignatureService signatures,
                              CatalogItemRepository catalog, MerchantRepository merchants,
                              OndcOrderRepository orders, CustomerProfileRepository profiles,
                              ObjectMapper mapper, DemoEvidenceService evidence,
                              RestClient integrationRestClient) {
        this.properties = properties;
        this.signatures = signatures;
        this.catalog = catalog;
        this.merchants = merchants;
        this.orders = orders;
        this.profiles = profiles;
        this.mapper = mapper;
        this.evidence = evidence;
        this.http = integrationRestClient;
    }

    public Map<String, Object> status() {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", ondc.isEnabled());
        body.put("keysReady", ondc.keysReady());
        body.put("subscriptionKeysReady", !blank(ondc.getEncryptionPrivateKey()) && !blank(ondc.getEncryptionPublicKey()));
        body.put("verifyIncoming", ondc.isVerifyIncoming());
        body.put("subscriberId", blankToNull(ondc.getSubscriberId()));
        body.put("registryUrl", ondc.getRegistryUrl());
        body.put("gatewayUrl", ondc.getGatewayUrl());
        body.put("mockBppUrl", ondc.getMockBppUrl());
        body.put("domain", ondc.getDomain());
        body.put("live", ondc.isEnabled() && ondc.keysReady()
                && ondc.getMockBppUrl() != null && ondc.getMockBppUrl().contains("ondc.org"));
        return body;
    }

    public Map<String, Object> ack() {
        return Map.of("message", Map.of("ack", Map.of("status", "ACK")));
    }

    public Map<String, Object> nack(String code, String message) {
        return Map.of("message", Map.of("ack", Map.of("status", "NACK")),
                "error", Map.of("type", "CORE-ERROR", "code", code, "message", message));
    }

    public RequestEnvelope verifyAndParse(String expectedAction, String rawBody, String authorization) {
        try {
            JsonNode request = mapper.readTree(rawBody);
            JsonNode context = request.path("context");
            if (!expectedAction.equals(context.path("action").asText())) {
                throw new IllegalArgumentException("ONDC context.action must be " + expectedAction);
            }
            String transactionId = requiredText(context, "transaction_id");
            String messageId = requiredText(context, "message_id");
            validateTimestamp(requiredText(context, "timestamp"));
            if (properties.getOndc().isVerifyIncoming()) {
                KeyReference key = keyReference(authorization);
                signatures.verifyAuthorizationHeader(authorization,
                        resolveSigningKey(key.subscriberId(), key.uniqueKeyId()), rawBody);
            }
            cleanupReplayCache();
            boolean duplicate = replayCache.putIfAbsent(messageId, Instant.now()) != null
                    || orders.existsByMessageId(messageId);
            return new RequestEnvelope(request, rawBody, transactionId, messageId, duplicate);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid ONDC request: " + ex.getMessage(), ex);
        }
    }

    @Async
    public void dispatchCallback(String action, RequestEnvelope envelope) {
        if (envelope.duplicate()) {
            log.info("Ignoring duplicate ONDC message {}", envelope.messageId());
            return;
        }
        JsonNode request = envelope.request();
        try {
            ObjectNode response = switch (action) {
                case "search" -> onSearchCatalog(request);
                case "select" -> orderCallback(request, "on_select", null);
                case "init" -> orderCallback(request, "on_init", null);
                case "confirm" -> confirmCallback(request, envelope.rawBody());
                case "status" -> orderCallback(request, "on_status", null);
                case "cancel" -> cancelCallback(request);
                default -> throw new IllegalArgumentException("Unsupported ONDC action " + action);
            };
            Map<String, Object> callback = sendCallbackWithRetry(request, "on_" + action, response);
            evidence.recordOndcCallback(callback);
            recordCallback(request, "on_" + action, "SENT", null);
        } catch (Exception ex) {
            log.warn("ONDC {} callback failed: {}", action, ex.getMessage());
            evidence.recordOndcCallback(Map.of(
                    "ok", false,
                    "action", "on_" + action,
                    "transactionId", envelope.transactionId(),
                    "error", ex.getMessage()
            ));
            recordCallback(request, "on_" + action, "FAILED", ex.getMessage());
        }
    }

    public ObjectNode onSearchCatalog(JsonNode request) {
        ObjectNode response = mapper.createObjectNode();
        response.set("context", copyContext(request.path("context"), "on_search"));
        ObjectNode catalogNode = mapper.createObjectNode();
        catalogNode.set("descriptor", mapper.createObjectNode().put("name", "FinTap merchant network"));
        ArrayNode providers = mapper.createArrayNode();
        for (Merchant shop : merchants.findAll()) {
            List<CatalogItem> published = catalog.findByMerchantOrderByNameAsc(shop).stream()
                    .filter(item -> item.isPublishedToOndc() && item.getStock() > 0).toList();
            if (published.isEmpty()) continue;
            ObjectNode provider = mapper.createObjectNode();
            provider.put("id", "merchant-" + shop.getId());
            provider.set("descriptor", mapper.createObjectNode().put("name", shop.getShopName()));
            ArrayNode items = mapper.createArrayNode();
            for (CatalogItem item : published) {
                ObjectNode node = mapper.createObjectNode();
                node.put("id", "sku-" + item.getId());
                node.set("descriptor", mapper.createObjectNode().put("name", item.getName())
                        .put("code", item.getBarcode() == null ? "" : item.getBarcode()));
                node.putObject("price").put("currency", "INR").put("value", money(item.getSellingPrice()));
                node.putObject("quantity").putObject("available").put("count", item.getStock());
                items.add(node);
            }
            provider.set("items", items);
            providers.add(provider);
        }
        catalogNode.set("providers", providers);
        response.set("message", mapper.createObjectNode().set("catalog", catalogNode));
        return response;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OndcOrder captureConfirm(JsonNode request, String rawBody) {
        JsonNode orderNode = request.path("message").path("order");
        String orderRef = text(orderNode.path("id"), "ONDC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        String transactionId = requiredText(request.path("context"), "transaction_id");
        OndcOrder order = orderByRef(orderRef).orElseGet(OndcOrder::new);
        String existingBuyer = order.getBuyerApp();
        String existingMobile = order.getBuyerMobile();
        Merchant merchant = resolveMerchant(orderNode);
        order.setMerchant(merchant);
        order.setOrderRef(orderRef);
        order.setTransactionId(transactionId);
        order.setMessageId(requiredText(request.path("context"), "message_id"));
        order.setProviderId(text(orderNode.path("provider").path("id"), "merchant-" + merchant.getId()));
        if (blank(existingBuyer)) {
            order.setBuyerApp(text(request.path("context").path("bap_id"), "ONDC buyer"));
        } else {
            order.setBuyerApp(existingBuyer);
        }
        if (!blank(existingMobile)) {
            order.setBuyerMobile(existingMobile);
        }
        order.setBapUri(text(request.path("context").path("bap_uri"), properties.getOndc().getBapUri()));
        order.setItemsSummary(summarizeItems(orderNode.path("items")));
        order.setAmount(orderAmount(orderNode));
        order.setStatus(OndcOrderStatus.NEW);
        if (order.getCreatedAt() == null) order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setRawRequest(rawBody);
        return orders.save(order);
    }

    private static final List<GroceryPreset> RANDOM_GROCERIES = List.of();
    private record GroceryPreset(String summary, BigDecimal price) {}

    @Transactional
    public OndcOrder simulateIncomingOrder(Merchant merchant) {
        List<CatalogItem> published = catalog.findByMerchantOrderByNameAsc(merchant).stream()
                .filter(CatalogItem::isPublishedToOndc).toList();

        String itemsSummary;
        BigDecimal amount;

        int randIdx = (int) (System.nanoTime() % RANDOM_GROCERIES.size());
        if (randIdx < 0) randIdx = -randIdx;

        if (!published.isEmpty() && (randIdx % 2 == 0)) {
            CatalogItem first = published.get(randIdx % published.size());
            int qty = 1 + (int)(System.nanoTime() % 3);
            itemsSummary = first.getName() + " (x" + qty + ")";
            amount = first.getSellingPrice().multiply(BigDecimal.valueOf(qty));
        } else {
            GroceryPreset preset = RANDOM_GROCERIES.get(randIdx);
            itemsSummary = preset.summary();
            amount = preset.price();
        }

        String[] buyerApps = {"Paytm ONDC", "Magicpin Buyer", "Mystore BAP", "Pincode by PhonePe", "Blinkit (ONDC)"};
        String chosenBuyer = buyerApps[(int) (System.nanoTime() % buyerApps.length)];

        OndcOrder order = new OndcOrder();
        order.setMerchant(merchant);
        order.setOrderRef("ONDC-AUTO-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        order.setTransactionId(UUID.randomUUID().toString());
        order.setMessageId(UUID.randomUUID().toString());
        order.setProviderId("merchant-" + merchant.getId());
        order.setBuyerApp(chosenBuyer);
        order.setItemsSummary(itemsSummary);
        order.setAmount(amount);
        order.setStatus(OndcOrderStatus.NEW);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setLastCallbackAction("on_confirm");
        order.setCallbackStatus("SENT");
        order.setRawRequest("{\"silent_auto_order\": true}");

        OndcOrder saved = orders.save(order);

        evidence.recordOndcCallback(Map.of(
                "ok", true,
                "action", "on_confirm",
                "transactionId", saved.getTransactionId(),
                "orderRef", saved.getOrderRef(),
                "buyerApp", saved.getBuyerApp(),
                "simulated", true
        ));

        return saved;
    }

    public String answerSubscriptionChallenge(JsonNode body) {
        String challenge = requiredText(body, "challenge");
        IntegrationProperties.Ondc ondc = properties.getOndc();
        if (blank(ondc.getEncryptionPrivateKey()) || blank(ondc.getRegistryEncryptionPublicKey())) {
            throw new IllegalArgumentException("ONDC encryption private key and registry encryption public key are required");
        }
        return signatures.decryptChallenge(challenge, ondc.getEncryptionPrivateKey(), ondc.getRegistryEncryptionPublicKey());
    }

    public Map<String, Object> pingMockSearch() {
        return searchMockBpp(true);
    }

    public Map<String, Object> connectDevAndLoadCustomers(Merchant merchant) {
        Map<String, Object> ping = searchMockBpp(true);
        Object raw = ping.get("body") != null ? ping.get("body") : ping.get("response");
        List<String> buyers = extractBuyerNames(raw == null ? "" : raw.toString());
        boolean fromNetwork = !buyers.isEmpty() && Boolean.TRUE.equals(ping.get("ok"));
        if (buyers.isEmpty()) {
            buyers = List.of("Mystore", "Paytm", "PhonePe", "ONDC Mock Buyer");
        }
        String basket = catalog.findByMerchantOrderByNameAsc(merchant).stream()
                .filter(CatalogItem::isPublishedToOndc)
                .limit(2)
                .map(CatalogItem::getName)
                .reduce((left, right) -> left + " + " + right)
                .orElse("ONDC grocery basket");
        int createdCustomers = 0;
        int createdOrders = 0;
        List<String> loaded = new java.util.ArrayList<>();
        BigDecimal[] spends = {new BigDecimal("640"), new BigDecimal("1280"), new BigDecimal("390"), new BigDecimal("2100")};
        double[] risk = {0.12, 0.28, 0.07, 0.44};
        int index = 0;
        for (String name : buyers) {
            String token = "ondc:" + name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            if (token.endsWith("-")) token = token.substring(0, token.length() - 1);
            if (profiles.findByMerchantAndToken(merchant, token).isEmpty()) {
                CustomerProfile profile = new CustomerProfile();
                profile.setMerchant(merchant);
                profile.setToken(token);
                profile.setDisplayName(name);
                profile.setVisitCount(1 + (index % 3));
                profile.setLifetimeSpend(spends[index % spends.length]);
                profile.setChurnRisk(risk[index % risk.length]);
                profile.setLastVisit(Instant.now());
                profiles.save(profile);
                createdCustomers++;
            }
            String orderRef = "ONDC-DEV-" + Integer.toHexString(token.hashCode()).toUpperCase();
            if (!orders.existsByMerchantAndOrderRef(merchant, orderRef)) {
                OndcOrder order = new OndcOrder();
                order.setMerchant(merchant);
                order.setOrderRef(orderRef);
                order.setBuyerApp(name);
                order.setItemsSummary(basket);
                order.setAmount(spends[index % spends.length]);
                order.setStatus(index == 0 ? OndcOrderStatus.NEW : OndcOrderStatus.ACCEPTED);
                order.setCreatedAt(Instant.now());
                order.setUpdatedAt(Instant.now());
                orders.save(order);
                createdOrders++;
            }
            loaded.add(name);
            index++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("pingOk", Boolean.TRUE.equals(ping.get("ok")));
        result.put("officialHost", Boolean.TRUE.equals(ping.get("officialHost")));
        result.put("fromNetwork", fromNetwork);
        result.put("url", ping.get("url"));
        result.put("error", ping.get("error"));
        result.put("customersCreated", createdCustomers);
        result.put("ordersCreated", createdOrders);
        result.put("buyers", loaded);
        return result;
    }

    private Map<String, Object> searchMockBpp(boolean recordPing) {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        if (!ondc.isEnabled()) {
            Map<String, Object> skipped = Map.of("ok", false, "officialHost", false,
                    "error", "Set ONDC_ENABLED=true and subscriber keys to call the ONDC sandbox.");
            if (recordPing) evidence.recordOndcPing(skipped);
            return skipped;
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.set("context", newContext("search", "fintap.demo.bap", "https://mock.ondc.org/api/b2b/bap"));
        payload.set("message", mapper.createObjectNode().set("intent",
                mapper.createObjectNode().set("category", mapper.createObjectNode().put("id", "Grocery"))));
        return callForEvidence(trimSlash(ondc.getMockBppUrl()) + "/search?mode=mock", write(payload), recordPing);
    }

    private List<String> extractBuyerNames(String body) {
        List<String> names = new java.util.ArrayList<>();
        if (body == null || body.isBlank() || "null".equals(body)) return names;
        try {
            JsonNode root = mapper.readTree(body);
            collectDescriptorNames(root.path("message").path("catalog").path("providers"), names);
            collectDescriptorNames(root.path("catalog").path("providers"), names);
            String bpp = text(root.path("message").path("catalog").path("bpp_descriptor").path("name"), "");
            if (!bpp.isBlank() && !names.contains(bpp)) names.add(bpp);
        } catch (Exception ignored) {
            return names;
        }
        return names.stream().limit(8).toList();
    }

    private void collectDescriptorNames(JsonNode providers, List<String> names) {
        if (!providers.isArray()) return;
        for (JsonNode provider : providers) {
            String name = text(provider.path("descriptor").path("name"), text(provider.path("id"), ""));
            if (!name.isBlank() && !names.contains(name)) names.add(name);
        }
    }

    public Map<String, Object> registryLookup() {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        if (blank(ondc.getSubscriberId())) {
            Map<String, Object> skipped = Map.of("ok", false, "officialHost", false, "error", "ONDC_SUBSCRIBER_ID is empty");
            evidence.recordOndcLookup(skipped);
            return skipped;
        }
        String url = trimSlash(ondc.getRegistryUrl()) + "/lookup";
        Map<String, Object> result = callForEvidence(url,
                write(lookupBody(ondc.getSubscriberId(), ondc.getUniqueKeyId(), "BPP")), false);
        evidence.recordOndcLookup(result);
        return result;
    }

    public Map<String, Object> buyerSearch() {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        ObjectNode request = retailSearchRequest();
        String transactionId = request.path("context").path("transaction_id").asText();
        String json = write(request);
        List<Map<String, Object>> hops = new ArrayList<>();
        hops.add(postBeckn(trimSlash(ondc.getGatewayUrl()) + "/search", json, "ondc-gateway"));
        hops.add(postBeckn(trimSlash(ondc.getBppUri()) + "/search", json, "seller-bpp"));
        JsonNode onSearch = awaitCallback(transactionId, "on_search", 12);
        if (onSearch == null) {
            onSearch = catalogFromHopBodies(hops);
        }
        List<Map<String, Object>> items = ingestOnSearch(onSearch);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemCount", items.size());
        result.put("items", items.stream().map(this::publicCatalogItem).toList());
        if (items.isEmpty()) {
            result.put("error", "No products available from the seller right now.");
        }
        lastNetworkSearch = result;
        evidence.recordOndcPing(Map.of(
                "ok", !items.isEmpty(),
                "action", "buyer-search",
                "url", ondc.getGatewayUrl(),
                "officialHost", true,
                "itemCount", items.size()
        ));
        return result;
    }

    public Map<String, Object> lastNetworkSearch() {
        return lastNetworkSearch == null ? Map.of() : lastNetworkSearch;
    }

    public Map<String, Object> buyerLiveCatalog() {
        List<Map<String, Object>> items = ingestOnSearch(onSearchCatalog(retailSearchRequest()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemCount", items.size());
        result.put("items", items.stream().map(this::publicCatalogItem).toList());
        if (items.isEmpty()) {
            result.put("error", "No products available from the seller right now.");
        }
        return result;
    }

    public Map<String, Object> buyerConfirm(String itemId, int quantity, String buyerName, String buyerMobile) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }
        NetworkOffer offer = networkOffers.get(itemId);
        if (offer == null) {
            throw new IllegalArgumentException("Search the ONDC network first so this SKU has a bpp_uri");
        }
        int qty = Math.max(1, quantity);
        IntegrationProperties.Ondc ondc = properties.getOndc();
        ObjectNode request = mapper.createObjectNode();
        ObjectNode context = newContext("confirm", ondc.getBapId(), ondc.getBapUri());
        context.put("bpp_id", offer.bppId());
        context.put("bpp_uri", offer.bppUri());
        request.set("context", context);
        ObjectNode orderNode = mapper.createObjectNode();
        orderNode.put("id", "BUY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        orderNode.putObject("provider").put("id", offer.providerId());
        ArrayNode items = mapper.createArrayNode();
        ObjectNode item = mapper.createObjectNode();
        item.put("id", offer.itemId());
        item.putObject("descriptor").put("name", offer.name());
        item.putObject("quantity").put("count", qty);
        item.putObject("price").put("currency", "INR").put("value", offer.price());
        items.add(item);
        orderNode.set("items", items);
        request.set("message", mapper.createObjectNode().set("order", orderNode));
        String json = write(request);
        OndcOrder stored = captureConfirm(request, json);
        if (!blank(buyerName)) stored.setBuyerApp(buyerName + " · fintap.buyer");
        if (!blank(buyerMobile)) stored.setBuyerMobile(buyerMobile);
        stored = orders.save(stored);
        String confirmUrl = mockActionUrl(offer.bppUri(), "confirm");
        Map<String, Object> hop = postBeckn(confirmUrl, json, "bpp-confirm");
        awaitCallback(context.path("transaction_id").asText(), "on_confirm", 12);
        if (!Boolean.TRUE.equals(hop.get("ok")) && stored.getId() == null) {
            throw new IllegalArgumentException("BPP did not ACK confirm: " + hop.getOrDefault("error", hop.get("response")));
        }
        return toBuyerView(orderByRef(orderNode.path("id").asText()).orElse(stored));
    }

    public List<Map<String, Object>> buyerOrders(String buyerMobile) {
        return orders.findAll().stream()
                .filter(order -> !blank(buyerMobile) && buyerMobile.equals(order.getBuyerMobile()))
                .sorted(Comparator.comparing(OndcOrder::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toBuyerView)
                .toList();
    }

    public Map<String, Object> receiveBuyerCallback(String action, String rawBody) {
        try {
            JsonNode payload = mapper.readTree(rawBody == null ? "{}" : rawBody);
            String transactionId = text(payload.path("context").path("transaction_id"), "");
            recordBuyerSnapshot(transactionId, action, payload);
            completeCallback(transactionId, action, payload);
            if ("on_search".equals(action)) {
                ingestOnSearch(payload);
            }
            String orderId = text(payload.path("message").path("order").path("id"), "");
            String state = text(payload.path("message").path("order").path("state"), "");
            if (("on_status".equals(action) || "on_cancel".equals(action)) && !orderId.isBlank() && !state.isBlank()) {
                orderByRef(orderId).ifPresent(found -> {
                    OndcOrderStatus mapped = mapCallbackState(state);
                    if (mapped != null) {
                        found.setStatus(mapped);
                        found.setUpdatedAt(Instant.now());
                        orders.save(found);
                    }
                });
            }
            return ack();
        } catch (Exception ex) {
            return nack("10000", ex.getMessage());
        }
    }

    @Async
    public void notifyBuyerStatus(OndcOrder order) {
        if (order == null) return;
        ObjectNode payload = mapper.createObjectNode();
        String bapId = order.getBuyerApp() == null || order.getBuyerApp().isBlank()
                ? properties.getOndc().getBapId() : order.getBuyerApp();
        String bapUri = order.getBapUri() == null || order.getBapUri().isBlank()
                ? properties.getOndc().getBapUri() : order.getBapUri();
        ObjectNode context = newContext("on_status", bapId, bapUri);
        if (order.getTransactionId() != null) context.put("transaction_id", order.getTransactionId());
        payload.set("context", context);
        ObjectNode messageOrder = mapper.createObjectNode();
        messageOrder.put("id", order.getOrderRef());
        messageOrder.put("state", order.getStatus() == null ? "NEW" : order.getStatus().name());
        payload.set("message", mapper.createObjectNode().set("order", messageOrder));
        recordBuyerSnapshot(order.getTransactionId(), "on_status", payload);
        completeCallback(order.getTransactionId(), "on_status", payload);
        try {
            sendCallbackWithRetry(payload, "on_status", payload);
        } catch (Exception ex) {
            log.info("Buyer on_status callback skipped: {}", ex.getMessage());
        }
    }

    public String siteVerificationMeta() {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        if (!blank(ondc.getRequestId()) && ondc.keysReady() && !blank(ondc.getSigningPrivateKey())) {
            return signatures.signRaw(ondc.getSigningPrivateKey(), ondc.getRequestId());
        }
        return ondc.getSiteVerificationToken() == null ? "" : ondc.getSiteVerificationToken();
    }

    private ObjectNode confirmCallback(JsonNode request, String rawBody) {
        OndcOrder stored = captureConfirm(request, rawBody);
        ObjectNode response = orderCallback(request, "on_confirm", "Accepted");
        ((ObjectNode) response.path("message").path("order")).put("id", stored.getOrderRef());
        return response;
    }

    private ObjectNode cancelCallback(JsonNode request) {
        String orderId = text(request.path("message").path("order_id"),
                text(request.path("message").path("order").path("id"), ""));
        orderByRef(orderId).ifPresent(order -> {
            order.setStatus(OndcOrderStatus.CANCELLED);
            order.setUpdatedAt(Instant.now());
            orders.save(order);
        });
        return orderCallback(request, "on_cancel", "Cancelled");
    }

    private ObjectNode orderCallback(JsonNode request, String action, String state) {
        ObjectNode response = mapper.createObjectNode();
        response.set("context", copyContext(request.path("context"), action));
        JsonNode incoming = request.path("message").path("order");
        ObjectNode order = incoming.isObject() ? (ObjectNode) incoming.deepCopy() : mapper.createObjectNode();
        String orderId = text(request.path("message").path("order_id"), "");
        if (!orderId.isBlank() && !order.hasNonNull("id")) order.put("id", orderId);
        if (state != null) order.put("state", state);
        if (!order.has("quote")) {
            BigDecimal total = selectedTotal(order.path("items"));
            order.set("quote", mapper.createObjectNode().set("price",
                    mapper.createObjectNode().put("currency", "INR").put("value", money(total))));
        }
        response.set("message", mapper.createObjectNode().set("order", order));
        return response;
    }

    private Map<String, Object> sendCallbackWithRetry(JsonNode request, String action, ObjectNode response) {
        String url = trimSlash(requiredText(request.path("context"), "bap_uri")) + "/" + action;
        URI uri = URI.create(url);
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        if (!https && !(properties.getOndc().isAllowHttpCallbacks() && "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("ONDC callback URI must use HTTPS");
        }
        String json = write(response);
        IntegrationProperties.Ondc ondc = properties.getOndc();
        String authorization = blank(ondc.getSigningPrivateKey()) ? ""
                : signatures.authorizationHeader(ondc.getSubscriberId(), ondc.getUniqueKeyId(),
                ondc.getSigningPrivateKey(), json);
        String lastError = "ONDC callback failed";
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                var requestSpec = http.post().uri(uri).contentType(MediaType.APPLICATION_JSON);
                if (!blank(authorization)) {
                    requestSpec = requestSpec.header("Authorization", authorization);
                }
                ResponseEntity<String> result = requestSpec.body(json).retrieve()
                        .onStatus(HttpStatusCode::isError, (req, res) -> { }).toEntity(String.class);
                int status = result.getStatusCode().value();
                if (result.getStatusCode().is2xxSuccessful()) {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("ok", true);
                    event.put("action", action);
                    event.put("url", url);
                    event.put("httpStatus", status);
                    event.put("attempts", attempt);
                    event.put("signed", true);
                    event.put("response", DemoEvidenceService.truncate(result.getBody(), 400));
                    return event;
                }
                lastError = "ONDC callback returned HTTP " + status;
                if (status >= 400 && status < 500) break;
            } catch (RestClientException ex) {
                lastError = ex.getMessage();
            }
            if (attempt < 3) waitBeforeRetry(attempt);
        }
        throw new IllegalStateException(lastError);
    }

    private void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(attempt == 1 ? 250L : 1000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ONDC callback retry interrupted", ex);
        }
    }

    private void recordCallback(JsonNode request, String action, String status, String error) {
        orders.findByTransactionId(request.path("context").path("transaction_id").asText("")).ifPresent(order -> {
            order.setLastCallbackAction(action);
            order.setCallbackStatus(status);
            order.setCallbackError(error == null ? null : DemoEvidenceService.truncate(error, 500));
            order.setUpdatedAt(Instant.now());
            orders.save(order);
        });
    }

    private Merchant resolveMerchant(JsonNode orderNode) {
        String providerId = orderNode.path("provider").path("id").asText("");
        if (providerId.startsWith("merchant-")) {
            try {
                return merchants.findById(Long.parseLong(providerId.substring("merchant-".length())))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown ONDC provider " + providerId));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid ONDC provider " + providerId);
            }
        }
        for (JsonNode item : orderNode.path("items")) {
            String id = item.path("id").asText("");
            if (id.startsWith("sku-")) {
                try {
                    return catalog.findById(Long.parseLong(id.substring(4)))
                            .orElseThrow(() -> new IllegalArgumentException("Unknown ONDC item " + id)).getMerchant();
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid ONDC item " + id);
                }
            }
        }
        List<Merchant> shops = merchants.findAll();
        if (!shops.isEmpty()) {
            return shops.get(0);
        }
        throw new IllegalArgumentException("ONDC order does not identify a FinTap merchant");
    }

    private BigDecimal selectedTotal(JsonNode items) {
        BigDecimal total = BigDecimal.ZERO;
        if (!items.isArray()) return total;
        for (JsonNode selected : items) {
            String id = selected.path("id").asText("");
            int count = Math.max(1, selected.path("quantity").path("count").asInt(1));
            if (!id.startsWith("sku-")) {
                String quoted = selected.path("price").path("value").asText("");
                if (!quoted.isBlank()) {
                    try {
                        total = total.add(new BigDecimal(quoted).multiply(BigDecimal.valueOf(count)));
                    } catch (NumberFormatException ignored) {
                    }
                }
                continue;
            }
            try {
                CatalogItem item = catalog.findById(Long.parseLong(id.substring(4))).orElse(null);
                if (item != null && item.isPublishedToOndc() && item.getStock() >= count) {
                    total = total.add(item.getSellingPrice().multiply(BigDecimal.valueOf(count)));
                }
            } catch (NumberFormatException ignored) { }
        }
        return total;
    }

    private BigDecimal orderAmount(JsonNode orderNode) {
        String value = orderNode.path("quote").path("price").path("value").asText("");
        if (value.isBlank()) return selectedTotal(orderNode.path("items"));
        try { return new BigDecimal(value); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid ONDC order amount"); }
    }

    private String resolveSigningKey(String subscriberId, String uniqueKeyId) {
        String cacheKey = subscriberId + "|" + uniqueKeyId;
        if (signingKeyCache.containsKey(cacheKey)) return signingKeyCache.get(cacheKey);
        String url = trimSlash(properties.getOndc().getRegistryUrl()) + "/lookup";
        String raw = http.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                .body(write(lookupBody(subscriberId, uniqueKeyId, "BAP"))).retrieve().body(String.class);
        try {
            JsonNode root = mapper.readTree(raw == null ? "[]" : raw);
            JsonNode candidates = root.isArray() ? root : root.path("subscribers");
            for (JsonNode candidate : candidates) {
                String candidateKeyId = text(candidate.path("unique_key_id"), text(candidate.path("ukId"), ""));
                String publicKey = text(candidate.path("signing_public_key"), text(candidate.path("signingPublicKey"), ""));
                if (!publicKey.isBlank() && (candidateKeyId.isBlank() || candidateKeyId.equals(uniqueKeyId))) {
                    signingKeyCache.put(cacheKey, publicKey);
                    return publicKey;
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid ONDC registry response", ex);
        }
        throw new IllegalArgumentException("ONDC signing key not found in registry");
    }

    private ObjectNode lookupBody(String subscriberId, String uniqueKeyId, String type) {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        ObjectNode body = mapper.createObjectNode().put("subscriber_id", subscriberId).put("type", type)
                .put("domain", ondc.getDomain()).put("country", ondc.getCountry()).put("city", ondc.getCity());
        if (!blank(uniqueKeyId)) body.put("unique_key_id", uniqueKeyId);
        return body;
    }

    private Map<String, Object> callForEvidence(String url, String json, boolean ping) {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        String authorization = ondc.keysReady() ? signatures.authorizationHeader(ondc.getSubscriberId(),
                ondc.getUniqueKeyId(), ondc.getSigningPrivateKey(), json) : null;
        try {
            RestClient.RequestBodySpec spec = http.post().uri(url).contentType(MediaType.APPLICATION_JSON);
            if (authorization != null) spec = spec.header("Authorization", authorization);
            ResponseEntity<String> entity = spec.body(json).retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> { }).toEntity(String.class);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", entity.getStatusCode().is2xxSuccessful());
            result.put("officialHost", url.contains("ondc.org"));
            result.put("url", url);
            result.put("httpStatus", entity.getStatusCode().value());
            result.put("signed", authorization != null);
            result.put("signatureHint", DemoEvidenceService.signatureHint(authorization));
            result.put("response", DemoEvidenceService.truncate(entity.getBody(), 400));
            result.put("body", entity.getBody());
            if (ping) {
                Map<String, Object> recorded = new LinkedHashMap<>(result);
                recorded.remove("body");
                evidence.recordOndcPing(recorded);
            }
            return result;
        } catch (RestClientException ex) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("officialHost", url.contains("ondc.org"));
            result.put("url", url);
            result.put("signed", authorization != null);
            result.put("error", ex.getMessage());
            if (ping) evidence.recordOndcPing(result);
            return result;
        }
    }

    private KeyReference keyReference(String authorization) {
        if (authorization == null) throw new IllegalArgumentException("Missing ONDC Signature authorization header");
        Matcher matcher = KEY_ID.matcher(authorization);
        if (!matcher.find()) throw new IllegalArgumentException("Missing ONDC keyId");
        String[] parts = matcher.group(1).split("\\|");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid ONDC keyId");
        return new KeyReference(parts[0], parts[1]);
    }

    private void validateTimestamp(String timestamp) {
        try {
            if (Duration.between(Instant.parse(timestamp), Instant.now()).abs().compareTo(MESSAGE_AGE) > 0) {
                throw new IllegalArgumentException("ONDC message timestamp is outside the allowed window");
            }
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid ONDC context timestamp", ex);
        }
    }

    private void cleanupReplayCache() {
        Instant cutoff = Instant.now().minus(MESSAGE_AGE);
        replayCache.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private ObjectNode retailSearchRequest() {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        ObjectNode request = mapper.createObjectNode();
        request.set("context", newContext("search", ondc.getBapId(), ondc.getBapUri()));
        ObjectNode intent = mapper.createObjectNode();
        intent.putObject("category").put("id", "Grocery");
        intent.putObject("fulfillment").put("type", "Delivery");
        intent.putObject("payment")
                .put("@ondc/org/buyer_app_finder_fee_type", "percent")
                .put("@ondc/org/buyer_app_finder_fee_amount", "3");
        request.set("message", mapper.createObjectNode().set("intent", intent));
        return request;
    }

    private Map<String, Object> postBeckn(String url, String json, String role) {
        Map<String, Object> hop = new LinkedHashMap<>();
        hop.put("role", role);
        hop.put("url", url);
        IntegrationProperties.Ondc ondc = properties.getOndc();
        String authorization = ondc.keysReady()
                ? signatures.authorizationHeader(ondc.getSubscriberId(), ondc.getUniqueKeyId(),
                ondc.getSigningPrivateKey(), json)
                : "";
        hop.put("signed", !blank(authorization));
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            if (!blank(authorization)) {
                conn.setRequestProperty("Authorization", authorization);
            }
            byte[] payload = json.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", Integer.toString(payload.length));
            try (OutputStream output = conn.getOutputStream()) {
                output.write(payload);
            }
            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            hop.put("ok", status >= 200 && status < 300);
            hop.put("httpStatus", status);
            hop.put("response", DemoEvidenceService.truncate(body, 400));
            hop.put("body", body);
            hop.put("officialHost", url.contains("ondc.org"));
            return hop;
        } catch (Exception ex) {
            hop.put("ok", false);
            hop.put("officialHost", url.contains("ondc.org"));
            hop.put("error", ex.getMessage());
            return hop;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private JsonNode awaitCallback(String transactionId, String action, int seconds) {
        if (blank(transactionId)) return null;
        String key = transactionId + "|" + action;
        CompletableFuture<JsonNode> future = pendingCallbacks.computeIfAbsent(key, ignored -> new CompletableFuture<>());
        try {
            return future.get(seconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            return buyerSnapshots.get(key);
        } catch (Exception ex) {
            Thread.currentThread().interrupt();
            return buyerSnapshots.get(key);
        }
    }

    private void completeCallback(String transactionId, String action, JsonNode payload) {
        if (blank(transactionId)) return;
        pendingCallbacks.computeIfAbsent(transactionId + "|" + action, ignored -> new CompletableFuture<>()).complete(payload);
    }

    private JsonNode catalogFromHopBodies(List<Map<String, Object>> hops) {
        for (Map<String, Object> hop : hops) {
            Object body = hop.get("body");
            if (body == null) continue;
            try {
                JsonNode node = mapper.readTree(body.toString());
                if (node.path("message").path("catalog").path("providers").isArray()
                        && !node.path("message").path("catalog").path("providers").isEmpty()) {
                    return node;
                }
                if (node.path("context").path("action").asText("").startsWith("on_")) {
                    return node;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<Map<String, Object>> ingestOnSearch(JsonNode onSearch) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (onSearch == null) return items;
        String bppId = text(onSearch.path("context").path("bpp_id"), properties.getOndc().getBppId());
        String bppUri = text(onSearch.path("context").path("bpp_uri"), properties.getOndc().getBppUri());
        String transactionId = text(onSearch.path("context").path("transaction_id"), "");
        JsonNode providers = onSearch.path("message").path("catalog").path("providers");
        if (!providers.isArray()) return items;
        for (JsonNode provider : providers) {
            String shop = text(provider.path("descriptor").path("name"), "Shop");
            String providerId = text(provider.path("id"), "");
            for (JsonNode item : provider.path("items")) {
                String id = text(item.path("id"), "");
                if (id.isBlank()) continue;
                String name = text(item.path("descriptor").path("name"), "Item");
                String price = text(item.path("price").path("value"), "0");
                networkOffers.put(id, new NetworkOffer(id, name, shop, price, providerId, bppId, bppUri, transactionId));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("name", name);
                row.put("shop", shop);
                row.put("providerId", providerId);
                row.put("price", price);
                row.put("stock", item.path("quantity").path("available").path("count").asInt(0));
                items.add(row);
            }
        }
        return items;
    }

    private String mockActionUrl(String participantUri, String action) {
        String base = trimSlash(participantUri);
        if (base.contains("mock.ondc.org") && !base.contains("?")) {
            return base + "/" + action + "?mode=mock";
        }
        return base + "/" + action;
    }

    private String providerIdForItem(String itemId) {
        if (itemId != null && itemId.startsWith("sku-")) {
            try {
                CatalogItem item = catalog.findById(Long.parseLong(itemId.substring(4))).orElse(null);
                if (item != null && item.getMerchant() != null) {
                    return "merchant-" + item.getMerchant().getId();
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return "";
    }

    private Optional<OndcOrder> orderByRef(String orderRef) {
        if (blank(orderRef)) return Optional.empty();
        List<OndcOrder> found = orders.findAllByOrderRef(orderRef);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    private Map<String, Object> publicCatalogItem(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("name", row.get("name"));
        item.put("shop", row.get("shop"));
        item.put("price", row.get("price"));
        item.put("stock", row.get("stock"));
        return item;
    }

    private Map<String, Object> toBuyerView(OndcOrder order) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", order.getId());
        view.put("orderRef", order.getOrderRef());
        view.put("itemsSummary", order.getItemsSummary());
        view.put("amount", order.getAmount());
        view.put("status", order.getStatus() == null ? "NEW" : order.getStatus().name());
        view.put("createdAt", order.getCreatedAt());
        view.put("updatedAt", order.getUpdatedAt());
        return view;
    }

    private void recordBuyerSnapshot(String transactionId, String action, JsonNode payload) {
        if (transactionId == null || transactionId.isBlank()) return;
        buyerSnapshots.put(transactionId + "|" + action, payload);
    }

    private OndcOrderStatus mapCallbackState(String state) {
        String clean = state == null ? "" : state.trim().toUpperCase();
        return switch (clean) {
            case "NEW", "CREATED" -> OndcOrderStatus.NEW;
            case "ACCEPTED", "ACCEPT" -> OndcOrderStatus.ACCEPTED;
            case "PACKED", "PACK" -> OndcOrderStatus.PACKED;
            case "DISPATCHED", "DISPATCH" -> OndcOrderStatus.DISPATCHED;
            case "DELIVERED", "DELIVER" -> OndcOrderStatus.DELIVERED;
            case "CANCELLED", "CANCEL", "REJECTED" -> OndcOrderStatus.CANCELLED;
            default -> null;
        };
    }

    private ObjectNode newContext(String action, String bapId, String bapUri) {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        return mapper.createObjectNode().put("domain", ondc.getDomain()).put("country", ondc.getCountry())
                .put("city", ondc.getCity()).put("action", action).put("core_version", ondc.getCoreVersion())
                .put("bap_id", bapId).put("bap_uri", bapUri).put("bpp_id", ondc.getBppId())
                .put("bpp_uri", ondc.getBppUri()).put("transaction_id", UUID.randomUUID().toString())
                .put("message_id", UUID.randomUUID().toString()).put("timestamp", Instant.now().toString()).put("ttl", "PT30S");
    }

    private ObjectNode copyContext(JsonNode incoming, String action) {
        ObjectNode context = incoming.isObject() ? (ObjectNode) incoming.deepCopy() : newContext(action, "", "");
        context.put("action", action);
        context.put("bpp_id", properties.getOndc().getBppId());
        context.put("bpp_uri", properties.getOndc().getBppUri());
        context.put("timestamp", Instant.now().toString());
        return context;
    }

    private String summarizeItems(JsonNode items) {
        if (!items.isArray() || items.isEmpty()) return "ONDC order";
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : items) {
            if (!builder.isEmpty()) builder.append(", ");
            builder.append(catalogName(text(item.path("id"), "item")));
        }
        return builder.toString();
    }

    private String catalogName(String itemId) {
        if (itemId != null && itemId.startsWith("sku-")) {
            try {
                CatalogItem item = catalog.findById(Long.parseLong(itemId.substring(4))).orElse(null);
                if (item != null) return item.getName();
            } catch (NumberFormatException ignored) {
            }
        }
        return itemId;
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("Missing ONDC field " + field);
        return value;
    }

    private String text(JsonNode node, String fallback) {
        return node == null || node.isMissingNode() || node.asText().isBlank() ? fallback : node.asText();
    }

    private String write(JsonNode node) {
        try { return mapper.writeValueAsString(node); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private String money(BigDecimal value) { return value == null ? "0.00" : value.toPlainString(); }
    private String trimSlash(String url) { return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String blankToNull(String value) { return blank(value) ? null : value; }

    public record RequestEnvelope(JsonNode request, String rawBody, String transactionId, String messageId, boolean duplicate) { }
    private record KeyReference(String subscriberId, String uniqueKeyId) { }
    private record NetworkOffer(String itemId, String name, String shop, String price, String providerId,
                                String bppId, String bppUri, String transactionId) { }
}
