package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class MealVoucherDTO {

    private Long voucherId;
    private String voucherCode;
    private Long userId;
    private String username;
    private String userFullName;
    private String department;
    private String mealType;
    private LocalDate voucherDate;
    private LocalDateTime generatedAt;
    private String status;
    private LocalDateTime usedAt;
    private String qrCodePath;

    // Visitor info
    private Boolean isVisitor = false;
    private String visitorName;
    private String visitReason;
    private String visitingWhom;

    // For generation
    private Integer quantity;
    private String generatedBy;
}
