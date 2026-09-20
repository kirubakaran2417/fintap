package com.fintap.digikadai.repo;

import com.fintap.digikadai.domain.KhataEntry;
import com.fintap.digikadai.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KhataEntryRepository extends JpaRepository<KhataEntry, Long> {
    List<KhataEntry> findByMerchantOrderByCreatedAtDesc(Merchant merchant);
}
