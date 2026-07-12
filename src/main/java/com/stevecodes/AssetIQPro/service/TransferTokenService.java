package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Employee;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.entity.TransferToken;
import com.stevecodes.AssetIQPro.repository.EmployeeRepository;
import com.stevecodes.AssetIQPro.repository.TransferTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferTokenService {

    private final TransferTokenRepository transferTokenRepository;
    private final EmployeeRepository employeeRepository;
    private final JavaMailSender mailSender;

    private static class SignerInfo {
        private final Long employeeId;
        private final String email;
        private final String role;

        public SignerInfo(Long employeeId, String email, String role) {
            this.employeeId = employeeId;
            this.email = email;
            this.role = role;
        }

        public Long getEmployeeId() { return employeeId; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
    }

    @Transactional(readOnly = true)
    public Optional<TransferToken> findByTokenWithEagerLoading(String token) {
        try {
            Optional<TransferToken> tokenOpt = transferTokenRepository.findByToken(token);
            if (tokenOpt.isPresent()) {
                TransferToken transferToken = tokenOpt.get();
                if (transferToken.getSignerEmployeeId() != null) {
                    Optional<Employee> employee = employeeRepository.findById(transferToken.getSignerEmployeeId());
                    if (employee.isPresent()) {
                        employee.get().getFirstName();
                        employee.get().getSurName();
                        employee.get().getEmailAddress();
                    }
                }
                log.debug("Token found with eager loading: {} for transfer: {}", token, transferToken.getTransferId());
            }
            return tokenOpt;
        } catch (Exception e) {
            log.error("Error finding token with eager loading: {}", token, e);
            return Optional.empty();
        }
    }

    public Map<String, Long> getTokenStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Long> result = new HashMap<>();
        List<TransferToken> tokens = transferTokenRepository.findByCreatedAtBetween(startDate, endDate);

        long total = tokens.size();
        long used = tokens.stream().filter(TransferToken::getIsUsed).count();
        long expired = tokens.stream()
                .filter(t -> !t.getIsUsed() && t.getExpiresAt().isBefore(LocalDateTime.now()))
                .count();
        long active = total - used - expired;

        result.put("totalTokens", total);
        result.put("usedTokens", used);
        result.put("expiredTokens", expired);
        result.put("activeTokens", active);

        log.info("Total tokens: {}, Used tokens: {}, Expired tokens: {}, Active tokens: {}",
                total, used, expired, active);
        return result;
    }

    public void createTokensAndSendEmails(Transfer transfer) {
        log.info("=========================================");
        log.info("🚀 STARTING TOKEN CREATION FOR TRANSFER: {}", transfer.getTransferId());
        log.info("=========================================");

        List<SignerInfo> signers = getRequiredSigners(transfer);
        if (signers.isEmpty()) {
            log.warn("⚠️ No signers found for transfer: {}", transfer.getTransferId());
            return;
        }

        log.info("📋 Found {} signers for transfer: {}", signers.size(), transfer.getTransferId());
        List<TransferToken> createdTokens = new ArrayList<>();

        for (SignerInfo signer : signers) {
            try {
                if (signer.getEmail() == null || signer.getEmail().trim().isEmpty()) {
                    log.warn("⚠️ Skipping token creation for employee ID {} - no email address found", signer.getEmployeeId());
                    continue;
                }

                TransferToken token = new TransferToken();
                token.setTransferId(transfer.getTransferId());
                token.setSignerEmployeeId(signer.getEmployeeId());
                token.setSignerEmail(signer.getEmail());
                token.setToken(generateUniqueToken());
                token.setIsUsed(false);
                token.setExpiresAt(LocalDateTime.now().plusDays(7));
                token.setCreatedAt(LocalDateTime.now());
                token.setSignerRole(signer.getRole());

                TransferToken savedToken = transferTokenRepository.save(token);
                createdTokens.add(savedToken);

                String signingLink = "https://sandbox.interswitch.io/digitalassetsmgmt/transfers/sign?token=" + savedToken.getToken();

                log.info("=========================================");
                log.info("🔐 SIGNING LINK GENERATED FOR TRANSFER: {}", transfer.getTransferId());
                log.info("👤 Signer: {} ({})", signer.getEmail(), signer.getRole());
                log.info("🔗 LINK: {}", signingLink);
                log.info("⏰ Expires: {}", savedToken.getExpiresAt());
                log.info("📝 Token: {}", savedToken.getToken());
                log.info("=========================================");

            } catch (Exception e) {
                log.error("❌ Failed to create token for signer: {} (Transfer: {})", signer.getEmail(), transfer.getTransferId(), e);
            }
        }

        if (!createdTokens.isEmpty()) {
            log.info("📧 Sending emails for {} tokens on transfer {}...", createdTokens.size(), transfer.getTransferId());
            CompletableFuture.runAsync(() -> sendEmailsInBackground(transfer, createdTokens));
            log.info("✅ Background email sending initiated for {} tokens on transfer {} - returning control immediately",
                    createdTokens.size(), transfer.getTransferId());
        } else {
            log.warn("⚠️ No tokens were created for transfer {}", transfer.getTransferId());
        }
    }

    private void sendEmailsInBackground(Transfer transfer, List<TransferToken> tokens) {
        log.info("🔄 Starting background email sending for {} tokens on transfer {} [Thread: {}]",
                tokens.size(), transfer.getTransferId(), Thread.currentThread().getName());

        for (TransferToken token : tokens) {
            try {
                SignerInfo signerInfo = new SignerInfo(token.getSignerEmployeeId(), token.getSignerEmail(), token.getSignerRole());
                sendSigningEmail(transfer, signerInfo, token);
                log.info("✅ Email sent successfully to: {} [Thread: {}]", token.getSignerEmail(), Thread.currentThread().getName());
            } catch (Exception e) {
                log.error("❌ Failed to send email to {} [Thread: {}]: {}",
                        token.getSignerEmail(), Thread.currentThread().getName(), e.getMessage());
                String manualLink = "https://sandbox.interswitch.io/digitalassetsmgmt/transfers/sign?token=" + token.getToken();
                log.info("🔗 MANUAL LINK FOR {}: {}", token.getSignerEmail(), manualLink);
            }
        }
        log.info("✅ Completed background email sending for transfer {} [Thread: {}]",
                transfer.getTransferId(), Thread.currentThread().getName());
    }

    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendSigningEmailsAsyncNonBlocking(Transfer transfer, List<TransferToken> tokens) {
        log.info("🚀 Starting async email sending for {} tokens on transfer {} [Thread: {}]",
                tokens.size(), transfer.getTransferId(), Thread.currentThread().getName());

        int successCount = 0;
        int failureCount = 0;

        for (TransferToken token : tokens) {
            try {
                SignerInfo signerInfo = new SignerInfo(token.getSignerEmployeeId(), token.getSignerEmail(), token.getSignerRole());
                sendSigningEmail(transfer, signerInfo, token);
                successCount++;
                log.info("✅ Email sent successfully to: {} (Role: {}) with token: {} [Thread: {}]",
                        token.getSignerEmail(), token.getSignerRole(), token.getToken(), Thread.currentThread().getName());
            } catch (Exception e) {
                failureCount++;
                log.error("❌ Failed to send email to {} for transfer {} [Thread: {}]: {}",
                        token.getSignerEmail(), transfer.getTransferId(), Thread.currentThread().getName(), e.getMessage(), e);
                String manualLink = "https://sandbox.interswitch.io/digitalassetsmgmt/transfers/sign?token=" + token.getToken();
                log.info("🔗 MANUAL LINK FOR {}: {}", token.getSignerEmail(), manualLink);
            }
        }

        log.info("✅ Completed async email sending for transfer {} [Thread: {}]: {} successful, {} failed",
                transfer.getTransferId(), Thread.currentThread().getName(), successCount, failureCount);
        return CompletableFuture.completedFuture(null);
    }

    private List<SignerInfo> getRequiredSigners(Transfer transfer) {
        List<SignerInfo> signers = new ArrayList<>();
        log.info("📋 Getting required signers for transfer: {}", transfer.getTransferId());

        if (transfer.getOldHandoverById() != null) {
            String email = getEmployeeEmail(transfer.getOldHandoverById());
            if (email != null) {
                signers.add(new SignerInfo(transfer.getOldHandoverById(), email, "OLD_HANDOVER"));
                log.debug("✅ Added OLD_HANDOVER signer: {} (ID: {})", email, transfer.getOldHandoverById());
            } else {
                log.warn("⚠️ No email found for OLD_HANDOVER employee ID: {}", transfer.getOldHandoverById());
            }
        }

        if (transfer.getOldReceivedById() != null) {
            String email = getEmployeeEmail(transfer.getOldReceivedById());
            if (email != null) {
                signers.add(new SignerInfo(transfer.getOldReceivedById(), email, "OLD_RECEIVED"));
                log.debug("✅ Added OLD_RECEIVED signer: {} (ID: {})", email, transfer.getOldReceivedById());
            } else {
                log.warn("⚠️ No email found for OLD_RECEIVED employee ID: {}", transfer.getOldReceivedById());
            }
        }

        if (transfer.getNewHandoverById() != null) {
            String email = getEmployeeEmail(transfer.getNewHandoverById());
            if (email != null) {
                signers.add(new SignerInfo(transfer.getNewHandoverById(), email, "NEW_HANDOVER"));
                log.debug("✅ Added NEW_HANDOVER signer: {} (ID: {})", email, transfer.getNewHandoverById());
            } else {
                log.warn("⚠️ No email found for NEW_HANDOVER employee ID: {}", transfer.getNewHandoverById());
            }
        }

        if (transfer.getNewReceivedById() != null) {
            String email = getEmployeeEmail(transfer.getNewReceivedById());
            if (email != null) {
                signers.add(new SignerInfo(transfer.getNewReceivedById(), email, "NEW_RECEIVED"));
                log.debug("✅ Added NEW_RECEIVED signer: {} (ID: {})", email, transfer.getNewReceivedById());
            } else {
                log.warn("⚠️ No email found for NEW_RECEIVED employee ID: {}", transfer.getNewReceivedById());
            }
        }

        if (transfer.getConfiguredById() != null) {
            String email = getEmployeeEmail(transfer.getConfiguredById());
            if (email != null) {
                signers.add(new SignerInfo(transfer.getConfiguredById(), email, "CONFIGURED_BY"));
                log.debug("✅ Added CONFIGURED_BY signer: {} (ID: {})", email, transfer.getConfiguredById());
            } else {
                log.warn("⚠️ No email found for CONFIGURED_BY employee ID: {}", transfer.getConfiguredById());
            }
        }

        if (transfer.getInfraRepresentativeId() != null) {
            Long infraId = transfer.getInfraRepresentativeId();
            String email = getEmployeeEmail(infraId);
            if (email != null) {
                signers.add(new SignerInfo(infraId, email, "INFRA_REP"));
                log.debug("✅ Added INFRA_REP signer: {} (ID: {})", email, infraId);
            } else {
                log.warn("⚠️ No email found for INFRA_REP employee ID: {}", infraId);
            }
        } else {
            log.warn("⚠️ No Infrastructure Representative assigned to transfer: {}", transfer.getTransferId());
        }

        if (transfer.getFinanceRepresentativeId() != null) {
            Long financeId = transfer.getFinanceRepresentativeId();
            String email = getEmployeeEmail(financeId);
            if (email != null) {
                signers.add(new SignerInfo(financeId, email, "FINANCE_REP"));
                log.debug("✅ Added FINANCE_REP signer: {} (ID: {})", email, financeId);
            } else {
                log.warn("⚠️ No email found for FINANCE_REP employee ID: {}", financeId);
            }
        } else {
            log.warn("⚠️ No Finance Representative assigned to transfer: {}", transfer.getTransferId());
        }

        log.info("📋 Total signers found for transfer {}: {}", transfer.getTransferId(), signers.size());
        for (SignerInfo signer : signers) {
            log.info("   👤 Signer: {} (ID: {}, Role: {})", signer.getEmail(), signer.getEmployeeId(), signer.getRole());
        }
        return signers;
    }

    private String getEmployeeEmail(Long employeeId) {
        try {
            if (employeeId == null) {
                log.warn("⚠️ Employee ID is null when trying to get email");
                return null;
            }
            Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
            if (employeeOpt.isPresent()) {
                Employee employee = employeeOpt.get();
                String email = employee.getEmailAddress();
                if (email != null && !email.trim().isEmpty()) {
                    log.debug("📧 Found email for employee ID {}: {}", employeeId, email);
                    return email.trim();
                }
                log.warn("⚠️ Employee ID {} has no email address", employeeId);
                return null;
            }
            log.warn("⚠️ Employee not found with ID: {}", employeeId);
            return null;
        } catch (Exception e) {
            log.error("❌ Error retrieving email for employee ID: {}", employeeId, e);
            return null;
        }
    }

    private void sendSigningEmail(Transfer transfer, SignerInfo signer, TransferToken token) {
        try {
            log.debug("📧 Sending email to {} on thread: {}", signer.getEmail(), Thread.currentThread().getName());

            String employeeName = getEmployeeName(signer.getEmployeeId());
            String greeting = (employeeName != null) ? employeeName : getRoleDisplayName(signer.getRole());
            String signingLink = "https://sandbox.interswitch.io/digitalassetsmgmt/transfers/sign?token=" + token.getToken();

            String subject = "Transfer Signature Required - Asset: " + transfer.getAssetTag() + " (Role: " + getRoleDisplayName(signer.getRole()) + ")";
            String body = String.format("""
                    Dear %s,
                    
                    You are required to sign a transfer for asset: %s
                    
                    Transfer Details:
                    - Transfer ID: %s
                    - Asset Tag: %s
                    - Your Role: %s
                    - Transfer Date: %s
                    
                    IMPORTANT: This signature is required to complete the asset transfer process.
                    Your digital signature confirms your approval of this transfer in your capacity as %s.
                    
                    Please click the link below to review details and sign:
                    %s
                    
                    Token Details:
                    - Signing Token: %s
                    - Token expires at: %s
                    
                    If you have any questions about this transfer, please contact the IT Asset Management team.
                    
                    Thank you for your prompt attention to this matter.
                    
                    Best regards,
                    Asset Management System
                    Interswitch E.A (U) Ltd
                    """,
                    greeting, transfer.getAssetTag(), transfer.getTransferId(), transfer.getAssetTag(),
                    getRoleDisplayName(signer.getRole()),
                    (transfer.getTransferDate() != null) ? transfer.getTransferDate().toString() : "N/A",
                    getRoleDisplayName(signer.getRole()), signingLink, token.getToken(), token.getExpiresAt());

            log.info("📧 SENDING EMAIL TO: {}", signer.getEmail());
            log.info("🔗 LINK IN EMAIL: {}", signingLink);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(signer.getEmail());
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("products_ea@interswitchgroup.com");
            mailSender.send(message);

            log.info("✅ Email sent successfully to: {} (Link: {})", signer.getEmail(), signingLink);

        } catch (Exception e) {
            log.error("❌ Failed to send email to: {} (Role: {}) on thread: {}",
                    signer.getEmail(), signer.getRole(), Thread.currentThread().getName(), e);

            String manualLink = "https://sandbox.interswitch.io/digitalassetsmgmt/transfers/sign?token=" + token.getToken();
            log.info("🔗 MANUAL LINK FOR {}: {}", signer.getEmail(), manualLink);

            throw new RuntimeException("Email sending failed", e);
        }
    }

    private String getRoleDisplayName(String role) {
        switch (role) {
            case "OLD_HANDOVER": return "Old Department Handover";
            case "OLD_RECEIVED": return "Old Department Receiver";
            case "NEW_HANDOVER": return "New Department Handover";
            case "NEW_RECEIVED": return "New Department Receiver";
            case "CONFIGURED_BY": return "Asset Configurator";
            case "INFRA_REP": return "Infrastructure Representative";
            case "FINANCE_REP": return "Finance Representative";
            default: return role;
        }
    }

    private String getEmployeeName(Long employeeId) {
        try {
            if (employeeId == null) return null;
            Optional<Employee> employeeOpt = employeeRepository.findById(employeeId);
            if (employeeOpt.isPresent()) {
                Employee employee = employeeOpt.get();
                String firstName = employee.getFirstName();
                String surname = employee.getSurName();
                if (firstName != null && surname != null) {
                    return firstName.trim() + " " + surname.trim();
                }
                if (firstName != null) return firstName.trim();
                if (surname != null) return surname.trim();
            }
        } catch (Exception e) {
            log.warn("Could not retrieve employee name for ID: {}", employeeId, e);
        }
        return null;
    }

    private String generateUniqueToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 32);
    }

    public Optional<TransferToken> findByToken(String token) {
        return transferTokenRepository.findByToken(token);
    }

    public List<TransferToken> findTokensByTransferId(Integer transferId) {
        return transferTokenRepository.findByTransferId(transferId);
    }

    public boolean hasValidToken(Integer transferId, Long signerEmployeeId) {
        return transferTokenRepository.hasValidTokenForSigner(transferId, signerEmployeeId, LocalDateTime.now());
    }

    public int cleanupExpiredTokens() {
        List<TransferToken> expiredTokens = transferTokenRepository.findByIsUsedFalseAndExpiresAtBefore(LocalDateTime.now());
        int count = expiredTokens.size();
        transferTokenRepository.deleteByIsUsedFalseAndExpiresAtBefore(LocalDateTime.now());
        log.info("🧹 Cleaned up {} expired tokens", count);
        return count;
    }

    public List<TransferToken> getTokensExpiringSoon(int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime futureDate = now.plusDays(days);
        return transferTokenRepository.findTokensExpiringSoon(now, futureDate);
    }

    public long countUnusedTokensForTransfer(Integer transferId) {
        return transferTokenRepository.countByTransferIdAndIsUsedFalse(transferId);
    }

    public long countUsedTokensForTransfer(Integer transferId) {
        return transferTokenRepository.countByTransferIdAndIsUsedTrue(transferId);
    }

    public List<TransferToken> findTokensBySignerEmail(String signerEmail) {
        return transferTokenRepository.findBySignerEmail(signerEmail);
    }

    public Optional<TransferToken> getMostRecentTokenForSigner(Integer transferId, Long signerEmployeeId) {
        return transferTokenRepository.findTopByTransferIdAndSignerEmployeeIdOrderByCreatedAtDesc(transferId, signerEmployeeId);
    }

    public TransferToken saveToken(TransferToken token) {
        return transferTokenRepository.save(token);
    }

    public Optional<TransferToken> findById(UUID tokenId) {
        return transferTokenRepository.findById(tokenId);
    }

    public void deleteToken(UUID tokenId) {
        transferTokenRepository.deleteById(tokenId);
    }

    @Async
    public CompletableFuture<Void> sendReminderEmailsAsync(int reminderDaysBefore) {
        List<TransferToken> expiringSoon = getTokensExpiringSoon(reminderDaysBefore);
        log.info("📧 Found {} tokens expiring within {} days", expiringSoon.size(), reminderDaysBefore);

        for (TransferToken token : expiringSoon) {
            try {
                log.info("📧 Sending reminder email to: {} for token: {}", token.getSignerEmail(), token.getToken());
                String employeeName = getEmployeeName(token.getSignerEmployeeId());
                String greeting = (employeeName != null) ? employeeName : getRoleDisplayName(token.getSignerRole());
                String signingLink = "https://sandbox.interswitch.io/digitalassetsmgmt/transfers/sign?token=" + token.getToken();

                String subject = "URGENT: Transfer Signature Required - Token Expires Soon (Role: " + getRoleDisplayName(token.getSignerRole()) + ")";
                String body = String.format("""
                        Dear %s,
                        
                        URGENT REMINDER: Your signing token for transfer ID: %s is expiring soon.
                        
                        Your Role: %s
                        Token expires at: %s
                        
                        This transfer requires your digital signature to proceed. Please sign immediately to avoid delays.
                        
                        Please click the link below to sign:
                        %s
                        
                        If you cannot access the system or have questions, please contact IT Asset Management immediately.
                        
                        Thank you for your urgent attention.
                        
                        Best regards,
                        Asset Management System
                        Interswitch E.A (U) Ltd
                        """,
                        greeting, token.getTransferId(), getRoleDisplayName(token.getSignerRole()),
                        token.getExpiresAt(), signingLink);

                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(token.getSignerEmail());
                message.setSubject(subject);
                message.setText(body);
                message.setFrom("products_ea@interswitchgroup.com");
                mailSender.send(message);
                log.info("✅ Reminder email sent successfully to: {}", token.getSignerEmail());
                log.info("🔗 Link: {}", signingLink);
            } catch (Exception e) {
                log.error("❌ Failed to send reminder email to: {} for token: {}", token.getSignerEmail(), token.getToken(), e);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public void sendReminderEmails(int reminderDaysBefore) {
        sendReminderEmailsAsync(reminderDaysBefore);
    }
}