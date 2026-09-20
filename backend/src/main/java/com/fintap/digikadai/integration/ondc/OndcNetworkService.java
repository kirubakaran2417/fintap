package com.fintap.digikadai.integration.ondc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintap.digikadai.config.IntegrationProperties;
import com.fintap.digikadai.domain.CatalogItem;
import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.domain.OndcOrder;
import com.fintap.digikadai.domain.OndcOrderStatus;
import com.fintap.digikadai.repo.CatalogItemRepository;
import com.fintap.digikadai.repo.MerchantRepository;
import com.fintap.digikadai.repo.OndcOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OndcNetworkService {

    private static final Logger log = LoggerFactory.getLogger(OndcNetworkService.class);

    private final IntegrationProperties properties;
    private final OndcSignatureService signatures;
    private final CatalogItemRepository catalog;
    private final MerchantRepository merchants;
    private final OndcOrderRepository orders;
    private final ObjectMapper mapper;
    private final RestClient http = RestClient.create();

    public OndcNetworkService(
            IntegrationProperties properties,
            OndcSignatureService signatures,
            CatalogItemRepository catalog,
            MerchantRepository merchants,
            OndcOrderRepository orders,
            ObjectMapper mapper
    ) {
        this.properties = properties;
        this.signatures = signatures;
        this.catalog = catalog;
        this.merchants = merchants;
        this.orders = orders;
        this.mapper = mapper;
    }

    public Map<String, Object> status() {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", ondc.isEnabled());
        body.put("keysReady", ondc.keysReady());
        body.put("subscriberId", blankToNull(ondc.getSubscriberId()));
        body.put("registryUrl", ondc.getRegistryUrl());
        body.put("gatewayUrl", ondc.getGatewayUrl());
        body.put("mockBppUrl", ondc.getMockBppUrl());
        body.put("domain", ondc.getDomain());
        body.put("live", ondc.isEnabled() && ondc.keysReady());
        return body;
    }

    public Map<String, Object> ack() {
        return Map.of("message", Map.of("ack", Map.of("status", "ACK")));
    }

    public Map<String, Object> nack(String message) {
        return Map.of("message", Map.of("ack", Map.of("status", "NACK")), "error", Map.of("message", message));
    }

    public ObjectNode onSearchCatalog(JsonNode request) {
        ObjectNode response = mapper.createObjectNode();
        ObjectNode context = copyContext(request.path("context"), "on_search");
        response.set("context", context);
        ObjectNode catalogNode = mapper.createObjectNode();
        Merchant shop = merchants.findAll().stream().findFirst().orElse(null);
        catalogNode.set("bpp/descriptor", mapper.createObjectNode().put("name", shop == null ? "Digi Kadai" : shop.getShopName()));
        ArrayNode providers = mapper.createArrayNode();
        ObjectNode provider = mapper.createObjectNode();
        provider.put("id", shop == null ? "kirana-1" : "merchant-" + shop.getId());
        provider.set("descriptor", mapper.createObjectNode().put("name", shop == null ? "Kirana" : shop.getShopName()));
        ArrayNode items = mapper.createArrayNode();
        List<CatalogItem> published = shop == null ? List.of() : catalog.findByMerchantOrderByNameAsc(shop).stream()
                .filter(CatalogItem::isPublishedToOndc)
                .toList();
        for (CatalogItem item : published) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "sku-" + item.getId());
            node.set("descriptor", mapper.createObjectNode()
                    .put("name", item.getName())
                    .put("code", item.getBarcode() == null ? "" : item.getBarcode()));
            node.putObject("price")
                    .put("currency", "INR")
                    .put("value", item.getSellingPrice() == null ? "0" : item.getSellingPrice().toPlainString());
            items.add(node);
        }
        provider.set("items", items);
        providers.add(provider);
        catalogNode.set("bpp/providers", providers);
        response.set("message", mapper.createObjectNode().set("catalog", catalogNode));
        return response;
    }

    public OndcOrder captureConfirm(JsonNode request) {
        JsonNode orderNode = request.path("message").path("order");
        OndcOrder order = new OndcOrder();
        merchants.findAll().stream().findFirst().ifPresent(order::setMerchant);
        order.setOrderRef(text(orderNode.path("id"), "ONDC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()));
        order.setBuyerApp(text(request.path("context").path("bap_id"), "ONDC buyer"));
        order.setItemsSummary(summarizeItems(orderNode.path("items")));
        order.setAmount(new BigDecimal(text(orderNode.path("quote").path("price").path("value"), "0")));
        order.setStatus(OndcOrderStatus.NEW);
        order.setCreatedAt(Instant.now());
        return orders.save(order);
    }

    public Map<String, Object> pingMockSearch() {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        if (!ondc.isEnabled()) {
            return Map.of("ok", false, "error", "Set ONDC_ENABLED=true and subscriber keys to call the ONDC sandbox.");
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.set("context", newContext("search", "", ""));
        ObjectNode intent = mapper.createObjectNode();
        intent.set("category", mapper.createObjectNode().put("id", "Grocery"));
        payload.set("message", mapper.createObjectNode().set("intent", intent));
        String json = write(payload);
        String url = trimSlash(ondc.getMockBppUrl()) + "/search";
        try {
            RestClient.RequestBodySpec spec = http.post().uri(url).contentType(MediaType.APPLICATION_JSON);
            if (ondc.keysReady()) {
                spec = spec.header("Authorization", signatures.authorizationHeader(
                        ondc.getSubscriberId(), ondc.getUniqueKeyId(), ondc.getSigningPrivateKey(), json));
            }
            String response = spec.body(json).retrieve().body(String.class);
            return Map.of("ok", true, "url", url, "response", response == null ? "" : response);
        } catch (RestClientException ex) {
            log.warn("ONDC mock search failed: {}", ex.getMessage());
            return Map.of("ok", false, "url", url, "error", ex.getMessage());
        }
    }

    public Map<String, Object> registryLookup() {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        if (ondc.getSubscriberId() == null || ondc.getSubscriberId().isBlank()) {
            return Map.of("ok", false, "error", "ONDC_SUBSCRIBER_ID is empty");
        }
        ObjectNode body = mapper.createObjectNode()
                .put("subscriber_id", ondc.getSubscriberId())
                .put("type", "BPP")
                .put("domain", ondc.getDomain())
                .put("country", ondc.getCountry())
                .put("city", ondc.getCity());
        String json = write(body);
        String url = trimSlash(ondc.getRegistryUrl()) + "/lookup";
        try {
            RestClient.RequestBodySpec spec = http.post().uri(url).contentType(MediaType.APPLICATION_JSON);
            if (ondc.keysReady()) {
                spec = spec.header("Authorization", signatures.authorizationHeader(
                        ondc.getSubscriberId(), ondc.getUniqueKeyId(), ondc.getSigningPrivateKey(), json));
            }
            String response = spec.body(json).retrieve().body(String.class);
            return Map.of("ok", true, "url", url, "response", response == null ? "" : response);
        } catch (RestClientException ex) {
            log.warn("ONDC lookup failed: {}", ex.getMessage());
            return Map.of("ok", false, "url", url, "error", ex.getMessage());
        }
    }

    public String siteVerificationMeta() {
        return properties.getOndc().getSigningPublicKey() == null ? "" : properties.getOndc().getSigningPublicKey();
    }

    private ObjectNode newContext(String action, String bapId, String bapUri) {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        ObjectNode context = mapper.createObjectNode();
        context.put("domain", ondc.getDomain());
        context.put("country", ondc.getCountry());
        context.put("city", ondc.getCity());
        context.put("action", action);
        context.put("core_version", ondc.getCoreVersion());
        context.put("bap_id", bapId);
        context.put("bap_uri", bapUri);
        context.put("bpp_id", ondc.getBppId());
        context.put("bpp_uri", ondc.getBppUri());
        context.put("transaction_id", UUID.randomUUID().toString());
        context.put("message_id", UUID.randomUUID().toString());
        context.put("timestamp", Instant.now().toString());
        context.put("ttl", "PT30S");
        return context;
    }

    private ObjectNode copyContext(JsonNode incoming, String action) {
        ObjectNode context = incoming.isMissingNode() || incoming.isEmpty()
                ? newContext(action, "", "")
                : (ObjectNode) incoming.deepCopy();
        context.put("action", action);
        context.put("bpp_id", properties.getOndc().getBppId());
        context.put("bpp_uri", properties.getOndc().getBppUri());
        context.put("timestamp", Instant.now().toString());
        return context;
    }

    private String summarizeItems(JsonNode items) {
        if (!items.isArray() || items.isEmpty()) {
            return "ONDC order";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : items) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(text(item.path("id"), "item"));
        }
        return builder.toString();
    }

    private String text(JsonNode node, String fallback) {
        return node == null || node.isMissingNode() || node.asText().isBlank() ? fallback : node.asText();
    }

    private String write(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
