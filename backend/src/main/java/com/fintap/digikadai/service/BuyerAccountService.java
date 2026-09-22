package com.fintap.digikadai.service;

import com.fintap.digikadai.domain.BuyerAccount;
import com.fintap.digikadai.repo.BuyerAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BuyerAccountService {

    private final BuyerAccountRepository buyers;

    public BuyerAccountService(BuyerAccountRepository buyers) {
        this.buyers = buyers;
    }

    public Map<String, Object> register(String name, String mobile, String pin, String address) {
        String digits = digits(mobile);
        if (name == null || name.isBlank() || digits.length() < 10 || pin == null || pin.length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name, 10-digit mobile and 4–6 digit PIN are required");
        }
        if (buyers.findByMobile(digits).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already exists for this mobile. Sign in instead.");
        }
        BuyerAccount account = new BuyerAccount();
        account.setName(name.trim());
        account.setMobile(digits);
        account.setPinHash(MerchantService.hash(pin.trim()));
        account.setAddress(address == null ? "" : address.trim());
        account.setToken(UUID.randomUUID().toString());
        return toView(buyers.save(account));
    }

    public Map<String, Object> login(String mobile, String pin) {
        BuyerAccount account = buyers.findByMobile(digits(mobile))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No buyer account for this mobile"));
        if (!MerchantService.hash(pin == null ? "" : pin.trim()).equals(account.getPinHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid PIN");
        }
        if (account.getToken() == null || account.getToken().isBlank()) {
            account.setToken(UUID.randomUUID().toString());
            account = buyers.save(account);
        }
        return toView(account);
    }

    public BuyerAccount require(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        }
        return buyers.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid buyer session"));
    }

    private Map<String, Object> toView(BuyerAccount account) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("token", account.getToken());
        view.put("name", account.getName());
        view.put("mobile", account.getMobile());
        view.put("address", account.getAddress());
        return view;
    }

    private String digits(String mobile) {
        return mobile == null ? "" : mobile.replaceAll("\\D", "");
    }
}
