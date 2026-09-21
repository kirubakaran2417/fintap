package com.fintap.digikadai.service;

import com.fintap.digikadai.domain.PaymentRail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
class PaymentServiceTest {

    @Test
    void recommendsCardWhenTicketIsLarge() {
        PaymentService service = new PaymentService(null, null, null, null);
        assertEquals(PaymentRail.CARD, service.route(new BigDecimal("350")).recommendedRail());
        assertEquals(PaymentRail.UPI, service.route(new BigDecimal("40")).recommendedRail());
    }
}
