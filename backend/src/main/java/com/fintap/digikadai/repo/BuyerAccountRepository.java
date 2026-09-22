package com.fintap.digikadai.repo;

import com.fintap.digikadai.domain.BuyerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuyerAccountRepository extends JpaRepository<BuyerAccount, Long> {
    Optional<BuyerAccount> findByMobile(String mobile);
    Optional<BuyerAccount> findByToken(String token);
}
