package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class TransferDTO {

    private Integer transferId;
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

    public void setIsFullySigned(Boolean isFullySigned) {
        this.isFullySigned = isFullySigned;
    }
}
