package com.fintap.digikadai.integration.ondc;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Component
public class OndcSignatureService {

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

    private byte[] slice(byte[] source, int from, int len) {
        byte[] out = new byte[len];
        System.arraycopy(source, from, out, 0, len);
        return out;
    }
}
