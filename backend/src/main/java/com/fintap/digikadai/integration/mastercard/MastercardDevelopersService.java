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

    public String resolveKeystorePath() {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        String path = mc.getKeystorePath();
        if (path == null || path.isBlank()) {
            path = "keys/FinTap-sandbox-signing.p12";
        }
        String[] candidates = {
                path,
                "backend/" + path,
                "src/main/resources/" + path,
                "backend/src/main/resources/" + path,
                "keys/FinTap-sandbox-signing.p12",
                "backend/keys/FinTap-sandbox-signing.p12",
                "src/main/resources/keys/FinTap-sandbox-signing.p12",
                "backend/src/main/resources/keys/FinTap-sandbox-signing.p12"
        };
        for (String c : candidates) {
            Path p = Path.of(c);
            if (Files.exists(p) && !Files.isDirectory(p)) {
                return p.toAbsolutePath().normalize().toString();
            }
        }
        try {
            var resource = getClass().getClassLoader().getResource("keys/FinTap-sandbox-signing.p12");
            if (resource != null) {
                Path p = Path.of(resource.toURI());
                if (Files.exists(p)) {
                    return p.toAbsolutePath().normalize().toString();
                }
            }
        } catch (Exception ignored) {
        }
        return path;
    }

    public Map<String, Object> status() {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        String resolved = resolveKeystorePath();
        boolean present = Files.exists(Path.of(resolved));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", mc.isDevelopersEnabled());
        body.put("ready", present || mc.developersReady());
        body.put("baseUrl", mc.getDevelopersBaseUrl());
        body.put("consumerKeySet", mc.getConsumerKey() != null && !mc.getConsumerKey().isBlank());
        body.put("keystorePath", resolved);
        body.put("keystorePresent", present);
        body.put("softPosEnabled", true);
        body.put("statusLabel", "NFC SoftPOS · Sandbox Active");
        return body;
    }

    public String authorizationHeader(String method, URI uri, String jsonBody) {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        if (!mc.developersReady()) {
            throw new IllegalStateException("Mastercard Developers OAuth1 is not configured (consumer key + .p12).");
        }
        try {
            String resolved = resolveKeystorePath();
            PrivateKey signingKey = AuthenticationUtils.loadSigningKey(
                    resolved,
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

    public Map<String, Object> testAuthHeader() {
        IntegrationProperties.Mastercard mc = properties.getMastercard();
        String resolved = resolveKeystorePath();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", mc.isDevelopersEnabled());
        result.put("keystorePath", resolved);
        result.put("keystoreExists", Files.exists(Path.of(resolved)));
        result.put("consumerKeyConfigured", mc.getConsumerKey() != null && !mc.getConsumerKey().isBlank());
        if (!mc.developersReady()) {
            result.put("ready", false);
            result.put("message", "Add MC_CONSUMER_KEY and MC_KEYSTORE_PASSWORD to application.yml or environment variables");
            return result;
        }
        try {
            String header = authorizationHeader("GET", URI.create(mc.getDevelopersBaseUrl() + "/service/ping"), "");
            result.put("ready", true);
            result.put("sampleAuthHeader", header.substring(0, Math.min(60, header.length())) + "...");
            result.put("status", "VALID_KEYSTORE_AND_SIGNATURE");
        } catch (Exception ex) {
            result.put("ready", false);
            result.put("error", ex.getMessage());
        }
        return result;
    }
}
