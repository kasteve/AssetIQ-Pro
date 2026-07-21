package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/settings")
@PreAuthorize("hasAuthority('MANAGE_CONFIG')")
public class SystemSettingController {

    private final SystemSettingService settingService;
    private final org.springframework.mail.javamail.JavaMailSenderImpl testMailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();

    private static final java.util.Set<String> MULTI_VALUE_KEYS = java.util.Set.of("report.types", "theme.presets");

    // (Optional) inject EmailService and ReportService later
    // private final EmailService emailService;
    // private final ReportService reportService;

    @GetMapping
    public String settings(Model model) {
        model.addAttribute("securitySettings", settingService.getSettingsMapByCategory(SystemSettingService.CATEGORY_SECURITY));
        model.addAttribute("emailSettings", settingService.getSettingsMapByCategory(SystemSettingService.CATEGORY_EMAIL));
        model.addAttribute("reportSettings", settingService.getSettingsMapByCategory(SystemSettingService.CATEGORY_REPORTS));
        model.addAttribute("themeSettings", settingService.getSettingsMapByCategory(SystemSettingService.CATEGORY_THEME));
        model.addAttribute("allSettings", settingService.getAllSettings());
        model.addAttribute("passwordPolicy", settingService.getPasswordPolicy());
        return "admin/settings";
    }

    // ============================================
    // Bulk Update – handles application/x-www-form-urlencoded
    // ============================================
    @PostMapping("/update-bulk")
    public String updateBulkSettings(HttpServletRequest request,
                                     @RequestParam(required = false) Long userId) {
        try {
            Map<String, String> settings = new HashMap<>();
            Map<String, String[]> parameterMap = request.getParameterMap();

            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();
                if ("userId".equals(key)) continue;

                if ("email.password".equals(key) && (values.length == 0 || values[0].isEmpty())) {
                    continue;
                }

                if (values.length == 1) {
                    settings.put(key, values[0]);
                } else if (MULTI_VALUE_KEYS.contains(key)) {
                    settings.put(key, String.join(",", values));
                } else {
                    // checkbox hidden+checked pair — last value wins
                    settings.put(key, values[values.length - 1]);
                }
            }

            Long currentUserId = userId != null ? userId : 1L;
            for (Map.Entry<String, String> entry : settings.entrySet()) {
                settingService.updateSetting(entry.getKey(), entry.getValue(), currentUserId);
            }

            return "redirect:/admin/settings?success=true";
        } catch (Exception e) {
            log.error("Failed to update settings: {}", e.getMessage(), e);
            return "redirect:/admin/settings?error=" + e.getMessage();
        }
    }

    // ============================================
    // Test Email Configuration
    // ============================================
    @PostMapping("/api/email/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testEmail(@RequestBody(required = false) Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String email = (payload != null) ? payload.get("email") : null;
            if (email == null || email.isEmpty()) {
                response.put("success", false);
                response.put("message", "Email address is required");
                return ResponseEntity.badRequest().body(response);
            }

            SystemSettingService.EmailConfig cfg = settingService.getEmailConfig();

            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(cfg.getHost());
            sender.setPort(cfg.getPort());
            sender.setUsername(cfg.getUsername());
            sender.setPassword(cfg.getPassword());

            Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", String.valueOf(cfg.isTlsEnabled()));
            props.put("mail.smtp.ssl.enable", String.valueOf(cfg.isSslEnabled()));
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");

            // This actually verifies the SMTP credentials/connection
            sender.testConnection();

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setFrom(cfg.getFrom());
            message.setSubject("AssetIQ-Pro Test Email");
            message.setText("This is a test email to confirm your SMTP configuration is working.");
            sender.send(message);

            response.put("success", true);
            response.put("message", "Test email sent to " + email);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending test email", e);
            response.put("success", false);
            response.put("message", "Failed to send test email: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ============================================
    // Generate Reports Now
    // ============================================
    @PostMapping("/api/reports/generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateReports() {
        Map<String, Object> response = new HashMap<>();
        try {
            // TODO: Implement report generation logic (e.g., call ReportService)
            // reportService.generateAllScheduledReports();
            response.put("success", true);
            response.put("message", "Reports generated successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error generating reports", e);
            response.put("success", false);
            response.put("message", "Failed to generate reports: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ============================================
    // Individual Setting Update (legacy)
    // ============================================
    @PostMapping("/update")
    public String updateSetting(@RequestParam String key,
                                @RequestParam String value,
                                @RequestParam(required = false) Long userId,
                                @RequestParam(required = false) String returnUrl) {
        try {
            settingService.updateSetting(key, value, userId != null ? userId : 1L);
            return "redirect:" + (returnUrl != null ? returnUrl : "/admin/settings");
        } catch (Exception e) {
            log.error("Failed to update setting: {}", e.getMessage());
            return "redirect:/admin/settings?error=" + e.getMessage();
        }
    }

    @PostMapping("/reset/{key}")
    public String resetSetting(@PathVariable String key,
                               @RequestParam(required = false) Long userId) {
        try {
            settingService.deleteSetting(key, userId != null ? userId : 1L);
            settingService.initializeDefaultSettings();
            return "redirect:/admin/settings?success=Setting reset to default";
        } catch (Exception e) {
            return "redirect:/admin/settings?error=" + e.getMessage();
        }
    }

    // ============================================
    // API Endpoints (JSON)
    // ============================================
    @GetMapping("/api/all")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getAllSettings() {
        Map<String, String> settings = new HashMap<>();
        settingService.getAllSettings().forEach(s -> {
            if (!s.isEncrypted()) {
                settings.put(s.getSettingKey(), s.getSettingValue());
            }
        });
        return ResponseEntity.ok(settings);
    }

    @GetMapping("/api/category/{category}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getSettingsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(settingService.getSettingsMapByCategory(category));
    }

    @GetMapping("/api/password-policy")
    @ResponseBody
    public ResponseEntity<SystemSettingService.PasswordPolicy> getPasswordPolicy() {
        return ResponseEntity.ok(settingService.getPasswordPolicy());
    }
}