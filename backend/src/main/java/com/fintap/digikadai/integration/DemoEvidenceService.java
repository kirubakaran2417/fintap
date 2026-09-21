package com.fintap.digikadai.integration;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DemoEvidenceService {

    private final Map<String, Object> ondcPing = new ConcurrentHashMap<>();
    private final Map<String, Object> ondcLookup = new ConcurrentHashMap<>();
    private final Map<String, Object> ondcCallback = new ConcurrentHashMap<>();
    private final Map<String, Object> mastercard = new ConcurrentHashMap<>();
    private final Map<String, Object> razorpay = new ConcurrentHashMap<>();

    public void recordOndcPing(Map<String, Object> event) {
        ondcPing.clear();
        ondcPing.putAll(event);
        ondcPing.put("recordedAt", Instant.now().toString());
    }

    public void recordOndcLookup(Map<String, Object> event) {
        ondcLookup.clear();
        ondcLookup.putAll(event);
        ondcLookup.put("recordedAt", Instant.now().toString());
    }

    public void recordOndcCallback(Map<String, Object> event) {
        ondcCallback.clear();
        ondcCallback.putAll(event);
        ondcCallback.put("recordedAt", Instant.now().toString());
    }

    public void recordMastercard(Map<String, Object> event) {
        mastercard.clear();
        mastercard.putAll(event);
        mastercard.put("recordedAt", Instant.now().toString());
    }

    public void recordRazorpay(Map<String, Object> event) {
        razorpay.clear();
        razorpay.putAll(event);
        razorpay.put("recordedAt", Instant.now().toString());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("disclaimer", "Live proof is a call whose URL host is mock.ondc.org, preprod.registry.ondc.org, test-gateway.mastercard.com, or api.razorpay.com.");
        body.put("ondcPing", copy(ondcPing));
        body.put("ondcLookup", copy(ondcLookup));
        body.put("ondcCallback", copy(ondcCallback));
        body.put("mastercard", copy(mastercard));
        body.put("razorpay", copy(razorpay));
        return body;
    }

    public static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    public static String signatureHint(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return "unsigned";
        }
        int start = authorization.indexOf("signature=\"");
        if (start < 0) {
            return truncate(authorization, 72);
        }
        String sig = authorization.substring(start + 11);
        int end = sig.indexOf('"');
        if (end > 0) {
            sig = sig.substring(0, end);
        }
        return truncate(sig, 24);
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return source.isEmpty() ? Map.of("status", "No call recorded yet") : new LinkedHashMap<>(source);
    }
}
