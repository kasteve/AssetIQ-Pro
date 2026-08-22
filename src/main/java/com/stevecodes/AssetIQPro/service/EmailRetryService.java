package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.EmailQueue;
import com.stevecodes.AssetIQPro.repository.EmailQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailRetryService {

    private final EmailQueueRepository emailQueueRepository;
    private final EmailService emailService;

    // ============================================
    // Queue Email Methods
    // ============================================

    /**
     * Queue an email for asynchronous sending with retry capability
     */
    @Async("emailTaskExecutor")
    public void queueEmail(String to, String subject, String body, int maxRetries) {
        try {
            EmailQueue emailQueue = new EmailQueue();
            emailQueue.setTo(to);
            emailQueue.setSubject(subject);
            emailQueue.setBody(body);
            emailQueue.setRetryCount(0);
            emailQueue.setMaxRetries(maxRetries > 0 ? maxRetries : 5);
            emailQueue.setStatus("PENDING");
            emailQueue.setCreatedAt(LocalDateTime.now());
            emailQueue.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
            emailQueue.setErrorMessage(null);
            emailQueue.setPriority(1);
            emailQueueRepository.save(emailQueue);
            log.info("📧 Email queued for: {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to queue email for {}: {}", to, e.getMessage());
        }
    }

    /**
     * Queue an email with default retry settings (5 retries)
     */
    @Async("emailTaskExecutor")
    public void queueEmail(String to, String subject, String body) {
        queueEmail(to, subject, body, 5);
    }

    /**
     * Queue a high-priority email (urgent)
     */
    @Async("emailTaskExecutor")
    public void queueUrgentEmail(String to, String subject, String body) {
        try {
            EmailQueue emailQueue = new EmailQueue();
            emailQueue.setTo(to);
            emailQueue.setSubject(subject);
            emailQueue.setBody(body);
            emailQueue.setRetryCount(0);
            emailQueue.setMaxRetries(3);
            emailQueue.setStatus("PENDING");
            emailQueue.setCreatedAt(LocalDateTime.now());
            emailQueue.setNextRetryAt(LocalDateTime.now().plusSeconds(30));
            emailQueue.setErrorMessage(null);
            emailQueue.setPriority(3); // High priority
            emailQueueRepository.save(emailQueue);
            log.info("📧 URGENT email queued for: {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to queue urgent email for {}: {}", to, e.getMessage());
        }
    }

    /**
     * Queue a batch of emails
     */
    @Async("emailTaskExecutor")
    public void queueEmails(List<EmailQueue> emails) {
        try {
            for (EmailQueue email : emails) {
                email.setCreatedAt(LocalDateTime.now());
                email.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
                email.setStatus("PENDING");
                email.setRetryCount(0);
            }
            emailQueueRepository.saveAll(emails);
            log.info("📧 Queued {} emails", emails.size());
        } catch (Exception e) {
            log.error("❌ Failed to queue batch emails: {}", e.getMessage());
        }
    }

    /**
     * Queue an email with attachment
     */
    @Async("emailTaskExecutor")
    public void queueEmailWithAttachment(String to, String subject, String body, String attachmentPath) {
        try {
            EmailQueue emailQueue = new EmailQueue();
            emailQueue.setTo(to);
            emailQueue.setSubject(subject);
            emailQueue.setBody(body);
            emailQueue.setAttachmentPath(attachmentPath);
            emailQueue.setRetryCount(0);
            emailQueue.setMaxRetries(5);
            emailQueue.setStatus("PENDING");
            emailQueue.setCreatedAt(LocalDateTime.now());
            emailQueue.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
            emailQueue.setErrorMessage(null);
            emailQueue.setPriority(2);
            emailQueueRepository.save(emailQueue);
            log.info("📧 Email with attachment queued for: {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to queue email with attachment for {}: {}", to, e.getMessage());
        }
    }

    /**
     * Queue an email with entity context (for tracking)
     */
    @Async("emailTaskExecutor")
    public void queueEmailWithContext(String to, String subject, String body,
                                      String emailType, Long relatedEntityId, String relatedEntityType) {
        try {
            EmailQueue emailQueue = new EmailQueue();
            emailQueue.setTo(to);
            emailQueue.setSubject(subject);
            emailQueue.setBody(body);
            emailQueue.setEmailType(emailType);
            emailQueue.setRelatedEntityId(relatedEntityId);
            emailQueue.setRelatedEntityType(relatedEntityType);
            emailQueue.setRetryCount(0);
            emailQueue.setMaxRetries(5);
            emailQueue.setStatus("PENDING");
            emailQueue.setCreatedAt(LocalDateTime.now());
            emailQueue.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
            emailQueue.setErrorMessage(null);
            emailQueue.setPriority(1);
            emailQueueRepository.save(emailQueue);
            log.info("📧 Email queued for: {} (Type: {}, Entity: {})", to, emailType, relatedEntityId);
        } catch (Exception e) {
            log.error("❌ Failed to queue email with context for {}: {}", to, e.getMessage());
        }
    }

    // ============================================
    // Process Pending Emails (Scheduled)
    // ============================================

    /**
     * Process pending emails - runs every 30 seconds
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    @Transactional
    public void processPendingEmails() {
        log.debug("⏰ Processing pending emails...");

        try {
            List<EmailQueue> pendingEmails = emailQueueRepository.findEmailsReadyForRetry(LocalDateTime.now());

            if (pendingEmails.isEmpty()) {
                log.debug("No pending emails to process");
                return;
            }

            log.info("📧 Found {} pending emails to process", pendingEmails.size());

            int sentCount = 0;
            int failedCount = 0;

            for (EmailQueue email : pendingEmails) {
                try {
                    // Send the email
                    if (email.getAttachmentPath() != null && !email.getAttachmentPath().isEmpty()) {
                        // Send with attachment
                        emailService.sendEmailWithAttachment(
                                email.getTo(),
                                email.getSubject(),
                                email.getBody(),
                                email.getAttachmentPath()
                        );
                    } else {
                        // Send simple email
                        emailService.sendSimpleEmail(email.getTo(), email.getSubject(), email.getBody());
                    }

                    // Mark as sent
                    email.setStatus("SENT");
                    email.setSentAt(LocalDateTime.now());
                    emailQueueRepository.save(email);
                    sentCount++;
                    log.info("✅ Email sent from queue to: {}", email.getTo());

                } catch (Exception e) {
                    // Increment retry count
                    email.setRetryCount(email.getRetryCount() + 1);
                    email.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Unknown error");

                    if (email.getRetryCount() >= email.getMaxRetries()) {
                        // Max retries exceeded - mark as failed
                        email.setStatus("FAILED");
                        emailQueueRepository.save(email);
                        log.error("❌ Email failed after {} retries: {} - Error: {}",
                                email.getMaxRetries(), email.getTo(), e.getMessage());
                    } else {
                        // Calculate backoff: exponential backoff
                        long backoffMinutes = (long) Math.pow(2, email.getRetryCount());
                        email.setNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes));
                        emailQueueRepository.save(email);
                        log.warn("⚠️ Email failed, retry {} for {} in {} minutes (Error: {})",
                                email.getRetryCount(), email.getTo(), backoffMinutes, e.getMessage());
                    }
                    failedCount++;
                }
            }

            log.info("✅ Processed {} emails: {} sent, {} failed",
                    pendingEmails.size(), sentCount, failedCount);

        } catch (Exception e) {
            log.error("❌ Error processing pending emails: {}", e.getMessage(), e);
        }
    }

    // ============================================
    // Cleanup Methods (Scheduled)
    // ============================================

    /**
     * Clean up old sent emails - runs daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldSentEmails() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7); // Keep last 7 days
            int deleted = emailQueueRepository.deleteOldSentEmails(cutoff);
            if (deleted > 0) {
                log.info("🧹 Cleaned up {} old sent emails", deleted);
            }
        } catch (Exception e) {
            log.error("❌ Error cleaning up old sent emails: {}", e.getMessage());
        }
    }

    /**
     * Check for stuck emails (pending for more than 24 hours) - runs every hour
     */
    @Scheduled(fixedDelay = 3600000, initialDelay = 60000)
    @Transactional
    public void checkStuckEmails() {
        try {
            LocalDateTime stuckThreshold = LocalDateTime.now().minusHours(24);
            List<EmailQueue> stuckEmails = emailQueueRepository.findStuckPendingEmails(stuckThreshold);

            if (!stuckEmails.isEmpty()) {
                log.warn("⚠️ Found {} stuck emails (pending > 24 hours)", stuckEmails.size());
                for (EmailQueue email : stuckEmails) {
                    log.warn("   Stuck email: ID={}, To={}, Created={}, RetryCount={}",
                            email.getId(), email.getTo(), email.getCreatedAt(), email.getRetryCount());
                    // Mark as failed
                    email.setStatus("FAILED");
                    email.setErrorMessage("Stuck - exceeded 24 hours in PENDING state");
                    emailQueueRepository.save(email);
                }
                log.info("🔄 Marked {} stuck emails as FAILED", stuckEmails.size());
            }
        } catch (Exception e) {
            log.error("❌ Error checking stuck emails: {}", e.getMessage());
        }
    }

    // ============================================
    // Query Methods
    // ============================================

    /**
     * Get all pending emails
     */
    public List<EmailQueue> getPendingEmails() {
        return emailQueueRepository.findByStatusOrderByCreatedAtAsc("PENDING");
    }

    /**
     * Get all failed emails
     */
    public List<EmailQueue> getFailedEmails() {
        return emailQueueRepository.findFailedEmails();
    }

    /**
     * Get all sent emails
     */
    public List<EmailQueue> getSentEmails() {
        return emailQueueRepository.findByStatus("SENT");
    }

    /**
     * Get emails by recipient
     */
    public List<EmailQueue> getEmailsByRecipient(String email) {
        return emailQueueRepository.findByToOrderByCreatedAtDesc(email);
    }

    /**
     * Get email by ID
     */
    public EmailQueue getEmailById(Long id) {
        return emailQueueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Email not found: " + id));
    }

    /**
     * Get count of pending emails
     */
    public long getPendingCount() {
        return emailQueueRepository.countByStatus("PENDING");
    }

    /**
     * Get count of failed emails
     */
    public long getFailedCount() {
        return emailQueueRepository.countFailedEmails();
    }

    /**
     * Get count of sent emails in last 24 hours
     */
    public long getSentCountLast24Hours() {
        return emailQueueRepository.countSentSince(LocalDateTime.now().minusHours(24));
    }

    // ============================================
    // Management Methods
    // ============================================

    /**
     * Reset all failed emails for manual retry
     */
    @Transactional
    public int resetAllFailedEmails() {
        try {
            LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(1);
            emailQueueRepository.resetFailedEmails(nextRetry);
            long count = emailQueueRepository.countByStatus("FAILED");
            log.info("🔄 Reset {} failed emails for retry", count);
            return (int) count;
        } catch (Exception e) {
            log.error("❌ Error resetting failed emails: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Reset a specific failed email for retry
     */
    @Transactional
    public void resetFailedEmail(Long emailId) {
        try {
            EmailQueue email = emailQueueRepository.findById(emailId)
                    .orElseThrow(() -> new RuntimeException("Email not found: " + emailId));

            if (!"FAILED".equals(email.getStatus())) {
                throw new IllegalStateException("Email is not in FAILED status: " + email.getStatus());
            }

            email.setStatus("PENDING");
            email.setRetryCount(0);
            email.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
            email.setErrorMessage(null);
            emailQueueRepository.save(email);
            log.info("🔄 Reset failed email {} for retry", emailId);
        } catch (Exception e) {
            log.error("❌ Error resetting failed email {}: {}", emailId, e.getMessage());
            throw e;
        }
    }

    /**
     * Manually retry a failed email immediately
     */
    @Transactional
    public void retryEmailNow(Long emailId) {
        try {
            EmailQueue email = emailQueueRepository.findById(emailId)
                    .orElseThrow(() -> new RuntimeException("Email not found: " + emailId));

            if (!"FAILED".equals(email.getStatus()) && !"PENDING".equals(email.getStatus())) {
                throw new IllegalStateException("Email must be in PENDING or FAILED status: " + email.getStatus());
            }

            email.setStatus("PENDING");
            email.setNextRetryAt(LocalDateTime.now());
            emailQueueRepository.save(email);
            log.info("🔄 Manual retry triggered for email {}", emailId);

            // Process immediately
            processPendingEmails();
        } catch (Exception e) {
            log.error("❌ Error retrying email {}: {}", emailId, e.getMessage());
            throw e;
        }
    }

    /**
     * Delete an email from the queue
     */
    @Transactional
    public void deleteEmail(Long emailId) {
        try {
            EmailQueue email = emailQueueRepository.findById(emailId)
                    .orElseThrow(() -> new RuntimeException("Email not found: " + emailId));

            if ("SENT".equals(email.getStatus())) {
                log.warn("Deleting sent email: {}", emailId);
            }

            emailQueueRepository.delete(email);
            log.info("🗑️ Deleted email {}", emailId);
        } catch (Exception e) {
            log.error("❌ Error deleting email {}: {}", emailId, e.getMessage());
            throw e;
        }
    }

    /**
     * Delete all failed emails
     */
    @Transactional
    public int deleteAllFailedEmails() {
        try {
            List<EmailQueue> failedEmails = emailQueueRepository.findFailedEmails();
            int count = failedEmails.size();
            emailQueueRepository.deleteAll(failedEmails);
            log.info("🗑️ Deleted {} failed emails", count);
            return count;
        } catch (Exception e) {
            log.error("❌ Error deleting failed emails: {}", e.getMessage());
            return 0;
        }
    }

    // ============================================
    // Statistics Methods
    // ============================================

    /**
     * Get comprehensive email statistics
     */
    public EmailStats getStats() {
        try {
            EmailStats stats = new EmailStats();
            stats.setTotal(emailQueueRepository.count());
            stats.setPending(emailQueueRepository.countByStatus("PENDING"));
            stats.setSent(emailQueueRepository.countByStatus("SENT"));
            stats.setFailed(emailQueueRepository.countByStatus("FAILED"));
            stats.setSentLast24Hours(emailQueueRepository.countSentSince(LocalDateTime.now().minusHours(24)));

            // Get status breakdown
            List<Object[]> statusGroups = emailQueueRepository.countByStatusGroup();
            for (Object[] group : statusGroups) {
                String status = (String) group[0];
                Long count = (Long) group[1];
                switch (status) {
                    case "PENDING":
                        stats.setPending(count);
                        break;
                    case "SENT":
                        stats.setSent(count);
                        break;
                    case "FAILED":
                        stats.setFailed(count);
                        break;
                }
            }

            return stats;
        } catch (Exception e) {
            log.error("❌ Error getting email stats: {}", e.getMessage());
            return new EmailStats();
        }
    }

    // ============================================
    // Inner Class for Statistics
    // ============================================

    @lombok.Data
    public static class EmailStats {
        private long total;
        private long pending;
        private long sent;
        private long failed;
        private long sentLast24Hours;
    }
}