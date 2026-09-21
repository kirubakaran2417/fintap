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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    @Transactional
    public OndcOrder captureConfirm(JsonNode request, String rawBody) {
        JsonNode orderNode = request.path("message").path("order");
        String orderRef = text(orderNode.path("id"), "ONDC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        String transactionId = requiredText(request.path("context"), "transaction_id");
        OndcOrder order = orders.findByOrderRef(orderRef).or(() -> orders.findByTransactionId(transactionId))
                .orElseGet(OndcOrder::new);
        Merchant merchant = resolveMerchant(orderNode);
        order.setMerchant(merchant);
        order.setOrderRef(orderRef);
        order.setTransactionId(transactionId);
        order.setMessageId(requiredText(request.path("context"), "message_id"));
        order.setProviderId(text(orderNode.path("provider").path("id"), "merchant-" + merchant.getId()));
        order.setBuyerApp(text(request.path("context").path("bap_id"), "ONDC buyer"));
        order.setItemsSummary(summarizeItems(orderNode.path("items")));
        order.setAmount(orderAmount(orderNode));
        order.setStatus(OndcOrderStatus.ACCEPTED);
        if (order.getCreatedAt() == null) order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setRawRequest(rawBody);
        return orders.save(order);
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

    public String siteVerificationMeta() {
        return properties.getOndc().getSiteVerificationToken() == null
                ? "" : properties.getOndc().getSiteVerificationToken();
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
        orders.findByOrderRef(orderId).ifPresent(order -> {
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
        String authorization = signatures.authorizationHeader(ondc.getSubscriberId(), ondc.getUniqueKeyId(),
                ondc.getSigningPrivateKey(), json);
        String lastError = "ONDC callback failed";
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ResponseEntity<String> result = http.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authorization).body(json).retrieve()
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
        throw new IllegalArgumentException("ONDC order does not identify a FinTap merchant");
    }

    private BigDecimal selectedTotal(JsonNode items) {
        BigDecimal total = BigDecimal.ZERO;
        if (!items.isArray()) return total;
        for (JsonNode selected : items) {
            String id = selected.path("id").asText("");
            int count = Math.max(1, selected.path("quantity").path("count").asInt(1));
            if (!id.startsWith("sku-")) continue;
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
            builder.append(text(item.path("id"), "item"));
        }
        return builder.toString();
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
}
