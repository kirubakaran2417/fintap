package com.fintap.digikadai.web;

import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.service.MerchantService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = "com.fintap.digikadai.web")
public class AuthAdvice {

    private final MerchantService merchants;

    public AuthAdvice(MerchantService merchants) {
        this.merchants = merchants;
    }

    @ModelAttribute
    public Merchant currentMerchant(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/api/auth")
                || path.startsWith("/api/buyer")
                || path.equals("/api/health")
                || path.startsWith("/pay/")
                || path.startsWith("/api/payments/mastercard/local/")) {
            return null;
        }
        if (!path.startsWith("/api/")) {
            return null;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return merchants.require("missing");
        }
        return merchants.require(header.substring(7));
    }
}
