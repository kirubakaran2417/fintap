package com.fintap.digikadai.integration.mastercard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintap.digikadai.config.IntegrationProperties;
import com.fintap.digikadai.integration.DemoEvidenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final DemoEvidenceService evidence;
    private final RestClient http;

    public MastercardGatewayService(
            IntegrationProperties properties,
            ObjectMapper mapper,
            DemoEvidenceService evidence,
            RestClient integrationRestClient
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.evidence = evidence;
        this.http = integrationRestClient;
    }

    public Map<String, Object> status() {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gatewayEnabled", mc.isGatewayEnabled());
        body.put("gatewayReady", mc.gatewayReady());
        body.put("merchantId", blank(mc.getMerchantId()));
        body.put("gatewayBaseUrl", mc.getGatewayBaseUrl());
        body.put("currency", mc.getCurrency());
        body.put("checkoutScriptUrl", mc.getCheckoutScriptUrl());
        body.put("note", "Tap on Phone NFC still needs the Mastercard CPoC/MPoC SDK. MPGS sandbox sessions need merchant ID + API password from Merchant Manager or your acquirer.");
        return body;
    }

    public Map<String, Object> probeOfficialHost() {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        if (mc.gatewayReady()) {
            try {
                CheckoutSession session = createCheckout(new BigDecimal("1.00"));
                return Map.of(
                        "ok", session.live() && session.sessionId() != null && !session.sessionId().isBlank(),
                        "live", session.live(),
                        "sessionId", session.sessionId() == null ? "" : session.sessionId()
                );
            } catch (Exception ex) {
                return Map.of("ok", false, "error", ex.getMessage());
            }
        }
        String url = trimSlash(mc.getGatewayBaseUrl()) + "/api/rest/version/" + mc.getApiVersion() + "/merchant/TEST/session";
        try {
            ResponseEntity<String> entity = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(String.class);
            Map<String, Object> proof = new LinkedHashMap<>();
            proof.put("ok", false);
            proof.put("live", false);
            proof.put("officialHost", url.contains("mastercard.com"));
            proof.put("url", url);
            proof.put("httpStatus", entity.getStatusCode().value());
            proof.put("note", "Official Mastercard sandbox host responded. Paste merchant ID + API password to create a real session.");
            proof.put("response", DemoEvidenceService.truncate(entity.getBody(), 400));
            evidence.recordMastercard(proof);
            return proof;
        } catch (Exception ex) {
            Map<String, Object> proof = new LinkedHashMap<>();
            proof.put("ok", false);
            proof.put("live", false);
            proof.put("officialHost", url.contains("mastercard.com"));
            proof.put("url", url);
            proof.put("error", ex.getMessage());
            evidence.recordMastercard(proof);
            return proof;
        }
    }

    public CheckoutSession createCheckout(BigDecimal amount) {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        String orderId = "DK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        if (!mc.gatewayReady()) {
            String checkout = trimSlash(properties.getPublicBaseUrl()) + "/pay/mastercard/" + orderId;
            String sessionId = "SESSION-MOCK-MC-" + UUID.randomUUID().toString().substring(0, 8);
            evidence.recordMastercard(Map.of(
                    "ok", true,
                    "live", false,
                    "officialHost", false,
                    "orderId", orderId,
                    "sessionId", sessionId,
                    "checkoutUrl", checkout,
                    "note", "Mastercard MPGS credentials not set; using Mastercard interactive gateway simulator."
            ));
            return new CheckoutSession(orderId, sessionId, checkout, false, "Local Mastercard simulator session");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("apiOperation", "CREATE_CHECKOUT_SESSION");
        body.putObject("order")
                .put("id", orderId)
                .put("amount", amount.toPlainString())
                .put("currency", mc.getCurrency());
        String returnUrl = trimSlash(properties.getPublicBaseUrl()) + "/pay/mastercard/" + orderId + "/return";
        body.putObject("interaction")
                .put("operation", "PURCHASE")
                .put("returnUrl", returnUrl)
                .put("cancelUrl", trimSlash(properties.getPublicBaseUrl()) + "/pay/mastercard/" + orderId + "/cancel");
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
            String checkout = trimSlash(properties.getPublicBaseUrl()) + "/pay/mastercard/" + orderId;
            Map<String, Object> proof = new LinkedHashMap<>();
            proof.put("ok", !sessionId.isBlank());
            proof.put("live", true);
            proof.put("officialHost", url.contains("mastercard.com"));
            proof.put("url", url);
            proof.put("orderId", orderId);
            proof.put("sessionId", sessionId);
            proof.put("result", node.path("result").asText(null));
            proof.put("checkoutUrl", checkout);
            proof.put("response", DemoEvidenceService.truncate(raw, 400));
            evidence.recordMastercard(proof);
            return new CheckoutSession(orderId, sessionId, checkout, true, raw);
        } catch (Exception ex) {
            log.warn("MPGS checkout session failed: {}", ex.getMessage());
            evidence.recordMastercard(Map.of(
                    "ok", false,
                    "live", false,
                    "officialHost", url.contains("mastercard.com"),
                    "url", url,
                    "orderId", orderId,
                    "error", ex.getMessage()
            ));
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

    public boolean ready() {
        return properties.getMastercard().gatewayReady();
    }

    public JsonNode retrieveOrderNode(String orderId) {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        if (!mc.gatewayReady()) {
            ObjectNode mock = mapper.createObjectNode();
            mock.put("result", "SUCCESS");
            mock.putObject("order")
                    .put("id", orderId)
                    .put("status", "CAPTURED");
            return mock;
        }
        String raw = http.get().uri(merchantPath(mc) + "/order/" + orderId)
                .header("Authorization", basic(mc)).retrieve().body(String.class);
        try {
            return mapper.readTree(raw == null ? "{}" : raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid Mastercard order response", ex);
        }
    }

    public Map<String, Object> payWithDevicePayload(String orderId, String sessionId, JsonNode devicePayment) {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        if (!mc.gatewayReady()) {
            return Map.of("ok", false, "error", "Gateway not configured");
        }
        if (orderId == null || orderId.isBlank() || devicePayment == null || devicePayment.isEmpty()) {
            return Map.of("ok", false, "error", "Order ID and certified SDK devicePayment payload are required");
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
