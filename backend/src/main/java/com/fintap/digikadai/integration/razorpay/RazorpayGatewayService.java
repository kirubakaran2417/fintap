package com.fintap.digikadai.integration.razorpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintap.digikadai.config.IntegrationProperties;
import com.fintap.digikadai.integration.DemoEvidenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RazorpayGatewayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGatewayService.class);

    private final IntegrationProperties properties;
    private final ObjectMapper mapper;
    private final DemoEvidenceService evidence;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public RazorpayGatewayService(
            IntegrationProperties properties,
            ObjectMapper mapper,
            DemoEvidenceService evidence
    ) {
        this.properties = properties;
        this.mapper = mapper;
        this.evidence = evidence;
    }

    public boolean ready() {
        return properties.getRazorpay().ready();
    }

    public Map<String, Object> status() {
        IntegrationProperties.Razorpay rzp = properties.getRazorpay();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", rzp.isEnabled());
        body.put("ready", rzp.ready());
        body.put("keyId", rzp.ready() ? rzp.getKeyId() : null);
        body.put("baseUrl", rzp.getBaseUrl());
        body.put("note", "Razorpay test keys create a live order on api.razorpay.com. Use Razorpay test cards on checkout.");
        return body;
    }

    public CheckoutOrder createOrder(BigDecimal amount) {
        IntegrationProperties.Razorpay rzp = properties.getRazorpay();
        if (!rzp.ready()) {
            throw new IllegalStateException("Razorpay keys are not configured.");
        }
        long paise = amount.setScale(2, java.math.RoundingMode.HALF_UP).movePointRight(2).longValue();
        ObjectNode body = mapper.createObjectNode();
        body.put("amount", paise);
        body.put("currency", "INR");
        body.put("receipt", "FT-" + System.currentTimeMillis());
        body.put("payment_capture", 1);
        String json = write(body);
        String url = trimSlash(rzp.getBaseUrl()) + "/v1/orders";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Authorization", basic(rzp))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> entity = http.send(request, HttpResponse.BodyHandlers.ofString());
            String raw = entity.body() == null ? "{}" : entity.body();
            JsonNode node = mapper.readTree(raw);
            String orderId = node.path("id").asText("");
            boolean ok = entity.statusCode() >= 200 && entity.statusCode() < 300 && orderId.startsWith("order_");
            Map<String, Object> proof = new LinkedHashMap<>();
            proof.put("ok", ok);
            proof.put("live", ok);
            proof.put("officialHost", url.contains("razorpay.com"));
            proof.put("url", url);
            proof.put("httpStatus", entity.statusCode());
            proof.put("orderId", orderId);
            proof.put("amountPaise", paise);
            proof.put("response", DemoEvidenceService.truncate(raw, 400));
            evidence.recordRazorpay(proof);
            if (!ok) {
                throw new IllegalStateException("Razorpay order failed: " + DemoEvidenceService.truncate(raw, 180));
            }
            String checkout = "http://localhost:8080/pay/razorpay/" + orderId;
            return new CheckoutOrder(orderId, checkout, paise, true);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Razorpay order failed: {}", ex.getMessage());
            evidence.recordRazorpay(Map.of(
                    "ok", false,
                    "live", false,
                    "officialHost", url.contains("razorpay.com"),
                    "url", url,
                    "error", ex.getMessage()
            ));
            throw new IllegalStateException("Razorpay order failed: " + ex.getMessage());
        }
    }

    public Map<String, Object> probe() {
        try {
            CheckoutOrder order = createOrder(new BigDecimal("1.00"));
            return Map.of("ok", true, "orderId", order.orderId(), "checkoutUrl", order.checkoutUrl());
        } catch (Exception ex) {
            return Map.of("ok", false, "error", ex.getMessage());
        }
    }

    private String basic(IntegrationProperties.Razorpay rzp) {
        String token = Base64.getEncoder().encodeToString(
                (rzp.getKeyId() + ":" + rzp.getKeySecret()).getBytes(StandardCharsets.UTF_8));
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

    public record CheckoutOrder(String orderId, String checkoutUrl, long amountPaise, boolean live) {
    }
}
