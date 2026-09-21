package com.fintap.digikadai.integration;

import com.fintap.digikadai.integration.ondc.OndcNetworkService;
import com.fintap.digikadai.integration.razorpay.RazorpayGatewayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class LiveSandboxBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LiveSandboxBootstrap.class);

    private final LiveIntegrationService live;
    private final OndcNetworkService ondc;
    private final RazorpayGatewayService razorpay;

    public LiveSandboxBootstrap(
            LiveIntegrationService live,
            OndcNetworkService ondc,
            RazorpayGatewayService razorpay
    ) {
        this.live = live;
        this.ondc = ondc;
        this.razorpay = razorpay;
    }

    @Override
    public void run(ApplicationArguments args) {
        live.loadPersisted();
        live.ensureOndcLive();
        try {
            ondc.registryLookup();
        } catch (Exception ex) {
            log.warn("ONDC registry lookup on startup: {}", ex.getMessage());
        }
        try {
            if (razorpay.ready()) {
                razorpay.probe();
            }
        } catch (Exception ex) {
            log.warn("Razorpay live probe on startup: {}", ex.getMessage());
        }
    }
}
