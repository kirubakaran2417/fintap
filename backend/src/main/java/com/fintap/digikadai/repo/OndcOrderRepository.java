package com.fintap.digikadai.repo;

import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.domain.OndcOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OndcOrderRepository extends JpaRepository<OndcOrder, Long> {
    List<OndcOrder> findByMerchantOrderByCreatedAtDesc(Merchant merchant);
    boolean existsByMerchantAndOrderRef(Merchant merchant, String orderRef);
    Optional<OndcOrder> findByOrderRef(String orderRef);
    List<OndcOrder> findAllByOrderRef(String orderRef);
    Optional<OndcOrder> findByTransactionId(String transactionId);
    boolean existsByMessageId(String messageId);
}
