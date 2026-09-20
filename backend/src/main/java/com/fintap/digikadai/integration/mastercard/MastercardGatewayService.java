package com.fintap.digikadai.integration.mastercard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintap.digikadai.config.IntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MastercardGatewayService {

    private static final Logger log = LoggerFactory.getLogger(MastercardGatewayService.class);

    private final IntegrationProperties properties;
    private final ObjectMapper mapper;
    private final RestClient http = RestClient.create();

    public MastercardGatewayService(IntegrationProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    public Map<String, Object> status() {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gatewayEnabled", mc.isGatewayEnabled());
        body.put("gatewayReady", mc.gatewayReady());
        body.put("merchantId", blank(mc.getMerchantId()));
        body.put("gatewayBaseUrl", mc.getGatewayBaseUrl());
        body.put("currency", mc.getCurrency());
        body.put("note", "Tap on Phone NFC still needs the Mastercard CPoC/MPoC SDK on Android. This API creates MPGS sandbox sessions and accepts devicePayment payloads from that SDK.");
        return body;
    }

    public CheckoutSession createCheckout(BigDecimal amount) {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        String orderId = "DK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        if (!mc.gatewayReady()) {
            return new CheckoutSession(orderId, "SESSION-LOCAL", null, false, "Mastercard gateway credentials are not set; using local collect.");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("apiOperation", "CREATE_CHECKOUT_SESSION");
        body.putObject("order")
                .put("id", orderId)
                .put("amount", amount.toPlainString())
                .put("currency", mc.getCurrency());
        body.putObject("interaction")
                .put("operation", "PURCHASE")
                .put("returnUrl", "https://localhost/pay/return");
        String json = write(body);
        String url = merchantPath(mc) + "/session";
        try {
            String raw = http.post()
                    .uri(url)
                    .header("Authorization", basic(mc))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);
            JsonNode node = mapper.readTree(raw == null ? "{}" : raw);
            String sessionId = node.path("session").path("id").asText();
            String checkout = trimSlash(mc.getGatewayBaseUrl()) + "/checkout/pay/" + sessionId;
            return new CheckoutSession(orderId, sessionId, checkout, true, raw);
        } catch (Exception ex) {
            log.warn("MPGS checkout session failed: {}", ex.getMessage());
            throw new IllegalStateException("Mastercard gateway session failed: " + ex.getMessage());
        }
    }

    public Map<String, Object> retrieveOrder(String orderId) {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        if (!mc.gatewayReady()) {
            return Map.of("ok", false, "error", "Gateway not configured");
        }
        String url = merchantPath(mc) + "/order/" + orderId;
        try {
            String raw = http.get()
                    .uri(url)
                    .header("Authorization", basic(mc))
                    .retrieve()
                    .body(String.class);
            return Map.of("ok", true, "order", mapper.readTree(raw == null ? "{}" : raw));
        } catch (Exception ex) {
            return Map.of("ok", false, "error", ex.getMessage());
        }
    }

    public Map<String, Object> payWithDevicePayload(String orderId, String sessionId, JsonNode devicePayment) {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        if (!mc.gatewayReady()) {
            return Map.of("ok", false, "error", "Gateway not configured");
        }
        String txnId = "txn-" + UUID.randomUUID().toString().substring(0, 8);
        ObjectNode body = mapper.createObjectNode();
        body.put("apiOperation", "PAY");
        if (sessionId != null && !sessionId.isBlank()) {
            body.putObject("session").put("id", sessionId);
        }
        if (devicePayment != null && !devicePayment.isEmpty()) {
            body.putObject("sourceOfFunds").putObject("provided").putObject("card").set("devicePayment", devicePayment);
        }
        String url = merchantPath(mc) + "/order/" + orderId + "/transaction/" + txnId;
        try {
            String raw = http.put()
                    .uri(url)
                    .header("Authorization", basic(mc))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(write(body))
                    .retrieve()
                    .body(String.class);
            JsonNode node = mapper.readTree(raw == null ? "{}" : raw);
            return Map.of("ok", "SUCCESS".equals(node.path("result").asText()), "transaction", node);
        } catch (Exception ex) {
            return Map.of("ok", false, "error", ex.getMessage());
        }
    }

    private String merchantPath(IntegrationProperties.Mastercard mc) {
        return trimSlash(mc.getGatewayBaseUrl()) + "/api/rest/version/" + mc.getApiVersion() + "/merchant/" + mc.getMerchantId();
    }

    private String basic(IntegrationProperties.Mastercard mc) {
        String token = Base64.getEncoder().encodeToString(
                ("merchant." + mc.getMerchantId() + ":" + mc.getApiPassword()).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private String localCheckoutUrl(String orderId) {
        return "http://localhost:8080/pay/card/" + orderId;
    }

    private String write(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record CheckoutSession(String orderId, String sessionId, String checkoutUrl, boolean live, String raw) {
    }
}
