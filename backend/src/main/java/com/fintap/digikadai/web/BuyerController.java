package com.fintap.digikadai.web;

import com.fintap.digikadai.integration.ondc.OndcBuyerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/buyer")
public class BuyerController {

    private final OndcBuyerService buyer;

    public BuyerController(OndcBuyerService buyer) {
        this.buyer = buyer;
    }

    @PostMapping("/search")
    public Map<String, Object> search(@RequestBody(required = false) Map<String, String> body) {
        String query = body == null ? "" : body.getOrDefault("query", "");
        return buyer.search(query);
    }

    @GetMapping("/catalog")
    public List<Map<String, Object>> catalog() {
        return buyer.catalog();
    }

    @PostMapping("/select")
    public Map<String, Object> select(@RequestBody Map<String, Object> body) {
        return buyer.confirm(String.valueOf(body.get("itemId")), asInt(body.get("quantity")));
    }

    @PostMapping("/init")
    public Map<String, Object> init(@RequestBody Map<String, Object> body) {
        return buyer.confirm(String.valueOf(body.get("itemId")), asInt(body.get("quantity")));
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, Object> body) {
        return buyer.confirm(String.valueOf(body.get("itemId")), asInt(body.get("quantity")));
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders() {
        return buyer.orders();
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return 1;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}
