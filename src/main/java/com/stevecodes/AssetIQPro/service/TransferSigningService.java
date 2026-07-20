package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.entity.TransferToken;
import com.stevecodes.AssetIQPro.repository.EmployeeRepository;
import com.stevecodes.AssetIQPro.repository.TransferRepository;
import com.stevecodes.AssetIQPro.repository.TransferTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.stevecodes.AssetIQPro.entity.Employee;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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
    private final BaseUrlService baseUrlService;  // ✅ ADDED

    public void initiateTransferSigning(Integer transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        log.info("=========================================");
        log.info("🔐 INITIATING SIGNING PROCESS FOR TRANSFER: {}", transferId);
        log.info("=========================================");

        tokenService.createTokensAndSendEmails(transfer);

        List<TransferToken> tokens = tokenRepository.findByTransferId(transferId);
        log.info("📋 ALL SIGNING LINKS FOR TRANSFER {}:", transferId);
        for (TransferToken token : tokens) {
            // ✅ DYNAMIC URL
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

    public void signTransferWithSignature(Integer transferId, String tokenValue, String base64Signature) {
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

    public String determineSignerRole(Integer transferId, Long employeeId) {
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

    public boolean isTransferFullySigned(Integer transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        if (Boolean.TRUE.equals(transfer.getIsFullySigned())) {
            return true;
        }

        List<TransferToken> allTokens = tokenRepository.findByTransferId(transferId);
        int requiredSigners = countRequiredSigners(transfer);

        log.info("Transfer {} has {} required signers and {} tokens",
                transferId, requiredSigners, allTokens.size());

        if (!allTokens.isEmpty() && allTokens.size() == requiredSigners) {
            boolean allTokensUsed = allTokens.stream().allMatch(TransferToken::getIsUsed);
            if (allTokensUsed) {
                log.info("Transfer {} fully signed via tokens - all {} tokens used",
                        transferId, allTokens.size());
                completeTransferSigning(transfer);
                return true;
            }
        }

        boolean timestampsSigned = isTransferFullySignedByTimestamps(transfer);
        if (timestampsSigned) {
            log.info("Transfer {} fully signed via timestamps", transferId);
            completeTransferSigning(transfer);
            return true;
        }

        if (!allTokens.isEmpty() && requiredSigners > 0) {
            boolean hybridComplete = isTransferCompleteByHybridApproach(transfer, allTokens);
            if (hybridComplete) {
                log.info("Transfer {} fully signed via hybrid approach", transferId);
                completeTransferSigning(transfer);
                return true;
            }
        }

        log.info("Transfer {} is NOT fully signed", transferId);
        return false;
    }

    private int countRequiredSigners(Transfer transfer) {
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
        return allSigned;
    }

    private boolean isSignerSignedHybrid(List<TransferToken> tokens, Long signerId, LocalDateTime timestamp) {
        if (signerId == null) return false;
        boolean tokenUsed = tokens.stream()
                .anyMatch(token -> token.getSignerEmployeeId().equals(signerId) && token.getIsUsed());
        boolean timestampExists = (timestamp != null);
        return (tokenUsed || timestampExists);
    }

    private void completeTransferSigning(Transfer transfer) {
        transfer.setIsFullySigned(true);
        transferRepository.save(transfer);
        generateFullySignedPdf(transfer.getTransferId());
        sendCompletedTransferNotification(transfer.getTransferId());
        log.info("Transfer {} marked as fully signed", transfer.getTransferId());
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

    public boolean isTransferFullySignedByTokens(Integer transferId) {
        List<TransferToken> allTokens = tokenRepository.findByTransferId(transferId);
        if (allTokens.isEmpty()) return false;
        return allTokens.stream().allMatch(TransferToken::getIsUsed);
    }

    public boolean isTransferFullySignedByTimestamps(Integer transferId) {
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

    public void saveSignature(Integer transferId, String role, String base64Signature) {
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

    public void generateFullySignedPdf(Integer transferId) {
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

    private void sendCompletedTransferNotification(Integer transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        List<String> signerEmails = new ArrayList<>();

        if (transfer.getOldHandoverById() != null) {
            String email = getEmployeeEmail(transfer.getOldHandoverById());
            if (email != null) signerEmails.add(email);
        }
        if (transfer.getOldReceivedById() != null) {
            String email = getEmployeeEmail(transfer.getOldReceivedById());
            if (email != null) signerEmails.add(email);
        }
        if (transfer.getNewHandoverById() != null) {
            String email = getEmployeeEmail(transfer.getNewHandoverById());
            if (email != null) signerEmails.add(email);
        }
        if (transfer.getNewReceivedById() != null) {
            String email = getEmployeeEmail(transfer.getNewReceivedById());
            if (email != null) signerEmails.add(email);
        }
        if (transfer.getConfiguredById() != null) {
            String email = getEmployeeEmail(transfer.getConfiguredById());
            if (email != null) signerEmails.add(email);
        }
        if (transfer.getInfraRepresentativeId() != null) {
            String email = getEmployeeEmail(transfer.getInfraRepresentativeId());
            if (email != null) signerEmails.add(email);
        }
        if (transfer.getFinanceRepresentativeId() != null) {
            String email = getEmployeeEmail(transfer.getFinanceRepresentativeId());
            if (email != null) signerEmails.add(email);
        }

        signerEmails.add("admin@company.com");

        if (!signerEmails.isEmpty()) {
            try {
                byte[] pdfBytes = Base64.getDecoder().decode(transfer.getFullySignedPdf());
                log.info("Sending completed transfer email to {} recipients for transfer {}", signerEmails.size(), transferId);
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

    public boolean manuallyCheckTransferCompletion(Integer transferId) {
        log.info("Manual completion check triggered for transfer {}", transferId);
        return isTransferFullySigned(transferId);
    }

    public byte[] getFullySignedPdf(Integer transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        if (transfer.getFullySignedPdf() == null) {
            throw new IllegalStateException("PDF not generated yet for transfer: " + transferId);
        }

        return Base64.getDecoder().decode(transfer.getFullySignedPdf());
    }
}