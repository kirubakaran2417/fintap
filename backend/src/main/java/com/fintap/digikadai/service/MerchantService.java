package com.fintap.digikadai.service;

import com.fintap.digikadai.domain.AuthSession;
import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.dto.AuthResponse;
import com.fintap.digikadai.dto.LoginRequest;
import com.fintap.digikadai.dto.MerchantDto;
import com.fintap.digikadai.dto.OnboardRequest;
import com.fintap.digikadai.dto.RegisterRequest;
import com.fintap.digikadai.repo.AuthSessionRepository;
import com.fintap.digikadai.repo.MerchantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchants;
    private final AuthSessionRepository sessions;

    public MerchantService(MerchantRepository merchants, AuthSessionRepository sessions) {
        this.merchants = merchants;
        this.sessions = sessions;
    }

    public AuthResponse login(LoginRequest request) {
        String mobile = digits(request.mobile());
        Merchant merchant = merchants.findByMobile(mobile)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No account for this mobile. Register once, then sign in."));
        if (!hash(request.pin().trim()).equals(merchant.getPinHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid PIN");
        }
        return session(merchant);
    }

    public AuthResponse register(RegisterRequest request) {
        String mobile = digits(request.mobile());
        var existing = merchants.findByMobile(mobile);
        if (existing.isPresent()) {
            Merchant merchant = existing.get();
            if (hash(request.pin().trim()).equals(merchant.getPinHash())) {
                return session(merchant);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This mobile is already registered. Sign in with your PIN.");
        }
        Merchant merchant = new Merchant();
        merchant.setMobile(mobile);
        merchant.setPinHash(hash(request.pin().trim()));
        merchant.setOwnerName(request.ownerName().trim());
        merchant.setShopName(request.ownerName().trim() + "'s store");
        merchant.setCategory("Grocery");
        merchant.setLanguage("en");
        merchant.setCity("");
        merchant.setOnboarded(false);
        merchants.save(merchant);
        return session(merchant);
    }

    public Merchant require(String token) {
        AuthSession session = sessions.findById(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again"));
        return merchants.findById(session.getMerchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Merchant missing"));
    }

    public MerchantDto me(Merchant merchant) {
        return toDto(merchant);
    }

    public MerchantDto onboard(Merchant merchant, OnboardRequest request) {
        merchant.setShopName(request.shopName().trim());
        merchant.setOwnerName(request.ownerName().trim());
        merchant.setCategory(request.category());
        merchant.setLanguage(request.language());
        merchant.setGstin(request.gstin());
        merchant.setAddress(request.address());
        merchant.setCity(request.city());
        merchant.setOnboarded(true);
        return toDto(merchants.save(merchant));
    }

    public void issueDemoToken(Merchant merchant, String token) {
        AuthSession session = sessions.findById(token).orElseGet(AuthSession::new);
        session.setToken(token);
        session.setMerchantId(merchant.getId());
        session.setCreatedAt(Instant.now());
        sessions.save(session);
    }

    public static String hash(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(pin.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String digits(String mobile) {
        if (mobile == null) {
            return "";
        }
        String only = mobile.replaceAll("\\D", "");
        return only.length() > 10 ? only.substring(only.length() - 10) : only;
    }

    private AuthResponse session(Merchant merchant) {
        String token = UUID.randomUUID().toString();
        AuthSession session = new AuthSession();
        session.setToken(token);
        session.setMerchantId(merchant.getId());
        sessions.save(session);
        return new AuthResponse(token, merchant.getId(), merchant.isOnboarded(), merchant.getShopName(), merchant.getLanguage());
    }

    private MerchantDto toDto(Merchant merchant) {
        return new MerchantDto(
                merchant.getId(),
                merchant.getShopName(),
                merchant.getOwnerName(),
                merchant.getMobile(),
                merchant.getCategory(),
                merchant.getLanguage(),
                merchant.getGstin(),
                merchant.getAddress(),
                merchant.getCity(),
                merchant.getBankAccountMasked(),
                merchant.isOnboarded()
        );
    }
}
