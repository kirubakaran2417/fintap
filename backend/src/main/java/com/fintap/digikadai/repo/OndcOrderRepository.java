package com.fintap.digikadai.repo;

import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.domain.OndcOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OndcOrderRepository extends JpaRepository<OndcOrder, Long> {
    List<OndcOrder> findByMerchantOrderByCreatedAtDesc(Merchant merchant);
}
