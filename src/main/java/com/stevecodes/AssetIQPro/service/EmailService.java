package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Transfer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Slf4j
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    private static final String FROM_EMAIL = "assetiq@company.com";

    // ============================================
    // Helper Method to Log Email Content
    // ============================================

    private void logEmailContent(String emailType, String toEmail, String subject, String body) {
        log.info("=========================================");
        log.info("📧 EMAIL CONTENT (Send Failed)");
        log.info("Type: {}", emailType);
        log.info("To: {}", toEmail);
        log.info("Subject: {}", subject);
        log.info("-----------------------------------------");
        log.info("Body:");
        log.info(body);
        log.info("=========================================");
    }

    // ============================================
    // User Management Emails
    // ============================================

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName, String username, String tempPassword) {
        String subject = "Welcome to AssetIQ-Pro - Your Account Details";
        String body = String.format("""
            Dear %s,
            
            Welcome to AssetIQ-Pro - Your Unified Asset Management System!
            
            Your account has been created successfully.
            
            Login Credentials:
            Username: %s
            Temporary Password: %s
            
            IMPORTANT: This is a temporary password. You will be required to change it on your first login.
            
            Please login to access the system: http://localhost:8091/assetIQ-pro/login
            
            If you have any questions, please contact IT Support.
            
            Best regards,
            AssetIQ-Pro Team
            """, fullName, username, tempPassword);

        try {
            sendSimpleEmail(toEmail, subject, body);
            log.info("✅ Welcome email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send welcome email to {}: {}", toEmail, e.getMessage());
            logEmailContent("Welcome Email", toEmail, subject, body);
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetToken) {
        String resetLink = "http://localhost:8091/assetIQ-pro/reset-password?token=" + resetToken;
        String subject = "Password Reset Request - AssetIQ-Pro";
        String body = String.format("""
            Dear %s,
            
            We received a request to reset your password for AssetIQ-Pro.
            
            Click the link below to reset your password:
            %s
            
            This link will expire in 24 hours.
            
            If you did not request a password reset, please ignore this email or contact IT Support.
            
            Best regards,
            AssetIQ-Pro Team
            """, fullName, resetLink);

        try {
            sendSimpleEmail(toEmail, subject, body);
            log.info("✅ Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send password reset email to {}: {}", toEmail, e.getMessage());
            logEmailContent("Password Reset Email", toEmail, subject, body);
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

        try {
            sendSimpleEmail(toEmail, subject, body);
            log.info("✅ Password change confirmation sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send password change confirmation to {}: {}", toEmail, e.getMessage());
            logEmailContent("Password Change Confirmation", toEmail, subject, body);
        }
    }

    // ============================================
    // Infrastructure Request Emails
    // ============================================

    @Async
    public void sendInfraRequestStatusUpdate(String toEmail, String requesterName,
                                             String requestId, String status,
                                             String comment, String resourceType) {
        String subject = "Infrastructure Request " + status + " - #" + requestId;
        String body = String.format("""
            Dear %s,
            
            Your infrastructure request for %s (#%s) has been %s.
            
            Status: %s
            Comment: %s
            
            You can view the request details here: http://localhost:8091/assetIQ-pro/infra-requests/%s
            
            Best regards,
            Infrastructure Team
            AssetIQ-Pro
            """, requesterName, resourceType, requestId, status, status, comment, requestId);

        try {
            sendSimpleEmail(toEmail, subject, body);
            log.info("✅ Infra request status update sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send infra request status update to {}: {}", toEmail, e.getMessage());
            logEmailContent("Infra Request Status Update", toEmail, subject, body);
        }
    }

    @Async
    public void sendInfraRequestApproval(String toEmail, String approverName,
                                         String requestId, String requesterName,
                                         String resourceType, String approvalLink) {
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
            """, approverName, requestId, requesterName, resourceType, approvalLink);

        try {
            sendSimpleEmail(toEmail, subject, body);
            log.info("✅ Infra request approval email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send infra request approval to {}: {}", toEmail, e.getMessage());
            logEmailContent("Infra Request Approval", toEmail, subject, body);
        }
    }

    // ============================================
    // Transfer/Asset Emails
    // ============================================

    @Async
    public void sendTransferSignatureRequest(String toEmail, String fullName,
                                             String assetTag, String transferId,
                                             String role, String signingLink,
                                             String expiresAt) {
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
            """, fullName, transferId, assetTag, role, signingLink, expiresAt);

        try {
            sendSimpleEmail(toEmail, subject, body);
            log.info("✅ Transfer signature request sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send transfer signature request to {}: {}", toEmail, e.getMessage());
            logEmailContent("Transfer Signature Request", toEmail, subject, body);
        }
    }

    @Async
    public void sendTransferCompletionNotification(List<String> toEmails, String assetTag,
                                                   String transferId, byte[] pdfBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(toEmails.toArray(new String[0]));
            helper.setSubject("Transfer Complete: " + assetTag + " - #" + transferId);
            helper.setText(String.format("""
                Dear Team,
                
                The asset transfer for %s has been completed successfully.
                
                Transfer ID: %s
                
                Please find the fully signed transfer certificate attached.
                
                Best regards,
                Asset Management Team
                AssetIQ-Pro
                """, assetTag, transferId));

            helper.addAttachment("Transfer_" + assetTag + ".pdf",
                    new ByteArrayResource(pdfBytes));

            mailSender.send(message);
            log.info("✅ Transfer completion notification sent to {} recipients", toEmails.size());
        } catch (MessagingException e) {
            log.error("❌ Failed to send transfer completion notification: {}", e.getMessage());
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

        try {
            sendSimpleEmail(toEmail, subject, body);
            log.info("✅ Booking confirmation sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send booking confirmation to {}: {}", toEmail, e.getMessage());
            logEmailContent("Booking Confirmation", toEmail, subject, body);
        }
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

        try {
            sendSimpleEmail(toEmail, subject, body);
            log.info("✅ Warranty expiry alert sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send warranty expiry alert to {}: {}", toEmail, e.getMessage());
            logEmailContent("Warranty Expiry Alert", toEmail, subject, body);
        }
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

        try {
            sendSimpleEmail(toEmail, subject, body);
            log.info("✅ EOL alert sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send EOL alert to {}: {}", toEmail, e.getMessage());
            logEmailContent("EOL Alert", toEmail, subject, body);
        }
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
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body);

            File qrFile = new File(qrCodePath);
            if (qrFile.exists()) {
                helper.addAttachment("voucher_qr.png", qrFile);
            }

            mailSender.send(message);
            log.info("✅ Voucher email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send voucher email to {}: {}", toEmail, e.getMessage());
            logEmailContent("Voucher Generated", toEmail, subject, body);
        }
    }

    // ============================================
    // Completed Transfer Report (with PDF attachment)
    // ============================================

    @Async
    public void sendCompletedTransferReport(List<String> signerEmails, Transfer transfer, byte[] pdfBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(signerEmails.toArray(new String[0]));
            helper.setSubject("Transfer Complete: " + transfer.getAssetTag() + " - #" + transfer.getTransferId());
            helper.setText(String.format("""
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
                    transfer.getTransferDate() != null ? transfer.getTransferDate().toString() : "N/A"));

            helper.addAttachment("Transfer_" + transfer.getAssetTag() + ".pdf",
                    new ByteArrayResource(pdfBytes));

            mailSender.send(message);
            log.info("✅ Transfer completion notification sent to {} recipients", signerEmails.size());
        } catch (MessagingException e) {
            log.error("❌ Failed to send transfer completion notification: {}", e.getMessage());
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

    private void sendSimpleEmail(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        message.setFrom(FROM_EMAIL);
        mailSender.send(message);
    }
}