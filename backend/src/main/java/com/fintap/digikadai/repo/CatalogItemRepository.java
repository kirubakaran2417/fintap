package com.fintap.digikadai.repo;

import com.fintap.digikadai.domain.CatalogItem;
import com.fintap.digikadai.domain.Merchant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, Long> {
    List<CatalogItem> findByMerchantOrderByNameAsc(Merchant merchant);
}
