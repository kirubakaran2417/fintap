package com.fintap.digikadai.repo;

import com.fintap.digikadai.domain.Insight;
import com.fintap.digikadai.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsightRepository extends JpaRepository<Insight, Long> {
    List<Insight> findByMerchantOrderByCreatedAtDesc(Merchant merchant);
}
