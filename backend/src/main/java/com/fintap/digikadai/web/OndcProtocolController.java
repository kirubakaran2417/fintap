package com.fintap.digikadai.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintap.digikadai.integration.ondc.OndcNetworkService;
import com.fintap.digikadai.integration.ondc.OndcSignatureService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/protocol/v1")
public class OndcProtocolController {

    private final OndcNetworkService ondc;

    public OndcProtocolController(OndcNetworkService ondc) {
        this.ondc = ondc;
    }

    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody JsonNode body) {
        ondc.onSearchCatalog(body);
        return ondc.ack();
    }

    @PostMapping("/select")
    public Map<String, Object> select(@RequestBody JsonNode body) {
        return ondc.ack();
    }

    @PostMapping("/init")
    public Map<String, Object> init(@RequestBody JsonNode body) {
        return ondc.ack();
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody JsonNode body) {
        ondc.captureConfirm(body);
        return ondc.ack();
    }

    @PostMapping("/status")
    public Map<String, Object> status(@RequestBody JsonNode body) {
        return ondc.ack();
    }

    @PostMapping("/cancel")
    public Map<String, Object> cancel(@RequestBody JsonNode body) {
        return ondc.ack();
    }

    @PostMapping("/on_subscribe")
    public Map<String, Object> onSubscribe(@RequestBody JsonNode body) {
        return Map.of("answer", body.path("challenge").asText(""));
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

    IntegrationController(
            OndcNetworkService ondc,
            OndcSignatureService signatures,
            com.fintap.digikadai.integration.mastercard.MastercardGatewayService gateway,
            com.fintap.digikadai.integration.mastercard.MastercardDevelopersService developers
    ) {
        this.ondc = ondc;
        this.signatures = signatures;
        this.gateway = gateway;
        this.developers = developers;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "ondc", ondc.status(),
                "mastercardGateway", gateway.status(),
                "mastercardDevelopers", developers.status()
        );
    }

    @PostMapping("/ondc/keys")
    public Map<String, String> keys() {
        return signatures.generateSigningKeyPair();
    }

    @PostMapping("/ondc/ping")
    public Map<String, Object> ping() {
        return ondc.pingMockSearch();
    }

    @PostMapping("/ondc/lookup")
    public Map<String, Object> lookup() {
        return ondc.registryLookup();
    }
}
