package com.fintap.digikadai.integration.ondc;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

@Component
public class OndcSignatureService {

    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z]+)=\"([^\"]*)\"");

    public String digest(String body) {
        Blake2bDigest blake = new Blake2bDigest(512);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        blake.update(bytes, 0, bytes.length);
        byte[] out = new byte[64];
        blake.doFinal(out, 0);
        return Base64.getEncoder().encodeToString(out);
    }

    public String authorizationHeader(String subscriberId, String uniqueKeyId, String privateKeyB64, String body) {
        long created = Instant.now().getEpochSecond();
        long expires = created + 3600;
        String digest = digest(body);
        String signingString = "(created): " + created + "\n(expires): " + expires + "\ndigest: BLAKE-512=" + digest;
        String signature = sign(privateKeyB64, signingString);
        return "Signature keyId=\"" + subscriberId + "|" + uniqueKeyId + "|ed25519\",algorithm=\"ed25519\",created=\""
                + created + "\",expires=\"" + expires + "\",headers=\"(created) (expires) digest\",signature=\""
                + signature + "\"";
    }

    public String sign(String privateKeyB64, String signingString) {
        byte[] key = Base64.getDecoder().decode(privateKeyB64);
        byte[] seed = key.length >= 64 ? slice(key, 0, 32) : key;
        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(seed, 0);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        byte[] message = signingString.getBytes(StandardCharsets.UTF_8);
        signer.update(message, 0, message.length);
        return Base64.getEncoder().encodeToString(signer.generateSignature());
    }

    public boolean verify(String publicKeyB64, String signingString, String signatureB64) {
        Ed25519PublicKeyParameters publicKey = new Ed25519PublicKeyParameters(Base64.getDecoder().decode(publicKeyB64), 0);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(false, publicKey);
        byte[] message = signingString.getBytes(StandardCharsets.UTF_8);
        signer.update(message, 0, message.length);
        return signer.verifySignature(Base64.getDecoder().decode(signatureB64));
    }

    public SignatureDetails verifyAuthorizationHeader(String authorization, String publicKeyB64, String body) {
        if (authorization == null || !authorization.startsWith("Signature ")) {
            throw new IllegalArgumentException("Missing ONDC Signature authorization header");
        }
        Map<String, String> attributes = new HashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(authorization.substring("Signature ".length()));
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2));
        }
        String keyId = required(attributes, "keyId");
        String signature = required(attributes, "signature");
        long created = parseEpoch(required(attributes, "created"), "created");
        long expires = parseEpoch(required(attributes, "expires"), "expires");
        long now = Instant.now().getEpochSecond();
        if (created > now + 60 || expires < now || expires <= created || expires - created > 3600) {
            throw new IllegalArgumentException("Expired or invalid ONDC signature window");
        }
        String signingString = "(created): " + created + "\n(expires): " + expires
                + "\ndigest: BLAKE-512=" + digest(body);
        if (!verify(publicKeyB64, signingString, signature)) {
            throw new IllegalArgumentException("Invalid ONDC request signature");
        }
        String[] keyParts = keyId.split("\\|");
        if (keyParts.length < 2) {
            throw new IllegalArgumentException("Invalid ONDC keyId");
        }
        return new SignatureDetails(keyParts[0], keyParts[1], created, expires);
    }

    public String signRaw(String privateKeyB64, String message) {
        return sign(privateKeyB64, message);
    }

    public Map<String, String> generateSigningKeyPair() {
        SecureRandom random = new SecureRandom();
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(seed, 0);
        byte[] private64 = new byte[64];
        System.arraycopy(seed, 0, private64, 0, 32);
        System.arraycopy(privateKey.generatePublicKey().getEncoded(), 0, private64, 32, 32);
        return Map.of(
                "signingPrivateKey", Base64.getEncoder().encodeToString(private64),
                "signingPublicKey", Base64.getEncoder().encodeToString(privateKey.generatePublicKey().getEncoded())
        );
    }

    public Map<String, String> generateEncryptionKeyPair() {
        SecureRandom random = new SecureRandom();
        X25519PrivateKeyParameters privateKey = new X25519PrivateKeyParameters(random);
        return Map.of(
                "encryptionPrivateKey", Base64.getEncoder().encodeToString(privateKey.getEncoded()),
                "encryptionPublicKey", toX25519Spki(privateKey.generatePublicKey().getEncoded())
        );
    }

    public String decryptChallenge(String challengeB64, String privateKeyB64, String registryPublicKeyB64) {
        try {
            byte[] privateRaw = x25519Raw(privateKeyB64);
            byte[] publicRaw = x25519Raw(registryPublicKeyB64);
            X25519PrivateKeyParameters privateKey = new X25519PrivateKeyParameters(privateRaw, 0);
            X25519PublicKeyParameters publicKey = new X25519PublicKeyParameters(publicRaw, 0);
            byte[] shared = new byte[32];
            privateKey.generateSecret(publicKey, shared, 0);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(shared, "AES"));
            return new String(cipher.doFinal(Base64.getDecoder().decode(challengeB64)), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to decrypt ONDC subscription challenge", ex);
        }
    }

    public String toX25519Spki(byte[] rawPublic) {
        byte[] prefix = new byte[]{0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00};
        byte[] der = new byte[prefix.length + rawPublic.length];
        System.arraycopy(prefix, 0, der, 0, prefix.length);
        System.arraycopy(rawPublic, 0, der, prefix.length, rawPublic.length);
        return Base64.getEncoder().encodeToString(der);
    }

    public byte[] x25519Raw(String keyB64) {
        byte[] decoded = Base64.getDecoder().decode(keyB64);
        if (decoded.length == 32) return decoded;
        if (decoded.length > 32) {
            byte[] raw = new byte[32];
            System.arraycopy(decoded, decoded.length - 32, raw, 0, 32);
            return raw;
        }
        throw new IllegalArgumentException("Invalid X25519 key length " + decoded.length);
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing ONDC signature attribute: " + key);
        }
        return value;
    }

    private long parseEpoch(String value, String label) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid ONDC signature " + label, ex);
        }
    }

    private byte[] slice(byte[] source, int from, int len) {
        byte[] out = new byte[len];
        System.arraycopy(source, from, out, 0, len);
        return out;
    }

    public record SignatureDetails(String subscriberId, String uniqueKeyId, long created, long expires) {
    }
}
