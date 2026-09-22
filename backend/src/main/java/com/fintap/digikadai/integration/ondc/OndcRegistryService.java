package com.fintap.digikadai.integration.ondc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fintap.digikadai.config.IntegrationProperties;
import com.fintap.digikadai.integration.LiveIntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OndcRegistryService {

    private static final Logger log = LoggerFactory.getLogger(OndcRegistryService.class);
    private static final String PREPROD_ENC_KEY = "MCowBQYDK2VuAyEARa/WcMCzNQp4DWjvTI4DK7vHL6EdaHqN4GjFIu9wxxM=";

    private final IntegrationProperties properties;
    private final OndcSignatureService signatures;
    private final LiveIntegrationService live;
    private final OndcPublicEndpointService endpoints;
    private final ObjectMapper mapper;
    private volatile Map<String, Object> lastSubscribe = Map.of();

    public OndcRegistryService(
            IntegrationProperties properties,
            OndcSignatureService signatures,
            LiveIntegrationService live,
            OndcPublicEndpointService endpoints,
            ObjectMapper mapper
    ) {
        this.properties = properties;
        this.signatures = signatures;
        this.live = live;
        this.endpoints = endpoints;
        this.mapper = mapper;
    }

    public Map<String, Object> register() {
        live.ensureOndcLive();
        IntegrationProperties.Ondc ondc = properties.getOndc();
        if (blank(ondc.getRegistryEncryptionPublicKey())) {
            ondc.setRegistryEncryptionPublicKey(PREPROD_ENC_KEY);
        }
        String publicBase = endpoints.ensurePublicHttps();
        URI base = URI.create(publicBase);
        String host = base.getHost();
        ondc.setSubscriberId(host);
        ondc.setBppId(host);
        ondc.setBapId(host);
        String protocol = publicBase + "/protocol/v1";
        ondc.setSubscriberUrl(protocol);
        ondc.setBppUri(protocol);
        ondc.setBapUri(protocol);
        if (blank(ondc.getRequestId())) {
            ondc.setRequestId(UUID.randomUUID().toString());
        }
        if (ondc.getEncryptionPublicKey() != null && ondc.getEncryptionPublicKey().length() < 50) {
            ondc.setEncryptionPublicKey(signatures.toX25519Spki(
                    signatures.x25519Raw(ondc.getEncryptionPublicKey())));
        }
        live.ensureOndcLive();
        ObjectNode payload = subscribePayload(ondc);
        String registry = trimSlash(ondc.getRegistryUrl());
        String subscribeUrl = registry.endsWith("/ondc") ? registry + "/subscribe" : registry + "/ondc/subscribe";
        Map<String, Object> result = postJson(subscribeUrl, write(payload));
        result.put("subscriberId", ondc.getSubscriberId());
        result.put("subscriberUrl", ondc.getSubscriberUrl());
        result.put("requestId", ondc.getRequestId());
        result.put("siteVerification", publicBase + "/ondc-site-verification.html");
        result.put("onSubscribe", protocol + "/on_subscribe");
        result.put("opsNo", 4);
        lastSubscribe = result;
        log.info("ONDC subscribe {} status={} body={}", subscribeUrl, result.get("httpStatus"), result.get("response"));
        return result;
    }

    public Map<String, Object> lastSubscribe() {
        return lastSubscribe;
    }

    private ObjectNode subscribePayload(IntegrationProperties.Ondc ondc) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        ObjectNode root = mapper.createObjectNode();
        root.putObject("context").putObject("operation").put("ops_no", 4);
        ObjectNode message = root.putObject("message");
        message.put("request_id", ondc.getRequestId());
        message.put("timestamp", now.toString());
        ObjectNode entity = message.putObject("entity");
        entity.putObject("gst")
                .put("legal_entity_name", ondc.getLegalName())
                .put("business_address", ondc.getBusinessAddress())
                .put("gst_no", ondc.getGstNo())
                .set("city_code", mapper.createArrayNode().add(ondc.getCity()));
        entity.putObject("pan")
                .put("name_as_per_pan", ondc.getLegalName())
                .put("pan_no", ondc.getPanNo())
                .put("date_of_incorporation", "2024-01-15");
        entity.put("name_of_authorised_signatory", ondc.getSignatoryName());
        entity.put("address_of_authorised_signatory", ondc.getBusinessAddress());
        entity.put("email_id", ondc.getSignatoryEmail());
        entity.put("mobile_no", ondc.getSignatoryMobile());
        entity.put("country", ondc.getCountry());
        entity.put("subscriber_id", ondc.getSubscriberId());
        entity.put("unique_key_id", ondc.getUniqueKeyId());
        entity.put("callback_url", "/protocol/v1");
        entity.putObject("key_pair")
                .put("signing_public_key", ondc.getSigningPublicKey())
                .put("encryption_public_key", ondc.getEncryptionPublicKey())
                .put("valid_from", now.toString())
                .put("valid_until", now.plus(365, ChronoUnit.DAYS).toString());
        ArrayNode participants = message.putArray("network_participant");
        participants.add(participant(ondc, "buyerApp"));
        participants.add(participant(ondc, "sellerApp"));
        return root;
    }

    private ObjectNode participant(IntegrationProperties.Ondc ondc, String type) {
        ObjectNode node = mapper.createObjectNode();
        node.put("subscriber_url", "/protocol/v1");
        node.put("domain", "nic2004:52110");
        node.put("type", type);
        node.put("msn", false);
        node.set("city_code", mapper.createArrayNode().add(ondc.getCity()));
        return node;
    }

    private Map<String, Object> postJson(String url, String json) {
        Map<String, Object> hop = new LinkedHashMap<>();
        hop.put("url", url);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(45_000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
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
            hop.put("response", body);
            return hop;
        } catch (Exception ex) {
            hop.put("ok", false);
            hop.put("error", ex.getMessage());
            return hop;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String write(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
