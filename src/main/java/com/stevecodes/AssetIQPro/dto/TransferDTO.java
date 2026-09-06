package com.stevecodes.AssetIQPro.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stevecodes.AssetIQPro.entity.Category;
import com.stevecodes.AssetIQPro.entity.Transfer;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferDTO {

    // ============================================
    // Basic Transfer Information
    // ============================================
    private Long transferId;
    private String assetTag;
    private String serialNumber;
    private String versionMake;
    private String modelBuild;
    private LocalDate transferDate;

    // ============================================
    // Department Information
    // ============================================
    private Integer oldDepartmentId;
    private String oldDepartmentName;
    private Integer newDepartmentId;
    private String newDepartmentName;

    // ============================================
    // Employee Information
    // ============================================
    private Long oldEmployeeId;
    private String oldEmployeeName;
    private Long newEmployeeId;
    private String newEmployeeName;

    // ============================================
    // Signer Information
    // ============================================
    private Long oldHandoverById;
    private String oldHandoverByName;
    private Long oldReceivedById;
    private String oldReceivedByName;
    private Long newHandoverById;
    private String newHandoverByName;
    private Long newReceivedById;
    private String newReceivedByName;
    private Long configuredById;
    private String configuredByName;
    private Long infraRepresentativeId;
    private String infraRepresentativeName;
    private Long financeRepresentativeId;
    private String financeRepresentativeName;

    // ============================================
    // Staff IDs
    // ============================================
    private String oldEmployeeStaffId;
    private String newEmployeeStaffId;
    private String oldHandoverByStaffId;
    private String oldReceivedByStaffId;
    private String newHandoverByStaffId;
    private String newReceivedByStaffId;
    private String configuredByStaffId;
    private String infraRepresentativeStaffId;
    private String financeRepresentativeStaffId;

    // ============================================
    // Asset Information
    // ============================================
    private Integer categoryId;
    private String categoryName;
    private Long companyId;
    private String companyName;

    // ============================================
    // Condition & Accessories
    // ============================================
    private String conditionOld;
    private String conditionNew;
    private String accessoriesOld;
    private String accessoriesNew;
    private String softwareInstalled;
    private String comments;

    // ============================================
    // Signature Status
    //
    // NOTE ON JSON NAMES: these Java fields/getters keep their original
    // names (oldHandoverSigned, infraSigned, financeSigned, etc.) so any
    // existing backend code calling these methods keeps compiling. But the
    // transfers list page's "View" modal JS reads transfer.oldHandoverBySignedAt,
    // transfer.configuredBySignedAt, transfer.infraRepSignedAt,
    // transfer.financeRepSignedAt (matching the entity's own naming), which
    // never matched these DTO fields' default JSON keys - so the signature
    // table always showed "Pending" even for fully-signed roles. The
    // @JsonProperty annotations below fix only the JSON wire name, without
    // renaming anything on the Java side.
    // ============================================
    private boolean oldHandoverSigned;
    private LocalDateTime oldHandoverSignedAt;
    private boolean oldReceivedSigned;
    private LocalDateTime oldReceivedSignedAt;
    private boolean newHandoverSigned;
    private LocalDateTime newHandoverSignedAt;
    private boolean newReceivedSigned;
    private LocalDateTime newReceivedSignedAt;
    private boolean configuredSigned;
    private LocalDateTime configuredSignedAt;
    private boolean infraSigned;
    private LocalDateTime infraSignedAt;
    private boolean financeSigned;
    private LocalDateTime financeSignedAt;

    // ============================================
    // Transfer Status
    // ============================================
    private boolean isFullySigned;
    private String fullySignedPdfUrl;

    // ============================================
    // History Linking Fields
    // ============================================
    private Long originalTransferId;
    private Long previousTransferId;
    private Integer transferSequence;
    private Boolean isInitialTransfer;

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Gets the transfer status for reports
     */
    public String getStatus() {
        if (isFullySigned) {
            return "COMPLETED";
        }

        // ✅ Check if all required signers have signed
        boolean allRequiredSigned = true;
        boolean hasAnySigner = false;

        if (oldHandoverById != null) {
            hasAnySigner = true;
            if (oldHandoverSignedAt == null) allRequiredSigned = false;
        }
        if (oldReceivedById != null) {
            hasAnySigner = true;
            if (oldReceivedSignedAt == null) allRequiredSigned = false;
        }
        if (newHandoverById != null) {
            hasAnySigner = true;
            if (newHandoverSignedAt == null) allRequiredSigned = false;
        }
        if (newReceivedById != null) {
            hasAnySigner = true;
            if (newReceivedSignedAt == null) allRequiredSigned = false;
        }
        if (configuredById != null) {
            hasAnySigner = true;
            if (configuredSignedAt == null) allRequiredSigned = false;
        }
        if (infraRepresentativeId != null) {
            hasAnySigner = true;
            if (infraSignedAt == null) allRequiredSigned = false;
        }
        if (financeRepresentativeId != null) {
            hasAnySigner = true;
            if (financeSignedAt == null) allRequiredSigned = false;
        }

        if (allRequiredSigned && hasAnySigner) {
            return "COMPLETED";
        }

        if (oldHandoverSignedAt != null || oldReceivedSignedAt != null ||
                newHandoverSignedAt != null || newReceivedSignedAt != null ||
                configuredSignedAt != null || infraSignedAt != null || financeSignedAt != null) {
            return "IN_PROGRESS";
        }
        return "PENDING";
    }

    /**
     * Gets the category type for reports
     */
    public String getCategoryType() {
        if (categoryName != null && !categoryName.isEmpty()) {
            String cat = categoryName.toUpperCase();
            if (cat.contains("ASSET") || cat.contains("EQUIPMENT") ||
                    cat.contains("IT") || cat.contains("HARDWARE") ||
                    cat.contains("LAPTOP") || cat.contains("SERVER") ||
                    cat.contains("NETWORK") || cat.contains("PERIPHERAL")) {
                return "ASSET";
            } else if (cat.contains("CASH") || cat.contains("MONEY") ||
                    cat.contains("FINANCE")) {
                return "CASH";
            } else if (cat.contains("INVENTORY") || cat.contains("STOCK") ||
                    cat.contains("SUPPLIES") || cat.contains("CONSUMABLE")) {
                return "INVENTORY";
            }
            return "OTHER";
        }
        if (assetTag != null && !assetTag.isEmpty()) {
            return "ASSET";
        }
        return "OTHER";
    }

    public void setIsFullySigned(Boolean isFullySigned) {
        this.isFullySigned = isFullySigned;
    }

    public Boolean getIsFullySigned() {
        return isFullySigned;
    }

    // ============================================
    // Getters and Setters for History Linking
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

    // ============================================
    // Getters and Setters for Signature Status
    // (Java names unchanged; @JsonProperty controls the JSON key only)
    // ============================================

    @JsonProperty("oldHandoverBySigned")
    public boolean isOldHandoverSigned() {
        return oldHandoverSigned;
    }

    public void setOldHandoverSigned(boolean oldHandoverSigned) {
        this.oldHandoverSigned = oldHandoverSigned;
    }

    @JsonProperty("oldHandoverBySignedAt")
    public LocalDateTime getOldHandoverSignedAt() {
        return oldHandoverSignedAt;
    }

    public void setOldHandoverSignedAt(LocalDateTime oldHandoverSignedAt) {
        this.oldHandoverSignedAt = oldHandoverSignedAt;
    }

    @JsonProperty("oldReceivedBySigned")
    public boolean isOldReceivedSigned() {
        return oldReceivedSigned;
    }

    public void setOldReceivedSigned(boolean oldReceivedSigned) {
        this.oldReceivedSigned = oldReceivedSigned;
    }

    @JsonProperty("oldReceivedBySignedAt")
    public LocalDateTime getOldReceivedSignedAt() {
        return oldReceivedSignedAt;
    }

    public void setOldReceivedSignedAt(LocalDateTime oldReceivedSignedAt) {
        this.oldReceivedSignedAt = oldReceivedSignedAt;
    }

    @JsonProperty("newHandoverBySigned")
    public boolean isNewHandoverSigned() {
        return newHandoverSigned;
    }

    public void setNewHandoverSigned(boolean newHandoverSigned) {
        this.newHandoverSigned = newHandoverSigned;
    }

    @JsonProperty("newHandoverBySignedAt")
    public LocalDateTime getNewHandoverSignedAt() {
        return newHandoverSignedAt;
    }

    public void setNewHandoverSignedAt(LocalDateTime newHandoverSignedAt) {
        this.newHandoverSignedAt = newHandoverSignedAt;
    }

    @JsonProperty("newReceivedBySigned")
    public boolean isNewReceivedSigned() {
        return newReceivedSigned;
    }

    public void setNewReceivedSigned(boolean newReceivedSigned) {
        this.newReceivedSigned = newReceivedSigned;
    }

    @JsonProperty("newReceivedBySignedAt")
    public LocalDateTime getNewReceivedSignedAt() {
        return newReceivedSignedAt;
    }

    public void setNewReceivedSignedAt(LocalDateTime newReceivedSignedAt) {
        this.newReceivedSignedAt = newReceivedSignedAt;
    }

    @JsonProperty("configuredBySigned")
    public boolean isConfiguredSigned() {
        return configuredSigned;
    }

    public void setConfiguredSigned(boolean configuredSigned) {
        this.configuredSigned = configuredSigned;
    }

    @JsonProperty("configuredBySignedAt")
    public LocalDateTime getConfiguredSignedAt() {
        return configuredSignedAt;
    }

    public void setConfiguredSignedAt(LocalDateTime configuredSignedAt) {
        this.configuredSignedAt = configuredSignedAt;
    }

    @JsonProperty("infraRepSigned")
    public boolean isInfraSigned() {
        return infraSigned;
    }

    public void setInfraSigned(boolean infraSigned) {
        this.infraSigned = infraSigned;
    }

    @JsonProperty("infraRepSignedAt")
    public LocalDateTime getInfraSignedAt() {
        return infraSignedAt;
    }

    public void setInfraSignedAt(LocalDateTime infraSignedAt) {
        this.infraSignedAt = infraSignedAt;
    }

    @JsonProperty("financeRepSigned")
    public boolean isFinanceSigned() {
        return financeSigned;
    }

    public void setFinanceSigned(boolean financeSigned) {
        this.financeSigned = financeSigned;
    }

    @JsonProperty("financeRepSignedAt")
    public LocalDateTime getFinanceSignedAt() {
        return financeSignedAt;
    }

    public void setFinanceSignedAt(LocalDateTime financeSignedAt) {
        this.financeSignedAt = financeSignedAt;
    }

    // ============================================
    // Getters and Setters for Basic Fields
    // ============================================

    public Long getTransferId() {
        return transferId;
    }

    public void setTransferId(Long transferId) {
        this.transferId = transferId;
    }

    public String getAssetTag() {
        return assetTag;
    }

    public void setAssetTag(String assetTag) {
        this.assetTag = assetTag;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getVersionMake() {
        return versionMake;
    }

    public void setVersionMake(String versionMake) {
        this.versionMake = versionMake;
    }

    public String getModelBuild() {
        return modelBuild;
    }

    public void setModelBuild(String modelBuild) {
        this.modelBuild = modelBuild;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public void setTransferDate(LocalDate transferDate) {
        this.transferDate = transferDate;
    }

    public Integer getOldDepartmentId() {
        return oldDepartmentId;
    }

    public void setOldDepartmentId(Integer oldDepartmentId) {
        this.oldDepartmentId = oldDepartmentId;
    }

    public String getOldDepartmentName() {
        return oldDepartmentName;
    }

    public void setOldDepartmentName(String oldDepartmentName) {
        this.oldDepartmentName = oldDepartmentName;
    }

    public Integer getNewDepartmentId() {
        return newDepartmentId;
    }

    public void setNewDepartmentId(Integer newDepartmentId) {
        this.newDepartmentId = newDepartmentId;
    }

    public String getNewDepartmentName() {
        return newDepartmentName;
    }

    public void setNewDepartmentName(String newDepartmentName) {
        this.newDepartmentName = newDepartmentName;
    }

    public Long getOldEmployeeId() {
        return oldEmployeeId;
    }

    public void setOldEmployeeId(Long oldEmployeeId) {
        this.oldEmployeeId = oldEmployeeId;
    }

    public String getOldEmployeeName() {
        return oldEmployeeName;
    }

    public void setOldEmployeeName(String oldEmployeeName) {
        this.oldEmployeeName = oldEmployeeName;
    }

    public Long getNewEmployeeId() {
        return newEmployeeId;
    }

    public void setNewEmployeeId(Long newEmployeeId) {
        this.newEmployeeId = newEmployeeId;
    }

    public String getNewEmployeeName() {
        return newEmployeeName;
    }

    public void setNewEmployeeName(String newEmployeeName) {
        this.newEmployeeName = newEmployeeName;
    }

    public Long getOldHandoverById() {
        return oldHandoverById;
    }

    public void setOldHandoverById(Long oldHandoverById) {
        this.oldHandoverById = oldHandoverById;
    }

    public String getOldHandoverByName() {
        return oldHandoverByName;
    }

    public void setOldHandoverByName(String oldHandoverByName) {
        this.oldHandoverByName = oldHandoverByName;
    }

    public Long getOldReceivedById() {
        return oldReceivedById;
    }

    public void setOldReceivedById(Long oldReceivedById) {
        this.oldReceivedById = oldReceivedById;
    }

    public String getOldReceivedByName() {
        return oldReceivedByName;
    }

    public void setOldReceivedByName(String oldReceivedByName) {
        this.oldReceivedByName = oldReceivedByName;
    }

    public Long getNewHandoverById() {
        return newHandoverById;
    }

    public void setNewHandoverById(Long newHandoverById) {
        this.newHandoverById = newHandoverById;
    }

    public String getNewHandoverByName() {
        return newHandoverByName;
    }

    public void setNewHandoverByName(String newHandoverByName) {
        this.newHandoverByName = newHandoverByName;
    }

    public Long getNewReceivedById() {
        return newReceivedById;
    }

    public void setNewReceivedById(Long newReceivedById) {
        this.newReceivedById = newReceivedById;
    }

    public String getNewReceivedByName() {
        return newReceivedByName;
    }

    public void setNewReceivedByName(String newReceivedByName) {
        this.newReceivedByName = newReceivedByName;
    }

    public Long getConfiguredById() {
        return configuredById;
    }

    public void setConfiguredById(Long configuredById) {
        this.configuredById = configuredById;
    }

    public String getConfiguredByName() {
        return configuredByName;
    }

    public void setConfiguredByName(String configuredByName) {
        this.configuredByName = configuredByName;
    }

    public Long getInfraRepresentativeId() {
        return infraRepresentativeId;
    }

    public void setInfraRepresentativeId(Long infraRepresentativeId) {
        this.infraRepresentativeId = infraRepresentativeId;
    }

    public String getInfraRepresentativeName() {
        return infraRepresentativeName;
    }

    public void setInfraRepresentativeName(String infraRepresentativeName) {
        this.infraRepresentativeName = infraRepresentativeName;
    }

    public Long getFinanceRepresentativeId() {
        return financeRepresentativeId;
    }

    public void setFinanceRepresentativeId(Long financeRepresentativeId) {
        this.financeRepresentativeId = financeRepresentativeId;
    }

    public String getFinanceRepresentativeName() {
        return financeRepresentativeName;
    }

    public void setFinanceRepresentativeName(String financeRepresentativeName) {
        this.financeRepresentativeName = financeRepresentativeName;
    }

    public String getOldEmployeeStaffId() {
        return oldEmployeeStaffId;
    }

    public void setOldEmployeeStaffId(String oldEmployeeStaffId) {
        this.oldEmployeeStaffId = oldEmployeeStaffId;
    }

    public String getNewEmployeeStaffId() {
        return newEmployeeStaffId;
    }

    public void setNewEmployeeStaffId(String newEmployeeStaffId) {
        this.newEmployeeStaffId = newEmployeeStaffId;
    }

    public String getOldHandoverByStaffId() {
        return oldHandoverByStaffId;
    }

    public void setOldHandoverByStaffId(String oldHandoverByStaffId) {
        this.oldHandoverByStaffId = oldHandoverByStaffId;
    }

    public String getOldReceivedByStaffId() {
        return oldReceivedByStaffId;
    }

    public void setOldReceivedByStaffId(String oldReceivedByStaffId) {
        this.oldReceivedByStaffId = oldReceivedByStaffId;
    }

    public String getNewHandoverByStaffId() {
        return newHandoverByStaffId;
    }

    public void setNewHandoverByStaffId(String newHandoverByStaffId) {
        this.newHandoverByStaffId = newHandoverByStaffId;
    }

    public String getNewReceivedByStaffId() {
        return newReceivedByStaffId;
    }

    public void setNewReceivedByStaffId(String newReceivedByStaffId) {
        this.newReceivedByStaffId = newReceivedByStaffId;
    }

    public String getConfiguredByStaffId() {
        return configuredByStaffId;
    }

    public void setConfiguredByStaffId(String configuredByStaffId) {
        this.configuredByStaffId = configuredByStaffId;
    }

    public String getInfraRepresentativeStaffId() {
        return infraRepresentativeStaffId;
    }

    public void setInfraRepresentativeStaffId(String infraRepresentativeStaffId) {
        this.infraRepresentativeStaffId = infraRepresentativeStaffId;
    }

    public String getFinanceRepresentativeStaffId() {
        return financeRepresentativeStaffId;
    }

    public void setFinanceRepresentativeStaffId(String financeRepresentativeStaffId) {
        this.financeRepresentativeStaffId = financeRepresentativeStaffId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getConditionOld() {
        return conditionOld;
    }

    public void setConditionOld(String conditionOld) {
        this.conditionOld = conditionOld;
    }

    public String getConditionNew() {
        return conditionNew;
    }

    public void setConditionNew(String conditionNew) {
        this.conditionNew = conditionNew;
    }

    public String getAccessoriesOld() {
        return accessoriesOld;
    }

    public void setAccessoriesOld(String accessoriesOld) {
        this.accessoriesOld = accessoriesOld;
    }

    public String getAccessoriesNew() {
        return accessoriesNew;
    }

    public void setAccessoriesNew(String accessoriesNew) {
        this.accessoriesNew = accessoriesNew;
    }

    public String getSoftwareInstalled() {
        return softwareInstalled;
    }

    public void setSoftwareInstalled(String softwareInstalled) {
        this.softwareInstalled = softwareInstalled;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getFullySignedPdfUrl() {
        return fullySignedPdfUrl;
    }

    public void setFullySignedPdfUrl(String fullySignedPdfUrl) {
        this.fullySignedPdfUrl = fullySignedPdfUrl;
    }

    // ============================================
    // Entity to DTO Conversion
    // ============================================

    /**
     * Converts from entity to DTO
     */
    public static TransferDTO fromEntity(Transfer transfer) {
        if (transfer == null) {
            return null;
        }

        TransferDTO dto = new TransferDTO();

        // Basic Information
        dto.setTransferId(transfer.getTransferId());
        dto.setAssetTag(transfer.getAssetTag());
        dto.setSerialNumber(transfer.getSerialNumber());
        dto.setVersionMake(transfer.getVersionMake());
        dto.setModelBuild(transfer.getModelBuild());
        dto.setTransferDate(transfer.getTransferDate());

        // Department
        dto.setOldDepartmentId(transfer.getOldDepartmentId());
        dto.setOldDepartmentName(transfer.getOldDepartmentName());
        dto.setNewDepartmentId(transfer.getNewDepartmentId());
        dto.setNewDepartmentName(transfer.getNewDepartmentName());

        // Employees
        dto.setOldEmployeeId(transfer.getOldEmployeeId());
        dto.setOldEmployeeName(transfer.getOldEmployeeName());
        dto.setNewEmployeeId(transfer.getNewEmployeeId());
        dto.setNewEmployeeName(transfer.getNewEmployeeName());

        // Signers
        dto.setOldHandoverById(transfer.getOldHandoverById());
        dto.setOldHandoverByName(transfer.getOldHandoverByName());
        dto.setOldReceivedById(transfer.getOldReceivedById());
        dto.setOldReceivedByName(transfer.getOldReceivedByName());
        dto.setNewHandoverById(transfer.getNewHandoverById());
        dto.setNewHandoverByName(transfer.getNewHandoverByName());
        dto.setNewReceivedById(transfer.getNewReceivedById());
        dto.setNewReceivedByName(transfer.getNewReceivedByName());
        dto.setConfiguredById(transfer.getConfiguredById());
        dto.setConfiguredByName(transfer.getConfiguredByName());
        dto.setInfraRepresentativeId(transfer.getInfraRepresentativeId());
        dto.setInfraRepresentativeName(transfer.getInfraRepresentativeName());
        dto.setFinanceRepresentativeId(transfer.getFinanceRepresentativeId());
        dto.setFinanceRepresentativeName(transfer.getFinanceRepresentativeName());

        // Staff IDs
        dto.setOldEmployeeStaffId(transfer.getOldEmployeeStaffId());
        dto.setNewEmployeeStaffId(transfer.getNewEmployeeStaffId());
        dto.setOldHandoverByStaffId(transfer.getOldHandoverByStaffId());
        dto.setOldReceivedByStaffId(transfer.getOldReceivedByStaffId());
        dto.setNewHandoverByStaffId(transfer.getNewHandoverByStaffId());
        dto.setNewReceivedByStaffId(transfer.getNewReceivedByStaffId());
        dto.setConfiguredByStaffId(transfer.getConfiguredByStaffId());
        dto.setInfraRepresentativeStaffId(transfer.getInfraRepresentativeStaffId());
        dto.setFinanceRepresentativeStaffId(transfer.getFinanceRepresentativeStaffId());

        // Asset Info - Get category name from the Category entity
        Category category = transfer.getCategory();
        if (category != null) {
            dto.setCategoryId(category.getCategoryId());
            dto.setCategoryName(category.getName());
        } else {
            dto.setCategoryName("N/A");
        }

        dto.setCompanyId(transfer.getCompanyId());

        // Condition & Accessories
        dto.setConditionOld(transfer.getConditionOld());
        dto.setConditionNew(transfer.getConditionNew());
        dto.setAccessoriesOld(transfer.getAccessoriesOld());
        dto.setAccessoriesNew(transfer.getAccessoriesNew());
        dto.setSoftwareInstalled(transfer.getSoftwareInstalled());
        dto.setComments(transfer.getComments());

        // Signature status
        dto.setOldHandoverSigned(transfer.getOldHandoverBySignedAt() != null);
        dto.setOldHandoverSignedAt(transfer.getOldHandoverBySignedAt());
        dto.setOldReceivedSigned(transfer.getOldReceivedBySignedAt() != null);
        dto.setOldReceivedSignedAt(transfer.getOldReceivedBySignedAt());
        dto.setNewHandoverSigned(transfer.getNewHandoverBySignedAt() != null);
        dto.setNewHandoverSignedAt(transfer.getNewHandoverBySignedAt());
        dto.setNewReceivedSigned(transfer.getNewReceivedBySignedAt() != null);
        dto.setNewReceivedSignedAt(transfer.getNewReceivedBySignedAt());
        dto.setConfiguredSigned(transfer.getConfiguredBySignedAt() != null);
        dto.setConfiguredSignedAt(transfer.getConfiguredBySignedAt());
        dto.setInfraSigned(transfer.getInfraRepSignedAt() != null);
        dto.setInfraSignedAt(transfer.getInfraRepSignedAt());
        dto.setFinanceSigned(transfer.getFinanceRepSignedAt() != null);
        dto.setFinanceSignedAt(transfer.getFinanceRepSignedAt());

        // Transfer Status
        dto.setIsFullySigned(transfer.getIsFullySigned());

        // History linking fields
        dto.setOriginalTransferId(transfer.getOriginalTransferId());
        dto.setPreviousTransferId(transfer.getPreviousTransferId());
        dto.setTransferSequence(transfer.getTransferSequence());
        dto.setIsInitialTransfer(transfer.getIsInitialTransfer());

        return dto;
    }
}