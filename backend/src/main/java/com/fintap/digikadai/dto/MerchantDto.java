package com.fintap.digikadai.dto;

public record MerchantDto(
        Long id,
        String shopName,
        String ownerName,
        String mobile,
        String category,
        String language,
        String gstin,
        String address,
        String city,
        String bankAccountMasked,
        boolean onboarded
) {
}
