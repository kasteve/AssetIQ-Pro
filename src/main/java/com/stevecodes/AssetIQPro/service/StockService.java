package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.StockCategory;
import com.stevecodes.AssetIQPro.entity.StockItem;
import com.stevecodes.AssetIQPro.repository.StockCategoryRepository;
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
    private final StockCategoryRepository stockCategoryRepository;
    private final EmailService emailService;
    private final SystemSettingService settingService;

    // ============================================
    // Category Methods
    // ============================================

    public List<StockCategory> getAllCategories() {
        return stockCategoryRepository.findAllByOrderByNameAsc();
    }

    public StockCategory getCategory(Long id) {
        return stockCategoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    @Transactional
    public StockCategory createCategory(String name, String description) {
        if (stockCategoryRepository.existsByName(name)) {
            throw new RuntimeException("Category with name '" + name + "' already exists");
        }
        StockCategory category = new StockCategory(name, description);
        return stockCategoryRepository.save(category);
    }

    @Transactional
    public StockCategory updateCategory(Long id, String name, String description) {
        StockCategory category = getCategory(id);
        // Check if new name conflicts with existing (except itself)
        if (!category.getName().equals(name) && stockCategoryRepository.existsByName(name)) {
            throw new RuntimeException("Category with name '" + name + "' already exists");
        }
        category.setName(name);
        category.setDescription(description);
        return stockCategoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        StockCategory category = getCategory(id);
        // Check if category has items
        if (!category.getItems().isEmpty()) {
            throw new RuntimeException("Cannot delete category with existing stock items. Move or delete items first.");
        }
        stockCategoryRepository.delete(category);
    }

    // ============================================
    // Stock Item Methods
    // ============================================

    public List<StockItem> getAllStockItems() {
        return stockItemRepository.findAllOrderByCategoryAndName();
    }

    public List<StockItem> getStockItemsByCategory(Long categoryId) {
        return stockItemRepository.findByCategoryIdOrderByNameAsc(categoryId);
    }

    public StockItem getStockItem(Long id) {
        return stockItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock item not found"));
    }

    @Transactional
    public StockItem saveStockItem(StockItem item) {
        item.setLastUpdated(LocalDateTime.now());
        item.setAlertSent(false);

        // Validate category if provided
        if (item.getCategory() != null && item.getCategory().getId() != null) {
            StockCategory category = getCategory(item.getCategory().getId());
            item.setCategory(category);
        }

        return stockItemRepository.save(item);
    }

    @Transactional
    public StockItem createStockItem(String name, String description, Integer quantity,
                                     String unit, Integer lowStockThreshold, Long categoryId) {
        StockCategory category = categoryId != null ? getCategory(categoryId) : null;

        StockItem item = new StockItem();
        item.setName(name);
        item.setDescription(description);
        item.setQuantity(quantity != null ? quantity : 0);
        item.setUnit(unit);
        item.setLowStockThreshold(lowStockThreshold != null ? lowStockThreshold : 5);
        item.setCategory(category);
        item.setLastUpdated(LocalDateTime.now());

        return stockItemRepository.save(item);
    }

    @Transactional
    public StockItem deductStock(Long itemId, int amount, String reason) {
        StockItem item = getStockItem(itemId);
        if (item.getQuantity() < amount) {
            throw new RuntimeException("Insufficient stock. Available: " + item.getQuantity());
        }
        item.setQuantity(item.getQuantity() - amount);
        item.setLastUpdated(LocalDateTime.now());
        if (item.getQuantity() <= item.getLowStockThreshold()) {
            item.setAlertSent(false);
        }
        StockItem saved = stockItemRepository.save(item);
        checkAndSendLowStockAlert(saved);
        return saved;
    }

    @Transactional
    public StockItem restock(Long itemId, int amount) {
        StockItem item = getStockItem(itemId);
        item.setQuantity(item.getQuantity() + amount);
        item.setLastUpdated(LocalDateTime.now());
        if (item.getQuantity() > item.getLowStockThreshold()) {
            item.setAlertSent(false);
        }
        return stockItemRepository.save(item);
    }

    @Transactional
    public void deleteStockItem(Long id) {
        stockItemRepository.deleteById(id);
    }

    public void checkAndSendLowStockAlert(StockItem item) {
        if (item.getQuantity() <= item.getLowStockThreshold() && !item.isAlertSent()) {
            sendLowStockAlert(item);
            item.setAlertSent(true);
            stockItemRepository.save(item);
        }
    }

    private void sendLowStockAlert(StockItem item) {
        String adminEmail = settingService.getString(SystemSettingService.KEY_REPORT_RECIPIENTS);
        if (adminEmail == null || adminEmail.isEmpty()) {
            adminEmail = "admin@company.com";
        }
        String subject = "⚠️ Low Stock Alert: " + item.getName();
        String categoryName = item.getCategory() != null ? item.getCategory().getName() : "Uncategorized";
        String body = String.format("""
                Stock item "%s" is running low.
                Category: %s
                Current quantity: %d
                Threshold: %d
                Unit: %s

                Please restock as soon as possible.

                Item ID: %d
                """,
                item.getName(),
                categoryName,
                item.getQuantity(),
                item.getLowStockThreshold(),
                item.getUnit() != null ? item.getUnit() : "N/A",
                item.getId()
        );
        emailService.sendSimpleEmail(adminEmail, subject, body);
        log.info("Low-stock alert sent for item '{}' to {}", item.getName(), adminEmail);
    }

    @Scheduled(cron = "0 0 * * * *")
    public void scheduledLowStockCheck() {
        log.info("Running scheduled low-stock check...");
        List<StockItem> items = stockItemRepository.findItemsBelowThresholdWithoutAlert();
        for (StockItem item : items) {
            checkAndSendLowStockAlert(item);
        }
    }
}