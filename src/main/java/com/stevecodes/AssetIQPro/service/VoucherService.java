package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.MealVoucherDTO;
import com.stevecodes.AssetIQPro.entity.MealVoucher;
import com.stevecodes.AssetIQPro.entity.MealVoucher.MealType;
import com.stevecodes.AssetIQPro.entity.MealVoucher.VoucherStatus;
import com.stevecodes.AssetIQPro.repository.MealVoucherRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final MealVoucherRepository voucherRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    private static final String QR_CODE_DIR = "./uploads/qr-codes/";

    // ============================================
    // Voucher Generation
    // ============================================

    @Transactional
    public MealVoucherDTO generateVoucher(MealVoucherDTO dto) {
        log.info("Generating meal voucher for user: {}", dto.getUserId());

        // Check if user already has voucher for today
        if (dto.getMealType() != null && dto.getUserId() != null) {
            MealType mealType = MealType.valueOf(dto.getMealType());
            boolean exists = voucherRepository.existsByUserAndMealTypeAndDate(
                    dto.getUserId(), mealType, LocalDate.now()
            );
            if (exists) {
                throw new IllegalStateException("User already has a " + mealType + " voucher for today");
            }
        }

        MealVoucher voucher = new MealVoucher();
        voucher.setVoucherCode(generateVoucherCode());
        voucher.setUserId(dto.getUserId());
        voucher.setUsername(dto.getUsername());
        voucher.setUserFullName(dto.getUserFullName());
        voucher.setDepartment(dto.getDepartment());
        voucher.setMealType(MealType.valueOf(dto.getMealType()));
        voucher.setVoucherDate(LocalDate.now());
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setGeneratedAt(LocalDateTime.now());
        voucher.setIsVisitor(dto.getIsVisitor() != null && dto.getIsVisitor());

        if (dto.getIsVisitor() != null && dto.getIsVisitor()) {
            voucher.setVisitorName(dto.getVisitorName());
            voucher.setVisitReason(dto.getVisitReason());
            voucher.setVisitingWhom(dto.getVisitingWhom());
        }

        // Generate QR code
        String qrPath = generateQRCode(voucher.getVoucherCode());
        voucher.setQrCodePath(qrPath);

        MealVoucher saved = voucherRepository.save(voucher);

        // Send email with QR code
        emailService.sendVoucherGenerated(
                getEmailForUser(dto.getUserId()),
                dto.getUserFullName(),
                saved.getVoucherCode(),
                qrPath,
                dto.getMealType()
        );

        auditService.logAction("VOUCHER_GENERATED",
                "Voucher generated: " + saved.getVoucherCode() + " for user: " + dto.getUserId(),
                dto.getUserId());

        return convertToDTO(saved);
    }

    @Transactional
    public List<MealVoucherDTO> generateBulkVouchers(List<MealVoucherDTO> vouchers) {
        log.info("Generating {} meal vouchers in bulk", vouchers.size());

        List<MealVoucherDTO> generated = new ArrayList<>();
        for (MealVoucherDTO dto : vouchers) {
            try {
                generated.add(generateVoucher(dto));
            } catch (Exception e) {
                log.error("Failed to generate voucher for user {}: {}", dto.getUserId(), e.getMessage());
            }
        }

        auditService.logAction("BULK_VOUCHERS_GENERATED",
                "Generated " + generated.size() + " vouchers in bulk", null);

        return generated;
    }

    // ============================================
    // Voucher Retrieval
    // ============================================

    public MealVoucherDTO getVoucherByCode(String voucherCode) {
        MealVoucher voucher = voucherRepository.findByVoucherCode(voucherCode)
                .orElseThrow(() -> new RuntimeException("Voucher not found: " + voucherCode));
        return convertToDTO(voucher);
    }

    public List<MealVoucherDTO> getUserVouchers(Long userId) {
        return voucherRepository.findByUserIdOrderByGeneratedAtDesc(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<MealVoucherDTO> getVouchersByDate(LocalDate date) {
        return voucherRepository.findByVoucherDateOrderByGeneratedAtDesc(date).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<MealVoucherDTO> getVouchersByDateRange(LocalDate startDate, LocalDate endDate) {
        return voucherRepository.findByDateRange(startDate, endDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ============================================
    // Voucher Usage
    // ============================================

    @Transactional
    public void useVoucher(String voucherCode) {
        log.info("Using voucher: {}", voucherCode);

        MealVoucher voucher = voucherRepository.findByVoucherCode(voucherCode)
                .orElseThrow(() -> new RuntimeException("Voucher not found: " + voucherCode));

        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new IllegalStateException("Voucher is not active. Status: " + voucher.getStatus());
        }

        voucher.setStatus(VoucherStatus.USED);
        voucher.setUsedAt(LocalDateTime.now());
        voucherRepository.save(voucher);

        auditService.logAction("VOUCHER_USED", "Voucher used: " + voucherCode, voucher.getUserId());
    }

    @Transactional
    public void cancelVoucher(String voucherCode) {
        log.info("Cancelling voucher: {}", voucherCode);

        MealVoucher voucher = voucherRepository.findByVoucherCode(voucherCode)
                .orElseThrow(() -> new RuntimeException("Voucher not found: " + voucherCode));

        if (voucher.getStatus() == VoucherStatus.USED) {
            throw new IllegalStateException("Cannot cancel a used voucher");
        }

        voucher.setStatus(VoucherStatus.CANCELLED);
        voucherRepository.save(voucher);

        auditService.logAction("VOUCHER_CANCELLED", "Voucher cancelled: " + voucherCode, voucher.getUserId());
    }

    // ============================================
    // Voucher Statistics
    // ============================================

    public Map<String, Object> getVoucherStats(LocalDate date) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("date", date.toString());
        stats.put("total", voucherRepository.countByVoucherDate(date));

        for (MealType type : MealType.values()) {
            stats.put(type.name().toLowerCase() + "_count",
                    voucherRepository.countByVoucherDateAndMealType(date, type));
        }

        stats.put("used", voucherRepository.countByVoucherDateAndStatus(date, VoucherStatus.USED));
        stats.put("active", voucherRepository.countByVoucherDateAndStatus(date, VoucherStatus.ACTIVE));
        stats.put("cancelled", voucherRepository.countByVoucherDateAndStatus(date, VoucherStatus.CANCELLED));

        return stats;
    }

    public Map<String, Object> getVoucherStatsForPeriod(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> stats = new HashMap<>();

        List<MealVoucher> vouchers = voucherRepository.findByDateRange(startDate, endDate);

        stats.put("period", startDate + " to " + endDate);
        stats.put("total", vouchers.size());
        stats.put("used", vouchers.stream().filter(v -> v.getStatus() == VoucherStatus.USED).count());
        stats.put("active", vouchers.stream().filter(v -> v.getStatus() == VoucherStatus.ACTIVE).count());
        stats.put("cancelled", vouchers.stream().filter(v -> v.getStatus() == VoucherStatus.CANCELLED).count());

        return stats;
    }

    // ============================================
    // Helper Methods
    // ============================================

    private String generateVoucherCode() {
        // Format: VCH-YYYYMMDD-XXXX
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "VCH-" + date + "-" + random;
    }

    private String generateQRCode(String voucherCode) {
        try {
            // Create directory if it doesn't exist
            Path dirPath = Paths.get(QR_CODE_DIR);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            // Generate QR code
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(voucherCode, BarcodeFormat.QR_CODE, 200, 200);

            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            // Save QR code
            String filename = voucherCode + ".png";
            Path filePath = dirPath.resolve(filename);
            ImageIO.write(qrImage, "PNG", filePath.toFile());

            return filePath.toString();

        } catch (WriterException | IOException e) {
            log.error("Failed to generate QR code for voucher: {}", voucherCode, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    private String getEmailForUser(Long userId) {
        // Get email from user service
        return "user@company.com"; // Placeholder
    }

    private MealVoucherDTO convertToDTO(MealVoucher voucher) {
        MealVoucherDTO dto = new MealVoucherDTO();
        dto.setVoucherId(voucher.getVoucherId());
        dto.setVoucherCode(voucher.getVoucherCode());
        dto.setUserId(voucher.getUserId());
        dto.setUsername(voucher.getUsername());
        dto.setUserFullName(voucher.getUserFullName());
        dto.setDepartment(voucher.getDepartment());
        dto.setMealType(voucher.getMealType().name());
        dto.setVoucherDate(voucher.getVoucherDate());
        dto.setGeneratedAt(voucher.getGeneratedAt());
        dto.setStatus(voucher.getStatus().name());
        dto.setUsedAt(voucher.getUsedAt());
        dto.setQrCodePath(voucher.getQrCodePath());
        dto.setIsVisitor(voucher.getIsVisitor());
        dto.setVisitorName(voucher.getVisitorName());
        dto.setVisitReason(voucher.getVisitReason());
        dto.setVisitingWhom(voucher.getVisitingWhom());
        return dto;
    }
}
