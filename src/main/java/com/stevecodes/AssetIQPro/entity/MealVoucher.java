package com.stevecodes.AssetIQPro.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_meal_vouchers")
@Data
@NoArgsConstructor
public class MealVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "voucher_id")
    private Long voucherId;

    @Column(name = "voucher_code", unique = true, nullable = false, length = 50)
    private String voucherCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "user_full_name")
    private String userFullName;

    @Column(name = "department")
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    @CreationTimestamp
    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private VoucherStatus status = VoucherStatus.ACTIVE;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "qr_code_path", length = 500)
    private String qrCodePath;

    @Column(name = "is_visitor")
    private Boolean isVisitor = false;

    @Column(name = "visitor_name", length = 255)
    private String visitorName;

    @Column(name = "visit_reason", length = 500)
    private String visitReason;

    @Column(name = "visiting_whom", length = 255)
    private String visitingWhom;

    public enum MealType {
        BREAKFAST, LUNCH, BREAK
    }

    public enum VoucherStatus {
        ACTIVE, USED, CANCELLED, EXPIRED
    }

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = VoucherStatus.ACTIVE;
        }
        if (voucherDate == null) {
            voucherDate = LocalDate.now();
        }
        if (isVisitor == null) {
            isVisitor = false;
        }
    }

    public String getUserFullName() {
        if (userFullName != null && !userFullName.isEmpty()) {
            return userFullName;
        }
        return username;
    }

    public String getDisplayName() {
        if (isVisitor != null && isVisitor && visitorName != null && !visitorName.isEmpty()) {
            return visitorName + " (Visitor)";
        }
        return getUserFullName();
    }
}