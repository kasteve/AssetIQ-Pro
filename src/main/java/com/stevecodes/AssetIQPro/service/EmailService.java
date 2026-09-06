package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.entity.Booking;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    // Brand / Design Tokens
    // ============================================

    private static final String PAGE_BG = "#f0f4f8";
    private static final String CARD_BG = "#ffffff";
    private static final String SURFACE_COLOR = "#f8fafc";
    private static final String BORDER_COLOR = "#e8edf4";
    private static final String TEXT_COLOR = "#0a1a2f";
    private static final String TEXT_MUTED = "#64748b";
    private static final String TEXT_BODY = "#334155";

    private static final String PRIMARY_COLOR = "#4f46e5";
    private static final String PRIMARY_DARK = "#3730a3";
    private static final String SUCCESS_COLOR = "#059669";
    private static final String WARNING_COLOR = "#d97706";
    private static final String DANGER_COLOR = "#dc2626";
    private static final String INFO_COLOR = "#2563eb";
    private static final String ACCENT_DRIVER = "#8b5cf6";
    private static final String ACCENT_BOOKING = "#06b6d4";
    private static final String ACCENT_TRANSFER = "#4f46e5";

    private static final String FONT_STACK =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    // ============================================
    // Mail Sender Configuration
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
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return sender;
    }

    private String getFromAddress() {
        String from = settingService.getString(SystemSettingService.KEY_EMAIL_FROM);
        if (from == null || from.isEmpty()) {
            from = "noreply@asset-iq-pro.com";
        }
        return from;
    }

    // ============================================
    // Core Email Methods
    // ============================================

    public void sendSimpleEmail(String toEmail, String subject, String body) {
        try {
            JavaMailSender mailSender = buildMailSender();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom(getFromAddress());
            mailSender.send(message);
            log.info("✅ Simple email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send simple email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        try {
            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(getFromAddress());
            mailSender.send(message);
            log.info("✅ HTML email sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("❌ Failed to send HTML email to {}: {}", toEmail, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, "Please view this email in HTML format.");
        } catch (Exception e) {
            log.error("❌ Unexpected error sending HTML email to {}: {}", toEmail, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, "Failed to send HTML email.");
        }
    }

    // ============================================
    // Email with Attachment - ALL OVERLOADS
    // ============================================

    public void sendEmailWithAttachment(String toEmail, String subject, String body, byte[] attachment, String fileName) {
        try {
            if (attachment == null || attachment.length == 0) {
                log.warn("⚠️ Attachment is empty, sending without attachment");
                sendSimpleEmail(toEmail, subject, body);
                return;
            }

            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.setFrom(getFromAddress());

            ByteArrayResource resource = new ByteArrayResource(attachment);
            helper.addAttachment(fileName, resource);

            mailSender.send(message);
            log.info("✅ Email with attachment sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("❌ MessagingException sending email with attachment to {}: {}", toEmail, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, body + "\n\n(Attachment could not be sent.)");
        } catch (Exception e) {
            log.error("❌ Unexpected error sending email with attachment to {}: {}", toEmail, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, body + "\n\n(Attachment could not be sent.)");
        }
    }

    public void sendEmailWithAttachment(String toEmail, String subject, String body, String attachmentPath) {
        try {
            if (attachmentPath == null || attachmentPath.isEmpty()) {
                log.warn("⚠️ Attachment path is empty, sending without attachment");
                sendSimpleEmail(toEmail, subject, body);
                return;
            }

            Path path = Paths.get(attachmentPath);
            if (!Files.exists(path)) {
                log.warn("⚠️ Attachment file not found: {}", attachmentPath);
                sendSimpleEmail(toEmail, subject, body + "\n\n(Attachment file not found.)");
                return;
            }

            byte[] attachmentBytes = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();
            sendEmailWithAttachment(toEmail, subject, body, attachmentBytes, fileName);

        } catch (Exception e) {
            log.error("❌ Failed to send email with attachment from path {}: {}", attachmentPath, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, body + "\n\n(Attachment could not be sent.)");
        }
    }

    public void sendEmailWithAttachment(String toEmail, String subject, String body, File attachment) {
        try {
            if (attachment == null || !attachment.exists()) {
                log.warn("⚠️ Attachment file not found");
                sendSimpleEmail(toEmail, subject, body);
                return;
            }
            byte[] attachmentBytes = Files.readAllBytes(attachment.toPath());
            sendEmailWithAttachment(toEmail, subject, body, attachmentBytes, attachment.getName());
        } catch (Exception e) {
            log.error("❌ Failed to send email with attachment: {}", e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, body + "\n\n(Attachment could not be sent.)");
        }
    }

    public void sendHtmlEmailWithAttachment(String toEmail, String subject, String htmlContent, byte[] attachment, String fileName) {
        try {
            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(getFromAddress());

            if (attachment != null && attachment.length > 0) {
                ByteArrayResource resource = new ByteArrayResource(attachment);
                helper.addAttachment(fileName, resource);
            }

            mailSender.send(message);
            log.info("✅ HTML email with attachment sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("❌ Failed to send HTML email with attachment to {}: {}", toEmail, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, "Please view this email in HTML format.");
        } catch (Exception e) {
            log.error("❌ Unexpected error sending HTML email with attachment to {}: {}", toEmail, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, "Failed to send email.");
        }
    }

    // ============================================
    // Template Builders (Modern)
    // ============================================

    private String buildModernHeader(String title, String accentColor) {
        return """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:linear-gradient(135deg, %s, %s); border-radius:16px 16px 0 0;">
                <tr>
                    <td style="padding:32px 36px;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                            <tr>
                                <td style="vertical-align:middle;">
                                    <span style="color:#ffffff; font-size:22px; font-weight:800; letter-spacing:-0.5px; font-family:%s;">
                                        Asset<span style="color:rgba(255,255,255,0.7);">IQ</span>-Pro
                                    </span>
                                </td>
                                <td style="text-align:right; vertical-align:middle;">
                                    <span style="display:inline-block; background:rgba(255,255,255,0.15); backdrop-filter:blur(10px); color:#ffffff; font-size:11px; font-weight:600; letter-spacing:0.5px; text-transform:uppercase; padding:6px 16px; border-radius:999px; font-family:%s; border:1px solid rgba(255,255,255,0.1);">%s</span>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td style="padding:0 36px 28px 36px;">
                        <h1 style="color:#ffffff; margin:0; font-size:26px; font-weight:700; font-family:%s; letter-spacing:-0.3px;">%s</h1>
                        <p style="color:rgba(255,255,255,0.85); margin:8px 0 0 0; font-size:14px; font-family:%s;">Your request requires attention</p>
                    </td>
                </tr>
                <tr><td style="height:4px; background:linear-gradient(90deg,%s,%s);"></td></tr>
            </table>
            """.formatted(accentColor, PRIMARY_DARK, FONT_STACK, FONT_STACK, title, FONT_STACK, title, FONT_STACK, accentColor, PRIMARY_DARK);
    }

    private String buildModernFooter() {
        return """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s; border-radius:0 0 16px 16px; border-top:1px solid %s;">
                <tr>
                    <td style="padding:28px 36px;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                            <tr>
                                <td style="text-align:center;">
                                    <p style="color:%s; font-size:12px; margin:0 0 4px 0; font-family:%s;">&copy; %s AssetIQ-Pro. All rights reserved.</p>
                                    <p style="color:%s; font-size:11px; margin:0; font-family:%s;">This is an automated message from AssetIQ-Pro — please do not reply.</p>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
            """.formatted(SURFACE_COLOR, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                String.valueOf(LocalDateTime.now().getYear()), TEXT_MUTED, FONT_STACK);
    }

    private String buildModernButton(String text, String url, String color) {
        return """
            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto;">
                <tr>
                    <td style="border-radius:12px; background:linear-gradient(135deg, %s, %s); box-shadow:0 4px 14px rgba(79,70,229,0.3);">
                        <a href="%s" style="display:inline-block; padding:14px 40px; font-size:15px; font-weight:600; color:#ffffff; text-decoration:none; border-radius:12px; font-family:%s; letter-spacing:0.3px;">%s</a>
                    </td>
                </tr>
            </table>
            """.formatted(color, PRIMARY_DARK, url, FONT_STACK, text);
    }

    private String buildModernInfoCard(String icon, String title, String content, String color) {
        return """
            <div style="background:%s; border:1px solid %s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border-left:4px solid %s;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    <tr>
                        <td style="width:40px; vertical-align:top; padding-top:2px;">
                            <span style="font-size:22px;">%s</span>
                        </td>
                        <td style="vertical-align:top;">
                            <h4 style="color:%s; margin:0 0 6px 0; font-size:15px; font-weight:700; font-family:%s;">%s</h4>
                            <p style="color:%s; margin:0; font-size:14px; line-height:1.6; font-family:%s;">%s</p>
                        </td>
                    </tr>
                </table>
            </div>
            """.formatted(SURFACE_COLOR, BORDER_COLOR, color, icon, TEXT_COLOR, FONT_STACK, title, TEXT_BODY, FONT_STACK, content);
    }

    private String buildModernDetailRow(String label, String value, String icon) {
        return """
            <tr>
                <td style="padding:10px 0; width:35%%; vertical-align:top;">
                    <span style="color:%s; font-size:13px; font-weight:500; font-family:%s;">%s %s</span>
                </td>
                <td style="padding:10px 0; vertical-align:top;">
                    <span style="color:%s; font-size:14px; font-weight:500; font-family:%s;">%s</span>
                </td>
            </tr>
            """.formatted(TEXT_MUTED, FONT_STACK, icon, label, TEXT_COLOR, FONT_STACK, value != null ? value : "N/A");
    }

    private String buildModernStatusBadge(String status, String color) {
        return """
            <span style="display:inline-block; background:%s; color:#ffffff; padding:4px 14px; border-radius:999px; font-size:12px; font-weight:600; letter-spacing:0.3px; font-family:%s;">%s</span>
            """.formatted(color, FONT_STACK, status);
    }

    private String buildExpiryNotice(String expiresAt) {
        return """
            <div style="background:#fefce8; border:1px solid #fde68a; border-radius:12px; padding:14px 20px; margin:16px 0 22px 0; display:flex; align-items:center; gap:12px;">
                <span style="font-size:20px;">⏰</span>
                <div>
                    <p style="margin:0; color:#92400e; font-size:13px; font-weight:600; font-family:%s;">This link will expire on:</p>
                    <p style="margin:0; color:#78350f; font-size:14px; font-weight:700; font-family:%s;">%s</p>
                </div>
            </div>
            """.formatted(FONT_STACK, FONT_STACK, expiresAt != null ? expiresAt : "N/A");
    }

    private String buildModernWrapper(String content, String title, String accentColor) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - AssetIQ-Pro</title>
            </head>
            <body style="margin:0; padding:32px 16px; background:%s; font-family:%s; -webkit-font-smoothing:antialiased;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px; margin:0 auto; background:%s; border-radius:16px; overflow:hidden; box-shadow:0 2px 4px rgba(15,23,42,0.06), 0 20px 60px rgba(15,23,42,0.08);">
                    <tr><td style="padding:0;">%s</td></tr>
                    <tr><td style="padding:32px 36px 16px 36px;">%s</td></tr>
                    <tr><td style="padding:0;">%s</td></tr>
                </table>
            </body>
            </html>
            """.formatted(title, PAGE_BG, FONT_STACK, CARD_BG,
                buildModernHeader(title, accentColor), content, buildModernFooter());
    }

    private String centered(String innerHtml) {
        return "<div style=\"text-align:center; margin:8px 0;\">" + innerHtml + "</div>";
    }

    // ============================================
    // INFRASTRUCTURE REQUEST EMAIL METHODS
    // ============================================

    /**
     * Send infrastructure request approval notification to line manager
     */
    @Async
    public void sendInfraRequestApproval(String toEmail, String approverName,
                                         String requestId, String requesterName,
                                         String resourceType, String approvalLink) {
        String subject = "📋 Action Required: Infrastructure Request Approval - #" + requestId;

        String content = buildInfraRequestApprovalContent(approverName, requestId, requesterName, resourceType, approvalLink);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Approval Required", WARNING_COLOR));
    }

    private String buildInfraRequestApprovalContent(String approverName, String requestId,
                                                    String requesterName, String resourceType,
                                                    String approvalLink) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("📬", "Hello " + approverName,
                "An infrastructure request requires your approval.",
                WARNING_COLOR));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <h4 style="color:%s; margin:0 0 16px 0; font-size:14px; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; font-family:%s;">📋 Request Details</h4>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                buildModernDetailRow("Request ID", requestId, "🔢"),
                buildModernDetailRow("Requester", requesterName, "👤"),
                buildModernDetailRow("Resource Type", resourceType, "📦")));

        sb.append(centered(buildModernButton("Review & Approve", approvalLink, WARNING_COLOR)));

        return sb.toString();
    }

    /**
     * Send infrastructure request status update to requester
     */
    @Async
    public void sendInfraRequestStatusUpdate(String toEmail, String requesterName,
                                             String requestId, String status,
                                             String comment, String resourceType) {
        String subject = "📋 Infrastructure Request " + status + " - #" + requestId;

        String statusColor = switch(status.toUpperCase()) {
            case "APPROVED" -> SUCCESS_COLOR;
            case "REJECTED" -> DANGER_COLOR;
            case "PENDING" -> WARNING_COLOR;
            case "COMPLETED" -> SUCCESS_COLOR;
            default -> PRIMARY_COLOR;
        };

        String content = buildInfraStatusUpdateContent(requesterName, requestId, status,
                comment, resourceType, statusColor);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Status Update", statusColor));
    }

    private String buildInfraStatusUpdateContent(String requesterName, String requestId,
                                                 String status, String comment,
                                                 String resourceType, String statusColor) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("📋", "Hello " + requesterName,
                "Your infrastructure request #" + requestId + " has been " + status.toLowerCase() + ".",
                statusColor));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <h4 style="color:%s; margin:0 0 16px 0; font-size:14px; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; font-family:%s;">📋 Request Details</h4>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                buildModernDetailRow("Request ID", requestId, "🔢"),
                buildModernDetailRow("Status", buildModernStatusBadge(status, statusColor), "📌"),
                buildModernDetailRow("Resource Type", resourceType, "📦"),
                buildModernDetailRow("Comment", comment != null ? comment : "N/A", "💬")));

        String dashboardLink = baseUrlService.buildUrl("/infra-requests/" + requestId);
        sb.append(centered(buildModernButton("View Request", dashboardLink, PRIMARY_COLOR)));

        return sb.toString();
    }

    /**
     * Send infrastructure request signing link to requester
     */
    @Async
    public void sendInfraRequestSigningLink(String toEmail, String requesterName,
                                            Long requestId, String signingLink,
                                            String expiryDate) {
        String subject = "✍️ Infrastructure Request - Sign to Complete #" + requestId;

        String content = buildInfraSigningLinkContent(requesterName, requestId, signingLink, expiryDate);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Sign Request", SUCCESS_COLOR));
    }

    private String buildInfraSigningLinkContent(String requesterName, Long requestId,
                                                String signingLink, String expiryDate) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("✍️", "Hello " + requesterName,
                "Your infrastructure request #" + requestId + " has been delivered and is ready for your signature.",
                SUCCESS_COLOR));

        sb.append(buildExpiryNotice(expiryDate));
        sb.append(centered(buildModernButton("✍️ Sign Now", signingLink, SUCCESS_COLOR)));

        sb.append(buildModernInfoCard("🔒", "Secure Signing",
                "This is a secure, one-time signing link. It cannot be reused after signing.",
                INFO_COLOR));

        return sb.toString();
    }

    // ============================================
    // ✅ NEW: Infra Request Completion Report with PDF Attachment
    // ============================================

    /**
     * Send infrastructure request completion report with PDF attachment
     * This method is called from InfraRequestService.sendCompletionReport()
     */
    @Async
    public void sendInfraRequestCompletionReport(List<String> recipientEmails,
                                                 String resourceType,
                                                 String requestId,
                                                 byte[] pdfBytes) {
        String subject = "✅ Infrastructure Request Completed - #" + requestId;

        String content = buildInfraCompletionContent(resourceType, requestId);
        String htmlContent = buildModernWrapper(content, "Request Completed", SUCCESS_COLOR);

        try {
            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(recipientEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(getFromAddress());

            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment("InfraRequest_" + requestId + ".pdf",
                        new ByteArrayResource(pdfBytes));
                log.info("📎 PDF attached: InfraRequest_{}.pdf", requestId);
            }

            mailSender.send(message);
            log.info("✅ Infra request completion report sent to {} recipients", recipientEmails.size());

        } catch (MessagingException e) {
            log.error("❌ Failed to send infra request completion report: {}", e.getMessage(), e);
            if (!recipientEmails.isEmpty()) {
                sendSimpleEmail(recipientEmails.get(0), subject,
                        "Request complete. Please check the portal for details.");
            }
        } catch (Exception e) {
            log.error("❌ Unexpected error sending infra request completion report: {}", e.getMessage(), e);
            if (!recipientEmails.isEmpty()) {
                sendSimpleEmail(recipientEmails.get(0), subject,
                        "Request complete. Please check the portal for details.");
            }
        }
    }

    /**
     * Build infrastructure completion content
     */
    private String buildInfraCompletionContent(String resourceType, String requestId) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("✅", "Request Completed!",
                "Infrastructure request <strong>#" + requestId + "</strong> for <strong>" + resourceType + "</strong> has been completed and signed off.",
                SUCCESS_COLOR));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR,
                buildModernDetailRow("Request ID", requestId, "🔢"),
                buildModernDetailRow("Resource Type", resourceType, "📦"),
                buildModernDetailRow("Status", buildModernStatusBadge("COMPLETED", SUCCESS_COLOR), "📌")));

        sb.append(buildModernInfoCard("📎", "Report Attached",
                "The completion report is attached to this email as a PDF for your records.",
                INFO_COLOR));

        String dashboardLink = baseUrlService.buildUrl("/infra-requests/" + requestId);
        sb.append(centered(buildModernButton("View Request", dashboardLink, PRIMARY_COLOR)));

        return sb.toString();
    }

    // ============================================
    // DRIVER EMAIL METHODS
    // ============================================

    @Async
    public void sendDriverRequestNotification(String toEmail, String requesterName,
                                              String pickupLocation, String dropoffLocation,
                                              String pickupTime, String purpose,
                                              String requestId, String requestType) {
        String subject = "🚗 New Driver Request - #" + requestId;

        String content = buildDriverRequestContent(requesterName, pickupLocation, dropoffLocation,
                pickupTime, purpose, requestId, requestType, "admin");
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Driver Request", ACCENT_DRIVER));
    }

    private String buildDriverRequestContent(String requesterName, String pickupLocation,
                                             String dropoffLocation, String pickupTime,
                                             String purpose, String requestId,
                                             String requestType, String recipientType) {
        StringBuilder sb = new StringBuilder();

        if (recipientType.equals("admin")) {
            sb.append(buildModernInfoCard("👋", "New Driver Request",
                    "A new " + requestType + " request has been submitted and requires your attention.",
                    ACCENT_DRIVER));
        } else {
            sb.append(buildModernInfoCard("👋", "Hello " + requesterName,
                    "Your driver request has been received and is being processed.",
                    ACCENT_DRIVER));
        }

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <h4 style="color:%s; margin:0 0 16px 0; font-size:14px; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; font-family:%s;">📋 Request Details</h4>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                    %s
                    %s
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                buildModernDetailRow("Request ID", requestId, "🔢"),
                buildModernDetailRow("Request Type", requestType, "📌"),
                buildModernDetailRow("Requester", requesterName, "👤"),
                buildModernDetailRow("Pickup Location", pickupLocation, "📍"),
                buildModernDetailRow("Dropoff Location", dropoffLocation, "🏁"),
                buildModernDetailRow("Pickup Time", pickupTime, "🕐"),
                buildModernDetailRow("Purpose", purpose, "📝")));

        if (recipientType.equals("admin")) {
            String approveLink = baseUrlService.buildUrl("/api/driver-requests/%s/approve", requestId);
            String assignLink = baseUrlService.buildUrl("/api/driver-requests/%s/assign", requestId);

            sb.append("""
                <div style="text-align:center; margin:24px 0 8px 0;">
                    <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto;">
                        <tr>
                            <td style="padding:0 8px;">
                                <a href="%s" style="display:inline-block; padding:10px 28px; background:%s; color:#ffffff; text-decoration:none; border-radius:10px; font-weight:600; font-size:13px; font-family:%s;">✅ Approve</a>
                            </td>
                            <td style="padding:0 8px;">
                                <a href="%s" style="display:inline-block; padding:10px 28px; background:%s; color:#ffffff; text-decoration:none; border-radius:10px; font-weight:600; font-size:13px; font-family:%s;">👤 Assign</a>
                            </td>
                        </tr>
                    </table>
                </div>
                """.formatted(approveLink, SUCCESS_COLOR, FONT_STACK,
                    assignLink, ACCENT_DRIVER, FONT_STACK));
        }

        return sb.toString();
    }

    @Async
    public void sendDriverRequestStatusUpdate(String toEmail, String requesterName,
                                              String requestId, String status,
                                              String driverName, String driverPhone,
                                              String eta, String comment) {
        String subject = "🚗 Driver Request " + status + " - #" + requestId;

        String statusColor = switch(status.toUpperCase()) {
            case "APPROVED" -> SUCCESS_COLOR;
            case "REJECTED" -> DANGER_COLOR;
            case "ASSIGNED" -> ACCENT_DRIVER;
            case "COMPLETED" -> SUCCESS_COLOR;
            default -> WARNING_COLOR;
        };

        String content = buildDriverStatusContent(requesterName, requestId, status,
                driverName, driverPhone, eta, comment, statusColor);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Driver Update", ACCENT_DRIVER));
    }

    private String buildDriverStatusContent(String requesterName, String requestId,
                                            String status, String driverName,
                                            String driverPhone, String eta,
                                            String comment, String statusColor) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("📋", "Hello " + requesterName,
                "Your driver request #" + requestId + " has been " + status.toLowerCase() + ".",
                statusColor));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <h4 style="color:%s; margin:0 0 16px 0; font-size:14px; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; font-family:%s;">📋 Request Details</h4>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                buildModernDetailRow("Request ID", requestId, "🔢"),
                buildModernDetailRow("Status", buildModernStatusBadge(status, statusColor), "📌"),
                buildModernDetailRow("Comment", comment != null ? comment : "N/A", "💬")));

        if (driverName != null && !driverName.isEmpty()) {
            sb.append("""
                <div style="background:%s; border:1px solid %s; border-radius:14px; padding:16px 20px; margin-bottom:20px;">
                    <h4 style="color:%s; margin:0 0 12px 0; font-size:14px; font-weight:700; font-family:%s;">🚗 Driver Details</h4>
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                        %s
                        %s
                        %s
                    </table>
                </div>
                """.formatted(SURFACE_COLOR, BORDER_COLOR, TEXT_COLOR, FONT_STACK,
                    buildModernDetailRow("Driver Name", driverName, "👤"),
                    buildModernDetailRow("Phone", driverPhone != null ? driverPhone : "N/A", "📞"),
                    buildModernDetailRow("ETA", eta != null ? eta : "N/A", "⏱️")));
        }

        return sb.toString();
    }

    @Async
    public void sendDriverAssignedNotification(String toEmail, String requesterName,
                                               String requestId, String driverName,
                                               String driverPhone, String vehicleType,
                                               String eta) {
        String subject = "🚗 Driver Assigned - #" + requestId;

        String content = buildDriverAssignedContent(requesterName, requestId, driverName,
                driverPhone, vehicleType, eta);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Driver Assigned", SUCCESS_COLOR));
    }

    private String buildDriverAssignedContent(String requesterName, String requestId,
                                              String driverName, String driverPhone,
                                              String vehicleType, String eta) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("🚗", "Hello " + requesterName,
                "A driver has been assigned to your request #" + requestId + ".",
                SUCCESS_COLOR));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <h4 style="color:%s; margin:0 0 16px 0; font-size:14px; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; font-family:%s;">🚗 Driver Details</h4>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                buildModernDetailRow("Driver Name", driverName, "👤"),
                buildModernDetailRow("Phone", driverPhone != null ? driverPhone : "N/A", "📞"),
                buildModernDetailRow("Vehicle", vehicleType != null ? vehicleType : "N/A", "🚘"),
                buildModernDetailRow("ETA", eta != null ? eta : "N/A", "⏱️")));

        String trackingLink = baseUrlService.buildUrl("/api/driver-requests/%s/track", requestId);
        sb.append(centered(buildModernButton("Track Request", trackingLink, ACCENT_DRIVER)));

        return sb.toString();
    }

    @Async
    public void sendDriverRequestAcknowledgment(String toEmail, String requesterName,
                                                String requestId) {
        String subject = "📋 Driver Request Received - #" + requestId;

        String content = buildDriverAcknowledgmentContent(requesterName, requestId);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Request Received", ACCENT_BOOKING));
    }

    private String buildDriverAcknowledgmentContent(String requesterName, String requestId) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("📋", "Hello " + requesterName,
                "Your driver request has been submitted successfully.",
                ACCENT_DRIVER));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR,
                buildModernDetailRow("Request ID", requestId, "🔢"),
                buildModernDetailRow("Status", buildModernStatusBadge("PENDING", WARNING_COLOR), "📌")));

        sb.append(buildModernInfoCard("ℹ️", "What's Next?",
                "Your request will be reviewed and a driver will be assigned shortly. You will receive a notification once a driver is assigned.",
                INFO_COLOR));

        String dashboardLink = baseUrlService.buildUrl("/api/driver-requests/my-requests");
        sb.append(centered(buildModernButton("View My Requests", dashboardLink, ACCENT_DRIVER)));

        return sb.toString();
    }

    // ============================================
    // TRANSFER EMAIL METHODS
    // ============================================

    @Async
    public void sendTransferSignatureRequest(String toEmail, String fullName,
                                             String assetTag, String transferId,
                                             String role, String signingLink,
                                             String expiresAt) {
        String subject = "✍️ Signature Required: Asset Transfer - " + assetTag;

        String content = buildTransferSignatureContent(fullName, assetTag, transferId,
                role, signingLink, expiresAt);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Signature Required", ACCENT_TRANSFER));
    }

    private String buildTransferSignatureContent(String fullName, String assetTag,
                                                 String transferId, String role,
                                                 String signingLink, String expiresAt) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("✍️", "Hello " + fullName,
                "Your signature is required to complete the asset transfer process for <strong>" + assetTag + "</strong>.",
                ACCENT_TRANSFER));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <h4 style="color:%s; margin:0 0 16px 0; font-size:14px; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; font-family:%s;">📋 Transfer Details</h4>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                buildModernDetailRow("Asset Tag", assetTag, "📦"),
                buildModernDetailRow("Transfer ID", transferId, "🔢"),
                buildModernDetailRow("Your Role", role, "👤")));

        sb.append(buildExpiryNotice(expiresAt));

        String link = signingLink != null ? signingLink : baseUrlService.buildUrl("/api/transfers/sign/%s", transferId);
        sb.append(centered(buildModernButton("✍️ Review & Sign", link, ACCENT_TRANSFER)));

        sb.append(buildModernInfoCard("🔒", "Secure Signing",
                "This is a secure, one-time signing link. It cannot be reused after signing.",
                INFO_COLOR));

        return sb.toString();
    }

    @Async
    public void sendTransferCompletionNotification(List<String> toEmails, String assetTag,
                                                   String transferId, byte[] pdfBytes) {
        String subject = "✅ Transfer Complete: " + assetTag + " - #" + transferId;

        String content = buildTransferCompletionContent(assetTag, transferId);
        String htmlContent = buildModernWrapper(content, "Transfer Complete", SUCCESS_COLOR);

        try {
            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(getFromAddress());

            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment("Transfer_" + assetTag + ".pdf",
                        new ByteArrayResource(pdfBytes));
            }

            mailSender.send(message);
            log.info("✅ Transfer completion email sent to {} recipients", toEmails.size());
        } catch (MessagingException e) {
            log.error("❌ Failed to send transfer completion notification: {}", e.getMessage(), e);
            sendSimpleEmail(toEmails.get(0), subject, "Transfer complete. Please check the portal for details.");
        }
    }

    private String buildTransferCompletionContent(String assetTag, String transferId) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("✅", "Transfer Complete!",
                "The asset transfer for <strong>" + assetTag + "</strong> has been successfully completed.",
                SUCCESS_COLOR));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR,
                buildModernDetailRow("Asset Tag", assetTag, "📦"),
                buildModernDetailRow("Transfer ID", transferId, "🔢"),
                buildModernDetailRow("Status", buildModernStatusBadge("FULLY SIGNED", SUCCESS_COLOR), "📌")));

        sb.append(buildModernInfoCard("📎", "Certificate Attached",
                "The fully signed transfer certificate is attached to this email for your records.",
                INFO_COLOR));

        String dashboardLink = baseUrlService.buildUrl("/api/transfers/" + transferId);
        sb.append(centered(buildModernButton("View Transfer", dashboardLink, ACCENT_TRANSFER)));

        return sb.toString();
    }

    @Async
    public void sendCompletedTransferReport(List<String> signerEmails, Transfer transfer, byte[] pdfBytes) {
        String subject = "Transfer Complete: " + transfer.getAssetTag() + " - #" + transfer.getTransferId();

        String content = buildTransferCompletionContent(
                transfer.getAssetTag(),
                String.valueOf(transfer.getTransferId())
        );
        String htmlContent = buildModernWrapper(content, "Transfer Complete", SUCCESS_COLOR);

        try {
            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(signerEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(getFromAddress());

            if (pdfBytes != null && pdfBytes.length > 0) {
                helper.addAttachment("Transfer_" + transfer.getAssetTag() + ".pdf",
                        new ByteArrayResource(pdfBytes));
            }

            mailSender.send(message);
            log.info("✅ Completed transfer report sent to {} recipients", signerEmails.size());
        } catch (MessagingException e) {
            log.error("❌ Failed to send completed transfer report: {}", e.getMessage(), e);
            sendSimpleEmail(signerEmails.get(0), subject, "Transfer complete. Please check the portal for details.");
        }
    }

    // ============================================
    // ROOM BOOKING EMAIL METHODS
    // ============================================

    @Async
    public void sendBookingConfirmation(String toEmail, String fullName,
                                        String resourceType, String bookingDetails) {
        String subject = "📅 Booking Confirmed - " + resourceType;

        String content = buildBookingConfirmationContent(fullName, resourceType, bookingDetails);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Booking Confirmed", ACCENT_BOOKING));
    }

    private String buildBookingConfirmationContent(String fullName, String resourceType,
                                                   String bookingDetails) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("📅", "Hello " + fullName,
                "Your booking for <strong>" + resourceType + "</strong> has been confirmed.",
                ACCENT_BOOKING));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <h4 style="color:%s; margin:0 0 16px 0; font-size:14px; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; font-family:%s;">📋 Booking Details</h4>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                buildModernDetailRow("Resource", resourceType, "🏢"),
                buildModernDetailRow("Details", bookingDetails != null ? bookingDetails : "N/A", "📝")));

        sb.append(buildModernInfoCard("ℹ️", "Need to make changes?",
                "You can manage your bookings from the dashboard. Cancellations must be made at least 2 hours in advance.",
                INFO_COLOR));

        String dashboardLink = baseUrlService.buildUrl("/api/bookings/bookings-dashboard");
        sb.append(centered(buildModernButton("View My Bookings", dashboardLink, ACCENT_BOOKING)));

        return sb.toString();
    }

    @Async
    public void sendRoomSlotRequest(String toEmail, String requesterName,
                                    String roomName, String timeSlot) {
        String subject = "🔄 Slot Request - " + roomName;

        String content = buildRoomSlotRequestContent(requesterName, roomName, timeSlot);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Slot Request", WARNING_COLOR));
    }

    private String buildRoomSlotRequestContent(String requesterName, String roomName,
                                               String timeSlot) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("🔄", "Slot Request Received",
                "<strong>" + requesterName + "</strong> has requested to use the room <strong>'" + roomName + "'</strong> during your booked time slot.",
                WARNING_COLOR));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <h4 style="color:%s; margin:0 0 16px 0; font-size:14px; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; font-family:%s;">📋 Request Details</h4>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                buildModernDetailRow("Room", roomName, "🏢"),
                buildModernDetailRow("Requester", requesterName, "👤"),
                buildModernDetailRow("Time Slot", timeSlot, "🕐")));

        String responseLink = baseUrlService.buildUrl("/api/bookings/slot-request/respond");
        sb.append(centered(buildModernButton("Review Request", responseLink, WARNING_COLOR)));

        sb.append(buildModernInfoCard("ℹ️", "What happens next?",
                "You can approve or decline this request from the dashboard. The requester will be notified of your decision.",
                INFO_COLOR));

        return sb.toString();
    }

    @Async
    public void sendServerRoomApproval(String toEmail, String requesterName,
                                       String roomName, String bookingId,
                                       String comment) {
        String subject = "✅ Server Room Approved - #" + bookingId;

        String content = buildServerRoomApprovalContent(requesterName, roomName, bookingId, comment);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Server Room Approved", SUCCESS_COLOR));
    }

    private String buildServerRoomApprovalContent(String requesterName, String roomName,
                                                  String bookingId, String comment) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("✅", "Hello " + requesterName,
                "Your server room booking for <strong>" + roomName + "</strong> has been approved by Infrastructure.",
                SUCCESS_COLOR));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <h4 style="color:%s; margin:0 0 16px 0; font-size:14px; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; font-family:%s;">📋 Booking Details</h4>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                buildModernDetailRow("Room", roomName, "🏢"),
                buildModernDetailRow("Booking ID", bookingId, "🔢"),
                buildModernDetailRow("Status", buildModernStatusBadge("APPROVED", SUCCESS_COLOR), "📌"),
                buildModernDetailRow("Infra Comment", comment != null ? comment : "N/A", "💬")));

        sb.append(buildModernInfoCard("ℹ️", "What's Next?",
                "You will receive a sign-out link once you have completed using the server room.",
                INFO_COLOR));

        String dashboardLink = baseUrlService.buildUrl("/api/bookings/bookings-dashboard");
        sb.append(centered(buildModernButton("View My Bookings", dashboardLink, ACCENT_BOOKING)));

        return sb.toString();
    }

    @Async
    public void sendServerRoomSignOutLink(String toEmail, String requesterName,
                                          String roomName, String signOutLink,
                                          String bookingId) {
        String subject = "🔑 Server Room Sign-Out - " + roomName;

        String content = buildServerRoomSignOutContent(requesterName, roomName, signOutLink, bookingId);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Server Room Sign-Out", ACCENT_DRIVER));
    }

    private String buildServerRoomSignOutContent(String requesterName, String roomName,
                                                 String signOutLink, String bookingId) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("🔑", "Hello " + requesterName,
                "Please sign out of the server room <strong>'" + roomName + "'</strong> to complete your session.",
                ACCENT_DRIVER));

        sb.append("""
            <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                    %s
                    %s
                </table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR,
                buildModernDetailRow("Room", roomName, "🏢"),
                buildModernDetailRow("Booking ID", bookingId, "🔢")));

        sb.append(buildExpiryNotice(LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))));

        sb.append(centered(buildModernButton("🔑 Sign Out Now", signOutLink, ACCENT_DRIVER)));

        sb.append(buildModernInfoCard("ℹ️", "Important",
                "Please ensure you sign out immediately after completing your work in the server room. This helps maintain security and availability for other users.",
                DANGER_COLOR));

        return sb.toString();
    }

    // ============================================
    // LEGACY / COMPATIBILITY METHODS
    // ============================================

    public void sendWelcomeEmail(String toEmail, String fullName, String username, String tempPassword) {
        String loginUrl = baseUrlService.buildUrl("/login");
        String subject = "Welcome to AssetIQ-Pro - Your Account Details";

        String content = buildModernWrapper(
                buildModernInfoCard("👋", "Welcome " + fullName,
                        "Your account has been created. Use your temporary password to login.",
                        PRIMARY_COLOR) +
                        "<p><strong>Username:</strong> " + username + "</p>" +
                        "<p><strong>Temporary Password:</strong> " + tempPassword + "</p>" +
                        centered(buildModernButton("Login Now", loginUrl, PRIMARY_COLOR)),
                "Welcome",
                PRIMARY_COLOR
        );
        sendHtmlEmail(toEmail, subject, content);
    }

    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        String resetLink = baseUrlService.buildUrl("/reset-password?token=%s", token);
        String subject = "Reset Your Password - AssetIQ-Pro";

        String content = buildModernWrapper(
                buildModernInfoCard("🔐", "Hello " + fullName,
                        "We received a request to reset your password. Click the button below to create a new one.",
                        WARNING_COLOR) +
                        buildExpiryNotice(LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))) +
                        centered(buildModernButton("Reset Password", resetLink, WARNING_COLOR)),
                "Password Reset",
                WARNING_COLOR
        );
        sendHtmlEmail(toEmail, subject, content);
    }

    public void sendPasswordChangeConfirmation(String toEmail, String fullName) {
        String subject = "Password Changed - AssetIQ-Pro";

        String content = buildModernWrapper(
                buildModernInfoCard("✅", "Hello " + fullName,
                        "Your password has been changed successfully.",
                        SUCCESS_COLOR),
                "Password Changed",
                SUCCESS_COLOR
        );
        sendHtmlEmail(toEmail, subject, content);
    }

    public void sendResourceRequestNotification(String toEmail, String title, String message) {
        String subject = "Resource Request - " + title;

        String content = buildModernWrapper(
                buildModernInfoCard("📋", title,
                        message != null ? message : "No additional details provided.",
                        PRIMARY_COLOR),
                title,
                PRIMARY_COLOR
        );
        sendHtmlEmail(toEmail, subject, content);
    }

    public void sendResourceRequestStatusUpdate(String toEmail, String title, String message) {
        String subject = "Resource Request Update - " + title;

        String content = buildModernWrapper(
                buildModernInfoCard("📋", title,
                        message != null ? message : "No additional details provided.",
                        ACCENT_BOOKING),
                title,
                ACCENT_BOOKING
        );
        sendHtmlEmail(toEmail, subject, content);
    }

    public void sendVoucherGenerated(String toEmail, String fullName,
                                     String voucherCode, String qrCodePath,
                                     String mealType) {
        String subject = "Meal Voucher Generated - " + mealType;

        String content = buildModernWrapper(
                buildModernInfoCard("🎫", "Hello " + fullName,
                        "Your meal voucher has been generated.",
                        PRIMARY_COLOR) +
                        """
                        <div style="background:%s; border-radius:12px; padding:20px; text-align:center; margin:16px 0; border:2px solid %s;">
                            <p style="font-size:16px; font-weight:700; color:%s;">%s</p>
                            <p style="font-size:24px; font-weight:800; color:%s; letter-spacing:2px;">%s</p>
                            <p style="color:%s; font-size:14px;">Meal Type: %s</p>
                        </div>
                        """.formatted(SURFACE_COLOR, PRIMARY_COLOR, TEXT_COLOR, mealType, PRIMARY_COLOR, voucherCode, TEXT_MUTED, mealType),
                "Voucher Generated",
                PRIMARY_COLOR
        );
        sendHtmlEmail(toEmail, subject, content);
    }

    public void sendWarrantyExpiryAlert(String toEmail, String assetTag,
                                        String expiryDate, int daysLeft) {
        String subject = "⚠️ WARRANTY EXPIRY: " + assetTag;
        String color = daysLeft < 15 ? DANGER_COLOR : WARNING_COLOR;

        String content = buildModernWrapper(
                buildModernInfoCard("⚠️", "Warranty Expiry Alert",
                        "Asset <strong>" + assetTag + "</strong> warranty expires in <strong>" + daysLeft + " days</strong>.",
                        color) +
                        "<p><strong>Expiry Date:</strong> " + expiryDate + "</p>",
                "Warranty Alert",
                color
        );
        sendHtmlEmail(toEmail, subject, content);
    }

    public void sendEOLAlert(String toEmail, String assetTag,
                             String eolDate, int daysLeft) {
        String subject = "🔴 EOL ALERT: " + assetTag;
        String color = daysLeft < 15 ? DANGER_COLOR : WARNING_COLOR;

        String content = buildModernWrapper(
                buildModernInfoCard("🔴", "End of Life Alert",
                        "Asset <strong>" + assetTag + "</strong> reaches EOL in <strong>" + daysLeft + " days</strong>.",
                        color) +
                        "<p><strong>EOL Date:</strong> " + eolDate + "</p>",
                "EOL Alert",
                color
        );
        sendHtmlEmail(toEmail, subject, content);
    }

    public void sendBookingConfirmation(String toEmail, String fullName,
                                        String resourceType, String bookingDetails,
                                        String date, String time) {
        sendBookingConfirmation(toEmail, fullName, resourceType,
                bookingDetails + " | Date: " + date + " | Time: " + time);
    }

    public void sendEmail(String to, String subject, String body) {
        sendSimpleEmail(to, subject, body);
    }

    public void processPendingEmails() {
        log.debug("EmailService.processPendingEmails() - Delegated to EmailRetryService");
    }

    // ============================================
// DRIVER RATING EMAIL METHOD
// ============================================

    /**
     * Send a dedicated driver rating email with a clear rating button
     */
    @Async
    public void sendDriverRatingEmail(String toEmail, String requesterName,
                                      String requestId, String driverName,
                                      String ratingLink) {
        String subject = "⭐ Rate Your Driver - Trip #" + requestId;

        String content = buildDriverRatingContent(requesterName, requestId, driverName, ratingLink);
        sendHtmlEmail(toEmail, subject, buildModernWrapper(content, "Rate Your Driver", SUCCESS_COLOR));
    }

    /**
     * Build driver rating email content with prominent rating button
     */
    private String buildDriverRatingContent(String requesterName, String requestId,
                                            String driverName, String ratingLink) {
        StringBuilder sb = new StringBuilder();

        sb.append(buildModernInfoCard("⭐", "Hello " + requesterName,
                "Your trip with <strong>" + driverName + "</strong> has been completed.",
                SUCCESS_COLOR));

        sb.append("""
        <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s;">
            <h4 style="color:%s; margin:0 0 16px 0; font-size:14px; font-weight:700; text-transform:uppercase; letter-spacing:0.5px; font-family:%s;">📋 Trip Details</h4>
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                %s
                %s
            </table>
        </div>
        """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK,
                buildModernDetailRow("Request ID", requestId, "🔢"),
                buildModernDetailRow("Driver", driverName, "👤"),
                buildModernDetailRow("Status", buildModernStatusBadge("COMPLETED", SUCCESS_COLOR), "📌")));

        sb.append("""
        <div style="background:%s; border-radius:14px; padding:20px 24px; margin-bottom:20px; border:1px solid %s; text-align:center;">
            <p style="color:%s; font-size:14px; margin-bottom:16px; font-family:%s;">
                Please take a moment to rate your driver. Your feedback helps us improve our service.
            </p>
        </div>
        """.formatted(SURFACE_COLOR, BORDER_COLOR, TEXT_BODY, FONT_STACK));

        sb.append(centered(buildModernButton("⭐ Rate Your Driver", ratingLink, SUCCESS_COLOR)));

        sb.append(buildModernInfoCard("ℹ️", "What happens next?",
                "Your rating helps us maintain high service standards. Thank you for using AssetIQ-Pro!",
                INFO_COLOR));

        return sb.toString();
    }

    public void testEmailConfig() {
        try {
            SystemSettingService.EmailConfig cfg = settingService.getEmailConfig();
            log.info("=== EMAIL CONFIG TEST ===");
            log.info("Host: {}", cfg.getHost());
            log.info("Port: {}", cfg.getPort());
            log.info("Username: {}", cfg.getUsername());
            log.info("From: {}", getFromAddress());
            log.info("TLS: {}, SSL: {}", cfg.isTlsEnabled(), cfg.isSslEnabled());

            JavaMailSender sender = buildMailSender();
            log.info("✅ Mail sender built successfully");
        } catch (Exception e) {
            log.error("❌ Email config test failed: {}", e.getMessage(), e);
        }
    }
}