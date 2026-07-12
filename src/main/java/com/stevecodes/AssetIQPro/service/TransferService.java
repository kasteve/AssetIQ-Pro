package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.AssetHistory;
import com.stevecodes.AssetIQPro.entity.Employee;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.repository.AssetHistoryRepository;
import com.stevecodes.AssetIQPro.repository.AssetRepository;
import com.stevecodes.AssetIQPro.repository.EmployeeRepository;
import com.stevecodes.AssetIQPro.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final AssetRepository assetRepository;
    private final AssetHistoryRepository historyRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    @Transactional
    public Transfer createTransfer(Transfer transfer) {
        log.info("Creating transfer for asset: {}", transfer.getAssetTag());

        if (transfer.getTransferDate() == null) {
            transfer.setTransferDate(LocalDate.now());
        }
        transfer.setIsFullySigned(false);

        // Populate employee names from IDs
        populateEmployeeNames(transfer);

        Transfer saved = transferRepository.save(transfer);
        createAssetHistory(saved);

        auditService.logAction("TRANSFER_CREATED",
                "Transfer created for asset: " + transfer.getAssetTag(), null);

        return saved;
    }

    private void populateEmployeeNames(Transfer transfer) {
        // Old Handover
        if (transfer.getOldHandoverById() != null) {
            employeeRepository.findById(transfer.getOldHandoverById())
                    .ifPresent(emp -> transfer.setOldHandoverByName(emp.getFullName()));
        }
        // Old Received
        if (transfer.getOldReceivedById() != null) {
            employeeRepository.findById(transfer.getOldReceivedById())
                    .ifPresent(emp -> transfer.setOldReceivedByName(emp.getFullName()));
        }
        // New Handover
        if (transfer.getNewHandoverById() != null) {
            employeeRepository.findById(transfer.getNewHandoverById())
                    .ifPresent(emp -> transfer.setNewHandoverByName(emp.getFullName()));
        }
        // New Received
        if (transfer.getNewReceivedById() != null) {
            employeeRepository.findById(transfer.getNewReceivedById())
                    .ifPresent(emp -> transfer.setNewReceivedByName(emp.getFullName()));
        }
        // Configured By
        if (transfer.getConfiguredById() != null) {
            employeeRepository.findById(transfer.getConfiguredById())
                    .ifPresent(emp -> transfer.setConfiguredByName(emp.getFullName()));
        }
        // Infrastructure Representative
        if (transfer.getInfraRepresentativeId() != null) {
            employeeRepository.findById(transfer.getInfraRepresentativeId())
                    .ifPresent(emp -> transfer.setInfraRepresentativeName(emp.getFullName()));
        }
        // Finance Representative
        if (transfer.getFinanceRepresentativeId() != null) {
            employeeRepository.findById(transfer.getFinanceRepresentativeId())
                    .ifPresent(emp -> transfer.setFinanceRepresentativeName(emp.getFullName()));
        }
    }

    public List<Transfer> getAllTransfers() {
        return transferRepository.findAll();
    }

    public Transfer getTransferById(Integer transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found: " + transferId));
    }

    public List<Transfer> getTransfersByAssetTag(String assetTag) {
        return transferRepository.findByAssetTag(assetTag);
    }

    public List<Transfer> getRelatedTransfers(Integer excludeTransferId, String assetTag, String serialNumber) {
        return transferRepository.findRelatedTransfers(assetTag, serialNumber, excludeTransferId);
    }

    @Transactional
    public void completeTransfer(Integer transferId) {
        Transfer transfer = getTransferById(transferId);
        transfer.setIsFullySigned(true);
        transferRepository.save(transfer);

        updateAssetStatus(transfer);
        createCompletionHistory(transfer);

        auditService.logAction("TRANSFER_COMPLETED",
                "Transfer completed for asset: " + transfer.getAssetTag(), null);
    }

    private void updateAssetStatus(Transfer transfer) {
        assetRepository.findByTag(transfer.getAssetTag())
                .ifPresent(asset -> {
                    asset.setStatus(Asset.AssetStatus.TRANSFERRED);
                    if (transfer.getNewDepartmentId() != null) {
                        log.info("Asset {} will be moved to department ID: {}", asset.getTag(), transfer.getNewDepartmentId());
                    }
                    assetRepository.save(asset);
                    log.info("Asset {} status updated to TRANSFERRED", asset.getTag());
                });
    }

    private void createAssetHistory(Transfer transfer) {
        // Only create history if asset exists
        if (transfer.getAssetTag() != null && !transfer.getAssetTag().isEmpty()) {
            assetRepository.findByTag(transfer.getAssetTag())
                    .ifPresent(asset -> {
                        String fromDepartment = transfer.getOldDepartmentId() != null ?
                                "Department ID: " + transfer.getOldDepartmentId() : "Unknown";
                        String toDepartment = transfer.getNewDepartmentId() != null ?
                                "Department ID: " + transfer.getNewDepartmentId() : "Unknown";

                        AssetHistory history = AssetHistory.createWithTransfer(
                                asset.getAssetId(),
                                null,
                                "Asset transferred from " + fromDepartment + " to " + toDepartment,
                                transfer.getTransferId().longValue()
                        );
                        historyRepository.save(history);
                        log.info("Asset history created for transfer: {}", transfer.getTransferId());
                    });
        }
    }

    private void createCompletionHistory(Transfer transfer) {
        // Only create history if asset exists
        if (transfer.getAssetTag() != null && !transfer.getAssetTag().isEmpty()) {
            assetRepository.findByTag(transfer.getAssetTag())
                    .ifPresent(asset -> {
                        AssetHistory history = AssetHistory.create(
                                asset.getAssetId(),
                                AssetHistory.EVENT_TRANSFER,
                                null,
                                "Transfer completed for asset: " + transfer.getAssetTag()
                        );
                        history.setTransferId(transfer.getTransferId().longValue());
                        historyRepository.save(history);
                        log.info("Completion history created for transfer: {}", transfer.getTransferId());
                    });
        }
    }
}