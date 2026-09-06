package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.AssetCsvRowDTO;
import com.stevecodes.AssetIQPro.dto.BatchUploadResultDTO;
import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.Category;
import com.stevecodes.AssetIQPro.entity.Location;
import com.stevecodes.AssetIQPro.entity.Supplier;
import com.stevecodes.AssetIQPro.repository.AssetRepository;
import com.stevecodes.AssetIQPro.repository.CategoryRepository;
import com.stevecodes.AssetIQPro.repository.LocationRepository;
import com.stevecodes.AssetIQPro.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetCsvImportService {

    private final AssetRepository assetRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final LocationRepository locationRepository;
    private final AuditService auditService;

    // ✅ Support multiple date formats
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private static final Set<String> VALID_STATUSES = Set.of(
            "AVAILABLE", "ASSIGNED", "MAINTENANCE", "RETIRED", "TRANSFERRED"
    );

    /**
     * Validate and import assets from CSV file
     */
    @Transactional
    public BatchUploadResultDTO importAssetsFromCsv(MultipartFile file, Long userId) {
        log.info("Starting CSV import for file: {}", file.getOriginalFilename());

        BatchUploadResultDTO result = new BatchUploadResultDTO();
        List<AssetCsvRowDTO> validatedRows = new ArrayList<>();

        try {
            // Parse CSV
            List<AssetCsvRowDTO> parsedRows = parseCsvFile(file);
            result.setTotalRecords(parsedRows.size());

            // Validate each row
            for (AssetCsvRowDTO row : parsedRows) {
                validateRow(row);
                if (row.isHasError()) {
                    result.addError(row);
                } else {
                    validatedRows.add(row);
                }
            }

            // Import validated rows
            if (!validatedRows.isEmpty()) {
                importValidatedRows(validatedRows, result, userId);
            }

            log.info("CSV import completed: {} successful, {} failed out of {}",
                    result.getSuccessfulRecords(), result.getFailedRecords(), result.getTotalRecords());

        } catch (Exception e) {
            log.error("Error processing CSV file: {}", e.getMessage(), e);
            AssetCsvRowDTO errorRow = new AssetCsvRowDTO();
            errorRow.setHasError(true);
            errorRow.setErrorMessage("Failed to process file: " + e.getMessage());
            result.addError(errorRow);
        }

        return result;
    }

    /**
     * Parse CSV file into rows
     */
    private List<AssetCsvRowDTO> parseCsvFile(MultipartFile file) throws Exception {
        List<AssetCsvRowDTO> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreHeaderCase()
                     .withTrim())) {

            int rowNumber = 1;
            for (CSVRecord record : csvParser) {
                rowNumber++;
                AssetCsvRowDTO row = new AssetCsvRowDTO();
                row.setRowNumber(rowNumber);

                try {
                    row.setTag(getValue(record, "tag"));
                    row.setName(getValue(record, "name"));
                    row.setSerialNumber(getValue(record, "serialNumber"));
                    row.setCategoryName(getValue(record, "category"));
                    row.setSupplierName(getValue(record, "supplier"));
                    row.setLocationName(getValue(record, "location"));
                    row.setPurchaseDate(getValue(record, "purchaseDate"));
                    row.setPurchaseCost(getValue(record, "purchaseCost"));
                    row.setStatus(getValue(record, "status"));
                    row.setWarrantyYears(parseInteger(getValue(record, "warrantyYears")));
                    row.setWarrantyEndDate(getValue(record, "warrantyEndDate"));
                    row.setEolDate(getValue(record, "eolDate"));
                    row.setWarrantyNotificationDays(parseInteger(getValue(record, "warrantyNotificationDays")));
                    row.setEolNotificationDays(parseInteger(getValue(record, "eolNotificationDays")));
                    row.setCurrentDepartment(getValue(record, "department"));
                    row.setLifespanYears(parseInteger(getValue(record, "lifespanYears")));
                } catch (Exception e) {
                    row.setHasError(true);
                    row.setErrorMessage("Failed to parse row: " + e.getMessage());
                }

                rows.add(row);
            }
        }

        return rows;
    }

    /**
     * Get value from CSV record, handling null/empty
     */
    private String getValue(CSVRecord record, String columnName) {
        try {
            String value = record.get(columnName);
            return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parse integer with error handling
     */
    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * ✅ Parse date with multiple formats
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        dateStr = dateStr.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }

        // If all formatters fail, throw exception with helpful message
        throw new DateTimeParseException(
                "Invalid date format: " + dateStr + ". Supported formats: yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy, MM/dd/yyyy",
                dateStr, 0);
    }

    /**
     * Validate a single row
     */
    private void validateRow(AssetCsvRowDTO row) {
        List<String> errors = new ArrayList<>();

        // Mandatory fields
        if (isEmpty(row.getTag())) {
            errors.add("Asset Tag is required");
        } else if (assetRepository.existsByTag(row.getTag())) {
            errors.add("Asset Tag '" + row.getTag() + "' already exists");
        }

        if (isEmpty(row.getName())) {
            errors.add("Asset Name is required");
        }

        if (isEmpty(row.getSerialNumber())) {
            errors.add("Serial Number is required");
        }

        // Validate status
        if (!isEmpty(row.getStatus()) && !VALID_STATUSES.contains(row.getStatus().toUpperCase())) {
            errors.add("Invalid status: " + row.getStatus() + ". Valid values: " + VALID_STATUSES);
        }

        // ✅ Validate dates with multiple formats
        if (!isEmpty(row.getPurchaseDate())) {
            try {
                parseDate(row.getPurchaseDate());
            } catch (DateTimeParseException e) {
                errors.add("Invalid purchase date format: " + row.getPurchaseDate() +
                        ". Supported formats: yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy, MM/dd/yyyy");
            }
        }

        if (!isEmpty(row.getWarrantyEndDate())) {
            try {
                parseDate(row.getWarrantyEndDate());
            } catch (DateTimeParseException e) {
                errors.add("Invalid warranty end date format: " + row.getWarrantyEndDate() +
                        ". Supported formats: yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy, MM/dd/yyyy");
            }
        }

        if (!isEmpty(row.getEolDate())) {
            try {
                parseDate(row.getEolDate());
            } catch (DateTimeParseException e) {
                errors.add("Invalid EOL date format: " + row.getEolDate() +
                        ". Supported formats: yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy, MM/dd/yyyy");
            }
        }

        // Validate numbers
        if (!isEmpty(row.getPurchaseCost())) {
            try {
                new BigDecimal(row.getPurchaseCost());
            } catch (NumberFormatException e) {
                errors.add("Invalid purchase cost: " + row.getPurchaseCost());
            }
        }

        // ✅ Check if category exists (warning only - will be skipped if not found)
        if (!isEmpty(row.getCategoryName())) {
            boolean categoryExists = categoryRepository.findByNameIgnoreCase(row.getCategoryName()).isPresent();
            if (!categoryExists) {
                errors.add("Category '" + row.getCategoryName() + "' not found. Please create it first or leave blank.");
            }
        }

        // ✅ Check if supplier exists (warning only - will be skipped if not found)
        if (!isEmpty(row.getSupplierName())) {
            boolean supplierExists = supplierRepository.findByNameIgnoreCase(row.getSupplierName()).isPresent();
            if (!supplierExists) {
                errors.add("Supplier '" + row.getSupplierName() + "' not found. Please create it first or leave blank.");
            }
        }

        // ✅ Check if location exists (warning only - will be skipped if not found)
        if (!isEmpty(row.getLocationName())) {
            boolean locationExists = locationRepository.findByNameIgnoreCase(row.getLocationName()).isPresent();
            if (!locationExists) {
                errors.add("Location '" + row.getLocationName() + "' not found. Please create it first or leave blank.");
            }
        }

        // Set error status
        if (!errors.isEmpty()) {
            row.setHasError(true);
            row.setErrorMessage(String.join("; ", errors));
        }
    }

    /**
     * Import validated rows - uses direct repository save to avoid circular dependency
     */
    private void importValidatedRows(List<AssetCsvRowDTO> rows, BatchUploadResultDTO result, Long userId) {
        for (AssetCsvRowDTO row : rows) {
            try {
                Asset asset = convertRowToAsset(row);
                Asset saved = assetRepository.save(asset);

                result.addSuccess("Asset '" + saved.getTag() + "' imported successfully");

                auditService.logAction("ASSET_CSV_IMPORTED",
                        "Asset imported via CSV: " + saved.getTag(),
                        userId);

            } catch (Exception e) {
                log.error("Error importing asset row {}: {}", row.getRowNumber(), e.getMessage());
                row.setHasError(true);
                row.setErrorMessage("Import failed: " + e.getMessage());
                result.addError(row);
            }
        }
    }

    /**
     * Convert CSV row to Asset entity
     */
    private Asset convertRowToAsset(AssetCsvRowDTO row) {
        Asset asset = new Asset();
        asset.setTag(row.getTag());
        asset.setName(row.getName());
        asset.setSerialNumber(row.getSerialNumber());

        // Category - only set if exists
        if (!isEmpty(row.getCategoryName())) {
            categoryRepository.findByNameIgnoreCase(row.getCategoryName())
                    .ifPresent(asset::setCategory);
        }

        // Supplier - only set if exists
        if (!isEmpty(row.getSupplierName())) {
            supplierRepository.findByNameIgnoreCase(row.getSupplierName())
                    .ifPresent(asset::setSupplier);
        }

        // Location - only set if exists
        if (!isEmpty(row.getLocationName())) {
            locationRepository.findByNameIgnoreCase(row.getLocationName())
                    .ifPresent(asset::setLocation);
        }

        // ✅ Purchase Date - parse with multiple formats
        if (!isEmpty(row.getPurchaseDate())) {
            try {
                asset.setPurchaseDate(parseDate(row.getPurchaseDate()));
            } catch (DateTimeParseException e) {
                // Already validated
            }
        }

        // Purchase Cost
        if (!isEmpty(row.getPurchaseCost())) {
            try {
                asset.setPurchaseCost(new BigDecimal(row.getPurchaseCost()));
            } catch (NumberFormatException e) {
                // Already validated
            }
        }

        // Status
        if (!isEmpty(row.getStatus())) {
            try {
                asset.setStatus(Asset.AssetStatus.valueOf(row.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Already validated
            }
        }

        // Warranty Years
        if (row.getWarrantyYears() != null) {
            asset.setWarrantyYears(row.getWarrantyYears());
        }

        // ✅ Warranty End Date - parse with multiple formats
        if (!isEmpty(row.getWarrantyEndDate())) {
            try {
                asset.setWarrantyEndDate(parseDate(row.getWarrantyEndDate()));
            } catch (DateTimeParseException e) {
                // Already validated
            }
        }

        // Auto-calc warranty end date from purchase date + warranty years
        if (asset.getPurchaseDate() != null && asset.getWarrantyYears() != null && asset.getWarrantyEndDate() == null) {
            asset.setWarrantyEndDate(asset.getPurchaseDate().plusYears(asset.getWarrantyYears()));
        }

        // ✅ EOL Date - parse with multiple formats
        if (!isEmpty(row.getEolDate())) {
            try {
                asset.setEolDate(parseDate(row.getEolDate()));
            } catch (DateTimeParseException e) {
                // Already validated
            }
        }

        // Auto-calc EOL date from purchase date + lifespan years
        if (asset.getPurchaseDate() != null && row.getLifespanYears() != null && asset.getEolDate() == null) {
            asset.setEolDate(asset.getPurchaseDate().plusYears(row.getLifespanYears()));
        }

        // Notification Days
        if (row.getWarrantyNotificationDays() != null) {
            asset.setWarrantyNotificationDays(row.getWarrantyNotificationDays());
        } else {
            asset.setWarrantyNotificationDays(30);
        }

        if (row.getEolNotificationDays() != null) {
            asset.setEolNotificationDays(row.getEolNotificationDays());
        } else {
            asset.setEolNotificationDays(30);
        }

        // Department
        if (!isEmpty(row.getCurrentDepartment())) {
            asset.setCurrentDepartment(row.getCurrentDepartment());
        }

        // Lifespan Years
        if (row.getLifespanYears() != null) {
            asset.setLifespanYears(row.getLifespanYears());
        }

        // Set default status if not provided
        if (asset.getStatus() == null) {
            asset.setStatus(Asset.AssetStatus.AVAILABLE);
        }

        return asset;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}