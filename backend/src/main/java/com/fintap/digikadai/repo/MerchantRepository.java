package com.fintap.digikadai.repo;

import com.fintap.digikadai.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {
    Optional<Merchant> findByMobile(String mobile);
}
