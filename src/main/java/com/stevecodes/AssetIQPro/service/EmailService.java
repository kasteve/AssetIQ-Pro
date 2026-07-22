package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Transfer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class EmailService {

    @Autowired
    private BaseUrlService baseUrlService;

    @Autowired
    private SystemSettingService settingService;

    // ============================================
    // Dynamic Mail Sender (built from DB-stored settings)
    // ============================================

    private JavaMailSender buildMailSender() {
        SystemSettingService.EmailConfig cfg = settingService.getEmailConfig();

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.getHost());
        sender.setPort(cfg.getPort());
        sender.setUsername(cfg.getUsername());
        sender.setPassword(cfg.getPassword());

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(cfg.isTlsEnabled()));
        props.put("mail.smtp.starttls.required", String.valueOf(cfg.isTlsEnabled()));
        props.put("mail.smtp.ssl.enable", String.valueOf(cfg.isSslEnabled()));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");

        return sender;
    }

    private String getFromAddress() {
        return settingService.getString(SystemSettingService.KEY_EMAIL_FROM);
    }

    // ============================================
    // Helper Method to Log Email Content
    // ============================================

    private void logEmailContent(String emailType, String toEmail, String subject, String body) {
        log.info("=========================================");
        log.info("📧 EMAIL CONTENT");
        log.info("Type: {}", emailType);
        log.info("To: {}", toEmail);
        log.info("Subject: {}", subject);
        log.info("-----------------------------------------");
        log.info("Body:");
        log.info(body);
        log.info("=========================================");
    }

    private void logEmailSent(String emailType, String toEmail, String subject) {
        log.info("=========================================");
        log.info("✅ EMAIL SENT SUCCESSFULLY");
        log.info("Type: {}", emailType);
        log.info("To: {}", toEmail);
        log.info("Subject: {}", subject);
        log.info("=========================================");
    }

    // ============================================
    // Core Email Methods
    // ============================================

    public void sendSimpleEmail(String toEmail, String subject, String body) {
        try {
            logEmailContent("Simple Email", toEmail, subject, body);

            JavaMailSender mailSender = buildMailSender();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom(getFromAddress());
            mailSender.send(message);

            logEmailSent("Simple Email", toEmail, subject);
        } catch (Exception e) {
            log.error("❌ Failed to send email to {}: {}", toEmail, e.getMessage());
            logEmailContent("Simple Email (FAILED)", toEmail, subject, body);
        }
    }

    /**
     * Send HTML email
     */
    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        try {
            logEmailContent("HTML Email", toEmail, subject, htmlContent);

            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(getFromAddress());

            mailSender.send(message);
            logEmailSent("HTML Email", toEmail, subject);
        } catch (MessagingException e) {
            log.error("❌ Failed to send HTML email to {}: {}", toEmail, e.getMessage());
            sendSimpleEmail(toEmail, subject, "Please view this email in HTML format.");
        } catch (Exception e) {
            log.error("❌ Unexpected error sending HTML email: {}", e.getMessage());
            sendSimpleEmail(toEmail, subject, "Failed to send HTML email.");
        }
    }

    /**
     * Send email with attachment
     */
    public void sendEmailWithAttachment(String toEmail, String subject, String body, byte[] attachment, String fileName) {
        try {
            log.info("📧 Sending email with attachment to: {}", toEmail);
            logEmailContent("Email with Attachment", toEmail, subject, body);

            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body);
            helper.setFrom(getFromAddress());

            if (attachment != null && attachment.length > 0) {
                ByteArrayResource resource = new ByteArrayResource(attachment);
                helper.addAttachment(fileName, resource);
                log.info("📎 Attachment added: {}", fileName);
            }

            mailSender.send(message);
            logEmailSent("Email with Attachment", toEmail, subject);
        } catch (MessagingException e) {
            log.error("❌ Failed to send email with attachment to {}: {}", toEmail, e.getMessage());
            sendSimpleEmail(toEmail, subject, body + "\n\n(Attachment could not be sent. Please download from the portal.)");
        } catch (Exception e) {
            log.error("❌ Unexpected error sending email with attachment: {}", e.getMessage());
            sendSimpleEmail(toEmail, subject, body + "\n\n(Attachment could not be sent. Please download from the portal.)");
        }
    }

    // ============================================
    // User Management Emails
    // ============================================

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName, String username, String tempPassword) {
        String loginUrl = baseUrlService.buildUrl("/login");
        String subject = "Welcome to AssetIQ-Pro - Your Account Details";
        String body = String.format("""
            Dear %s,
            
            Welcome to AssetIQ-Pro - Your Unified Asset Management System!
            
            Your account has been created successfully.
            
            Login Credentials:
            Username: %s
            Temporary Password: %s
            
            IMPORTANT: This is a temporary password. You will be required to change it on your first login.
            
            Please login to access the system: %s
            
            If you have any questions, please contact IT Support.
            
            Best regards,
            AssetIQ-Pro Team
            """, fullName, username, tempPassword, loginUrl);

        sendSimpleEmail(toEmail, subject, body);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        try {
            String subject = "Password Reset - AssetIQ-Pro";
            String resetLink = baseUrlService.buildUrl("/reset-password?token=%s", token);

            String htmlContent = "<html><body style='font-family: Arial, sans-serif;'>"
                    + "<div style='max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e9ecef; border-radius: 10px;'>"
                    + "<h2 style='color: #1a1a2e;'>Asset<span style='color: #0d6efd;'>IQ-Pro</span></h2>"
                    + "<hr style='border-color: #e9ecef;'>"
                    + "<p>Dear " + fullName + ",</p>"
                    + "<p>We received a request to reset your password. Click the button below to set a new password:</p>"
                    + "<p style='text-align: center; margin: 30px 0;'>"
                    + "<a href='" + resetLink + "' style='background: #0d6efd; color: white; padding: 12px 30px; text-decoration: none; border-radius: 8px; font-weight: 600;'>Reset Password</a>"
                    + "</p>"
                    + "<p>If you didn't request this, please ignore this email.</p>"
                    + "<p>This link will expire in 24 hours.</p>"
                    + "<hr style='border-color: #e9ecef;'>"
                    + "<p style='color: #6c757d; font-size: 12px;'>AssetIQ-Pro - Asset Management System</p>"
                    + "</div></body></html>";

            sendHtmlEmail(toEmail, subject, htmlContent);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            String fallbackBody = String.format("""
                Dear %s,
                
                We received a request to reset your password.
                
                Please use this token to reset your password: %s
                
                If you didn't request this, please ignore this email.
                
                Best regards,
                AssetIQ-Pro Team
                """, fullName, token);
            sendSimpleEmail(toEmail, "Password Reset - AssetIQ-Pro", fallbackBody);
        }
    }

    @Async
    public void sendPasswordChangeConfirmation(String toEmail, String fullName) {
        String subject = "Password Changed - AssetIQ-Pro";
        String body = String.format("""
            Dear %s,
            
            This is to confirm that your AssetIQ-Pro password has been changed successfully.
            
            If you did not make this change, please contact IT Support immediately.
            
            Best regards,
            AssetIQ-Pro Team
            """, fullName);

        sendSimpleEmail(toEmail, subject, body);
    }

    // ============================================
    // Infrastructure Request Emails
    // ============================================

    @Async
    public void sendInfraRequestStatusUpdate(String toEmail, String requesterName,
                                             String requestId, String status,
                                             String comment, String resourceType) {
        String requestUrl = baseUrlService.buildUrl("/infra-requests/%s", requestId);
        String subject = "Infrastructure Request " + status + " - #" + requestId;
        String body = String.format("""
            Dear %s,
            
            Your infrastructure request for %s (#%s) has been %s.
            
            Status: %s
            Comment: %s
            
            You can view the request details here: %s
            
            Best regards,
            Infrastructure Team
            AssetIQ-Pro
            """, requesterName, resourceType, requestId, status, status, comment, requestUrl);

        sendSimpleEmail(toEmail, subject, body);
    }

    @Async
    public void sendInfraRequestApproval(String toEmail, String approverName,
                                         String requestId, String requesterName,
                                         String resourceType, String approvalLink) {
        String link = approvalLink != null ? approvalLink : baseUrlService.buildUrl("/infra-requests/%s/approve", requestId);
        String subject = "Action Required: Infrastructure Request Approval - #" + requestId;
        String body = String.format("""
            Dear %s,
            
            An infrastructure request requires your approval:
            
            Request #: %s
            Requester: %s
            Resource: %s
            
            Click here to review and approve: %s
            
            Best regards,
            Infrastructure Team
            AssetIQ-Pro
            """, approverName, requestId, requesterName, resourceType, link);

        sendSimpleEmail(toEmail, subject, body);
    }

    @Async
    public void sendInfraRequestSigningLink(String toEmail, String requesterName,
                                            Long requestId, String signingLink,
                                            String expiryDate) {
        String link = signingLink != null ? signingLink : baseUrlService.buildUrl("/infra-requests/sign/%s", requestId);
        String subject = "Infrastructure Request - Sign to Complete #" + requestId;
        String body = String.format("""
            Dear %s,
            
            Your infrastructure request (#%s) has been delivered.
            
            Please click the link below to sign and acknowledge receipt:
            
            %s
            
            This link will expire on: %s
            
            If you have any questions, please contact the Infrastructure Team.
            
            Best regards,
            Infrastructure Team
            AssetIQ-Pro
            """, requesterName, requestId, link, expiryDate);

        sendSimpleEmail(toEmail, subject, body);
    }

    // ============================================
    // Driver Request Emails
    // ============================================

    @Async
    public void sendDriverRequestStatusUpdate(String toEmail, String title, String message) {
        String subject = "Driver Request Update - " + title;
        String body = String.format("""
            Dear User,
            
            %s
            
            For more details, please check your dashboard.
            
            Best regards,
            Transport Team
            AssetIQ-Pro
            """, message);

        sendSimpleEmail(toEmail, subject, body);
    }

    // ============================================
    // Resource Request Emails
    // ============================================

    @Async
    public void sendResourceRequestNotification(String toEmail, String title, String message) {
        String subject = "Resource Request - " + title;
        String body = String.format("""
            Dear Admin,
            
            %s
            
            Please review and take appropriate action.
            
            Best regards,
            AssetIQ-Pro System
            """, message);

        sendSimpleEmail(toEmail, subject, body);
    }

    @Async
    public void sendResourceRequestStatusUpdate(String toEmail, String title, String message) {
        String subject = "Resource Request Update - " + title;
        String body = String.format("""
            Dear User,
            
            %s
            
            For more details, please check your dashboard.
            
            Best regards,
            Resource Management Team
            AssetIQ-Pro
            """, message);

        sendSimpleEmail(toEmail, subject, body);
    }

    // ============================================
    // Transfer/Asset Emails
    // ============================================

    @Async
    public void sendTransferSignatureRequest(String toEmail, String fullName,
                                             String assetTag, String transferId,
                                             String role, String signingLink,
                                             String expiresAt) {
        String link = signingLink != null ? signingLink : baseUrlService.buildUrl("/transfers/sign/%s", transferId);
        String subject = "Signature Required: Asset Transfer - " + assetTag;
        String body = String.format("""
            Dear %s,
            
            Your signature is required for asset transfer #%s.
            
            Asset Tag: %s
            Your Role: %s
            
            Please review and sign the transfer document:
            %s
            
            This link expires on: %s
            
            Best regards,
            Asset Management Team
            AssetIQ-Pro
            """, fullName, transferId, assetTag, role, link, expiresAt);

        sendSimpleEmail(toEmail, subject, body);
    }

    @Async
    public void sendTransferCompletionNotification(List<String> toEmails, String assetTag,
                                                   String transferId, byte[] pdfBytes) {
        try {
            String subject = "Transfer Complete: " + assetTag + " - #" + transferId;
            String body = String.format("""
                Dear Team,
                
                The asset transfer for %s has been completed successfully.
                
                Transfer ID: %s
                
                Please find the fully signed transfer certificate attached.
                
                Best regards,
                Asset Management Team
                AssetIQ-Pro
                """, assetTag, transferId);

            logEmailContent("Transfer Completion", String.join(", ", toEmails), subject, body);

            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(toEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(body);
            helper.setFrom(getFromAddress());

            helper.addAttachment("Transfer_" + assetTag + ".pdf",
                    new ByteArrayResource(pdfBytes));

            mailSender.send(message);
            logEmailSent("Transfer Completion", String.join(", ", toEmails), subject);
        } catch (MessagingException e) {
            log.error("❌ Failed to send transfer completion notification: {}", e.getMessage());
        }
    }

    @Async
    public void sendCompletedTransferReport(List<String> signerEmails, Transfer transfer, byte[] pdfBytes) {
        try {
            String subject = "Transfer Complete: " + transfer.getAssetTag() + " - #" + transfer.getTransferId();
            String body = String.format("""
                Dear Team,
                
                The asset transfer for %s has been completed successfully.
                
                Transfer ID: %s
                Asset Tag: %s
                Transfer Date: %s
                
                Please find the fully signed transfer certificate attached.
                
                Best regards,
                Asset Management Team
                AssetIQ-Pro
                """,
                    transfer.getAssetTag(),
                    transfer.getTransferId(),
                    transfer.getAssetTag(),
                    transfer.getTransferDate() != null ? transfer.getTransferDate().toString() : "N/A");

            logEmailContent("Transfer Completion Report", String.join(", ", signerEmails), subject, body);

            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(signerEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(body);
            helper.setFrom(getFromAddress());

            helper.addAttachment("Transfer_" + transfer.getAssetTag() + ".pdf",
                    new ByteArrayResource(pdfBytes));

            mailSender.send(message);
            logEmailSent("Transfer Completion Report", String.join(", ", signerEmails), subject);
        } catch (MessagingException e) {
            log.error("❌ Failed to send transfer completion report: {}", e.getMessage());
        }
    }

    // ============================================
    // Booking/Resource Emails
    // ============================================

    @Async
    public void sendBookingConfirmation(String toEmail, String fullName,
                                        String resourceType, String bookingDetails) {
        String subject = "Booking Confirmation - " + resourceType;
        String body = String.format("""
            Dear %s,
            
            Your booking has been confirmed:
            
            Resource: %s
            Details: %s
            
            Thank you for using AssetIQ-Pro.
            
            Best regards,
            AssetIQ-Pro Team
            """, fullName, resourceType, bookingDetails);

        sendSimpleEmail(toEmail, subject, body);
    }

    // ============================================
    // Warranty/EOL Notifications
    // ============================================

    @Async
    public void sendWarrantyExpiryAlert(String toEmail, String assetTag,
                                        String expiryDate, int daysLeft) {
        String subject = "WARRANTY EXPIRY ALERT: " + assetTag;
        String body = String.format("""
            ALERT: Asset %s warranty expires in %d days.
            
            Expiry Date: %s
            
            Please take necessary action to renew or replace the asset.
            
            Best regards,
            AssetIQ-Pro System
            """, assetTag, daysLeft, expiryDate);

        sendSimpleEmail(toEmail, subject, body);
    }

    @Async
    public void sendEOLAlert(String toEmail, String assetTag,
                             String eolDate, int daysLeft) {
        String subject = "EOL ALERT: " + assetTag;
        String body = String.format("""
            ALERT: Asset %s reaches End of Life in %d days.
            
            EOL Date: %s
            
            Please plan for asset replacement/renewal.
            
            Best regards,
            AssetIQ-Pro System
            """, assetTag, daysLeft, eolDate);

        sendSimpleEmail(toEmail, subject, body);
    }

    // ============================================
    // Voucher/Meal Coupon Emails
    // ============================================

    @Async
    public void sendVoucherGenerated(String toEmail, String fullName,
                                     String voucherCode, String qrCodePath,
                                     String mealType) {
        String subject = "Meal Voucher Generated - " + mealType;
        String body = String.format("""
            Dear %s,
            
            Your meal voucher has been generated.
            
            Voucher Code: %s
            Meal Type: %s
            
            Please present the QR code in the attachment to receive your meal.
            
            Best regards,
            AssetIQ-Pro System
            """, fullName, voucherCode, mealType);

        try {
            logEmailContent("Voucher Generated", toEmail, subject, body);

            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body);
            helper.setFrom(getFromAddress());

            File qrFile = new File(qrCodePath);
            if (qrFile.exists()) {
                helper.addAttachment("voucher_qr.png", qrFile);
            }

            mailSender.send(message);
            logEmailSent("Voucher Generated", toEmail, subject);
        } catch (Exception e) {
            log.error("❌ Failed to send voucher email to {}: {}", toEmail, e.getMessage());
            logEmailContent("Voucher Generated (FAILED)", toEmail, subject, body);
        }
    }

    // ============================================
    // Room Booking Emails
    // ============================================

    @Async
    public void sendRoomSlotRequest(String toEmail, String requesterName,
                                    String roomName, String timeSlot) {
        String subject = "Room Slot Request - " + roomName;
        String body = String.format("""
            Dear User,
            
            %s has requested to use the room '%s' during your booked time slot (%s).
            
            Please login to approve or decline this request.
            
            Best regards,
            AssetIQ-Pro Team
            """, requesterName, roomName, timeSlot);

        sendSimpleEmail(toEmail, subject, body);
    }

    @Async
    public void sendResourceRequestAcknowledgment(String toEmail, String requesterName,
                                                  String resourceType, String requestId) {
        String subject = "Resource Request Acknowledged - #" + requestId;
        String body = String.format("""
            Dear %s,
            
            Thank you for acknowledging receipt of your resource request.
            
            Resource: %s
            Request #: %s
            
            Best regards,
            AssetIQ-Pro Team
            """, requesterName, resourceType, requestId);

        sendSimpleEmail(toEmail, subject, body);
    }
}