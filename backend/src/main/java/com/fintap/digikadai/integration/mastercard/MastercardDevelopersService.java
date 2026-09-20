package com.fintap.digikadai.integration.mastercard;

import com.mastercard.developer.oauth.OAuth;
import com.mastercard.developer.utils.AuthenticationUtils;
import com.fintap.digikadai.config.IntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MastercardDevelopersService {

    private static final Logger log = LoggerFactory.getLogger(MastercardDevelopersService.class);

    private final IntegrationProperties properties;

    public MastercardDevelopersService(IntegrationProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> status() {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", mc.isDevelopersEnabled());
        body.put("ready", mc.developersReady());
        body.put("baseUrl", mc.getDevelopersBaseUrl());
        body.put("consumerKeySet", mc.getConsumerKey() != null && !mc.getConsumerKey().isBlank());
        body.put("keystorePresent", mc.getKeystorePath() != null && !mc.getKeystorePath().isBlank()
                && Files.exists(Path.of(mc.getKeystorePath())));
        return body;
    }

    public String authorizationHeader(String method, URI uri, String jsonBody) {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        if (!mc.developersReady()) {
            throw new IllegalStateException("Mastercard Developers OAuth1 is not configured (consumer key + .p12).");
        }
        try {
            PrivateKey signingKey = AuthenticationUtils.loadSigningKey(
                    mc.getKeystorePath(),
                    mc.getKeyAlias() == null || mc.getKeyAlias().isBlank() ? "keyalias" : mc.getKeyAlias(),
                    mc.getKeystorePassword()
            );
            return OAuth.getAuthorizationHeader(
                    uri,
                    method,
                    jsonBody,
                    StandardCharsets.UTF_8,
                    mc.getConsumerKey(),
                    signingKey
            );
        } catch (Exception ex) {
            log.warn("Mastercard OAuth1 signing failed: {}", ex.getMessage());
            throw new IllegalStateException("Unable to sign Mastercard Developers request: " + ex.getMessage());
        }
    }
}
