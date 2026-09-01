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
import java.util.ArrayList;
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
    private final TransferSigningService signingService;

    // ============================================
    // CREATE TRANSFER (Initial)
    // ============================================

    @Transactional
    public Transfer createTransfer(Transfer transfer) {
        log.info("Creating initial transfer for asset: {}", transfer.getAssetTag());

        if (transfer.getTransferDate() == null) {
            transfer.setTransferDate(LocalDate.now());
        }

        // Set history linking fields for initial transfer
        transfer.setIsInitialTransfer(true);
        transfer.setTransferSequence(1);
        transfer.setOriginalTransferId(null);  // No original for first transfer
        transfer.setPreviousTransferId(null);

        transfer.setIsFullySigned(false);

        populateEmployeeDetails(transfer);

        Transfer saved = transferRepository.save(transfer);
        createAssetHistory(saved);

        auditService.logAction("TRANSFER_CREATED",
                "Initial transfer created for asset: " + transfer.getAssetTag() + " (ID: " + saved.getTransferId() + ")", null);

        // Initiate signing process
        signingService.initiateTransferSigning(saved.getTransferId());

        log.info("✅ Initial transfer created with ID: {} (Sequence: 1)", saved.getTransferId());
        return saved;
    }

    // ============================================
    // RE-TRANSFER ASSET (With History Linking)
    // ============================================

    @Transactional
    public Transfer retransferAsset(Transfer retransferData) {
        log.info("=========================================");
        log.info("🔄 RE-TRANSFER ASSET: {}", retransferData.getAssetTag());
        log.info("=========================================");

        // 1. Get the source transfer (the one being re-transferred)
        Transfer sourceTransfer = transferRepository.findById(retransferData.getPreviousTransferId())
                .orElseThrow(() -> new RuntimeException("Source transfer not found: " + retransferData.getPreviousTransferId()));

        log.info("📋 Source Transfer ID: {}", sourceTransfer.getTransferId());

        // 2. Determine the original transfer ID
        Long originalTransferId;
        if (sourceTransfer.getOriginalTransferId() != null) {
            originalTransferId = sourceTransfer.getOriginalTransferId();
        } else {
            originalTransferId = sourceTransfer.getTransferId();
        }
        log.info("📋 Original Transfer ID: {}", originalTransferId);

        // 3. Get the next sequence number
        int nextSequence = sourceTransfer.getTransferSequence() + 1;
        log.info("📋 Next Sequence: {}", nextSequence);

        // 4. Create the new transfer
        Transfer newTransfer = new Transfer();

        // Asset Info - Copy from source
        newTransfer.setAssetTag(sourceTransfer.getAssetTag());
        newTransfer.setSerialNumber(sourceTransfer.getSerialNumber());
        newTransfer.setVersionMake(sourceTransfer.getVersionMake());
        newTransfer.setModelBuild(sourceTransfer.getModelBuild());
        newTransfer.setCategory(sourceTransfer.getCategory());
        newTransfer.setCompanyId(sourceTransfer.getCompanyId());
        newTransfer.setTransferDate(LocalDate.now());

        // History Linking - CRITICAL
        newTransfer.setOriginalTransferId(originalTransferId);
        newTransfer.setPreviousTransferId(sourceTransfer.getTransferId());
        newTransfer.setTransferSequence(nextSequence);
        newTransfer.setIsInitialTransfer(false);

        // Department
        if (retransferData.getNewDepartmentId() != null) {
            newTransfer.setNewDepartmentId(retransferData.getNewDepartmentId());
            newTransfer.setOldDepartmentId(sourceTransfer.getNewDepartmentId());
        } else {
            newTransfer.setOldDepartmentId(sourceTransfer.getNewDepartmentId());
            newTransfer.setNewDepartmentId(sourceTransfer.getNewDepartmentId());
        }

        // Employee - Current holder becomes "From"
        Long currentEmployeeId = sourceTransfer.getNewEmployeeId();
        if (currentEmployeeId == null) {
            throw new RuntimeException("Current employee not found for asset: " + sourceTransfer.getAssetTag());
        }

        Employee currentEmployee = employeeRepository.findById(currentEmployeeId)
                .orElseThrow(() -> new RuntimeException("Current employee not found: " + currentEmployeeId));

        Employee newEmployee = employeeRepository.findById(retransferData.getNewEmployeeId())
                .orElseThrow(() -> new RuntimeException("New employee not found: " + retransferData.getNewEmployeeId()));

        newTransfer.setOldEmployeeId(currentEmployeeId);
        newTransfer.setOldEmployeeName(currentEmployee.getFullName());
        if (currentEmployee.getUser() != null) {
            newTransfer.setOldEmployeeStaffId(currentEmployee.getUser().getStaffId());
        }

        newTransfer.setNewEmployeeId(newEmployee.getEmployeeId());
        newTransfer.setNewEmployeeName(newEmployee.getFullName());
        if (newEmployee.getUser() != null) {
            newTransfer.setNewEmployeeStaffId(newEmployee.getUser().getStaffId());
        }

        // 5. Copy signers from source transfer
        newTransfer.setOldHandoverById(sourceTransfer.getOldHandoverById());
        newTransfer.setOldHandoverByName(sourceTransfer.getOldHandoverByName());
        newTransfer.setOldHandoverByStaffId(sourceTransfer.getOldHandoverByStaffId());

        newTransfer.setOldReceivedById(sourceTransfer.getOldReceivedById());
        newTransfer.setOldReceivedByName(sourceTransfer.getOldReceivedByName());
        newTransfer.setOldReceivedByStaffId(sourceTransfer.getOldReceivedByStaffId());

        newTransfer.setNewHandoverById(sourceTransfer.getNewHandoverById());
        newTransfer.setNewHandoverByName(sourceTransfer.getNewHandoverByName());
        newTransfer.setNewHandoverByStaffId(sourceTransfer.getNewHandoverByStaffId());

        newTransfer.setNewReceivedById(sourceTransfer.getNewReceivedById());
        newTransfer.setNewReceivedByName(sourceTransfer.getNewReceivedByName());
        newTransfer.setNewReceivedByStaffId(sourceTransfer.getNewReceivedByStaffId());

        newTransfer.setConfiguredById(sourceTransfer.getConfiguredById());
        newTransfer.setConfiguredByName(sourceTransfer.getConfiguredByName());
        newTransfer.setConfiguredByStaffId(sourceTransfer.getConfiguredByStaffId());

        newTransfer.setInfraRepresentativeId(sourceTransfer.getInfraRepresentativeId());
        newTransfer.setInfraRepresentativeName(sourceTransfer.getInfraRepresentativeName());
        newTransfer.setInfraRepresentativeStaffId(sourceTransfer.getInfraRepresentativeStaffId());

        newTransfer.setFinanceRepresentativeId(sourceTransfer.getFinanceRepresentativeId());
        newTransfer.setFinanceRepresentativeName(sourceTransfer.getFinanceRepresentativeName());
        newTransfer.setFinanceRepresentativeStaffId(sourceTransfer.getFinanceRepresentativeStaffId());

        // 6. Condition, accessories, software (from retransfer data or copy from source)
        newTransfer.setConditionOld(retransferData.getConditionOld() != null ?
                retransferData.getConditionOld() : sourceTransfer.getConditionNew());
        newTransfer.setConditionNew(retransferData.getConditionNew() != null ?
                retransferData.getConditionNew() : sourceTransfer.getConditionNew());
        newTransfer.setAccessoriesOld(retransferData.getAccessoriesOld() != null ?
                retransferData.getAccessoriesOld() : sourceTransfer.getAccessoriesNew());
        newTransfer.setAccessoriesNew(retransferData.getAccessoriesNew() != null ?
                retransferData.getAccessoriesNew() : sourceTransfer.getAccessoriesNew());
        newTransfer.setSoftwareInstalled(retransferData.getSoftwareInstalled() != null ?
                retransferData.getSoftwareInstalled() : sourceTransfer.getSoftwareInstalled());

        // 7. Comments
        String transferComment = "Re-transfer #" + nextSequence + " from " + currentEmployee.getFullName() +
                " to " + newEmployee.getFullName();
        if (retransferData.getComments() != null && !retransferData.getComments().isEmpty()) {
            transferComment += ". Notes: " + retransferData.getComments();
        }
        newTransfer.setComments(transferComment);

        // 8. Set status
        newTransfer.setIsFullySigned(false);

        // 9. Populate any missing employee details
        populateEmployeeDetails(newTransfer);

        // 10. Save the new transfer
        Transfer saved = transferRepository.save(newTransfer);
        log.info("✅ New transfer created with ID: {} (Sequence: {})", saved.getTransferId(), nextSequence);

        // 11. Create asset history
        createRetransferHistory(sourceTransfer, saved, currentEmployee, newEmployee);

        // 12. Log audit
        auditService.logAction("ASSET_RETRANSFERRED",
                "Asset " + saved.getAssetTag() + " re-transferred from " + currentEmployee.getFullName() +
                        " to " + newEmployee.getFullName() +
                        " (Transfer ID: " + saved.getTransferId() + ", Sequence: " + nextSequence + ")", null);

        // 13. Initiate signing process
        signingService.initiateTransferSigning(saved.getTransferId());

        log.info("=========================================");
        log.info("✅ RE-TRANSFER COMPLETED");
        log.info("   New Transfer ID: {}", saved.getTransferId());
        log.info("   Sequence: {}", nextSequence);
        log.info("   Original Transfer ID: {}", originalTransferId);
        log.info("   From: {} ({})", currentEmployee.getFullName(), currentEmployee.getStaffId());
        log.info("   To: {} ({})", newEmployee.getFullName(), newEmployee.getStaffId());
        log.info("=========================================");

        return saved;
    }

    // ============================================
    // HISTORY QUERY METHODS
    // ============================================

    /**
     * Get the complete transfer history for an asset
     */
    public List<Transfer> getAssetTransferHistory(String assetTag) {
        return transferRepository.findTransferHistoryByAssetTag(assetTag);
    }

    /**
     * Get the initial transfer for an asset
     */
    public Transfer getInitialTransfer(String assetTag) {
        return transferRepository.findInitialTransferByAssetTag(assetTag);
    }

    /**
     * Get the latest transfer for an asset (current holder)
     */
    public Transfer getLatestTransfer(String assetTag) {
        List<Transfer> transfers = transferRepository.findLatestTransferByAssetTag(assetTag);
        return transfers.isEmpty() ? null : transfers.get(0);
    }

    /**
     * Get the transfer chain (all transfers linked to an original)
     */
    public List<Transfer> getTransferChain(Long originalTransferId) {
        return transferRepository.findTransfersByOriginalId(originalTransferId);
    }

    /**
     * Get the current holder of an asset
     */
    public Employee getCurrentAssetHolder(String assetTag) {
        Transfer latest = getLatestTransfer(assetTag);
        if (latest == null || latest.getNewEmployeeId() == null) {
            return null;
        }
        return employeeRepository.findById(latest.getNewEmployeeId()).orElse(null);
    }

    /**
     * Check if an asset has a transfer history
     */
    public boolean hasTransferHistory(String assetTag) {
        return !transferRepository.findTransferHistoryByAssetTag(assetTag).isEmpty();
    }

    /**
     * Count transfers in the history chain
     */
    public long countTransfersInChain(Long originalTransferId) {
        return transferRepository.countTransfersInChain(originalTransferId);
    }

    // ============================================
    // PRIVATE HELPER METHODS
    // ============================================

    private void populateEmployeeDetails(Transfer transfer) {
        // Old Handover
        if (transfer.getOldHandoverById() != null) {
            employeeRepository.findById(transfer.getOldHandoverById()).ifPresent(emp -> {
                transfer.setOldHandoverByName(emp.getFullName());
                if (emp.getUser() != null) {
                    transfer.setOldHandoverByStaffId(emp.getUser().getStaffId());
                }
            });
        }
        // Old Received
        if (transfer.getOldReceivedById() != null) {
            employeeRepository.findById(transfer.getOldReceivedById()).ifPresent(emp -> {
                transfer.setOldReceivedByName(emp.getFullName());
                if (emp.getUser() != null) {
                    transfer.setOldReceivedByStaffId(emp.getUser().getStaffId());
                }
            });
        }
        // New Handover
        if (transfer.getNewHandoverById() != null) {
            employeeRepository.findById(transfer.getNewHandoverById()).ifPresent(emp -> {
                transfer.setNewHandoverByName(emp.getFullName());
                if (emp.getUser() != null) {
                    transfer.setNewHandoverByStaffId(emp.getUser().getStaffId());
                }
            });
        }
        // New Received
        if (transfer.getNewReceivedById() != null) {
            employeeRepository.findById(transfer.getNewReceivedById()).ifPresent(emp -> {
                transfer.setNewReceivedByName(emp.getFullName());
                if (emp.getUser() != null) {
                    transfer.setNewReceivedByStaffId(emp.getUser().getStaffId());
                }
            });
        }
        // Configured By
        if (transfer.getConfiguredById() != null) {
            employeeRepository.findById(transfer.getConfiguredById()).ifPresent(emp -> {
                transfer.setConfiguredByName(emp.getFullName());
                if (emp.getUser() != null) {
                    transfer.setConfiguredByStaffId(emp.getUser().getStaffId());
                }
            });
        }
        // Infrastructure Representative
        if (transfer.getInfraRepresentativeId() != null) {
            employeeRepository.findById(transfer.getInfraRepresentativeId()).ifPresent(emp -> {
                transfer.setInfraRepresentativeName(emp.getFullName());
                if (emp.getUser() != null) {
                    transfer.setInfraRepresentativeStaffId(emp.getUser().getStaffId());
                }
            });
        }
        // Finance Representative
        if (transfer.getFinanceRepresentativeId() != null) {
            employeeRepository.findById(transfer.getFinanceRepresentativeId()).ifPresent(emp -> {
                transfer.setFinanceRepresentativeName(emp.getFullName());
                if (emp.getUser() != null) {
                    transfer.setFinanceRepresentativeStaffId(emp.getUser().getStaffId());
                }
            });
        }
        // From Employee
        if (transfer.getOldEmployeeId() != null) {
            employeeRepository.findById(transfer.getOldEmployeeId()).ifPresent(emp -> {
                transfer.setOldEmployeeName(emp.getFullName());
                if (emp.getUser() != null) {
                    transfer.setOldEmployeeStaffId(emp.getUser().getStaffId());
                }
            });
        }
        // To Employee
        if (transfer.getNewEmployeeId() != null) {
            employeeRepository.findById(transfer.getNewEmployeeId()).ifPresent(emp -> {
                transfer.setNewEmployeeName(emp.getFullName());
                if (emp.getUser() != null) {
                    transfer.setNewEmployeeStaffId(emp.getUser().getStaffId());
                }
            });
        }
    }

    private void createAssetHistory(Transfer transfer) {
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
                                "Initial transfer from " + fromDepartment + " to " + toDepartment +
                                        " (Transfer ID: " + transfer.getTransferId() + ")",
                                transfer.getTransferId()
                        );
                        historyRepository.save(history);
                        log.info("Asset history created for transfer: {}", transfer.getTransferId());
                    });
        }
    }

    private void createRetransferHistory(Transfer sourceTransfer, Transfer newTransfer,
                                         Employee fromEmployee, Employee toEmployee) {
        if (sourceTransfer.getAssetTag() != null && !sourceTransfer.getAssetTag().isEmpty()) {
            assetRepository.findByTag(sourceTransfer.getAssetTag())
                    .ifPresent(asset -> {
                        String historyMessage = String.format(
                                "Re-transfer #%d: Asset re-transferred from %s (%s) to %s (%s). " +
                                        "Original Transfer ID: %d, Sequence: %d -> %d",
                                newTransfer.getTransferSequence(),
                                fromEmployee.getFullName(),
                                fromEmployee.getStaffId(),
                                toEmployee.getFullName(),
                                toEmployee.getStaffId(),
                                newTransfer.getOriginalTransferId(),
                                sourceTransfer.getTransferSequence(),
                                newTransfer.getTransferSequence()
                        );

                        AssetHistory history = AssetHistory.createWithTransfer(
                                asset.getAssetId(),
                                null,
                                historyMessage,
                                newTransfer.getTransferId()
                        );
                        history.setEventType(AssetHistory.EVENT_TRANSFER);
                        historyRepository.save(history);
                        log.info("Retransfer history created for asset: {}", asset.getTag());
                    });
        }
    }

    private void createCompletionHistory(Transfer transfer) {
        if (transfer.getAssetTag() != null && !transfer.getAssetTag().isEmpty()) {
            assetRepository.findByTag(transfer.getAssetTag())
                    .ifPresent(asset -> {
                        AssetHistory history = AssetHistory.create(
                                asset.getAssetId(),
                                AssetHistory.EVENT_TRANSFER,
                                null,
                                "Transfer completed for asset: " + transfer.getAssetTag() +
                                        " (Transfer ID: " + transfer.getTransferId() + ")"
                        );
                        history.setTransferId(transfer.getTransferId());
                        historyRepository.save(history);
                        log.info("Completion history created for transfer: {}", transfer.getTransferId());
                    });
        }
    }

    // ============================================
    // EXISTING METHODS
    // ============================================

    public List<Transfer> getAllTransfers() {
        return transferRepository.findAll();
    }

    public Transfer getTransferById(Long transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found: " + transferId));
    }

    public List<Transfer> getTransfersByAssetTag(String assetTag) {
        return transferRepository.findByAssetTag(assetTag);
    }

    public List<Transfer> getRelatedTransfers(Long excludeTransferId, String assetTag, String serialNumber) {
        return transferRepository.findRelatedTransfers(assetTag, serialNumber, excludeTransferId);
    }

    @Transactional
    public void completeTransfer(Long transferId) {
        Transfer transfer = getTransferById(transferId);
        transfer.setIsFullySigned(true);
        transferRepository.save(transfer);

        updateAssetStatus(transfer);
        createCompletionHistory(transfer);

        auditService.logAction("TRANSFER_COMPLETED",
                "Transfer completed for asset: " + transfer.getAssetTag() +
                        " (ID: " + transferId + ", Sequence: " + transfer.getTransferSequence() + ")", null);
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

    @Transactional
    public void deleteTransfer(Long transferId) {
        Transfer transfer = getTransferById(transferId);
        transferRepository.delete(transfer);
        log.info("Deleted transfer: {}", transferId);

        auditService.logAction("TRANSFER_DELETED",
                "Transfer deleted: " + transferId, null);
    }
}