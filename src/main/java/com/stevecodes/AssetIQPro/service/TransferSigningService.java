package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Employee;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.entity.TransferToken;
import com.stevecodes.AssetIQPro.repository.EmployeeRepository;
import com.stevecodes.AssetIQPro.repository.TransferRepository;
import com.stevecodes.AssetIQPro.repository.TransferTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSigningService {

    private final TransferRepository transferRepository;
    private final TransferTokenRepository tokenRepository;
    private final TransferTokenService tokenService;
    private final PdfGenerationService pdfGenerationService;
    private final EmailService emailService;
    private final EmployeeRepository employeeRepository;
    private final BaseUrlService baseUrlService;

    public void initiateTransferSigning(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        log.info("=========================================");
        log.info("🔐 INITIATING SIGNING PROCESS FOR TRANSFER: {}", transferId);
        log.info("=========================================");

        tokenService.createTokensAndSendEmails(transfer);

        List<TransferToken> tokens = tokenRepository.findByTransferId(transferId);
        log.info("📋 ALL SIGNING LINKS FOR TRANSFER {}:", transferId);
        for (TransferToken token : tokens) {
            String signingLink = baseUrlService.buildUrl("/transfers/sign?token=%s", token.getToken());
            log.info("   👤 {}: {}", token.getSignerRole(), signingLink);
            log.info("   📧 Email: {}", token.getSignerEmail());
            log.info("   ⏰ Expires: {}", token.getExpiresAt());
            log.info("   ---");
        }
        log.info("=========================================");
        log.info("✅ Signing process initiated for transfer {}", transferId);
        log.info("=========================================");
    }

    public void signTransferWithSignature(Long transferId, String tokenValue, String base64Signature) {
        TransferToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (token.getIsUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Token has expired or already used");
        }

        String signerRole = determineSignerRole(transferId, token.getSignerEmployeeId());
        saveSignature(transferId, signerRole, base64Signature);
        token.setIsUsed(true);
        tokenRepository.save(token);

        log.info("Employee {} signed transfer {} with role {}",
                token.getSignerEmployeeId(), transferId, signerRole);

        if (isTransferFullySigned(transferId)) {
            log.info("Transfer {} completed after signature from {}", transferId, signerRole);
        }
    }

    public String determineSignerRole(Long transferId, Long employeeId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        if (transfer.getOldHandoverById() != null && transfer.getOldHandoverById().equals(employeeId))
            return "OLD_HANDOVER";
        if (transfer.getOldReceivedById() != null && transfer.getOldReceivedById().equals(employeeId))
            return "OLD_RECEIVED";
        if (transfer.getNewHandoverById() != null && transfer.getNewHandoverById().equals(employeeId))
            return "NEW_HANDOVER";
        if (transfer.getNewReceivedById() != null && transfer.getNewReceivedById().equals(employeeId))
            return "NEW_RECEIVED";
        if (transfer.getConfiguredById() != null && transfer.getConfiguredById().equals(employeeId))
            return "CONFIGURED_BY";
        if (transfer.getInfraRepresentativeId() != null && transfer.getInfraRepresentativeId().equals(employeeId))
            return "INFRA_REP";
        if (transfer.getFinanceRepresentativeId() != null && transfer.getFinanceRepresentativeId().equals(employeeId))
            return "FINANCE_REP";

        throw new IllegalArgumentException("Employee " + employeeId + " is not a signer for transfer " + transferId);
    }

    public Transfer validateTokenAndGetTransfer(String tokenValue) {
        TransferToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (token.getIsUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Token expired or already used");
        }

        return transferRepository.findById(token.getTransferId())
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
    }

    public TransferToken validateToken(String tokenValue) {
        TransferToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (token.getIsUsed() || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Token expired or already used");
        }
        return token;
    }

    /**
     * ✅ FIXED: Count only signers who actually received tokens (selected to sign)
     * This replaces the old method that counted all possible signer fields
     */
    private int countRequiredSigners(Transfer transfer) {
        // Get all tokens created for this transfer
        List<TransferToken> tokens = tokenRepository.findByTransferId(transfer.getTransferId());

        // Count unique signers (by employee ID, since one person can have multiple roles)
        // This is the number of people who need to sign
        long uniqueSignerCount = tokens.stream()
                .map(TransferToken::getSignerEmployeeId)
                .filter(id -> id != null)
                .distinct()
                .count();

        // If no tokens, fall back to counting non-null signer fields (legacy)
        if (uniqueSignerCount == 0) {
            log.warn("⚠️ No tokens found for transfer {}, falling back to field-based signer count", transfer.getTransferId());
            return countSignerFields(transfer);
        }

        log.info("📊 Transfer {} requires {} unique signers (based on tokens)",
                transfer.getTransferId(), uniqueSignerCount);
        return (int) uniqueSignerCount;
    }

    /**
     * Legacy method to count signer fields (fallback)
     */
    private int countSignerFields(Transfer transfer) {
        int count = 0;
        if (transfer.getOldHandoverById() != null) count++;
        if (transfer.getOldReceivedById() != null) count++;
        if (transfer.getNewHandoverById() != null) count++;
        if (transfer.getNewReceivedById() != null) count++;
        if (transfer.getConfiguredById() != null) count++;
        if (transfer.getInfraRepresentativeId() != null) count++;
        if (transfer.getFinanceRepresentativeId() != null) count++;
        return count;
    }

    /**
     * ✅ UPDATED: Check if all signers have signed
     * A transfer is fully signed when all token holders have completed signing
     */
    public boolean isTransferFullySigned(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        log.info("🔍 Checking if transfer {} is fully signed", transferId);
        log.info("   isFullySigned flag: {}", transfer.getIsFullySigned());

        // First check: If isFullySigned flag is true
        if (Boolean.TRUE.equals(transfer.getIsFullySigned())) {
            log.info("✅ Transfer {} already marked as fully signed", transferId);
            return true;
        }

        // ✅ NEW APPROACH: Check based on tokens (only selected signers matter)
        List<TransferToken> allTokens = tokenRepository.findByTransferId(transferId);

        if (!allTokens.isEmpty()) {
            // Get unique signers (by employee ID)
            List<Long> uniqueSignerIds = allTokens.stream()
                    .map(TransferToken::getSignerEmployeeId)
                    .filter(id -> id != null)
                    .distinct()
                    .collect(Collectors.toList());

            int requiredSigners = uniqueSignerIds.size();

            if (requiredSigners > 0) {
                // Count how many unique signers have completed ALL their roles
                int completedSigners = 0;
                for (Long signerId : uniqueSignerIds) {
                    // Get all tokens for this signer
                    List<TransferToken> signerTokens = allTokens.stream()
                            .filter(t -> signerId.equals(t.getSignerEmployeeId()))
                            .collect(Collectors.toList());

                    // Check if all roles for this signer are signed
                    boolean allRolesSigned = true;
                    for (TransferToken token : signerTokens) {
                        // Check if this specific role is signed
                        String role = token.getSignerRole();
                        boolean roleSigned = transfer.isSignedForRole(role);
                        if (!roleSigned) {
                            allRolesSigned = false;
                            break;
                        }
                    }

                    if (allRolesSigned) {
                        completedSigners++;
                    }
                }

                log.info("📊 Transfer {}: {} of {} unique signers have completed all their roles",
                        transferId, completedSigners, requiredSigners);

                if (completedSigners == requiredSigners) {
                    log.info("✅ Transfer {} fully signed - all {} unique signers completed",
                            transferId, requiredSigners);
                    completeTransferSigning(transfer);
                    return true;
                }
            }
        }

        // Second check: Fallback to token-used check (all tokens used)
        if (!allTokens.isEmpty()) {
            boolean allTokensUsed = allTokens.stream().allMatch(TransferToken::getIsUsed);
            if (allTokensUsed) {
                log.info("✅ Transfer {} fully signed via tokens - all {} tokens used",
                        transferId, allTokens.size());
                completeTransferSigning(transfer);
                return true;
            }
        }

        // Third check: Legacy timestamp check (for backward compatibility)
        boolean timestampsSigned = isTransferFullySignedByTimestamps(transfer);
        if (timestampsSigned) {
            log.info("✅ Transfer {} fully signed via timestamps", transferId);
            completeTransferSigning(transfer);
            return true;
        }

        log.info("❌ Transfer {} is NOT fully signed", transferId);
        return false;
    }

    private boolean isTransferCompleteByHybridApproach(Transfer transfer, List<TransferToken> tokens) {
        boolean allSigned = true;
        int signedCount = 0, requiredCount = 0;

        if (transfer.getOldHandoverById() != null) {
            requiredCount++;
            if (isSignerSignedHybrid(tokens, transfer.getOldHandoverById(), transfer.getOldHandoverBySignedAt())) {
                signedCount++;
            } else {
                allSigned = false;
            }
        }
        if (transfer.getOldReceivedById() != null) {
            requiredCount++;
            if (isSignerSignedHybrid(tokens, transfer.getOldReceivedById(), transfer.getOldReceivedBySignedAt())) {
                signedCount++;
            } else {
                allSigned = false;
            }
        }
        if (transfer.getNewHandoverById() != null) {
            requiredCount++;
            if (isSignerSignedHybrid(tokens, transfer.getNewHandoverById(), transfer.getNewHandoverBySignedAt())) {
                signedCount++;
            } else {
                allSigned = false;
            }
        }
        if (transfer.getNewReceivedById() != null) {
            requiredCount++;
            if (isSignerSignedHybrid(tokens, transfer.getNewReceivedById(), transfer.getNewReceivedBySignedAt())) {
                signedCount++;
            } else {
                allSigned = false;
            }
        }
        if (transfer.getConfiguredById() != null) {
            requiredCount++;
            if (isSignerSignedHybrid(tokens, transfer.getConfiguredById(), transfer.getConfiguredBySignedAt())) {
                signedCount++;
            } else {
                allSigned = false;
            }
        }
        if (transfer.getInfraRepresentativeId() != null) {
            requiredCount++;
            Long infraId = transfer.getInfraRepresentativeId();
            if (isSignerSignedHybrid(tokens, infraId, transfer.getInfraRepSignedAt())) {
                signedCount++;
            } else {
                allSigned = false;
            }
        }
        if (transfer.getFinanceRepresentativeId() != null) {
            requiredCount++;
            Long financeId = transfer.getFinanceRepresentativeId();
            if (isSignerSignedHybrid(tokens, financeId, transfer.getFinanceRepSignedAt())) {
                signedCount++;
            } else {
                allSigned = false;
            }
        }

        log.info("Transfer {} hybrid check: {}/{} signers completed",
                transfer.getTransferId(), signedCount, requiredCount);
        return allSigned && signedCount == requiredCount;
    }

    private boolean isSignerSignedHybrid(List<TransferToken> tokens, Long signerId, LocalDateTime timestamp) {
        if (signerId == null) return false;
        boolean tokenUsed = tokens.stream()
                .anyMatch(token -> token.getSignerEmployeeId().equals(signerId) && token.getIsUsed());
        boolean timestampExists = (timestamp != null);
        return (tokenUsed || timestampExists);
    }

    private void completeTransferSigning(Transfer transfer) {
        log.info("=========================================");
        log.info("✅ COMPLETING TRANSFER SIGNING FOR: {}", transfer.getTransferId());
        log.info("=========================================");

        transfer.setIsFullySigned(true);

        Transfer saved = transferRepository.save(transfer);
        log.info("✅ Transfer {} marked as fully signed (isFullySigned = {})",
                saved.getTransferId(), saved.getIsFullySigned());

        generateFullySignedPdf(transfer.getTransferId());
        sendCompletedTransferNotification(transfer.getTransferId());

        log.info("=========================================");
        log.info("✅ TRANSFER {} FULLY COMPLETED", transfer.getTransferId());
        log.info("=========================================");
    }

    private boolean isTransferFullySignedByTimestamps(Transfer transfer) {
        boolean allSigned = true;
        if (transfer.getOldHandoverById() != null && transfer.getOldHandoverBySignedAt() == null) allSigned = false;
        if (transfer.getOldReceivedById() != null && transfer.getOldReceivedBySignedAt() == null) allSigned = false;
        if (transfer.getNewHandoverById() != null && transfer.getNewHandoverBySignedAt() == null) allSigned = false;
        if (transfer.getNewReceivedById() != null && transfer.getNewReceivedBySignedAt() == null) allSigned = false;
        if (transfer.getConfiguredById() != null && transfer.getConfiguredBySignedAt() == null) allSigned = false;
        if (transfer.getInfraRepresentativeId() != null && transfer.getInfraRepSignedAt() == null) allSigned = false;
        if (transfer.getFinanceRepresentativeId() != null && transfer.getFinanceRepSignedAt() == null) allSigned = false;
        return allSigned;
    }

    public boolean isTransferFullySignedByTokens(Long transferId) {
        List<TransferToken> allTokens = tokenRepository.findByTransferId(transferId);
        if (allTokens.isEmpty()) return false;
        return allTokens.stream().allMatch(TransferToken::getIsUsed);
    }

    public boolean isTransferFullySignedByTimestamps(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));
        return isTransferFullySignedByTimestamps(transfer);
    }

    @Scheduled(fixedRate = 300000L)
    public void checkForCompletedTransfers() {
        log.info("Running scheduled check for completed transfers...");
        try {
            List<Transfer> incompleteTransfers = transferRepository.findByIsFullySignedFalse();
            log.info("Found {} transfers to check for completion", incompleteTransfers.size());
            for (Transfer transfer : incompleteTransfers) {
                try {
                    if (isTransferFullySigned(transfer.getTransferId())) {
                        log.info("Found completed transfer {} via scheduled check", transfer.getTransferId());
                    }
                } catch (Exception e) {
                    log.error("Error checking transfer {} in scheduled task: {}", transfer.getTransferId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in scheduled transfer completion check: {}", e.getMessage());
        }
    }

    public void saveSignature(Long transferId, String role, String base64Signature) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));
        LocalDateTime now = LocalDateTime.now();

        String signature = base64Signature;
        if (signature != null && signature.length() > 1000000) {
            signature = signature.substring(0, 1000000);
            log.warn("Signature truncated for transfer {} role {}", transferId, role);
        }

        switch (role) {
            case "OLD_HANDOVER":
                transfer.setOldHandoverBySignature(signature);
                transfer.setOldHandoverBySignedAt(now);
                break;
            case "OLD_RECEIVED":
                transfer.setOldReceivedBySignature(signature);
                transfer.setOldReceivedBySignedAt(now);
                break;
            case "NEW_HANDOVER":
                transfer.setNewHandoverBySignature(signature);
                transfer.setNewHandoverBySignedAt(now);
                break;
            case "NEW_RECEIVED":
                transfer.setNewReceivedBySignature(signature);
                transfer.setNewReceivedBySignedAt(now);
                break;
            case "CONFIGURED_BY":
                transfer.setConfiguredBySignature(signature);
                transfer.setConfiguredBySignedAt(now);
                break;
            case "INFRA_REP":
                transfer.setInfraRepSignature(signature);
                transfer.setInfraRepSignedAt(now);
                break;
            case "FINANCE_REP":
                transfer.setFinanceRepSignature(signature);
                transfer.setFinanceRepSignedAt(now);
                break;
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
        transferRepository.save(transfer);
        log.info("Saved signature for role {} on transfer {}", role, transferId);
    }

    public void generateFullySignedPdf(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));
        try {
            List<Transfer> related = transferRepository.findRelatedTransfers(
                    transfer.getAssetTag(), transfer.getSerialNumber(), transferId);

            byte[] pdfBytes = pdfGenerationService.generateTransferCertificatePdf(transfer, related);
            transfer.setFullySignedPDF(Base64.getEncoder().encodeToString(pdfBytes));
            transfer.setIsFullySigned(true);
            transferRepository.save(transfer);
            log.info("Generated fully signed PDF for transfer {} ({} related transfer(s) included)",
                    transferId, related.size());
        } catch (Exception e) {
            log.error("Failed to generate PDF for transfer {}: {}", transferId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    private void sendCompletedTransferNotification(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        // ✅ Get signer emails from tokens (only the people who actually received signing links)
        List<TransferToken> tokens = tokenRepository.findByTransferId(transferId);
        List<String> signerEmails = tokens.stream()
                .map(TransferToken::getSignerEmail)
                .filter(email -> email != null && !email.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        // Add admin as fallback
        signerEmails.add("admin@company.com");

        if (!signerEmails.isEmpty()) {
            try {
                byte[] pdfBytes = Base64.getDecoder().decode(transfer.getFullySignedPDF());
                log.info("Sending completed transfer email to {} recipients for transfer {}",
                        signerEmails.size(), transferId);
                emailService.sendCompletedTransferReport(signerEmails, transfer, pdfBytes);
            } catch (Exception e) {
                log.error("Failed to send email for transfer {}: {}", transferId, e.getMessage());
            }
        } else {
            log.warn("No valid email addresses found for transfer {} signers", transferId);
        }
    }

    private String getEmployeeEmail(Long employeeId) {
        if (employeeId == null) return null;
        try {
            return employeeRepository.findById(employeeId)
                    .map(Employee::getEmailAddress)
                    .orElse(null);
        } catch (Exception e) {
            log.error("Error getting email for employee {}: {}", employeeId, e.getMessage());
            return null;
        }
    }

    public boolean manuallyCheckTransferCompletion(Long transferId) {
        log.info("Manual completion check triggered for transfer {}", transferId);
        return isTransferFullySigned(transferId);
    }

    public byte[] getFullySignedPdf(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        if (transfer.getFullySignedPDF() == null) {
            throw new IllegalStateException("PDF not generated yet for transfer: " + transferId);
        }

        return Base64.getDecoder().decode(transfer.getFullySignedPDF());
    }

    @Transactional
    public void processSignatureForRole(Long transferId, String tokenValue, String role, String base64Signature) {
        TransferToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (token.getIsUsed()) {
            throw new IllegalStateException("Token has already been used");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Token has expired");
        }

        List<String> allowedRoles = tokenService.getRolesFromToken(token);
        if (!allowedRoles.contains(role)) {
            throw new IllegalArgumentException("Role " + role + " is not assigned to this signer");
        }

        saveSignature(transferId, role, base64Signature);

        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        boolean allSlotsSigned = tokenService.areAllSlotsSigned(transfer, token);
        if (allSlotsSigned) {
            token.setIsUsed(true);
            tokenRepository.save(token);
            log.info("✅ Signer {} has completed all their roles for transfer {}", token.getSignerEmail(), transferId);
        }

        if (isTransferFullySigned(transferId)) {
            log.info("🎉 Transfer {} is now fully signed!", transferId);
        }
    }
}