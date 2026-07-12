package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Transfer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    private static final String FROM_EMAIL = "assetiq@company.com";

    // ============================================
    // User Management Emails
    // ============================================

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName, String username, String tempPassword) {
        try {
            String subject = "Welcome to AssetIQ-Pro - Your Account Details";
            String body = String.format("""
                Dear %s,
                
                Welcome to AssetIQ-Pro - Your Unified Asset Management System!
                
                Your account has been created successfully.
                
                Login Credentials:
                Username: %s
                Temporary Password: %s
                
                IMPORTANT: This is a temporary password. You will be required to change it on your first login.
                
                Please login to access the system: https://assetiq.company.com/login
                
                If you have any questions, please contact IT Support.
                
                Best regards,
                AssetIQ-Pro Team
                """, fullName, username, tempPassword);

            sendSimpleEmail(toEmail, subject, body);
            log.info("Welcome email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetToken) {
        try {
            String resetLink = "https://assetiq.company.com/reset-password?token=" + resetToken;
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

            sendSimpleEmail(toEmail, subject, body);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPasswordChangeConfirmation(String toEmail, String fullName) {
        try {
            String subject = "Password Changed - AssetIQ-Pro";
            String body = String.format("""
                Dear %s,
                
                This is to confirm that your AssetIQ-Pro password has been changed successfully.
                
                If you did not make this change, please contact IT Support immediately.
                
                Best regards,
                AssetIQ-Pro Team
                """, fullName);

            sendSimpleEmail(toEmail, subject, body);
            log.info("Password change confirmation sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password change confirmation to {}: {}", toEmail, e.getMessage());
        }
    }

    // ============================================
    // Infrastructure Request Emails
    // ============================================

    @Async
    public void sendInfraRequestStatusUpdate(String toEmail, String requesterName,
                                             String requestId, String status,
                                             String comment, String resourceType) {
        try {
            String subject = "Infrastructure Request " + status + " - #" + requestId;
            String body = String.format("""
                Dear %s,
                
                Your infrastructure request for %s (#%s) has been %s.
                
                Status: %s
                Comment: %s
                
                You can view the request details here: https://assetiq.company.com/infra-requests/%s
                
                Best regards,
                Infrastructure Team
                AssetIQ-Pro
                """, requesterName, resourceType, requestId, status, status, comment, requestId);

            sendSimpleEmail(toEmail, subject, body);
            log.info("Infra request status update sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send infra request status update to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendInfraRequestApproval(String toEmail, String approverName,
                                         String requestId, String requesterName,
                                         String resourceType, String approvalLink) {
        try {
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

            sendSimpleEmail(toEmail, subject, body);
            log.info("Infra request approval email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send infra request approval to {}: {}", toEmail, e.getMessage());
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
        try {
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

            sendSimpleEmail(toEmail, subject, body);
            log.info("Transfer signature request sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send transfer signature request to {}: {}", toEmail, e.getMessage());
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
                    new org.springframework.core.io.ByteArrayResource(pdfBytes));

            mailSender.send(message);
            log.info("Transfer completion notification sent to {} recipients", toEmails.size());
        } catch (MessagingException e) {
            log.error("Failed to send transfer completion notification: {}", e.getMessage());
        }
    }

    // ============================================
    // Booking/Resource Emails
    // ============================================

    @Async
    public void sendBookingConfirmation(String toEmail, String fullName,
                                        String resourceType, String bookingDetails) {
        try {
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
            log.info("Booking confirmation sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send booking confirmation to {}: {}", toEmail, e.getMessage());
        }
    }

    // ============================================
    // Warranty/EOL Notifications
    // ============================================

    @Async
    public void sendWarrantyExpiryAlert(String toEmail, String assetTag,
                                        String expiryDate, int daysLeft) {
        try {
            String subject = "WARRANTY EXPIRY ALERT: " + assetTag;
            String body = String.format("""
                ALERT: Asset %s warranty expires in %d days.
                
                Expiry Date: %s
                
                Please take necessary action to renew or replace the asset.
                
                Best regards,
                AssetIQ-Pro System
                """, assetTag, daysLeft, expiryDate);

            sendSimpleEmail(toEmail, subject, body);
            log.info("Warranty expiry alert sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send warranty expiry alert to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendEOLAlert(String toEmail, String assetTag,
                             String eolDate, int daysLeft) {
        try {
            String subject = "EOL ALERT: " + assetTag;
            String body = String.format("""
                ALERT: Asset %s reaches End of Life in %d days.
                
                EOL Date: %s
                
                Please plan for asset replacement/renewal.
                
                Best regards,
                AssetIQ-Pro System
                """, assetTag, daysLeft, eolDate);

            sendSimpleEmail(toEmail, subject, body);
            log.info("EOL alert sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send EOL alert to {}: {}", toEmail, e.getMessage());
        }
    }

    // ============================================
    // Voucher/Meal Coupon Emails
    // ============================================

    @Async
    public void sendVoucherGenerated(String toEmail, String fullName,
                                     String voucherCode, String qrCodePath,
                                     String mealType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(toEmail);
            helper.setSubject("Meal Voucher Generated - " + mealType);
            helper.setText(String.format("""
                Dear %s,
                
                Your meal voucher has been generated.
                
                Voucher Code: %s
                Meal Type: %s
                
                Please present the QR code in the attachment to receive your meal.
                
                Best regards,
                AssetIQ-Pro System
                """, fullName, voucherCode, mealType));

            // Attach QR code
            java.io.File qrFile = new java.io.File(qrCodePath);
            if (qrFile.exists()) {
                helper.addAttachment("voucher_qr.png", qrFile);
            }

            mailSender.send(message);
            log.info("Voucher email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send voucher email to {}: {}", toEmail, e.getMessage());
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
                    transfer.getTransferDate()));

            helper.addAttachment("Transfer_" + transfer.getAssetTag() + ".pdf",
                    new org.springframework.core.io.ByteArrayResource(pdfBytes));

            mailSender.send(message);
            log.info("Transfer completion notification sent to {} recipients", signerEmails.size());
        } catch (MessagingException e) {
            log.error("Failed to send transfer completion notification: {}", e.getMessage());
        }
    }
}
