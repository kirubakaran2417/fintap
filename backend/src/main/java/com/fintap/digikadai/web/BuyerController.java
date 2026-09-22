package com.fintap.digikadai.web;

import com.fintap.digikadai.integration.ondc.OndcNetworkService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/buyer")
public class BuyerController {

    private final OndcNetworkService ondc;

    public BuyerController(OndcNetworkService ondc) {
        this.ondc = ondc;
    }

    @GetMapping("/search")
    public Map<String, Object> search() {
        return ondc.buyerSearch();
    }

    @GetMapping("/network")
    public Map<String, Object> network() {
        return ondc.lastNetworkSearch();
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, Object> body) {
        try {
            Object itemId = body == null ? null : body.get("itemId");
            int quantity = 1;
            if (body != null && body.get("quantity") instanceof Number number) {
                quantity = number.intValue();
            }
            return ondc.buyerConfirm(itemId == null ? "" : itemId.toString(), quantity);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders() {
        return ondc.buyerOrders();
    }
}
