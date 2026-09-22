package com.fintap.digikadai.web;

import com.fintap.digikadai.domain.BuyerAccount;
import com.fintap.digikadai.integration.ondc.OndcNetworkService;
import com.fintap.digikadai.service.BuyerAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/buyer")
public class BuyerController {

    private final OndcNetworkService ondc;
    private final BuyerAccountService accounts;

    public BuyerController(OndcNetworkService ondc, BuyerAccountService accounts) {
        this.ondc = ondc;
        this.accounts = accounts;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        return accounts.register(
                body.get("name"),
                body.get("mobile"),
                body.get("pin"),
                body.get("address"));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        return accounts.login(body.get("mobile"), body.get("pin"));
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        BuyerAccount account = accounts.require(bearer(authorization));
        return Map.of(
                "name", account.getName(),
                "mobile", account.getMobile(),
                "address", account.getAddress() == null ? "" : account.getAddress()
        );
    }

    @GetMapping("/search")
    public Map<String, Object> search() {
        return ondc.buyerSearch();
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return ondc.buyerLiveCatalog();
    }

    @GetMapping("/network")
    public Map<String, Object> network() {
        return ondc.lastNetworkSearch();
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body
    ) {
        try {
            BuyerAccount account = accounts.require(bearer(authorization));
            Object itemId = body == null ? null : body.get("itemId");
            int quantity = 1;
            if (body != null && body.get("quantity") instanceof Number number) {
                quantity = number.intValue();
            }
            return ondc.buyerConfirm(
                    itemId == null ? "" : itemId.toString(),
                    quantity,
                    account.getName(),
                    account.getMobile());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders(@RequestHeader(value = "Authorization", required = false) String authorization) {
        BuyerAccount account = accounts.require(bearer(authorization));
        return ondc.buyerOrders(account.getMobile());
    }

    private String bearer(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
