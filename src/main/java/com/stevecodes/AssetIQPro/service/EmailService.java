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

    private static final String COMPANY_NAME = "AssetIQ-Pro";

    private static final String PAGE_BG = "#f1f4f9";
    private static final String CARD_BG = "#ffffff";
    private static final String SURFACE_COLOR = "#f8fafc";
    private static final String BORDER_COLOR = "#e2e8f0";
    private static final String TEXT_COLOR = "#0f172a";
    private static final String TEXT_MUTED = "#64748b";
    private static final String TEXT_BODY = "#475569";

    private static final String PRIMARY_COLOR = "#4f46e5";
    private static final String PRIMARY_SOFT = "#818cf8";
    private static final String DARK_COLOR = "#0f172a";
    private static final String MUTED_COLOR = "#64748b";
    private static final String SUCCESS_COLOR = "#16a34a";
    private static final String DANGER_COLOR = "#dc2626";
    private static final String WARNING_COLOR = "#d97706";

    private static final String FONT_STACK =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    // ============================================
    // Dynamic Mail Sender
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
    // Template Helpers
    // ============================================

    private String getCurrentYear() {
        return String.valueOf(LocalDateTime.now().getYear());
    }

    private String getCurrentDate() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
    }

    /**
     * Table-based header (NOT flexbox — flex is unreliable in Outlook/older
     * clients and can silently render as a blank/broken block).
     */
    private String buildHeader(String title) {
        return """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s; border-radius:16px 16px 0 0;">
                <tr>
                    <td style="padding:28px 36px;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                            <tr>
                                <td style="vertical-align:middle;">
                                    <span style="color:#ffffff; font-size:20px; font-weight:700; letter-spacing:0.3px; font-family:%s;">
                                        Asset<span style="color:%s;">IQ</span>-Pro
                                    </span>
                                </td>
                                <td style="text-align:right; vertical-align:middle;">
                                    <span style="display:inline-block; background:rgba(255,255,255,0.12); color:#e0e7ff; font-size:11px; font-weight:600; letter-spacing:0.5px; text-transform:uppercase; padding:6px 14px; border-radius:999px; font-family:%s;">%s</span>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr><td style="height:4px; background:linear-gradient(90deg,%s,%s);"></td></tr>
            </table>
            """.formatted(DARK_COLOR, FONT_STACK, PRIMARY_SOFT, FONT_STACK, title, PRIMARY_COLOR, PRIMARY_SOFT);
    }

    private String buildFooter() {
        return """
            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s; border-radius:0 0 16px 16px; border-top:1px solid %s;">
                <tr>
                    <td style="padding:22px 36px 8px 36px; font-family:%s;">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                            <tr>
                                <td style="font-size:12px; color:%s;">&copy; %s AssetIQ-Pro</td>
                                <td style="text-align:right; font-size:12px; color:%s;">%s</td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td style="padding:0 36px 22px 36px; font-family:%s;">
                        <p style="text-align:center; font-size:11px; color:#94a3b8; margin:0;">
                            This is an automated message from AssetIQ-Pro — please do not reply.
                        </p>
                    </td>
                </tr>
            </table>
            """.formatted(SURFACE_COLOR, BORDER_COLOR, FONT_STACK, TEXT_MUTED, getCurrentYear(),
                TEXT_MUTED, getCurrentDate(), FONT_STACK);
    }

    private String buildButton(String text, String url, String color) {
        return """
            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto;">
                <tr>
                    <td style="border-radius:10px; background:%s;">
                        <a href="%s" style="display:inline-block; padding:13px 34px; font-size:14px; font-weight:600; color:#ffffff; text-decoration:none; border-radius:10px; font-family:%s;">%s</a>
                    </td>
                </tr>
            </table>
            """.formatted(color, url, FONT_STACK, text);
    }

    /** Soft, tinted pill badge instead of a solid block of color. */
    private String buildStatusBadge(String status, String color) {
        return """
            <span style="display:inline-block; background:%s1a; color:%s; padding:5px 14px; border-radius:999px; font-size:12px; font-weight:700; letter-spacing:0.2px; font-family:%s;">%s</span>
            """.formatted(color, color, FONT_STACK, status);
    }

    private String buildInfoRow(String label, String value) {
        return """
            <tr>
                <td style="padding:10px 0; font-weight:600; color:#334155; font-size:13px; width:38%%; font-family:%s;">%s</td>
                <td style="padding:10px 0; color:%s; font-size:13px; font-family:%s;">%s</td>
            </tr>
            """.formatted(FONT_STACK, label, TEXT_COLOR, FONT_STACK, value != null ? value : "N/A");
    }

    /** Centered icon chip + title + subtitle used at the top of every email. */
    private String buildHeading(String icon, String title, String subtitle) {
        return """
            <div style="text-align:center; margin-bottom:28px;">
                <div style="width:52px; height:52px; line-height:52px; margin:0 auto 14px auto; background:#eef2ff; border-radius:14px; font-size:24px;">%s</div>
                <h2 style="color:%s; margin:0 0 6px 0; font-size:20px; font-weight:700; font-family:%s;">%s</h2>
                <p style="color:%s; margin:0; font-size:14px; font-family:%s;">%s</p>
            </div>
            """.formatted(icon, TEXT_COLOR, FONT_STACK, title, TEXT_MUTED, FONT_STACK, subtitle);
    }

    /** Lead paragraph panel with a colored accent border. */
    private String buildPanel(String accentColor, String bodyHtml) {
        return """
            <div style="background:%s; border-radius:12px; padding:20px; margin-bottom:22px; border-left:3px solid %s;">%s</div>
            """.formatted(SURFACE_COLOR, accentColor, bodyHtml);
    }

    /** Card containing a details table (rowsHtml is a sequence of <tr> built from buildInfoRow). */
    private String buildDetailCard(String title, String rowsHtml) {
        return """
            <div style="background:%s; border:1px solid %s; border-radius:12px; padding:20px; margin-bottom:22px;">
                <h3 style="color:%s; margin:0 0 14px 0; font-size:13px; font-weight:700; text-transform:uppercase; letter-spacing:0.4px; font-family:%s;">%s</h3>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">%s</table>
            </div>
            """.formatted(CARD_BG, BORDER_COLOR, TEXT_MUTED, FONT_STACK, title, rowsHtml);
    }

    /** Small tinted note box (used for warnings, expiry notices, security notes, etc). */
    private String buildCallout(String text, String color) {
        return """
            <div style="background:%s14; border:1px solid %s33; border-radius:10px; padding:14px 16px; margin-bottom:22px;">
                <p style="margin:0; color:%s; font-size:13px; line-height:1.5; font-family:%s;">%s</p>
            </div>
            """.formatted(color, color, color, FONT_STACK, text);
    }

    private String centered(String innerHtml) {
        return "<div style=\"text-align:center; margin-bottom:6px;\">" + innerHtml + "</div>";
    }

    // ============================================
    // Core Email Methods
    // ============================================

    public void sendSimpleEmail(String toEmail, String subject, String body) {
        try {
            log.info("📧 Sending email to: {} - Subject: {}", toEmail, subject);
            JavaMailSender mailSender = buildMailSender();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom(getFromAddress());
            mailSender.send(message);
            log.info("✅ Email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    public void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        try {
            log.info("📧 Sending HTML email to: {} - Subject: {}", toEmail, subject);
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
            // Logging the full exception (not just e.getMessage()) is what actually lets you
            // diagnose failures like NPEs from missing SMTP config, bad "from" addresses, etc.
            log.error("❌ Unexpected error sending HTML email to {}: {}", toEmail, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, "Failed to send HTML email.");
        }
    }

    public void sendEmailWithAttachment(String toEmail, String subject, String body, byte[] attachment, String fileName) {
        try {
            log.info("📧 Sending email with attachment to: {} - Subject: {}", toEmail, subject);
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
            log.info("✅ Email with attachment sent to: {}", toEmail);
        } catch (MessagingException e) {
            log.error("❌ Failed to send email with attachment to {}: {}", toEmail, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, body + "\n\n(Attachment could not be sent.)");
        } catch (Exception e) {
            log.error("❌ Unexpected error sending email with attachment to {}: {}", toEmail, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, body + "\n\n(Attachment could not be sent.)");
        }
    }

    public void sendEmailWithAttachment(String toEmail, String subject, String body, String attachmentPath) {
        try {
            if (attachmentPath == null || attachmentPath.isEmpty()) {
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

    // ============================================
    // Email Templates
    // ============================================

    private String buildEmailWrapper(String content, String title) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s - AssetIQ-Pro</title>
            </head>
            <body style="margin:0; padding:32px 16px; background:%s; font-family:%s; -webkit-font-smoothing:antialiased;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px; margin:0 auto; background:%s; border-radius:16px; overflow:hidden; box-shadow:0 1px 3px rgba(15,23,42,0.08), 0 12px 40px rgba(15,23,42,0.06);">
                    <tr><td style="padding:0;">%s</td></tr>
                    <tr><td style="padding:36px 36px 8px 36px;">%s</td></tr>
                    <tr><td style="padding:0;">%s</td></tr>
                </table>
            </body>
            </html>
            """.formatted(title, PAGE_BG, FONT_STACK, CARD_BG, buildHeader(title), content, buildFooter());
    }

    // ============================================
    // Email Content Builders
    // ============================================

    private String buildWelcomeContent(String fullName, String username, String tempPassword, String loginUrl) {
        String heading = buildHeading("👋", "Welcome to AssetIQ-Pro", "Your account has been created successfully");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Hello " + fullName + ",</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "Welcome to AssetIQ-Pro — your unified asset management platform. Your account has been created and is ready to use.</p>";
        String panel = buildPanel(PRIMARY_COLOR, panelBody);

        String tempPasswordRow = """
            <tr>
                <td style="padding:10px 0; font-weight:600; color:#334155; font-size:13px; width:38%%; font-family:%s;">Temporary Password</td>
                <td style="padding:10px 0; font-family:%s;">
                    <span style="font-family:'SFMono-Regular',Consolas,monospace; font-size:14px; font-weight:700; color:%s; background:#eef2ff; padding:3px 10px; border-radius:6px;">%s</span>
                </td>
            </tr>
            """.formatted(FONT_STACK, FONT_STACK, PRIMARY_COLOR, tempPassword);

        String rows = buildInfoRow("Username", username) + tempPasswordRow;
        String detailCard = buildDetailCard("Login Credentials", rows);

        String callout = buildCallout(
                "You'll be asked to set a new password on your first login. Keep this temporary password private and never share it with anyone.",
                WARNING_COLOR);

        String button = centered(buildButton("Access Your Account", loginUrl, PRIMARY_COLOR));

        return heading + panel + detailCard + callout + button;
    }

    private String buildPasswordResetContent(String fullName, String resetLink) {
        String heading = buildHeading("🔐", "Reset Your Password", "We received a request to reset your password");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Hello " + fullName + ",</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "We received a request to reset your password for your AssetIQ-Pro account. Click the button below to create a new one.</p>";
        String panel = buildPanel(WARNING_COLOR, panelBody);

        String callout = buildCallout("⏰ This link will expire in <strong>24 hours</strong> for security reasons.", WARNING_COLOR);
        String button = centered(buildButton("Reset Password", resetLink, WARNING_COLOR));

        String securityNote = buildCallout(
                "If you didn't request this, you can safely ignore this email — your password will remain unchanged.",
                TEXT_MUTED);

        return heading + panel + callout + button + securityNote;
    }

    private String buildPasswordChangeConfirmationContent(String fullName) {
        String heading = buildHeading("✅", "Password Changed", "Your password has been updated successfully");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Hello " + fullName + ",</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "This confirms that your AssetIQ-Pro password has been changed successfully.</p>";
        String panel = buildPanel(SUCCESS_COLOR, panelBody);

        String callout = buildCallout(
                "⚠️ If you did not make this change, please <strong>contact IT Support immediately</strong>.",
                DANGER_COLOR);

        return heading + panel + callout;
    }

    private String buildStatusUpdateContent(String requesterName, String resourceType,
                                            String requestId, String status, String comment,
                                            String requestUrl, String statusColor) {
        String heading = buildHeading("📋", "Request Status Update", "Your request has been updated");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Hello " + requesterName + ",</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "Your infrastructure request has been updated.</p>";
        String panel = buildPanel(statusColor, panelBody);

        String statusRow = """
            <tr>
                <td style="padding:10px 0; font-weight:600; color:#334155; font-size:13px; width:38%%; font-family:%s;">Status</td>
                <td style="padding:10px 0; font-family:%s;">%s</td>
            </tr>
            """.formatted(FONT_STACK, FONT_STACK, buildStatusBadge(status, statusColor));

        String rows = buildInfoRow("Request #", requestId) + buildInfoRow("Resource Type", resourceType) + statusRow;
        String detailCard = buildDetailCard("Request Details", rows);

        String commentBlock = buildCallout("<strong>Comment:</strong> " + (comment != null ? comment : "N/A"), TEXT_MUTED);
        String button = centered(buildButton("View Request Details", requestUrl, PRIMARY_COLOR));

        return heading + panel + detailCard + commentBlock + button;
    }

    private String buildApprovalRequestContent(String approverName, String requestId,
                                               String requesterName, String resourceType,
                                               String approvalLink) {
        String heading = buildHeading("📬", "Action Required", "A request is waiting for your approval");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Hello " + approverName + ",</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "An infrastructure request requires your approval. Please review the details below and take action.</p>";
        String panel = buildPanel(WARNING_COLOR, panelBody);

        String rows = buildInfoRow("Request #", requestId) + buildInfoRow("Requester", requesterName) + buildInfoRow("Resource Type", resourceType);
        String detailCard = buildDetailCard("Request Details", rows);

        String button = centered(buildButton("Review & Approve", approvalLink, WARNING_COLOR));

        return heading + panel + detailCard + button;
    }

    private String buildSignatureRequestContent(String requesterName, Long requestId,
                                                String signingLink, String expiryDate) {
        String heading = buildHeading("✍️", "Sign to Complete", "Your request is ready for your signature");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Hello " + requesterName + ",</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "Your infrastructure request (#" + requestId + ") has been delivered and is ready for your signature.</p>";
        String panel = buildPanel(SUCCESS_COLOR, panelBody);

        String callout = buildCallout("⏰ This link will expire on: <strong>" + (expiryDate != null ? expiryDate : "N/A") + "</strong>", WARNING_COLOR);
        String button = centered(buildButton("Sign Now", signingLink, SUCCESS_COLOR));

        return heading + panel + callout + button;
    }

    private String buildResourceRequestContent(String message, String title, boolean isAdmin) {
        String icon = isAdmin ? "📬" : "📋";
        String greeting = isAdmin ? "Dear Admin," : "Dear User,";
        String color = isAdmin ? WARNING_COLOR : PRIMARY_COLOR;

        String heading = buildHeading(icon, title, "");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">" + greeting + "</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; white-space:pre-line; font-family:" + FONT_STACK + ";\">"
                + (message != null ? message : "No additional details provided.") + "</p>";
        String panel = buildPanel(color, panelBody);

        String note = "<p style=\"color:" + TEXT_MUTED + "; font-size:13px; text-align:center; margin:0; font-family:" + FONT_STACK + ";\">For more details, please check your dashboard.</p>";

        return heading + panel + note;
    }

    private String buildTransferSignatureContent(String fullName, String assetTag, String transferId,
                                                 String role, String signingLink, String expiresAt) {
        String heading = buildHeading("📄", "Signature Required", "Asset transfer requires your signature");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Hello " + fullName + ",</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "Your signature is required for asset transfer #" + transferId + ".</p>";
        String panel = buildPanel(PRIMARY_COLOR, panelBody);

        String rows = buildInfoRow("Asset Tag", assetTag) + buildInfoRow("Your Role", role) + buildInfoRow("Transfer ID", transferId);
        String detailCard = buildDetailCard("Transfer Details", rows);

        String callout = buildCallout("⏰ This link expires on: <strong>" + (expiresAt != null ? expiresAt : "N/A") + "</strong>", WARNING_COLOR);
        String button = centered(buildButton("Review & Sign", signingLink, PRIMARY_COLOR));

        return heading + panel + detailCard + callout + button;
    }

    private String buildTransferCompletionContent(String assetTag, String transferId) {
        String heading = buildHeading("✅", "Transfer Complete", "Asset transfer has been completed successfully");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Dear Team,</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "The asset transfer for <strong>" + assetTag + "</strong> has been completed successfully.</p>";
        String panel = buildPanel(SUCCESS_COLOR, panelBody);

        String statusRow = """
            <tr>
                <td style="padding:10px 0; font-weight:600; color:#334155; font-size:13px; width:38%%; font-family:%s;">Status</td>
                <td style="padding:10px 0; font-family:%s;">%s</td>
            </tr>
            """.formatted(FONT_STACK, FONT_STACK, buildStatusBadge("FULLY SIGNED", SUCCESS_COLOR));

        String rows = buildInfoRow("Transfer ID", transferId) + statusRow;
        String detailCard = buildDetailCard("Transfer Details", rows);

        String note = buildCallout("📎 The fully signed transfer certificate is attached to this email.", TEXT_MUTED);

        return heading + panel + detailCard + note;
    }

    private String buildBookingConfirmationContent(String fullName, String resourceType, String bookingDetails) {
        String heading = buildHeading("📅", "Booking Confirmed", "Your booking has been confirmed");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Hello " + fullName + ",</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "Your booking has been confirmed.</p>";
        String panel = buildPanel(SUCCESS_COLOR, panelBody);

        String rows = buildInfoRow("Details", bookingDetails != null ? bookingDetails : "N/A") + buildInfoRow("Resource", resourceType);
        String detailCard = buildDetailCard("Booking Details", rows);

        String note = "<p style=\"color:" + TEXT_MUTED + "; font-size:13px; text-align:center; margin:0; font-family:" + FONT_STACK + ";\">Thank you for using AssetIQ-Pro! 🚀</p>";

        return heading + panel + detailCard + note;
    }

    private String buildVoucherContent(String fullName, String voucherCode, String mealType) {
        String heading = buildHeading("🎫", "Meal Voucher Generated", "Your meal voucher is ready");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Hello " + fullName + ",</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "Your meal voucher has been generated and is ready for use.</p>";
        String panel = buildPanel(PRIMARY_COLOR, panelBody);

        String voucherCard = """
            <div style="background:#ffffff; border:2px solid %s; border-radius:12px; padding:20px; margin-bottom:22px; text-align:center;">
                <h3 style="color:%s; margin:0 0 10px 0; font-size:18px; font-family:%s;">%s</h3>
                <div style="background:%s; border-radius:8px; padding:12px; display:inline-block; margin:6px 0;">
                    <span style="font-family:'SFMono-Regular',Consolas,monospace; font-size:18px; font-weight:700; color:%s; letter-spacing:2px;">%s</span>
                </div>
                <p style="margin:10px 0 0 0; color:%s; font-size:13px; font-family:%s;">Meal Type: <strong>%s</strong></p>
            </div>
            """.formatted(PRIMARY_COLOR, TEXT_COLOR, FONT_STACK, mealType, SURFACE_COLOR, PRIMARY_COLOR, voucherCode, TEXT_MUTED, FONT_STACK, mealType);

        String note = buildCallout("📎 Please present the QR code in the attachment to receive your meal.", TEXT_MUTED);

        return heading + panel + voucherCard + note;
    }

    private String buildAlertContent(String assetTag, String expiryDate, int daysLeft, String alertType) {
        boolean isWarranty = alertType.equals("WARRANTY");
        String icon = isWarranty ? "⚠️" : "🔴";
        String title = isWarranty ? "Warranty Expiry Alert" : "End of Life Alert";
        String color = isWarranty ? WARNING_COLOR : DANGER_COLOR;
        String expiryLabel = isWarranty ? "warranty" : "End of Life (EOL)";
        String daysColor = daysLeft < 15 ? DANGER_COLOR : WARNING_COLOR;

        String heading = buildHeading(icon, title, "Action required for asset " + assetTag);

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Dear Asset Manager,</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "<strong>Asset " + assetTag + "</strong> " + expiryLabel + " expires in <strong>" + daysLeft + " days</strong>.</p>";
        String panel = buildPanel(color, panelBody);

        String daysRow = """
            <tr>
                <td style="padding:10px 0; font-weight:600; color:#334155; font-size:13px; width:38%%; font-family:%s;">Days Left</td>
                <td style="padding:10px 0; color:%s; font-weight:700; font-size:13px; font-family:%s;">%d days</td>
            </tr>
            """.formatted(FONT_STACK, daysColor, FONT_STACK, daysLeft);

        String rows = buildInfoRow("Expiry Date", expiryDate) + daysRow;
        String detailCard = buildDetailCard("Asset Details", rows);

        String note = "<p style=\"color:" + TEXT_MUTED + "; font-size:13px; text-align:center; margin:0; font-family:" + FONT_STACK + ";\">Please take necessary action to renew or replace the asset.</p>";

        return heading + panel + detailCard + note;
    }

    private String buildRoomSlotRequestContent(String requesterName, String roomName, String timeSlot) {
        String heading = buildHeading("🔄", "Slot Request Received", "Someone wants to use your booked slot");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Dear User,</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "<strong>" + requesterName + "</strong> has requested to use the room <strong>'" + roomName + "'</strong> during your booked time slot.</p>";
        String panel = buildPanel(WARNING_COLOR, panelBody);

        String rows = buildInfoRow("Room", roomName) + buildInfoRow("Time Slot", timeSlot);
        String detailCard = buildDetailCard("Slot Details", rows);

        String note = "<p style=\"color:" + TEXT_MUTED + "; font-size:13px; text-align:center; margin:0; font-family:" + FONT_STACK + ";\">Please log in to approve or decline this request.</p>";

        return heading + panel + detailCard + note;
    }

    // ============================================
    // User Management Emails (Async)
    // ============================================

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName, String username, String tempPassword) {
        String loginUrl = baseUrlService.buildUrl("/login");
        String subject = "Welcome to AssetIQ-Pro - Your Account Details";
        String content = buildWelcomeContent(fullName, username, tempPassword, loginUrl);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Welcome"));
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        String resetLink = baseUrlService.buildUrl("/reset-password?token=%s", token);
        String subject = "Reset Your Password - AssetIQ-Pro";
        String content = buildPasswordResetContent(fullName, resetLink);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Password Reset"));
    }

    @Async
    public void sendPasswordChangeConfirmation(String toEmail, String fullName) {
        String subject = "Password Changed - AssetIQ-Pro";
        String content = buildPasswordChangeConfirmationContent(fullName);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Password Changed"));
    }

    // ============================================
    // Infrastructure Request Emails (Async)
    // ============================================

    @Async
    public void sendInfraRequestStatusUpdate(String toEmail, String requesterName,
                                             String requestId, String status,
                                             String comment, String resourceType) {
        String requestUrl = baseUrlService.buildUrl("/infra-requests/%s", requestId);
        String subject = "Infrastructure Request " + status + " - #" + requestId;

        String statusColor = switch(status.toUpperCase()) {
            case "APPROVED" -> SUCCESS_COLOR;
            case "REJECTED" -> DANGER_COLOR;
            case "PENDING" -> WARNING_COLOR;
            default -> PRIMARY_COLOR;
        };

        String content = buildStatusUpdateContent(requesterName, resourceType, requestId,
                status, comment, requestUrl, statusColor);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Request Status"));
    }

    @Async
    public void sendInfraRequestApproval(String toEmail, String approverName,
                                         String requestId, String requesterName,
                                         String resourceType, String approvalLink) {
        String link = approvalLink != null ? approvalLink : baseUrlService.buildUrl("/infra-requests/%s/approve", requestId);
        String subject = "Action Required: Infrastructure Request Approval - #" + requestId;
        String content = buildApprovalRequestContent(approverName, requestId, requesterName, resourceType, link);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Approval Required"));
    }

    @Async
    public void sendInfraRequestSigningLink(String toEmail, String requesterName,
                                            Long requestId, String signingLink,
                                            String expiryDate) {
        String link = signingLink != null ? signingLink : baseUrlService.buildUrl("/infra-requests/sign/%s", requestId);
        String subject = "Infrastructure Request - Sign to Complete #" + requestId;
        String content = buildSignatureRequestContent(requesterName, requestId, link, expiryDate);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Sign Request"));
    }

    // ============================================
    // Resource Request Emails (Async)
    // ============================================

    @Async
    public void sendResourceRequestNotification(String toEmail, String title, String message) {
        String subject = "Resource Request - " + title;
        String content = buildResourceRequestContent(message, title, true);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, title));
    }

    @Async
    public void sendResourceRequestStatusUpdate(String toEmail, String title, String message) {
        String subject = "Resource Request Update - " + title;
        String content = buildResourceRequestContent(message, title, false);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, title));
    }

    // ============================================
    // Transfer/Asset Emails (Async)
    // ============================================

    @Async
    public void sendTransferSignatureRequest(String toEmail, String fullName,
                                             String assetTag, String transferId,
                                             String role, String signingLink,
                                             String expiresAt) {
        String link = signingLink != null ? signingLink : baseUrlService.buildUrl("/transfers/sign/%s", transferId);
        String subject = "Signature Required: Asset Transfer - " + assetTag;
        String content = buildTransferSignatureContent(fullName, assetTag, transferId, role, link, expiresAt);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Transfer Signature"));
    }

    @Async
    public void sendTransferCompletionNotification(List<String> toEmails, String assetTag,
                                                   String transferId, byte[] pdfBytes) {
        String subject = "Transfer Complete: " + assetTag + " - #" + transferId;
        String content = buildTransferCompletionContent(assetTag, transferId);
        String htmlContent = buildEmailWrapper(content, "Transfer Complete");

        try {
            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

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

    @Async
    public void sendCompletedTransferReport(List<String> signerEmails, Transfer transfer, byte[] pdfBytes) {
        String subject = "Transfer Complete: " + transfer.getAssetTag() + " - #" + transfer.getTransferId();
        String content = buildTransferCompletionContent(transfer.getAssetTag(),
                String.valueOf(transfer.getTransferId()));
        String htmlContent = buildEmailWrapper(content, "Transfer Complete");

        try {
            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

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
    // Driver Request Emails (Async)
    // ============================================

    @Async
    public void sendDriverRequestStatusUpdate(String toEmail, String title, String message) {
        String subject = "Driver Request Update - " + title;
        String content = buildResourceRequestContent(message, title, false);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, title));
    }

    // ============================================
    // Booking/Resource Emails (Async)
    // ============================================

    @Async
    public void sendBookingConfirmation(String toEmail, String fullName,
                                        String resourceType, String bookingDetails) {
        String subject = "Booking Confirmation - " + resourceType;
        String content = buildBookingConfirmationContent(fullName, resourceType, bookingDetails);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Booking Confirmed"));
    }

    // ============================================
    // Room Booking Emails (Async)
    // ============================================

    @Async
    public void sendRoomSlotRequest(String toEmail, String requesterName,
                                    String roomName, String timeSlot) {
        String subject = "Room Slot Request - " + roomName;
        String content = buildRoomSlotRequestContent(requesterName, roomName, timeSlot);
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Slot Request"));
    }

    @Async
    public void sendResourceRequestAcknowledgment(String toEmail, String requesterName,
                                                  String resourceType, String requestId) {
        String subject = "Resource Request Acknowledged - #" + requestId;

        String heading = buildHeading("✅", "Receipt Acknowledged", "Thank you for acknowledging receipt");

        String panelBody = "<p style=\"margin:0 0 8px 0; color:" + TEXT_COLOR + "; font-weight:600; font-size:14px; font-family:" + FONT_STACK + ";\">Hello " + requesterName + ",</p>"
                + "<p style=\"margin:0; color:" + TEXT_BODY + "; font-size:14px; line-height:1.6; font-family:" + FONT_STACK + ";\">"
                + "Thank you for acknowledging receipt of your resource request.</p>";
        String panel = buildPanel(SUCCESS_COLOR, panelBody);

        String rows = buildInfoRow("Resource Type", resourceType) + buildInfoRow("Request #", requestId);
        String detailCard = buildDetailCard("Request Details", rows);

        String note = "<p style=\"color:" + TEXT_MUTED + "; font-size:13px; text-align:center; margin:0; font-family:" + FONT_STACK + ";\">Thank you for using AssetIQ-Pro! 🚀</p>";

        String content = heading + panel + detailCard + note;
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Receipt Acknowledged"));
    }

    // ============================================
    // Voucher/Meal Coupon Emails (Async)
    // ============================================

    @Async
    public void sendVoucherGenerated(String toEmail, String fullName,
                                     String voucherCode, String qrCodePath,
                                     String mealType) {
        String subject = "Meal Voucher Generated - " + mealType;
        String content = buildVoucherContent(fullName, voucherCode, mealType);
        String htmlContent = buildEmailWrapper(content, "Voucher Generated");

        try {
            JavaMailSender mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(getFromAddress());

            if (qrCodePath != null && !qrCodePath.isEmpty()) {
                File qrFile = new File(qrCodePath);
                if (qrFile.exists()) {
                    helper.addAttachment("voucher_qr.png", qrFile);
                }
            }

            mailSender.send(message);
            log.info("✅ Voucher email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ Failed to send voucher email to {}: {}", toEmail, e.getMessage(), e);
            sendSimpleEmail(toEmail, subject, "Your meal voucher has been generated. Please check the portal.");
        }
    }

    // ============================================
    // Warranty/EOL Notifications (Async)
    // ============================================

    @Async
    public void sendWarrantyExpiryAlert(String toEmail, String assetTag,
                                        String expiryDate, int daysLeft) {
        String subject = "WARRANTY EXPIRY ALERT: " + assetTag;
        String content = buildAlertContent(assetTag, expiryDate, daysLeft, "WARRANTY");
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "Warranty Alert"));
    }

    @Async
    public void sendEOLAlert(String toEmail, String assetTag,
                             String eolDate, int daysLeft) {
        String subject = "EOL ALERT: " + assetTag;
        String content = buildAlertContent(assetTag, eolDate, daysLeft, "EOL");
        sendHtmlEmail(toEmail, subject, buildEmailWrapper(content, "EOL Alert"));
    }

    // ============================================
    // Helper Methods
    // ============================================

    public void sendEmail(String to, String subject, String body) {
        sendSimpleEmail(to, subject, body);
    }

    public void processPendingEmails() {
        // Delegated to EmailRetryService
        log.debug("EmailService.processPendingEmails() - Delegated to EmailRetryService");
    }
}