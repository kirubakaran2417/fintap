package com.fintap.digikadai.repo;

import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.domain.Payment;
import com.fintap.digikadai.domain.PaymentRail;
import com.fintap.digikadai.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByMerchantOrderByCreatedAtDesc(Merchant merchant);

    List<Payment> findByMerchantAndCreatedAtAfterAndStatus(
            Merchant merchant, Instant after, TransactionStatus status);

    java.util.Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    long countByMerchantAndRailAndCreatedAtAfter(Merchant merchant, PaymentRail rail, Instant after);
}
