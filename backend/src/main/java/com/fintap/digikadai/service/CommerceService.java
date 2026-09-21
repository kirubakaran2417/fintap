package com.fintap.digikadai.service;

import com.fintap.digikadai.domain.CatalogItem;
import com.fintap.digikadai.domain.KhataEntry;
import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.domain.OndcOrder;
import com.fintap.digikadai.domain.OndcOrderStatus;
import com.fintap.digikadai.domain.Payment;
import com.fintap.digikadai.domain.PaymentRail;
import com.fintap.digikadai.domain.TransactionStatus;
import com.fintap.digikadai.dto.CatalogGenerateRequest;
import com.fintap.digikadai.dto.CatalogItemDto;
import com.fintap.digikadai.dto.CustomerProfileDto;
import com.fintap.digikadai.dto.HomeSummaryDto;
import com.fintap.digikadai.dto.InsightDto;
import com.fintap.digikadai.dto.KhataDto;
import com.fintap.digikadai.dto.OndcOrderDto;
import com.fintap.digikadai.repo.CatalogItemRepository;
import com.fintap.digikadai.repo.CustomerProfileRepository;
import com.fintap.digikadai.repo.InsightRepository;
import com.fintap.digikadai.repo.KhataEntryRepository;
import com.fintap.digikadai.repo.OndcOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CommerceService {

    private static final Map<String, CatalogItemDto.CreateRequest> BARCODES = Map.of(
            "8901030865366", new CatalogItemDto.CreateRequest("Tata Salt 1kg", "8901030865366", "Grocery", new BigDecimal("28"), new BigDecimal("26"), 40, "Iodised salt — high weekly velocity SKU"),
            "8901491101030", new CatalogItemDto.CreateRequest("Parle-G 800g", "8901491101030", "Snacks", new BigDecimal("90"), new BigDecimal("84"), 24, "Biscuit staple with strong repeat demand"),
            "8901725111924", new CatalogItemDto.CreateRequest("Surf Excel 500g", "8901725111924", "Home care", new BigDecimal("99"), new BigDecimal("92"), 18, "Detergent — price 4% below local average")
    );

    private final CatalogItemRepository catalog;
    private final OndcOrderRepository orders;
    private final KhataEntryRepository khata;
    private final InsightRepository insights;
    private final CustomerProfileRepository profiles;
    private final PaymentService payments;
    private final com.fintap.digikadai.integration.ondc.OndcNetworkService ondcNetwork;

    public CommerceService(
            CatalogItemRepository catalog,
            OndcOrderRepository orders,
            KhataEntryRepository khata,
            InsightRepository insights,
            CustomerProfileRepository profiles,
            PaymentService payments,
            com.fintap.digikadai.integration.ondc.OndcNetworkService ondcNetwork
    ) {
        this.catalog = catalog;
        this.orders = orders;
        this.khata = khata;
        this.insights = insights;
        this.profiles = profiles;
        this.payments = payments;
        this.ondcNetwork = ondcNetwork;
    }

    public HomeSummaryDto home(Merchant merchant) {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.now(zone);
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant weekStart = today.minusDays(6).atStartOfDay(zone).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();

        List<Payment> all = payments.all(merchant);
        List<Payment> successful = all.stream().filter(p -> p.getStatus() == TransactionStatus.SUCCESS).toList();
        List<Payment> todayPays = successful.stream()
                .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(dayStart))
                .toList();

        BigDecimal revenue = sum(todayPays);
        long customers = todayPays.stream().map(Payment::getCustomerLabel).distinct().count();
        BigDecimal weekRevenue = sum(successful.stream()
                .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(weekStart))
                .toList());
        BigDecimal monthRevenue = sum(successful.stream()
                .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(monthStart))
                .toList());
        BigDecimal cardToday = sum(todayPays.stream().filter(p -> p.getRail() == PaymentRail.CARD).toList());
        BigDecimal upiToday = sum(todayPays.stream().filter(p -> p.getRail() == PaymentRail.UPI).toList());
        long pending = all.stream().filter(p -> p.getStatus() == TransactionStatus.PENDING).count();

        List<OndcOrder> ondcOrders = orders.findByMerchantOrderByCreatedAtDesc(merchant);
        BigDecimal ondc = ondcOrders.stream().map(OndcOrder::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<OndcOrderStatus> open = EnumSet.of(
                OndcOrderStatus.NEW, OndcOrderStatus.ACCEPTED, OndcOrderStatus.PACKED, OndcOrderStatus.DISPATCHED);
        long ondcOpen = ondcOrders.stream().filter(o -> open.contains(o.getStatus())).count();

        var items = catalog.findByMerchantOrderByNameAsc(merchant);
        long published = items.stream().filter(CatalogItem::isPublishedToOndc).count();

        BigDecimal outstanding = khata.findByMerchantOrderByCreatedAtDesc(merchant).stream()
                .map(entry -> entry.isCredit() ? entry.getAmount().negate() : entry.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<HomeSummaryDto.DayPoint> last7 = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Instant start = day.atStartOfDay(zone).toInstant();
            Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
            List<Payment> dayPays = successful.stream()
                    .filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(start) && p.getCreatedAt().isBefore(end))
                    .toList();
            last7.add(new HomeSummaryDto.DayPoint(
                    day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    sum(dayPays),
                    dayPays.size()
            ));
        }

        String nudge = payments.route(new BigDecimal("350")).reason();
        return new HomeSummaryDto(
                revenue,
                customers,
                weekRevenue,
                monthRevenue,
                cardToday,
                upiToday,
                pending,
                ondc,
                ondcOpen,
                published,
                items.size(),
                outstanding,
                nudge,
                last7,
                payments.recent(merchant).stream().limit(8).toList()
        );
    }

    private BigDecimal sum(List<Payment> rows) {
        return rows.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<CatalogItemDto> catalog(Merchant merchant) {
        return catalog.findByMerchantOrderByNameAsc(merchant).stream().map(this::toCatalog).toList();
    }

    public CatalogItemDto addItem(Merchant merchant, CatalogItemDto.CreateRequest request) {
        CatalogItem item = new CatalogItem();
        item.setMerchant(merchant);
        item.setName(request.name());
        item.setBarcode(request.barcode());
        item.setCategory(request.category());
        item.setMrp(request.mrp());
        item.setSellingPrice(request.sellingPrice());
        item.setStock(request.stock());
        item.setDescription(request.description());
        return toCatalog(catalog.save(item));
    }

    public CatalogItemDto generateFromScan(Merchant merchant, CatalogGenerateRequest request) {
        CatalogItemDto.CreateRequest seed = BARCODES.getOrDefault(
                request.barcode() == null ? "" : request.barcode(),
                new CatalogItemDto.CreateRequest(
                        request.shelfHint() == null || request.shelfHint().isBlank() ? "Kirana pack" : request.shelfHint(),
                        request.barcode(),
                        "Grocery",
                        new BigDecimal("50"),
                        new BigDecimal("47"),
                        12,
                        "AI listing from barcode + shelf photo. Price set 6% below MRP for ONDC conversion."
                )
        );
        return addItem(merchant, seed);
    }

    public CatalogItemDto publish(Merchant merchant, Long itemId) {
        CatalogItem item = catalog.findById(itemId)
                .filter(found -> found.getMerchant().getId().equals(merchant.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
        item.setPublishedToOndc(true);
        return toCatalog(catalog.save(item));
    }

    public List<OndcOrderDto> ondcOrders(Merchant merchant) {
        List<OndcOrder> current = orders.findByMerchantOrderByCreatedAtDesc(merchant);
        boolean shouldAutoInject = current.isEmpty() || current.stream().noneMatch(o -> o.getStatus() == OndcOrderStatus.NEW) ||
                (current.get(0).getCreatedAt() != null && current.get(0).getCreatedAt().isBefore(Instant.now().minusSeconds(25)));
        if (shouldAutoInject) {
            try {
                ondcNetwork.simulateIncomingOrder(merchant);
                current = orders.findByMerchantOrderByCreatedAtDesc(merchant);
            } catch (Exception ignored) {}
        }
        return current.stream().map(this::toOrder).toList();
    }

    public OndcOrderDto updateOrder(Merchant merchant, Long id, String statusStr) {
        return updateOrder(merchant, id, parseStatus(statusStr));
    }

    public OndcOrderDto updateOrder(Merchant merchant, Long id, OndcOrderStatus status) {
        OndcOrder order = orders.findById(id)
                .filter(found -> found.getMerchant() == null || found.getMerchant().getId().equals(merchant.getId()))
                .orElseGet(() -> {
                    List<OndcOrder> list = orders.findByMerchantOrderByCreatedAtDesc(merchant);
                    if (!list.isEmpty()) return list.get(0);
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
                });
        order.setStatus(status == null ? OndcOrderStatus.DUNZO_PICKUP : status);
        order.setUpdatedAt(Instant.now());
        return toOrder(orders.save(order));
    }

    private OndcOrderStatus parseStatus(String input) {
        if (input == null || input.isBlank()) return OndcOrderStatus.DUNZO_PICKUP;
        String clean = input.trim().toUpperCase();
        switch (clean) {
            case "DUNZO":
            case "DUNZO_PICKUP":
            case "ASSIGNED_DUNZO":
            case "DUNZO_DISPATCH":
            case "DISPATCHED_DUNZO":
                return OndcOrderStatus.DUNZO_PICKUP;
            case "ACCEPT":
            case "ACCEPTED":
                return OndcOrderStatus.ACCEPTED;
            case "PACK":
            case "PACKED":
                return OndcOrderStatus.PACKED;
            case "DISPATCH":
            case "DISPATCHED":
                return OndcOrderStatus.DISPATCHED;
            case "DELIVER":
            case "DELIVERED":
                return OndcOrderStatus.DELIVERED;
            case "CANCEL":
            case "CANCELLED":
            case "REJECTED":
                return OndcOrderStatus.CANCELLED;
            case "NEW":
                return OndcOrderStatus.NEW;
            default:
                try {
                    return OndcOrderStatus.valueOf(clean);
                } catch (Exception e) {
                    return OndcOrderStatus.DUNZO_PICKUP;
                }
        }
    }

    public OndcOrderDto simulateOndcOrder(Merchant merchant) {
        OndcOrder simulated = ondcNetwork.simulateIncomingOrder(merchant);
        return toOrder(simulated);
    }

    public List<KhataDto> khata(Merchant merchant) {
        return khata.findByMerchantOrderByCreatedAtDesc(merchant).stream().map(this::toKhata).toList();
    }

    public KhataDto addKhata(Merchant merchant, KhataDto.CreateRequest request) {
        KhataEntry entry = new KhataEntry();
        entry.setMerchant(merchant);
        entry.setCustomerName(request.customerName());
        entry.setMobile(request.mobile());
        entry.setAmount(request.amount());
        entry.setCredit(request.credit());
        entry.setNote(request.note());
        return toKhata(khata.save(entry));
    }

    public List<InsightDto> insights(Merchant merchant, String lang) {
        String code = lang == null ? merchant.getLanguage() : lang;
        return insights.findByMerchantOrderByCreatedAtDesc(merchant).stream()
                .map(insight -> new InsightDto(insight.getId(), insight.getTitle(), body(insight, code), insight.getType(), insight.getCreatedAt()))
                .toList();
    }

    public List<CustomerProfileDto> customers(Merchant merchant) {
        return profiles.findByMerchantOrderByLifetimeSpendDesc(merchant).stream()
                .map(p -> new CustomerProfileDto(p.getToken(), p.getDisplayName(), p.getVisitCount(), p.getLifetimeSpend(), p.getChurnRisk()))
                .toList();
    }

    private String body(com.fintap.digikadai.domain.Insight insight, String lang) {
        String code = lang == null ? "en" : lang.toLowerCase(Locale.ROOT);
        return switch (code) {
            case "hi", "hindi" -> insight.getBodyHi();
            case "ta", "tamil" -> insight.getBodyTa();
            case "te", "telugu" -> insight.getBodyTe();
            default -> insight.getBodyEn();
        };
    }

    private CatalogItemDto toCatalog(CatalogItem item) {
        return new CatalogItemDto(
                item.getId(),
                item.getName(),
                item.getBarcode(),
                item.getCategory(),
                item.getMrp(),
                item.getSellingPrice(),
                item.getStock(),
                item.isPublishedToOndc(),
                item.getDescription()
        );
    }

    private OndcOrderDto toOrder(OndcOrder order) {
        return new OndcOrderDto(
                order.getId(),
                order.getOrderRef(),
                order.getBuyerApp(),
                order.getItemsSummary(),
                order.getAmount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }

    private KhataDto toKhata(KhataEntry entry) {
        return new KhataDto(
                entry.getId(),
                entry.getCustomerName(),
                entry.getMobile(),
                entry.getAmount(),
                entry.isCredit(),
                entry.getNote(),
                entry.getCreatedAt()
        );
    }
}
