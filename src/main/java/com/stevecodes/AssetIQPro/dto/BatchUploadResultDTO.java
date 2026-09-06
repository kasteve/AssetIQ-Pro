package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class BatchUploadResultDTO {
    private int totalRecords;
    private int successfulRecords;
    private int failedRecords;
    private List<AssetCsvRowDTO> errors = new ArrayList<>();
    private List<String> successMessages = new ArrayList<>();
    private boolean hasErrors;

    public void addError(AssetCsvRowDTO error) {
        errors.add(error);
        failedRecords++;
        hasErrors = true;
    }

    public void addSuccess(String message) {
        successMessages.add(message);
        successfulRecords++;
    }
}