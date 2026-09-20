package com.fintap.digikadai.repo;

import com.fintap.digikadai.domain.CustomerProfile;
import com.fintap.digikadai.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByMerchantAndToken(Merchant merchant, String token);

    List<CustomerProfile> findByMerchantOrderByLifetimeSpendDesc(Merchant merchant);
}
