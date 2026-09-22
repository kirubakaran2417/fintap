package com.fintap.digikadai.integration.ondc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintap.digikadai.config.IntegrationProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OndcBuyerService {

    private final OndcNetworkService ondc;
    private final IntegrationProperties properties;
    private final ObjectMapper mapper;
    private final List<Map<String, Object>> catalog = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> orders = new CopyOnWriteArrayList<>();

    public OndcBuyerService(OndcNetworkService ondc, IntegrationProperties properties, ObjectMapper mapper) {
        this.ondc = ondc;
        this.properties = properties;
        this.mapper = mapper;
    }

    public Map<String, Object> search(String query) {
        ObjectNode payload = ondc.buyerSearchPayload(query);
        Map<String, Object> network = ondc.searchGatewayThenLocal(true);
        ingestCatalog(ondc.onSearchCatalog(payload));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("fallback", network.getOrDefault("fallback", ""));
        body.put("officialHost", Boolean.TRUE.equals(network.get("officialHost")));
        body.put("url", network.get("url"));
        body.put("error", network.get("error"));
        body.put("items", catalog());
        return body;
    }

    public List<Map<String, Object>> catalog() {
        return List.copyOf(catalog);
    }

    public List<Map<String, Object>> orders() {
        return List.copyOf(orders);
    }

    public Map<String, Object> confirm(String itemId, Integer quantity) {
        Map<String, Object> item = catalog.stream()
                .filter(row -> itemId.equals(String.valueOf(row.get("id"))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item is not in the last ONDC catalog. Search first."));
        int count = quantity == null || quantity < 1 ? 1 : quantity;
        BigDecimal unit = new BigDecimal(String.valueOf(item.getOrDefault("price", "0")));
        BigDecimal amount = unit.multiply(BigDecimal.valueOf(count));
        IntegrationProperties.Ondc cfg = properties.getOndc();
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode context = mapper.createObjectNode()
                .put("domain", cfg.getDomain())
                .put("country", cfg.getCountry())
                .put("city", cfg.getCity())
                .put("action", "confirm")
                .put("core_version", cfg.getCoreVersion())
                .put("bap_id", cfg.getBapId())
                .put("bap_uri", cfg.getBapUri())
                .put("bpp_id", cfg.getBppId())
                .put("bpp_uri", cfg.getBppUri())
                .put("transaction_id", UUID.randomUUID().toString())
                .put("message_id", UUID.randomUUID().toString())
                .put("timestamp", Instant.now().toString())
                .put("ttl", "PT30S");
        payload.set("context", context);
        ObjectNode order = mapper.createObjectNode();
        order.put("id", "BUY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.putObject("provider").put("id", String.valueOf(item.get("providerId")));
        ObjectNode selected = mapper.createObjectNode().put("id", itemId);
        selected.putObject("quantity").put("count", count);
        order.putArray("items").add(selected);
        order.putObject("quote").putObject("price").put("currency", "INR").put("value", amount.toPlainString());
        payload.set("message", mapper.createObjectNode().set("order", order));
        Map<String, Object> posted = ondc.postToBpp("confirm", payload);
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("id", order.path("id").asText());
        saved.put("itemId", itemId);
        saved.put("name", item.get("name"));
        saved.put("providerName", item.get("providerName"));
        saved.put("amount", amount);
        saved.put("quantity", count);
        saved.put("status", "NEW");
        saved.put("createdAt", Instant.now().toString());
        saved.put("bppAck", Boolean.TRUE.equals(posted.get("ok")));
        orders.add(0, saved);
        return saved;
    }

    public void ingestOnSearch(JsonNode body) {
        ingestCatalog(body);
    }

    public void ingestOnStatus(JsonNode body) {
        String orderId = text(body.path("message").path("order").path("id"));
        String state = text(body.path("message").path("order").path("state"));
        if (orderId.isBlank() || state.isBlank()) return;
        for (Map<String, Object> order : orders) {
            if (orderId.equals(String.valueOf(order.get("id")))) {
                order.put("status", state);
            }
        }
    }

    private void ingestCatalog(JsonNode onSearch) {
        List<Map<String, Object>> next = new ArrayList<>();
        JsonNode catalogNode = onSearch.path("message").path("catalog");
        JsonNode providers = catalogNode.path("bpp/providers");
        if (!providers.isArray() || providers.isEmpty()) {
            providers = catalogNode.path("providers");
        }
        if (providers.isArray()) {
            for (JsonNode provider : providers) {
                String providerId = text(provider.path("id"));
                String providerName = text(provider.path("descriptor").path("name"));
                for (JsonNode item : provider.path("items")) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", text(item.path("id")));
                    row.put("name", text(item.path("descriptor").path("name")));
                    row.put("code", text(item.path("descriptor").path("code")));
                    row.put("price", text(item.path("price").path("value")));
                    row.put("currency", text(item.path("price").path("currency")));
                    row.put("stock", text(item.path("quantity").path("available").path("count")));
                    row.put("providerId", providerId);
                    row.put("providerName", providerName);
                    row.put("category", text(item.path("category_id")));
                    if (!String.valueOf(row.get("id")).isBlank()) next.add(row);
                }
            }
        }
        if (!next.isEmpty()) {
            catalog.clear();
            catalog.addAll(next);
        }
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }
}
