package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AssetCsvRowDTO {
    private String tag;
    private String name;
    private String serialNumber;
    private String categoryName;
    private String supplierName;
    private String locationName;
    private String purchaseDate;
    private String purchaseCost;
    private String status;
    private Integer warrantyYears;
    private String warrantyEndDate;
    private String eolDate;
    private Integer warrantyNotificationDays;
    private Integer eolNotificationDays;
    private String currentDepartment;
    private Integer lifespanYears;

    // Validation errors
    private String errorMessage;
    private boolean hasError;
    private Integer rowNumber;
}