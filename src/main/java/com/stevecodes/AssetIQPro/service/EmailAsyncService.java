package com.stevecodes.AssetIQPro.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAsyncService {

    private final EmailService emailService;

    @Async("emailTaskExecutor")
    public void sendEmailAsync(String to, String subject, String body) {
        try {
            emailService.sendEmail(to, subject, body);
            log.info("✅ Email sent asynchronously to: {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to send email asynchronously to: {} - {}", to, e.getMessage());
            // Could store in a retry queue here
        }
    }

    @Async("emailTaskExecutor")
    public void sendHtmlEmailAsync(String to, String subject, String htmlBody) {
        try {
            emailService.sendHtmlEmail(to, subject, htmlBody);
            log.info("✅ HTML email sent asynchronously to: {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to send HTML email asynchronously to: {} - {}", to, e.getMessage());
        }
    }

    @Async("emailTaskExecutor")
    public void sendEmailWithAttachmentAsync(String to, String subject, String body, String attachmentPath) {
        try {
            emailService.sendEmailWithAttachment(to, subject, body, attachmentPath);
            log.info("✅ Email with attachment sent asynchronously to: {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to send email with attachment asynchronously to: {} - {}", to, e.getMessage());
        }
    }
}