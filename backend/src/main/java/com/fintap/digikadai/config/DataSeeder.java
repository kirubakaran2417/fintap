package com.fintap.digikadai.config;

import com.fintap.digikadai.domain.CatalogItem;
import com.fintap.digikadai.domain.CustomerProfile;
import com.fintap.digikadai.domain.Insight;
import com.fintap.digikadai.domain.KhataEntry;
import com.fintap.digikadai.domain.Merchant;
import com.fintap.digikadai.domain.OndcOrder;
import com.fintap.digikadai.domain.OndcOrderStatus;
import com.fintap.digikadai.domain.Payment;
import com.fintap.digikadai.domain.PaymentRail;
import com.fintap.digikadai.domain.TransactionStatus;
import com.fintap.digikadai.repo.CatalogItemRepository;
import com.fintap.digikadai.repo.CustomerProfileRepository;
import com.fintap.digikadai.repo.InsightRepository;
import com.fintap.digikadai.repo.KhataEntryRepository;
import com.fintap.digikadai.repo.MerchantRepository;
import com.fintap.digikadai.repo.OndcOrderRepository;
import com.fintap.digikadai.repo.PaymentRepository;
import com.fintap.digikadai.service.MerchantService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(
            MerchantRepository merchants,
            PaymentRepository payments,
            CatalogItemRepository catalog,
            OndcOrderRepository orders,
            KhataEntryRepository khata,
            InsightRepository insights,
            CustomerProfileRepository profiles,
            MerchantService merchantService,
            JdbcTemplate jdbcTemplate
    ) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE ondc_orders ALTER COLUMN status SET DATA TYPE VARCHAR(64)");
            } catch (Exception e1) {
                try {
                    jdbcTemplate.execute("ALTER TABLE ondc_orders ALTER COLUMN status VARCHAR(64)");
                } catch (Exception ignored) {}
            }
            if (merchants.findByMobile("9876543210").isPresent()) {
                Merchant existing = merchants.findByMobile("9876543210").orElseThrow();
                merchantService.issueDemoToken(existing, "demo-token");
                return;
            }
            Merchant shop = new Merchant();
            shop.setShopName("Lakshmi Kirana");
            shop.setOwnerName("Lakshmi");
            shop.setMobile("9876543210");
            shop.setPinHash(MerchantService.hash("1234"));
            shop.setCategory("Grocery");
            shop.setLanguage("hi");
            shop.setGstin("29AABCU9603R1ZX");
            shop.setAddress("12 Market Road");
            shop.setCity("Bengaluru");
            shop.setBankAccountMasked("ABC Bank ****4412");
            shop.setOnboarded(true);
            merchants.save(shop);
            merchantService.issueDemoToken(shop, "demo-token");

            addPayment(payments, shop, "542.00", PaymentRail.CARD, "Ramesh", Instant.now().minus(40, ChronoUnit.MINUTES));
            addPayment(payments, shop, "180.00", PaymentRail.UPI, "Walk-in", Instant.now().minus(90, ChronoUnit.MINUTES));
            addPayment(payments, shop, "1493.00", PaymentRail.CARD, "Priya S", Instant.now().minus(3, ChronoUnit.HOURS));

            CustomerProfile ramesh = new CustomerProfile();
            ramesh.setMerchant(shop);
            ramesh.setToken("tok_ramesh");
            ramesh.setDisplayName("Ramesh");
            ramesh.setVisitCount(11);
            ramesh.setLifetimeSpend(new BigDecimal("8420"));
            ramesh.setChurnRisk(0.08);
            profiles.save(ramesh);

            CustomerProfile priya = new CustomerProfile();
            priya.setMerchant(shop);
            priya.setToken("tok_priya");
            priya.setDisplayName("Priya S");
            priya.setVisitCount(4);
            priya.setLifetimeSpend(new BigDecimal("2180"));
            priya.setLastVisit(Instant.now().minus(20, ChronoUnit.DAYS));
            priya.setChurnRisk(0.61);
            profiles.save(priya);

            addItem(catalog, shop, "Tata Salt 1kg", "8901030865366", "Grocery", "28", "26", 40, true);
            addItem(catalog, shop, "Parle-G 800g", "8901491101030", "Snacks", "90", "84", 24, true);
            addItem(catalog, shop, "Surf Excel 500g", "8901725111924", "Home care", "99", "92", 18, false);
            addItem(catalog, shop, "Aashirvaad Atta 5kg", "8901725992211", "Grocery", "270", "255", 10, true);

            addOrder(orders, shop, "ONDC-4418", "Mystore", "Atta 5kg + Salt", "281.00", OndcOrderStatus.NEW);
            addOrder(orders, shop, "ONDC-4402", "Paytm", "Parle-G x4", "336.00", OndcOrderStatus.PACKED);
            addOrder(orders, shop, "ONDC-4388", "PhonePe", "Surf Excel", "92.00", OndcOrderStatus.DELIVERED);

            addKhata(khata, shop, "Anil Tea Stall", "9845011122", "1200", false, "Weekly ration");
            addKhata(khata, shop, "Meena", "9845099988", "450", false, "Rice bag");
            addKhata(khata, shop, "Anil Tea Stall", "9845011122", "400", true, "Partial repayment");

            Insight one = new Insight();
            one.setMerchant(shop);
            one.setType("routing");
            one.setTitle("Ask for card on baskets above ₹200");
            one.setBodyEn("Today 3 customers spent above ₹200. Nudge card tap — estimated extra MDR ₹32 without hurting them.");
            one.setBodyHi("आज 3 ग्राहकों ने ₹200 से अधिक खरीदा। कार्ड टैप करवाएँ — दुकान को लगभग ₹32 अतिरिक्त मिलेगा।");
            one.setBodyTa("இன்று 3 வாடிக்கையாளர் ₹200க்கு மேல் வாங்கினார்கள். கார்டு தட்டச் சொல்லுங்கள்.");
            one.setBodyTe("ఈరోజు 3 మంది ₹200 పైన కొనుగోలు చేశారు. కార్డ్ ట్యాప్ చేయమని అడగండి.");
            insights.save(one);

            Insight two = new Insight();
            two.setMerchant(shop);
            two.setType("churn");
            two.setTitle("Priya has not visited in 20 days");
            two.setBodyEn("Priya usually buys atta and oil. Offer ₹10 off on the next card tap to bring her back.");
            two.setBodyHi("प्रिया 20 दिन से नहीं आईं। आटा और तेल पर ₹10 छूट दें — कार्ड से भुगतान पर।");
            two.setBodyTa("பிரியா 20 நாட்களாக வரவில்லை. அடுத்த கார்டு செலுத்தலில் ₹10 தள்ளுபடி கொடுங்கள்.");
            two.setBodyTe("ప్రియ 20 రోజులుగా రాలేదు. తర్వాతి కార్డ్ చెల్లింపుపై ₹10 తగ్గింపు ఇవ్వండి.");
            insights.save(two);

            Insight three = new Insight();
            three.setMerchant(shop);
            three.setType("ondc");
            three.setTitle("Publish Surf Excel to ONDC");
            three.setBodyEn("In-store velocity is high but the SKU is not on ONDC. One tap publish can add evening orders from 6 buyer apps.");
            three.setBodyHi("सर्फ एक्सेल दुकान में चल रहा है, ONDC पर नहीं है। एक टैप में लिस्ट करें।");
            three.setBodyTa("Surf Excel கடையில் நன்றாக விற்கிறது. ONDC-ல் இன்னும் இல்லை.");
            three.setBodyTe("సర్ఫ్ ఎక్సెల్ దుకాణంలో బాగా అమ్ముడవుతోంది. ONDCలో లేదు — ఒక్క ట్యాప్‌తో పెట్టండి.");
            insights.save(three);
        };
    }

    private void addPayment(PaymentRepository repo, Merchant shop, String amount, PaymentRail rail, String name, Instant at) {
        Payment payment = new Payment();
        payment.setMerchant(shop);
        payment.setAmount(new BigDecimal(amount));
        payment.setRail(rail);
        payment.setStatus(TransactionStatus.SUCCESS);
        payment.setCustomerLabel(name);
        payment.setReference(rail == PaymentRail.CARD ? "TAP-DEMO" : "UPI-DEMO");
        payment.setCreatedAt(at);
        repo.save(payment);
    }

    private void addItem(CatalogItemRepository repo, Merchant shop, String name, String barcode, String category, String mrp, String price, int stock, boolean published) {
        CatalogItem item = new CatalogItem();
        item.setMerchant(shop);
        item.setName(name);
        item.setBarcode(barcode);
        item.setCategory(category);
        item.setMrp(new BigDecimal(mrp));
        item.setSellingPrice(new BigDecimal(price));
        item.setStock(stock);
        item.setPublishedToOndc(published);
        item.setDescription("Seed catalogue SKU");
        repo.save(item);
    }

    private void addOrder(OndcOrderRepository repo, Merchant shop, String ref, String app, String items, String amount, OndcOrderStatus status) {
        OndcOrder order = new OndcOrder();
        order.setMerchant(shop);
        order.setOrderRef(ref);
        order.setBuyerApp(app);
        order.setItemsSummary(items);
        order.setAmount(new BigDecimal(amount));
        order.setStatus(status);
        repo.save(order);
    }

    private void addKhata(KhataEntryRepository repo, Merchant shop, String name, String mobile, String amount, boolean credit, String note) {
        KhataEntry entry = new KhataEntry();
        entry.setMerchant(shop);
        entry.setCustomerName(name);
        entry.setMobile(mobile);
        entry.setAmount(new BigDecimal(amount));
        entry.setCredit(credit);
        entry.setNote(note);
        repo.save(entry);
    }
}
