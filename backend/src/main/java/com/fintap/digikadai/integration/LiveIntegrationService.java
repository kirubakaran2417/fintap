package com.fintap.digikadai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintap.digikadai.config.IntegrationProperties;
import com.fintap.digikadai.integration.ondc.OndcSignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LiveIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(LiveIntegrationService.class);
    private static final Path FILE = Path.of("data", "live-integrations.json");

    private final IntegrationProperties properties;
    private final OndcSignatureService signatures;
    private final ObjectMapper mapper;

    public LiveIntegrationService(
            IntegrationProperties properties,
            OndcSignatureService signatures,
            ObjectMapper mapper
    ) {
        this.properties = properties;
        this.signatures = signatures;
        this.mapper = mapper;
    }

    public synchronized Map<String, Object> ensureOndcLive() {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        ondc.setEnabled(true);
        if (blank(ondc.getSubscriberId())) {
            ondc.setSubscriberId("fintap.local");
        }
        if (blank(ondc.getUniqueKeyId())) {
            ondc.setUniqueKeyId("ukid-1");
        }
        if (blank(ondc.getSigningPrivateKey()) || blank(ondc.getSigningPublicKey())) {
            Map<String, String> keys = signatures.generateSigningKeyPair();
            ondc.setSigningPrivateKey(keys.get("signingPrivateKey"));
            ondc.setSigningPublicKey(keys.get("signingPublicKey"));
        }
        if (blank(ondc.getEncryptionPrivateKey()) || blank(ondc.getEncryptionPublicKey())) {
            Map<String, String> keys = signatures.generateEncryptionKeyPair();
            ondc.setEncryptionPrivateKey(keys.get("encryptionPrivateKey"));
            ondc.setEncryptionPublicKey(keys.get("encryptionPublicKey"));
        }
        persist();
        return snapshot();
    }

    public synchronized Map<String, Object> saveRazorpay(String keyId, String keySecret) {
        if (blank(keyId) || blank(keySecret) || !keyId.trim().startsWith("rzp_")) {
            throw new IllegalArgumentException("Razorpay key ID (rzp_...) and key secret are required.");
        }
        IntegrationProperties.Razorpay rzp = properties.getRazorpay();
        rzp.setEnabled(true);
        rzp.setKeyId(keyId.trim());
        rzp.setKeySecret(keySecret.trim());
        persist();
        return snapshot();
    }

    public synchronized Map<String, Object> saveMastercard(String merchantId, String apiPassword) {
        if (blank(merchantId) || blank(apiPassword)) {
            throw new IllegalArgumentException("Mastercard merchant ID and API password are required.");
        }
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        mc.setGatewayEnabled(true);
        mc.setMerchantId(merchantId.trim());
        mc.setApiPassword(apiPassword.trim());
        persist();
        return snapshot();
    }

    public synchronized void loadPersisted() {
        loadRazorpayEnvFile();
        if (!Files.exists(FILE)) {
            return;
        }
        try {
            State state = mapper.readValue(FILE.toFile(), State.class);
            IntegrationProperties.Ondc ondc = properties.getOndc();
            if (state.ondcEnabled != null) {
                ondc.setEnabled(state.ondcEnabled);
            }
            if (!blank(state.subscriberId) && blank(ondc.getSubscriberId())) {
                ondc.setSubscriberId(state.subscriberId);
            }
            if (!blank(state.uniqueKeyId)) {
                ondc.setUniqueKeyId(state.uniqueKeyId);
            }
            if (!blank(state.signingPrivateKey) && blank(ondc.getSigningPrivateKey())) {
                ondc.setSigningPrivateKey(state.signingPrivateKey);
            }
            if (!blank(state.signingPublicKey) && blank(ondc.getSigningPublicKey())) {
                ondc.setSigningPublicKey(state.signingPublicKey);
            }
            if (!blank(state.encryptionPrivateKey) && blank(ondc.getEncryptionPrivateKey())) {
                ondc.setEncryptionPrivateKey(state.encryptionPrivateKey);
            }
            if (!blank(state.encryptionPublicKey) && blank(ondc.getEncryptionPublicKey())) {
                ondc.setEncryptionPublicKey(state.encryptionPublicKey);
            }
            if (!blank(state.requestId) && blank(ondc.getRequestId())) {
                ondc.setRequestId(state.requestId);
            }
            IntegrationProperties.Mastercard mc = properties.getMastercard();
            if (state.mcGatewayEnabled != null) {
                mc.setGatewayEnabled(state.mcGatewayEnabled);
            }
            if (!blank(state.mcMerchantId) && blank(mc.getMerchantId())) {
                mc.setMerchantId(state.mcMerchantId);
            }
            IntegrationProperties.Razorpay rzp = properties.getRazorpay();
            if (state.razorpayEnabled != null) {
                rzp.setEnabled(state.razorpayEnabled);
            }
            if (!blank(state.razorpayKeyId) && blank(rzp.getKeyId())) {
                rzp.setKeyId(state.razorpayKeyId);
            }
            // Rewrite legacy state without any private keys or gateway passwords.
            persist();
        } catch (Exception ex) {
            log.warn("Could not load live integration file: {}", ex.getMessage());
        }
    }

    public Map<String, Object> snapshot() {
        IntegrationProperties.Ondc ondc = properties.getOndc();
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        IntegrationProperties.Razorpay rzp = properties.getRazorpay();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ondcEnabled", ondc.isEnabled());
        body.put("ondcLive", ondc.isEnabled() && official(ondc.getMockBppUrl(), "ondc.org") && ondc.keysReady());
        body.put("subscriberId", ondc.getSubscriberId());
        body.put("mastercardEnabled", mc.isGatewayEnabled());
        body.put("mastercardReady", mc.gatewayReady());
        body.put("merchantId", blank(mc.getMerchantId()) ? null : mc.getMerchantId());
        body.put("gatewayBaseUrl", mc.getGatewayBaseUrl());
        body.put("razorpayEnabled", rzp.isEnabled());
        body.put("razorpayReady", rzp.ready());
        body.put("razorpayKeyId", rzp.ready() ? rzp.getKeyId() : null);
        body.put("runtimeCredentialsAllowed", properties.isAllowRuntimeCredentials());
        return body;
    }

    public boolean runtimeCredentialsAllowed() {
        return properties.isAllowRuntimeCredentials();
    }

    private void persist() {
        try {
            Files.createDirectories(FILE.getParent());
            IntegrationProperties.Ondc ondc = properties.getOndc();
            IntegrationProperties.Mastercard mc = properties.getMastercard();
            State state = new State();
            state.ondcEnabled = ondc.isEnabled();
            state.subscriberId = ondc.getSubscriberId();
            state.uniqueKeyId = ondc.getUniqueKeyId();
            state.signingPublicKey = ondc.getSigningPublicKey();
            state.signingPrivateKey = ondc.getSigningPrivateKey();
            state.encryptionPublicKey = ondc.getEncryptionPublicKey();
            state.encryptionPrivateKey = ondc.getEncryptionPrivateKey();
            state.requestId = ondc.getRequestId();
            state.mcGatewayEnabled = mc.isGatewayEnabled();
            state.mcMerchantId = mc.getMerchantId();
            IntegrationProperties.Razorpay rzp = properties.getRazorpay();
            state.razorpayEnabled = rzp.isEnabled();
            state.razorpayKeyId = rzp.getKeyId();
            mapper.writerWithDefaultPrettyPrinter().writeValue(FILE.toFile(), state);
        } catch (Exception ex) {
            log.warn("Could not persist live integration file: {}", ex.getMessage());
        }
    }

    private boolean official(String url, String host) {
        return url != null && url.contains(host);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class State {
        public Boolean ondcEnabled;
        public String subscriberId;
        public String uniqueKeyId;
        public String signingPublicKey;
        public String signingPrivateKey;
        public String encryptionPublicKey;
        public String encryptionPrivateKey;
        public String requestId;
        public Boolean mcGatewayEnabled;
        public String mcMerchantId;
        public Boolean razorpayEnabled;
        public String razorpayKeyId;
    }

    private void loadRazorpayEnvFile() {
        Path envFile = Path.of("data", "razorpay.env");
        if (!Files.exists(envFile)) {
            return;
        }
        try {
            IntegrationProperties.Razorpay rzp = properties.getRazorpay();
            for (String line : Files.readAllLines(envFile)) {
                if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int split = line.indexOf('=');
                String key = line.substring(0, split).trim();
                String value = line.substring(split + 1).trim();
                if ("RAZORPAY_KEY_ID".equals(key) && blank(rzp.getKeyId())) {
                    rzp.setKeyId(value);
                    rzp.setEnabled(true);
                }
                if ("RAZORPAY_KEY_SECRET".equals(key) && blank(rzp.getKeySecret())) {
                    rzp.setKeySecret(value);
                    rzp.setEnabled(true);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not load razorpay.env: {}", ex.getMessage());
        }
    }
}
