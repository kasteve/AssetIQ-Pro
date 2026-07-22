package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.StockItem;
import com.stevecodes.AssetIQPro.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockItemRepository stockItemRepository;
    private final EmailService emailService;
    private final SystemSettingService settingService;

    /**
     * Retrieve all stock items
     */
    public List<StockItem> getAllStockItems() {
        return stockItemRepository.findAll();
    }

    /**
     * Get a single stock item by ID
     */
    public StockItem getStockItem(Long id) {
        return stockItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock item not found"));
    }

    /**
     * Create or update a stock item
     */
    @Transactional
    public StockItem saveStockItem(StockItem item) {
        item.setLastUpdated(LocalDateTime.now());
        item.setAlertSent(false); // reset alert flag on update
        return stockItemRepository.save(item);
    }

    /**
     * Decrease stock quantity by a given amount (e.g., when an item is issued)
     */
    @Transactional
    public StockItem deductStock(Long itemId, int amount, String reason) {
        StockItem item = getStockItem(itemId);
        if (item.getQuantity() < amount) {
            throw new RuntimeException("Insufficient stock. Available: " + item.getQuantity());
        }
        item.setQuantity(item.getQuantity() - amount);
        item.setLastUpdated(LocalDateTime.now());
        // If stock is now at or below threshold, reset alert flag so a new email can be sent
        if (item.getQuantity() <= item.getLowStockThreshold()) {
            item.setAlertSent(false);
        }
        StockItem saved = stockItemRepository.save(item);
        checkAndSendLowStockAlert(saved);
        // Optionally create a transaction log (StockTransaction entity) – not implemented here for brevity
        return saved;
    }

    /**
     * Increase stock quantity (restock)
     */
    @Transactional
    public StockItem restock(Long itemId, int amount) {
        StockItem item = getStockItem(itemId);
        item.setQuantity(item.getQuantity() + amount);
        item.setLastUpdated(LocalDateTime.now());
        // When restocked above threshold, reset alert flag
        if (item.getQuantity() > item.getLowStockThreshold()) {
            item.setAlertSent(false);
        }
        return stockItemRepository.save(item);
    }

    /**
     * Delete a stock item
     */
    @Transactional
    public void deleteStockItem(Long id) {
        stockItemRepository.deleteById(id);
    }

    /**
     * Check if a stock item is below its threshold and send email if not already alerted
     */
    private void checkAndSendLowStockAlert(StockItem item) {
        if (item.getQuantity() <= item.getLowStockThreshold() && !item.isAlertSent()) {
            sendLowStockAlert(item);
            item.setAlertSent(true);
            stockItemRepository.save(item);
        }
    }

    /**
     * Send low‑stock email alert
     */
    private void sendLowStockAlert(StockItem item) {
        String adminEmail = settingService.getString(SystemSettingService.KEY_REPORT_RECIPIENTS);
        if (adminEmail == null || adminEmail.isEmpty()) {
            adminEmail = "admin@company.com"; // fallback
        }
        String subject = "⚠️ Low Stock Alert: " + item.getName();
        String body = String.format("""
                Stock item "%s" is running low.
                Current quantity: %d
                Threshold: %d
                Unit: %s

                Please restock as soon as possible.

                Item ID: %d
                """,
                item.getName(),
                item.getQuantity(),
                item.getLowStockThreshold(),
                item.getUnit() != null ? item.getUnit() : "N/A",
                item.getId()
        );
        emailService.sendSimpleEmail(adminEmail, subject, body);
        log.info("Low‑stock alert sent for item '{}' to {}", item.getName(), adminEmail);
    }

    /**
     * Scheduled task to check all items for low stock (runs every hour)
     */
    @Scheduled(cron = "0 0 * * * *") // every hour
    public void scheduledLowStockCheck() {
        log.info("Running scheduled low‑stock check...");
        List<StockItem> items = stockItemRepository.findItemsBelowThresholdWithoutAlert();
        for (StockItem item : items) {
            checkAndSendLowStockAlert(item);
        }
    }
}