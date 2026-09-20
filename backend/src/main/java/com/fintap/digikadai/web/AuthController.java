package com.fintap.digikadai.web;

import com.fintap.digikadai.dto.AuthResponse;
import com.fintap.digikadai.dto.LoginRequest;
import com.fintap.digikadai.dto.RegisterRequest;
import com.fintap.digikadai.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final MerchantService merchants;

    public AuthController(MerchantService merchants) {
        this.merchants = merchants;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return merchants.login(request);
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return merchants.register(request);
    }
}
