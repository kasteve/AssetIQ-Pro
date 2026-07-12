package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.MealVoucherDTO;
import com.stevecodes.AssetIQPro.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
@Tag(name = "Meal Vouchers", description = "Meal voucher management APIs")
public class VoucherController {

    private final VoucherService voucherService;

    // ============================================
    // Voucher Generation
    // ============================================

    @PostMapping
    @Operation(summary = "Generate a meal voucher")
    @PreAuthorize("hasAnyAuthority('GENERATE_VOUCHERS', 'ADMIN')")
    public ResponseEntity<MealVoucherDTO> generateVoucher(@Valid @RequestBody MealVoucherDTO voucherDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherService.generateVoucher(voucherDTO));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Generate multiple meal vouchers")
    @PreAuthorize("hasAnyAuthority('GENERATE_MULTIPLE_VOUCHERS', 'ADMIN')")
    public ResponseEntity<List<MealVoucherDTO>> generateBulkVouchers(@Valid @RequestBody List<MealVoucherDTO> vouchers) {
        return ResponseEntity.status(HttpStatus.CREATED).body(voucherService.generateBulkVouchers(vouchers));
    }

    // ============================================
    // Voucher Retrieval
    // ============================================

    @GetMapping("/{voucherCode}")
    @Operation(summary = "Get voucher by code")
    public ResponseEntity<MealVoucherDTO> getVoucherByCode(@PathVariable String voucherCode) {
        return ResponseEntity.ok(voucherService.getVoucherByCode(voucherCode));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get vouchers for a user")
    public ResponseEntity<List<MealVoucherDTO>> getUserVouchers(@PathVariable Long userId) {
        return ResponseEntity.ok(voucherService.getUserVouchers(userId));
    }

    @GetMapping("/date/{date}")
    @Operation(summary = "Get vouchers by date")
    public ResponseEntity<List<MealVoucherDTO>> getVouchersByDate(@PathVariable LocalDate date) {
        return ResponseEntity.ok(voucherService.getVouchersByDate(date));
    }

    @GetMapping("/date-range")
    @Operation(summary = "Get vouchers by date range")
    public ResponseEntity<List<MealVoucherDTO>> getVouchersByDateRange(@RequestParam LocalDate startDate,
                                                                       @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(voucherService.getVouchersByDateRange(startDate, endDate));
    }

    // ============================================
    // Voucher Usage
    // ============================================

    @PostMapping("/{voucherCode}/use")
    @Operation(summary = "Mark voucher as used")
    @PreAuthorize("hasAnyAuthority('GENERATE_VOUCHERS', 'ADMIN')")
    public ResponseEntity<Void> useVoucher(@PathVariable String voucherCode) {
        voucherService.useVoucher(voucherCode);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{voucherCode}/cancel")
    @Operation(summary = "Cancel a voucher")
    @PreAuthorize("hasAnyAuthority('GENERATE_VOUCHERS', 'ADMIN')")
    public ResponseEntity<Void> cancelVoucher(@PathVariable String voucherCode) {
        voucherService.cancelVoucher(voucherCode);
        return ResponseEntity.ok().build();
    }

    // ============================================
    // Voucher Statistics
    // ============================================

    @GetMapping("/stats")
    @Operation(summary = "Get voucher statistics")
    @PreAuthorize("hasAnyAuthority('VIEW_REPORTS', 'ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> getVoucherStats(@RequestParam LocalDate date) {
        return ResponseEntity.ok(voucherService.getVoucherStats(date));
    }

    @GetMapping("/stats/period")
    @Operation(summary = "Get voucher statistics for a period")
    @PreAuthorize("hasAnyAuthority('VIEW_REPORTS', 'ADMIN')")
    public ResponseEntity<java.util.Map<String, Object>> getVoucherStatsForPeriod(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        return ResponseEntity.ok(voucherService.getVoucherStatsForPeriod(startDate, endDate));
    }
}
