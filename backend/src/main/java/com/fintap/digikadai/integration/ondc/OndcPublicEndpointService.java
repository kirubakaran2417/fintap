package com.fintap.digikadai.integration.ondc;

import com.fintap.digikadai.config.IntegrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OndcPublicEndpointService {

    private static final Logger log = LoggerFactory.getLogger(OndcPublicEndpointService.class);
    private static final Pattern TUNNEL = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");

    private final IntegrationProperties properties;
    private Process tunnel;
    private volatile String publicHttps;

    public OndcPublicEndpointService(IntegrationProperties properties) {
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alignUrls() {
        String base = trimSlash(properties.getPublicBaseUrl());
        if (base != null && base.startsWith("https://") && !base.contains("localhost")) {
            apply(base);
        }
    }

    public synchronized String ensurePublicHttps() {
        String existing = trimSlash(properties.getPublicBaseUrl());
        if (existing != null && existing.startsWith("https://") && !existing.contains("localhost")) {
            apply(existing);
            return existing;
        }
        if (publicHttps != null) {
            return publicHttps;
        }
        String url = startCloudflared();
        if (url == null) {
            throw new IllegalStateException(
                    "ONDC needs a public HTTPS origin. Install cloudflared or set PUBLIC_BASE_URL to your domain.");
        }
        apply(url);
        return url;
    }

    public boolean publiclyReachable() {
        try {
            URI uri = URI.create(properties.getOndc().getBppUri());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().contains("localhost");
        } catch (Exception ex) {
            return false;
        }
    }

    private void apply(String base) {
        publicHttps = trimSlash(base);
        properties.setPublicBaseUrl(publicHttps);
        String protocol = publicHttps + "/protocol/v1";
        IntegrationProperties.Ondc ondc = properties.getOndc();
        ondc.setSubscriberUrl(protocol);
        ondc.setBppUri(protocol);
        ondc.setBapUri(protocol);
        log.info("ONDC protocol URLs aligned to {}", protocol);
    }

    private String startCloudflared() {
        try {
            Path exe = resolveCloudflared();
            ProcessBuilder builder = new ProcessBuilder(exe.toString(), "tunnel", "--url", "http://localhost:8080", "--no-autoupdate");
            builder.redirectErrorStream(true);
            tunnel = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(tunnel.getInputStream()));
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(45);
            String line;
            while (System.currentTimeMillis() < deadline && (line = reader.readLine()) != null) {
                log.info("cloudflared: {}", line);
                Matcher matcher = TUNNEL.matcher(line);
                if (matcher.find()) {
                    return matcher.group();
                }
            }
        } catch (Exception ex) {
            log.warn("Could not start cloudflared: {}", ex.getMessage());
        }
        return null;
    }

    private Path resolveCloudflared() throws Exception {
        Path local = Path.of(System.getProperty("java.io.tmpdir"), "cloudflared.exe");
        if (Files.exists(local)) return local;
        Process where = new ProcessBuilder("where", "cloudflared").start();
        if (where.waitFor() == 0) {
            String first = new String(where.getInputStream().readAllBytes()).lines().findFirst().orElse("");
            if (!first.isBlank()) return Path.of(first.trim());
        }
        log.info("Downloading cloudflared...");
        java.net.URL url = URI.create(
                "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe").toURL();
        try (java.io.InputStream in = url.openStream()) {
            Files.copy(in, local);
        }
        return local;
    }

    private String trimSlash(String url) {
        if (url == null) return null;
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
