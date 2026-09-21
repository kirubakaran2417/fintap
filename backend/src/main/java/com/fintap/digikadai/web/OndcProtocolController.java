package com.fintap.digikadai.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.integration.ondc.OndcNetworkService;
import com.fintap.digikadai.integration.ondc.OndcSignatureService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/protocol/v1")
public class OndcProtocolController {

    private final OndcNetworkService ondc;

    public OndcProtocolController(OndcNetworkService ondc) {
        this.ondc = ondc;
    }

    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody String body,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        return accept("search", body, authorization);
    }

    @PostMapping("/select")
    public Map<String, Object> select(@RequestBody String body,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        return accept("select", body, authorization);
    }

    @PostMapping("/init")
    public Map<String, Object> init(@RequestBody String body,
                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        return accept("init", body, authorization);
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody String body,
                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        return accept("confirm", body, authorization);
    }

    @PostMapping("/status")
    public Map<String, Object> status(@RequestBody String body,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        return accept("status", body, authorization);
    }

    @PostMapping("/cancel")
    public Map<String, Object> cancel(@RequestBody String body,
                                      @RequestHeader(value = "Authorization", required = false) String authorization) {
        return accept("cancel", body, authorization);
    }

    @PostMapping("/on_subscribe")
    public Map<String, Object> onSubscribe(@RequestBody JsonNode body) {
        return Map.of("answer", ondc.answerSubscriptionChallenge(body));
    }

    private Map<String, Object> accept(String action, String body, String authorization) {
        try {
            var envelope = ondc.verifyAndParse(action, body, authorization);
            ondc.dispatchCallback(action, envelope);
            return ondc.ack();
        } catch (IllegalArgumentException ex) {
            return ondc.nack("10000", ex.getMessage());
        }
    }
}

@RestController
class OndcSiteVerificationController {

    private final OndcNetworkService ondc;

    OndcSiteVerificationController(OndcNetworkService ondc) {
        this.ondc = ondc;
    }

    @GetMapping(value = "/ondc-site-verification.html", produces = MediaType.TEXT_HTML_VALUE)
    public String verification() {
        String content = ondc.siteVerificationMeta();
        return """
                <html>
                  <head>
                    <meta name="ondc-site-verification" content="%s" />
                  </head>
                  <body>ONDC Site Verification Page</body>
                </html>
                """.formatted(content);
    }
}

@RestController
@RequestMapping("/api/integrations")
class IntegrationController {

    private final OndcNetworkService ondc;
    private final OndcSignatureService signatures;
    private final com.fintap.digikadai.integration.mastercard.MastercardGatewayService gateway;
    private final com.fintap.digikadai.integration.mastercard.MastercardDevelopersService developers;
    private final com.fintap.digikadai.integration.DemoEvidenceService evidence;
    private final com.fintap.digikadai.integration.LiveIntegrationService live;
    private final com.fintap.digikadai.integration.razorpay.RazorpayGatewayService razorpay;

    IntegrationController(
            OndcNetworkService ondc,
            OndcSignatureService signatures,
            com.fintap.digikadai.integration.mastercard.MastercardGatewayService gateway,
            com.fintap.digikadai.integration.mastercard.MastercardDevelopersService developers,
            com.fintap.digikadai.integration.DemoEvidenceService evidence,
            com.fintap.digikadai.integration.LiveIntegrationService live,
            com.fintap.digikadai.integration.razorpay.RazorpayGatewayService razorpay
    ) {
        this.ondc = ondc;
        this.signatures = signatures;
        this.gateway = gateway;
        this.developers = developers;
        this.evidence = evidence;
        this.live = live;
        this.razorpay = razorpay;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "ondc", ondc.status(),
                "mastercardGateway", gateway.status(),
                "mastercardDevelopers", developers.status(),
                "razorpay", razorpay.status(),
                "live", live.snapshot()
        );
    }

    @PostMapping("/go-live")
    public Map<String, Object> goLive() {
        live.ensureOndcLive();
        return Map.of(
                "live", live.snapshot(),
                "ondcLookup", ondc.registryLookup(),
                "razorpay", razorpay.probe()
        );
    }

    @PostMapping("/razorpay/credentials")
    public Map<String, Object> razorpayCredentials(@RequestBody Map<String, String> body) {
        if (!live.runtimeCredentialsAllowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Runtime credentials are disabled; use environment variables or a secrets manager.");
        }
        try {
            live.saveRazorpay(body.get("keyId"), body.get("keySecret"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return Map.of("live", live.snapshot(), "razorpay", razorpay.probe());
    }

    @PostMapping("/mastercard/credentials")
    public Map<String, Object> mastercardCredentials(@RequestBody Map<String, String> body) {
        if (!live.runtimeCredentialsAllowed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Runtime credentials are disabled; use environment variables or a secrets manager.");
        }
        String merchantId = body.get("merchantId");
        String apiPassword = body.get("apiPassword");
        try {
            live.saveMastercard(merchantId, apiPassword);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return Map.of(
                "live", live.snapshot(),
                "mastercard", gateway.probeOfficialHost()
        );
    }

    @GetMapping("/evidence")
    public Map<String, Object> evidence() {
        return evidence.snapshot();
    }

    @PostMapping("/ondc/keys")
    public Map<String, String> keys() {
        Map<String, String> keys = new java.util.LinkedHashMap<>(signatures.generateSigningKeyPair());
        keys.putAll(signatures.generateEncryptionKeyPair());
        return keys;
    }

    @PostMapping("/ondc/ping")
    public Map<String, Object> ping() {
        return ondc.pingMockSearch();
    }

    @PostMapping("/ondc/connect-dev")
    public Map<String, Object> connectDev(@ModelAttribute Merchant merchant) {
        live.ensureOndcLive();
        return ondc.connectDevAndLoadCustomers(merchant);
    }

    @PostMapping("/ondc/lookup")
    public Map<String, Object> lookup() {
        return ondc.registryLookup();
    }
}
