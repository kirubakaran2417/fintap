package com.fintap.digikadai.integration.ondc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OndcSignatureServiceTest {

    private final OndcSignatureService signatures = new OndcSignatureService();

    @Test
    void signsAndVerifiesEd25519Payload() {
        Map<String, String> keys = signatures.generateSigningKeyPair();
        String body = "{\"context\":{\"action\":\"search\"}}";
        String header = signatures.authorizationHeader("digikadai.test", "ukid-1", keys.get("signingPrivateKey"), body);
        assertTrue(header.startsWith("Signature keyId=\"digikadai.test|ukid-1|ed25519\""));
        String digest = signatures.digest(body);
        assertEquals(88, digest.length());
    }
}
