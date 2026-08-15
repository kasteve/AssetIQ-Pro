package com.stevecodes.AssetIQPro.dto;

import com.stevecodes.AssetIQPro.entity.Transfer;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class TransferDTO {

    // FIXED: Changed from Integer to Long to match entity
    private Long transferId;
    private String assetTag;
    private String serialNumber;
    private String versionMake;
    private String modelBuild;
    private LocalDate transferDate;

    // Department
    private Integer oldDepartmentId;
    private String oldDepartmentName;
    private Integer newDepartmentId;
    private String newDepartmentName;

    // Employees
    private Long oldEmployeeId;
    private String oldEmployeeName;
    private Long newEmployeeId;
    private String newEmployeeName;

    // Signers
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
    private String oldEmployeeStaffId;
    private String newEmployeeStaffId;
    private String oldHandoverByStaffId;
    private String oldReceivedByStaffId;
    private String newHandoverByStaffId;
    private String newReceivedByStaffId;
    private String configuredByStaffId;
    private String infraRepresentativeStaffId;
    private String financeRepresentativeStaffId;

    // Asset Info
    private Integer categoryId;
    private String categoryName;
    private Long companyId;
    private String companyName;

    // Condition
    private String conditionOld;
    private String conditionNew;
    private String accessoriesOld;
    private String accessoriesNew;
    private String softwareInstalled;
    private String comments;

    // Signatures
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

    private boolean isFullySigned;
    private String fullySignedPdfUrl;

    // ============================================
    // ADDED: Status and Category methods for reports
    // ============================================

    /**
     * Gets the transfer status (for reports)
     */
    public String getStatus() {
        if (isFullySigned) {
            return "COMPLETED";
        }
        // Check if any signatures are present
        if (oldHandoverSignedAt != null || oldReceivedSignedAt != null ||
                newHandoverSignedAt != null || newReceivedSignedAt != null ||
                configuredSignedAt != null || infraSignedAt != null || financeSignedAt != null) {
            return "IN_PROGRESS";
        }
        return "PENDING";
    }

    /**
     * Gets the category (for reports)
     */
    public String getCategory() {
        // First check the categoryName field
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
        // If no category name, check asset tag
        if (assetTag != null && !assetTag.isEmpty()) {
            return "ASSET";
        }
        return "OTHER";
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    public void setIsFullySigned(Boolean isFullySigned) {
        this.isFullySigned = isFullySigned;
    }

    public Boolean getIsFullySigned() {
        return isFullySigned;
    }

    /**
     * Converts from entity to DTO
     */
    public static TransferDTO fromEntity(Transfer transfer) {
        TransferDTO dto = new TransferDTO();
        dto.setTransferId(transfer.getTransferId());
        dto.setAssetTag(transfer.getAssetTag());
        dto.setSerialNumber(transfer.getSerialNumber());
        dto.setVersionMake(transfer.getVersionMake());
        dto.setModelBuild(transfer.getModelBuild());
        dto.setTransferDate(transfer.getTransferDate());

        dto.setOldDepartmentId(transfer.getOldDepartmentId());
        dto.setOldDepartmentName(transfer.getOldDepartmentName());
        dto.setNewDepartmentId(transfer.getNewDepartmentId());
        dto.setNewDepartmentName(transfer.getNewDepartmentName());

        dto.setOldEmployeeId(transfer.getOldEmployeeId());
        dto.setOldEmployeeName(transfer.getOldEmployeeName());
        dto.setNewEmployeeId(transfer.getNewEmployeeId());
        dto.setNewEmployeeName(transfer.getNewEmployeeName());

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

        dto.setOldEmployeeStaffId(transfer.getOldEmployeeStaffId());
        dto.setNewEmployeeStaffId(transfer.getNewEmployeeStaffId());
        dto.setOldHandoverByStaffId(transfer.getOldHandoverByStaffId());
        dto.setOldReceivedByStaffId(transfer.getOldReceivedByStaffId());
        dto.setNewHandoverByStaffId(transfer.getNewHandoverByStaffId());
        dto.setNewReceivedByStaffId(transfer.getNewReceivedByStaffId());
        dto.setConfiguredByStaffId(transfer.getConfiguredByStaffId());
        dto.setInfraRepresentativeStaffId(transfer.getInfraRepresentativeStaffId());
        dto.setFinanceRepresentativeStaffId(transfer.getFinanceRepresentativeStaffId());

        // FIXED: Category is a String in Transfer entity, not a Category object
        dto.setCategoryName(transfer.getCategory());

        dto.setCompanyId(transfer.getCompanyId());

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

        dto.setIsFullySigned(transfer.getIsFullySigned());

        return dto;
    }
}