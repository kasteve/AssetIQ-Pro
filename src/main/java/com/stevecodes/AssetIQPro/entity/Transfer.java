package com.stevecodes.AssetIQPro.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "Transfers", schema = "dbo")
@Data
@NoArgsConstructor
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transferId")
    private Long transferId;

    // ============================================
    // HISTORY LINKING FIELDS
    // ============================================

    @Column(name = "originalTransferId")
    private Long originalTransferId;

    @Column(name = "previousTransferId")
    private Long previousTransferId;

    @Column(name = "transferSequence")
    private Integer transferSequence = 1;

    @Column(name = "isInitialTransfer")
    private Boolean isInitialTransfer = false;

    // ============================================
    // Asset Information
    // ============================================
    @Column(name = "assetTag")
    private String assetTag;

    @Column(name = "serialNumber")
    private String serialNumber;

    @Column(name = "versionMake")
    private String versionMake;

    @Column(name = "modelBuild")
    private String modelBuild;

    @Column(name = "transferDate")
    private LocalDate transferDate;

    // ============================================
    // Category - Add @JsonIgnore to prevent circular reference
    // ============================================
    @ManyToOne
    @JoinColumn(name = "CategoryId", referencedColumnName = "CategoryId")
    @JsonIgnore
    private Category category;

    // ============================================
    // Company
    // ============================================
    @Column(name = "CompanyId")
    private Long companyId;

    // ============================================
    // Department IDs
    // ============================================
    @Column(name = "OldDepartmentId")
    private Integer oldDepartmentId;

    @Column(name = "NewDepartmentId")
    private Integer newDepartmentId;

    // ============================================
    // Employee IDs
    // ============================================
    @Column(name = "OldEmployeeId")
    private Long oldEmployeeId;

    @Column(name = "NewEmployeeId")
    private Long newEmployeeId;

    // ============================================
    // Signer IDs
    // ============================================
    @Column(name = "OldHandoverById")
    private Long oldHandoverById;

    @Column(name = "OldReceivedById")
    private Long oldReceivedById;

    @Column(name = "NewHandoverById")
    private Long newHandoverById;

    @Column(name = "NewReceivedById")
    private Long newReceivedById;

    @Column(name = "ConfiguredById")
    private Long configuredById;

    @Column(name = "InfraRepresentativeId")
    private Long infraRepresentativeId;

    @Column(name = "FinanceRepresentativeId")
    private Long financeRepresentativeId;

    // ============================================
    // Staff IDs
    // ============================================
    @Column(name = "oldEmployeeStaffId")
    private String oldEmployeeStaffId;

    @Column(name = "newEmployeeStaffId")
    private String newEmployeeStaffId;

    @Column(name = "oldHandoverByStaffId")
    private String oldHandoverByStaffId;

    @Column(name = "oldReceivedByStaffId")
    private String oldReceivedByStaffId;

    @Column(name = "newHandoverByStaffId")
    private String newHandoverByStaffId;

    @Column(name = "newReceivedByStaffId")
    private String newReceivedByStaffId;

    @Column(name = "configuredByStaffId")
    private String configuredByStaffId;

    @Column(name = "infraRepresentativeStaffId")
    private String infraRepresentativeStaffId;

    @Column(name = "financeRepresentativeStaffId")
    private String financeRepresentativeStaffId;

    // ============================================
    // Signer Names
    // ============================================
    @Column(name = "oldHandoverByName")
    private String oldHandoverByName;

    @Column(name = "oldReceivedByName")
    private String oldReceivedByName;

    @Column(name = "newHandoverByName")
    private String newHandoverByName;

    @Column(name = "newReceivedByName")
    private String newReceivedByName;

    @Column(name = "configuredByName")
    private String configuredByName;

    @Column(name = "InfraRepresentativeName")
    private String infraRepresentativeName;

    @Column(name = "FinanceRepresentativeName")
    private String financeRepresentativeName;

    // ============================================
    // Asset Condition
    // ============================================
    @Column(name = "conditionOld")
    private String conditionOld;

    @Column(name = "conditionNew")
    private String conditionNew;

    @Column(name = "accessoriesOld")
    private String accessoriesOld;

    @Column(name = "accessoriesNew")
    private String accessoriesNew;

    @Column(name = "softwareInstalled")
    private String softwareInstalled;

    @Column(name = "comments")
    private String comments;

    // ============================================
    // Signature Fields
    // ============================================
    @Column(name = "FromEmployeeSignature")
    private String fromEmployeeSignature;

    @Column(name = "FromEmployeeSignedAt")
    private LocalDateTime fromEmployeeSignedAt;

    @Column(name = "ToEmployeeSignature")
    private String toEmployeeSignature;

    @Column(name = "ToEmployeeSignedAt")
    private LocalDateTime toEmployeeSignedAt;

    @Lob
    @Column(name = "OldHandoverBySignature", columnDefinition = "NVARCHAR(MAX)")
    private String oldHandoverBySignature;

    @Column(name = "OldHandoverBySignedAt")
    private LocalDateTime oldHandoverBySignedAt;

    @Lob
    @Column(name = "OldReceivedBySignature", columnDefinition = "NVARCHAR(MAX)")
    private String oldReceivedBySignature;

    @Column(name = "OldReceivedBySignedAt")
    private LocalDateTime oldReceivedBySignedAt;

    @Lob
    @Column(name = "NewHandoverBySignature", columnDefinition = "NVARCHAR(MAX)")
    private String newHandoverBySignature;

    @Column(name = "NewHandoverBySignedAt")
    private LocalDateTime newHandoverBySignedAt;

    @Lob
    @Column(name = "NewReceivedBySignature", columnDefinition = "NVARCHAR(MAX)")
    private String newReceivedBySignature;

    @Column(name = "NewReceivedBySignedAt")
    private LocalDateTime newReceivedBySignedAt;

    @Lob
    @Column(name = "ConfiguredBySignature", columnDefinition = "NVARCHAR(MAX)")
    private String configuredBySignature;

    @Column(name = "ConfiguredBySignedAt")
    private LocalDateTime configuredBySignedAt;

    @Lob
    @Column(name = "InfraRepSignature", columnDefinition = "NVARCHAR(MAX)")
    private String infraRepSignature;

    @Column(name = "InfraRepSignedAt")
    private LocalDateTime infraRepSignedAt;

    @Lob
    @Column(name = "FinanceRepSignature", columnDefinition = "NVARCHAR(MAX)")
    private String financeRepSignature;

    @Column(name = "FinanceRepSignedAt")
    private LocalDateTime financeRepSignedAt;

    // ============================================
    // PDF
    // ============================================
    @Lob
    @Column(name = "FullySignedPDF", columnDefinition = "NVARCHAR(MAX)")
    private String fullySignedPdf;

    @Column(name = "IsFullySigned")
    private Boolean isFullySigned = false;

    // ============================================
    // Transient Fields
    // ============================================
    @Transient
    private String oldDepartmentName;

    @Transient
    private String newDepartmentName;

    @Transient
    private String oldEmployeeName;

    @Transient
    private String newEmployeeName;

    // ============================================
    // Helper Methods
    // ============================================

    public boolean isSignedForRole(String role) {
        if (role == null) return false;
        switch (role) {
            case "OLD_HANDOVER": return oldHandoverBySignedAt != null;
            case "OLD_RECEIVED": return oldReceivedBySignedAt != null;
            case "NEW_HANDOVER": return newHandoverBySignedAt != null;
            case "NEW_RECEIVED": return newReceivedBySignedAt != null;
            case "CONFIGURED_BY": return configuredBySignedAt != null;
            case "INFRA_REP": return infraRepSignedAt != null;
            case "FINANCE_REP": return financeRepSignedAt != null;
            default: return false;
        }
    }

    public boolean isFullySigned() {
        return Boolean.TRUE.equals(isFullySigned);
    }

    public boolean getFullySigned() {
        return Boolean.TRUE.equals(isFullySigned);
    }

    public String getStatus() {
        // ✅ First check if fully signed flag is true
        if (Boolean.TRUE.equals(isFullySigned)) {
            return "COMPLETED";
        }

        // ✅ Check if all required signatures are present
        boolean allRequiredSignersSigned = true;
        boolean hasAnySigner = false;

        if (oldHandoverById != null) {
            hasAnySigner = true;
            if (oldHandoverBySignedAt == null) allRequiredSignersSigned = false;
        }
        if (oldReceivedById != null) {
            hasAnySigner = true;
            if (oldReceivedBySignedAt == null) allRequiredSignersSigned = false;
        }
        if (newHandoverById != null) {
            hasAnySigner = true;
            if (newHandoverBySignedAt == null) allRequiredSignersSigned = false;
        }
        if (newReceivedById != null) {
            hasAnySigner = true;
            if (newReceivedBySignedAt == null) allRequiredSignersSigned = false;
        }
        if (configuredById != null) {
            hasAnySigner = true;
            if (configuredBySignedAt == null) allRequiredSignersSigned = false;
        }
        if (infraRepresentativeId != null) {
            hasAnySigner = true;
            if (infraRepSignedAt == null) allRequiredSignersSigned = false;
        }
        if (financeRepresentativeId != null) {
            hasAnySigner = true;
            if (financeRepSignedAt == null) allRequiredSignersSigned = false;
        }

        // If all required signers have signed, mark as completed
        if (allRequiredSignersSigned && hasAnySigner) {
            isFullySigned = true;
            return "COMPLETED";
        }

        // Check if any signatures are present (in progress)
        if (oldHandoverBySignedAt != null || oldReceivedBySignedAt != null ||
                newHandoverBySignedAt != null || newReceivedBySignedAt != null ||
                configuredBySignedAt != null || infraRepSignedAt != null || financeRepSignedAt != null) {
            return "IN_PROGRESS";
        }

        return "PENDING";
    }

    public String getCategoryType() {
        if (category != null) {
            String categoryName = category.getName();
            if (categoryName != null) {
                String cat = categoryName.toUpperCase();
                if (cat.equals("LAPTOP") || cat.equals("HARDWARE2") ||
                        cat.equals("SERVER") || cat.equals("NETWORK") ||
                        cat.equals("HARDWARE12")) {
                    return "ASSET";
                } else if (cat.equals("CONSUMABLE") || cat.equals("TESTCATEGORY")) {
                    return "INVENTORY";
                } else if (cat.equals("ANOTHER ONE") || cat.equals("TP")) {
                    return "OTHER";
                }
                return "ASSET";
            }
        }
        if (assetTag != null && !assetTag.isEmpty()) {
            return "ASSET";
        }
        return "OTHER";
    }

    public String getTransferType() {
        return getCategoryType();
    }

    public String getAsset() {
        return assetTag != null ? assetTag : "N/A";
    }

    public String getAmount() {
        return "N/A";
    }

    public String getFromLocation() {
        return oldDepartmentName != null ? oldDepartmentName : "N/A";
    }

    public String getToLocation() {
        return newDepartmentName != null ? newDepartmentName : "N/A";
    }

    public LocalDate getCreatedAt() {
        return transferDate;
    }

    public String getCategoryName() {
        return category != null ? category.getName() : "N/A";
    }

    public Integer getCategoryId() {
        return category != null ? category.getCategoryId() : null;
    }

    // ============================================
    // Getters and Setters for missing fields
    // ============================================

    public String getOldHandoverByName() {
        return oldHandoverByName != null ? oldHandoverByName : "";
    }

    public String getOldReceivedByName() {
        return oldReceivedByName != null ? oldReceivedByName : "";
    }

    public String getNewHandoverByName() {
        return newHandoverByName != null ? newHandoverByName : "";
    }

    public String getNewReceivedByName() {
        return newReceivedByName != null ? newReceivedByName : "";
    }

    public String getConfiguredByName() {
        return configuredByName != null ? configuredByName : "";
    }

    public String getInfraRepresentativeName() {
        return infraRepresentativeName != null ? infraRepresentativeName : "";
    }

    public String getFinanceRepresentativeName() {
        return financeRepresentativeName != null ? financeRepresentativeName : "";
    }

    public String getOldDepartmentName() {
        return oldDepartmentName != null ? oldDepartmentName : "";
    }

    public void setOldDepartmentName(String oldDepartmentName) {
        this.oldDepartmentName = oldDepartmentName;
    }

    public String getNewDepartmentName() {
        return newDepartmentName != null ? newDepartmentName : "";
    }

    public void setNewDepartmentName(String newDepartmentName) {
        this.newDepartmentName = newDepartmentName;
    }

    public String getOldEmployeeName() {
        return oldEmployeeName != null ? oldEmployeeName : "";
    }

    public void setOldEmployeeName(String oldEmployeeName) {
        this.oldEmployeeName = oldEmployeeName;
    }

    public String getNewEmployeeName() {
        return newEmployeeName != null ? newEmployeeName : "";
    }

    public void setNewEmployeeName(String newEmployeeName) {
        this.newEmployeeName = newEmployeeName;
    }

    public void setFullySignedPDF(String pdfBase64) {
        this.fullySignedPdf = pdfBase64;
    }

    public String getFullySignedPDF() {
        return fullySignedPdf;
    }

    // ============================================
    // GETTERS AND SETTERS FOR HISTORY LINKING FIELDS
    // ============================================

    public Long getOriginalTransferId() {
        return originalTransferId;
    }

    public void setOriginalTransferId(Long originalTransferId) {
        this.originalTransferId = originalTransferId;
    }

    public Long getPreviousTransferId() {
        return previousTransferId;
    }

    public void setPreviousTransferId(Long previousTransferId) {
        this.previousTransferId = previousTransferId;
    }

    public Integer getTransferSequence() {
        return transferSequence != null ? transferSequence : 1;
    }

    public void setTransferSequence(Integer transferSequence) {
        this.transferSequence = transferSequence;
    }

    public Boolean getIsInitialTransfer() {
        return isInitialTransfer != null ? isInitialTransfer : false;
    }

    public void setIsInitialTransfer(Boolean isInitialTransfer) {
        this.isInitialTransfer = isInitialTransfer;
    }

    public boolean isInitialTransfer() {
        return Boolean.TRUE.equals(isInitialTransfer);
    }

    // ============================================
    // Category getter/setter (for the ENTITY)
    // ============================================

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}