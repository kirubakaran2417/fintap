package com.fintap.digikadai.web;

import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.dto.MerchantDto;
import com.fintap.digikadai.dto.OnboardRequest;
import com.fintap.digikadai.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantService merchants;

    public MerchantController(MerchantService merchants) {
        this.merchants = merchants;
    }

    @GetMapping("/me")
    public MerchantDto me(@ModelAttribute Merchant merchant) {
        return merchants.me(merchant);
    }

    @PostMapping("/onboard")
    public MerchantDto onboard(@ModelAttribute Merchant merchant, @Valid @RequestBody OnboardRequest request) {
        return merchants.onboard(merchant, request);
    }
}
