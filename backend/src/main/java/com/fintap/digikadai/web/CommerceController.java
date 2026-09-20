package com.fintap.digikadai.web;

import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.domain.OndcOrderStatus;
import com.fintap.digikadai.dto.CatalogGenerateRequest;
import com.fintap.digikadai.dto.CatalogItemDto;
import com.fintap.digikadai.dto.CustomerProfileDto;
import com.fintap.digikadai.dto.InsightDto;
import com.fintap.digikadai.dto.KhataDto;
import com.fintap.digikadai.dto.OndcOrderDto;
import com.fintap.digikadai.service.CommerceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CommerceController {

    private final CommerceService commerce;

    public CommerceController(CommerceService commerce) {
        this.commerce = commerce;
    }

    @GetMapping("/catalog")
    public List<CatalogItemDto> catalog(@ModelAttribute Merchant merchant) {
        return commerce.catalog(merchant);
    }

    @PostMapping("/catalog")
    public CatalogItemDto add(@ModelAttribute Merchant merchant, @Valid @RequestBody CatalogItemDto.CreateRequest request) {
        return commerce.addItem(merchant, request);
    }

    @PostMapping("/catalog/generate")
    public CatalogItemDto generate(@ModelAttribute Merchant merchant, @RequestBody CatalogGenerateRequest request) {
        return commerce.generateFromScan(merchant, request);
    }

    @PostMapping("/catalog/{id}/publish")
    public CatalogItemDto publish(@ModelAttribute Merchant merchant, @PathVariable Long id) {
        return commerce.publish(merchant, id);
    }

    @GetMapping("/ondc/orders")
    public List<OndcOrderDto> orders(@ModelAttribute Merchant merchant) {
        return commerce.ondcOrders(merchant);
    }

    @PostMapping("/ondc/orders/{id}/status")
    public OndcOrderDto status(
            @ModelAttribute Merchant merchant,
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        return commerce.updateOrder(merchant, id, OndcOrderStatus.valueOf(body.get("status")));
    }

    @GetMapping("/khata")
    public List<KhataDto> khata(@ModelAttribute Merchant merchant) {
        return commerce.khata(merchant);
    }

    @PostMapping("/khata")
    public KhataDto addKhata(@ModelAttribute Merchant merchant, @Valid @RequestBody KhataDto.CreateRequest request) {
        return commerce.addKhata(merchant, request);
    }

    @GetMapping("/insights")
    public List<InsightDto> insights(@ModelAttribute Merchant merchant, @RequestParam(required = false) String lang) {
        return commerce.insights(merchant, lang);
    }

    @GetMapping("/customers")
    public List<CustomerProfileDto> customers(@ModelAttribute Merchant merchant) {
        return commerce.customers(merchant);
    }
}
