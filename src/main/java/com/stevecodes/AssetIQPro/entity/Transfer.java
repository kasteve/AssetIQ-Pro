package com.stevecodes.AssetIQPro.entity;

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
    private Integer transferId;

    // Asset Information
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

    // Department IDs
    @Column(name = "OldDepartmentId")
    private Integer oldDepartmentId;

    @Column(name = "NewDepartmentId")
    private Integer newDepartmentId;

    // Employee IDs
    @Column(name = "OldEmployeeId")
    private Long oldEmployeeId;

    @Column(name = "NewEmployeeId")
    private Long newEmployeeId;

    // Signer IDs
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

    // Signer Names
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

    // Asset Condition
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

    // Category and Company
    @Column(name = "CategoryId")
    private Integer categoryId;

    @Column(name = "CompanyId")
    private Long companyId;

    // Legacy signature fields
    @Column(name = "FromEmployeeSignature")
    private String fromEmployeeSignature;

    @Column(name = "FromEmployeeSignedAt")
    private LocalDateTime fromEmployeeSignedAt;

    @Column(name = "ToEmployeeSignature")
    private String toEmployeeSignature;

    @Column(name = "ToEmployeeSignedAt")
    private LocalDateTime toEmployeeSignedAt;

    // Signature fields (Base64)
    @Column(name = "OldHandoverBySignature")
    private String oldHandoverBySignature;

    @Column(name = "OldHandoverBySignedAt")
    private LocalDateTime oldHandoverBySignedAt;

    @Column(name = "OldReceivedBySignature")
    private String oldReceivedBySignature;

    @Column(name = "OldReceivedBySignedAt")
    private LocalDateTime oldReceivedBySignedAt;

    @Column(name = "NewHandoverBySignature")
    private String newHandoverBySignature;

    @Column(name = "NewHandoverBySignedAt")
    private LocalDateTime newHandoverBySignedAt;

    @Column(name = "NewReceivedBySignature")
    private String newReceivedBySignature;

    @Column(name = "NewReceivedBySignedAt")
    private LocalDateTime newReceivedBySignedAt;

    @Column(name = "ConfiguredBySignature")
    private String configuredBySignature;

    @Column(name = "ConfiguredBySignedAt")
    private LocalDateTime configuredBySignedAt;

    @Column(name = "InfraRepSignature")
    private String infraRepSignature;

    @Column(name = "InfraRepSignedAt")
    private LocalDateTime infraRepSignedAt;

    @Column(name = "FinanceRepSignature")
    private String financeRepSignature;

    @Column(name = "FinanceRepSignedAt")
    private LocalDateTime financeRepSignedAt;

    // PDF
    @Column(name = "FullySignedPDF")
    private String fullySignedPdf;

    @Column(name = "IsFullySigned")
    private Boolean isFullySigned = false;

    @Transient
    private String oldDepartmentName;

    @Transient
    private String newDepartmentName;

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

    // Add this for the template
    public boolean getFullySigned() {
        return Boolean.TRUE.equals(isFullySigned);
    }

    // Setter for PDF with correct name
    public void setFullySignedPDF(String pdfBase64) {
        this.fullySignedPdf = pdfBase64;
    }
}