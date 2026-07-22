package com.stevecodes.AssetIQPro.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stevecodes.AssetIQPro.entity.SystemSetting;
import com.stevecodes.AssetIQPro.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository settingRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    private static final String ENCRYPTION_PASSWORD = "AssetIQPro-Secure-Key-2024!";
    private static final String ENCRYPTION_SALT = "a0b1c2d3e4f56789fedcba9876543210";

    private final TextEncryptor encryptor = Encryptors.text(ENCRYPTION_PASSWORD, ENCRYPTION_SALT);

    public static final String CATEGORY_SECURITY = "SECURITY";
    public static final String CATEGORY_EMAIL = "EMAIL";
    public static final String CATEGORY_REPORTS = "REPORTS";
    public static final String CATEGORY_THEME = "THEME";
    public static final String CATEGORY_NOTIFICATIONS = "NOTIFICATIONS";
    public static final String CATEGORY_SYSTEM = "SYSTEM";

    public static final String KEY_PASSWORD_MIN_LENGTH = "password.min.length";
    public static final String KEY_PASSWORD_REQUIRE_UPPERCASE = "password.require.uppercase";
    public static final String KEY_PASSWORD_REQUIRE_LOWERCASE = "password.require.lowercase";
    public static final String KEY_PASSWORD_REQUIRE_NUMBER = "password.require.number";
    public static final String KEY_PASSWORD_REQUIRE_SPECIAL = "password.require.special";
    public static final String KEY_PASSWORD_EXPIRY_DAYS = "password.expiry.days";
    public static final String KEY_PASSWORD_HISTORY_COUNT = "password.history.count";
    public static final String KEY_PASSWORD_MAX_ATTEMPTS = "password.max.attempts";
    public static final String KEY_2FA_ENABLED = "2fa.enabled";
    public static final String KEY_2FA_METHOD = "2fa.method";
    public static final String KEY_SESSION_TIMEOUT = "session.timeout.minutes";
    public static final String KEY_MAX_SESSIONS = "session.max.concurrent";

    public static final String KEY_EMAIL_HOST = "email.host";
    public static final String KEY_EMAIL_PORT = "email.port";
    public static final String KEY_EMAIL_USERNAME = "email.username";
    public static final String KEY_EMAIL_PASSWORD = "email.password";
    public static final String KEY_EMAIL_FROM = "email.from";
    public static final String KEY_EMAIL_SSL = "email.ssl.enabled";
    public static final String KEY_EMAIL_TLS = "email.tls.enabled";
    public static final String KEY_EMAIL_TEST_RECIPIENT = "email.test.recipient";

    public static final String KEY_REPORT_AUTO_GENERATE = "report.auto.generate";
    public static final String KEY_REPORT_SCHEDULE = "report.schedule.cron";
    public static final String KEY_REPORT_RECIPIENTS = "report.recipients";
    public static final String KEY_REPORT_TYPES = "report.types";
    public static final String KEY_REPORT_FORMAT = "report.format";

    public static final String KEY_THEME_DEFAULT = "theme.default";
    public static final String KEY_THEME_ALLOW_CUSTOM = "theme.allow.custom";
    public static final String KEY_THEME_PRESETS = "theme.presets";

    @Transactional
    public void initializeDefaultSettings() {
        log.info("Initializing default system settings...");

        createOrUpdateSetting(KEY_PASSWORD_MIN_LENGTH, "8", CATEGORY_SECURITY, "Minimum password length");
        createOrUpdateSetting(KEY_PASSWORD_REQUIRE_UPPERCASE, "true", CATEGORY_SECURITY, "Require uppercase letters");
        createOrUpdateSetting(KEY_PASSWORD_REQUIRE_LOWERCASE, "true", CATEGORY_SECURITY, "Require lowercase letters");
        createOrUpdateSetting(KEY_PASSWORD_REQUIRE_NUMBER, "true", CATEGORY_SECURITY, "Require numbers");
        createOrUpdateSetting(KEY_PASSWORD_REQUIRE_SPECIAL, "true", CATEGORY_SECURITY, "Require special characters");
        createOrUpdateSetting(KEY_PASSWORD_EXPIRY_DAYS, "90", CATEGORY_SECURITY, "Password expiry in days (0 = never)");
        createOrUpdateSetting(KEY_PASSWORD_HISTORY_COUNT, "5", CATEGORY_SECURITY, "Number of previous passwords to remember");
        createOrUpdateSetting(KEY_PASSWORD_MAX_ATTEMPTS, "5", CATEGORY_SECURITY, "Max login attempts before lockout");
        createOrUpdateSetting(KEY_2FA_ENABLED, "false", CATEGORY_SECURITY, "Enable Two-Factor Authentication");
        createOrUpdateSetting(KEY_2FA_METHOD, "email", CATEGORY_SECURITY, "2FA method: email, sms, authenticator");
        createOrUpdateSetting(KEY_SESSION_TIMEOUT, "30", CATEGORY_SECURITY, "Session timeout in minutes");
        createOrUpdateSetting(KEY_MAX_SESSIONS, "5", CATEGORY_SECURITY, "Maximum concurrent sessions per user");

        createOrUpdateSetting(KEY_EMAIL_HOST, "smtp.gmail.com", CATEGORY_EMAIL, "SMTP server host");
        createOrUpdateSetting(KEY_EMAIL_PORT, "587", CATEGORY_EMAIL, "SMTP server port");
        createOrUpdateSetting(KEY_EMAIL_USERNAME, "", CATEGORY_EMAIL, "SMTP username");
        createOrUpdateSetting(KEY_EMAIL_PASSWORD, "", CATEGORY_EMAIL, "SMTP password (encrypted)");
        createOrUpdateSetting(KEY_EMAIL_FROM, "noreply@asset-iq-pro.com", CATEGORY_EMAIL, "From email address");
        createOrUpdateSetting(KEY_EMAIL_SSL, "false", CATEGORY_EMAIL, "Enable SSL");
        createOrUpdateSetting(KEY_EMAIL_TLS, "true", CATEGORY_EMAIL, "Enable TLS");

        createOrUpdateSetting(KEY_REPORT_AUTO_GENERATE, "true", CATEGORY_REPORTS, "Auto-generate scheduled reports");
        createOrUpdateSetting(KEY_REPORT_SCHEDULE, "0 0 6 * * *", CATEGORY_REPORTS, "Cron schedule for reports (daily at 6 AM)");
        createOrUpdateSetting(KEY_REPORT_RECIPIENTS, "admin@company.com", CATEGORY_REPORTS, "Default report recipients");
        createOrUpdateSetting(KEY_REPORT_TYPES, "asset_summary,warranty_expiry,transfer_history", CATEGORY_REPORTS, "Report types to generate");
        createOrUpdateSetting(KEY_REPORT_FORMAT, "pdf", CATEGORY_REPORTS, "Report format: pdf, excel, both");

        createOrUpdateSetting(KEY_THEME_DEFAULT, "light", CATEGORY_THEME, "Default theme: light, dark, system");
        createOrUpdateSetting(KEY_THEME_ALLOW_CUSTOM, "true", CATEGORY_THEME, "Allow users to customize theme");
        createOrUpdateSetting(KEY_THEME_PRESETS, "[\"light\",\"dark\",\"blue\",\"green\"]", CATEGORY_THEME, "Available theme presets");

        log.info("Default system settings initialized successfully");
    }

    @Transactional
    public SystemSetting createOrUpdateSetting(String key, String value, String category, String description) {
        Optional<SystemSetting> existing = settingRepository.findBySettingKey(key);
        if (existing.isPresent()) {
            SystemSetting setting = existing.get();
            setting.setSettingValue(value);
            setting.setDescription(description);
            setting.setUpdatedAt(LocalDateTime.now());
            return settingRepository.save(setting);
        } else {
            SystemSetting setting = new SystemSetting(key, value, category, description);
            return settingRepository.save(setting);
        }
    }

    public Optional<SystemSetting> getSetting(String key) {
        return settingRepository.findBySettingKey(key);
    }

    public String getSettingValue(String key) {
        return settingRepository.findSettingValueByKey(key).orElse(null);
    }

    public List<SystemSetting> getSettingsByCategory(String category) {
        return settingRepository.findByCategory(category);
    }

    public Map<String, String> getSettingsMapByCategory(String category) {
        return settingRepository.findByCategory(category).stream()
                .collect(Collectors.toMap(
                        SystemSetting::getSettingKey,
                        SystemSetting::getSettingValue
                ));
    }

    public List<SystemSetting> getAllSettings() {
        return settingRepository.findAll();
    }

    /**
     * Upserts a setting. If the key doesn't exist yet (e.g. a newly added
     * form field with no matching DB row), it is created on the fly instead
     * of throwing — a missing key must never abort an entire bulk save.
     */
    @Transactional
    public SystemSetting updateSetting(String key, String value, Long updatedBy) {
        SystemSetting setting = settingRepository.findBySettingKey(key)
                .orElseGet(() -> {
                    log.warn("Setting '{}' did not exist — creating it now", key);
                    return new SystemSetting(key, null, inferCategoryFromKey(key), null);
                });

        if (isSensitiveKey(key)) {
            setting.setSettingValue(encrypt(value));
            setting.setEncrypted(true);
        } else {
            setting.setSettingValue(value);
        }

        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(updatedBy);

        SystemSetting updated = settingRepository.save(setting);

        auditService.logAction("SETTING_UPDATED",
                "Setting '" + key + "' updated by user: " + updatedBy,
                updatedBy);

        return updated;
    }

    private String inferCategoryFromKey(String key) {
        if (key.startsWith("password.") || key.startsWith("2fa.") || key.startsWith("session.")) {
            return CATEGORY_SECURITY;
        } else if (key.startsWith("email.")) {
            return CATEGORY_EMAIL;
        } else if (key.startsWith("report.")) {
            return CATEGORY_REPORTS;
        } else if (key.startsWith("theme.")) {
            return CATEGORY_THEME;
        } else if (key.startsWith("notification.")) {
            return CATEGORY_NOTIFICATIONS;
        }
        return CATEGORY_SYSTEM;
    }

    @Transactional
    public void deleteSetting(String key, Long deletedBy) {
        SystemSetting setting = settingRepository.findBySettingKey(key)
                .orElseThrow(() -> new RuntimeException("Setting not found: " + key));

        setting.setActive(false);
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(deletedBy);
        settingRepository.save(setting);

        auditService.logAction("SETTING_DELETED",
                "Setting '" + key + "' deleted by user: " + deletedBy,
                deletedBy);
    }

    public int getInt(String key) {
        String value = getSettingValue(key);
        return value != null ? Integer.parseInt(value) : 0;
    }

    public boolean getBoolean(String key) {
        String value = getSettingValue(key);
        return value != null && Boolean.parseBoolean(value);
    }

    public long getLong(String key) {
        String value = getSettingValue(key);
        return value != null ? Long.parseLong(value) : 0L;
    }

    public String getString(String key) {
        return getSettingValue(key);
    }

    public List<String> getStringList(String key) {
        String value = getSettingValue(key);
        if (value == null || value.isEmpty()) return List.of();
        try {
            return objectMapper.readValue(value, List.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse list from setting {}: {}", key, e.getMessage());
            return List.of(value.split(","));
        }
    }

    public PasswordPolicy getPasswordPolicy() {
        return new PasswordPolicy(
                getInt(KEY_PASSWORD_MIN_LENGTH),
                getBoolean(KEY_PASSWORD_REQUIRE_UPPERCASE),
                getBoolean(KEY_PASSWORD_REQUIRE_LOWERCASE),
                getBoolean(KEY_PASSWORD_REQUIRE_NUMBER),
                getBoolean(KEY_PASSWORD_REQUIRE_SPECIAL),
                getInt(KEY_PASSWORD_EXPIRY_DAYS),
                getInt(KEY_PASSWORD_HISTORY_COUNT),
                getInt(KEY_PASSWORD_MAX_ATTEMPTS)
        );
    }

    public TwoFactorAuthConfig getTwoFactorAuthConfig() {
        return new TwoFactorAuthConfig(
                getBoolean(KEY_2FA_ENABLED),
                getString(KEY_2FA_METHOD)
        );
    }

    public EmailConfig getEmailConfig() {
        EmailConfig config = new EmailConfig();
        config.setHost(getString(KEY_EMAIL_HOST));
        config.setPort(getInt(KEY_EMAIL_PORT));
        config.setUsername(getString(KEY_EMAIL_USERNAME));
        config.setPassword(decrypt(getString(KEY_EMAIL_PASSWORD)));
        config.setFrom(getString(KEY_EMAIL_FROM));
        config.setSslEnabled(getBoolean(KEY_EMAIL_SSL));
        config.setTlsEnabled(getBoolean(KEY_EMAIL_TLS));
        return config;
    }

    /**
     * Only truly secret values (the SMTP password, or any future key/secret/token)
     * should be encrypted. The previous check used key.contains("password"),
     * which also matched the numeric security-policy keys such as
     * "password.min.length", "password.expiry.days", "password.history.count"
     * and "password.max.attempts" — causing their plain numeric values to be
     * AES-encrypted on save. Any later call to getInt() on those keys then
     * tried to Integer.parseInt() the encrypted ciphertext and blew up with
     * a NumberFormatException. Scope the check to the actual sensitive key(s).
     */
    private boolean isSensitiveKey(String key) {
        return key.equals(KEY_EMAIL_PASSWORD) || key.contains("secret") || key.contains("token");
    }

    private String encrypt(String value) {
        if (value == null || value.isEmpty()) return value;
        try {
            return encryptor.encrypt(value);
        } catch (Exception e) {
            log.warn("Failed to encrypt value: {}", e.getMessage());
            return value;
        }
    }

    private String decrypt(String value) {
        if (value == null || value.isEmpty()) return value;
        try {
            return encryptor.decrypt(value);
        } catch (Exception e) {
            log.warn("Failed to decrypt value: {}", e.getMessage());
            return value;
        }
    }

    // ============================================
    // Theme Helper Methods (ADDED)
    // ============================================

    /**
     * Get the default theme from settings
     * @return theme name: "light", "dark", "blue", "green", "purple", or "system"
     */
    public String getDefaultTheme() {
        return getString(KEY_THEME_DEFAULT);
    }

    /**
     * Check if users are allowed to customize their theme
     */
    public boolean isThemeCustomizationAllowed() {
        return getBoolean(KEY_THEME_ALLOW_CUSTOM);
    }

    /**
     * Get the list of available theme presets
     * @return List of theme names
     */
    public List<String> getThemePresets() {
        return getStringList(KEY_THEME_PRESETS);
    }

    /**
     * Get a map of all theme settings
     */
    public Map<String, Object> getThemeSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("defaultTheme", getDefaultTheme());
        settings.put("allowCustomization", isThemeCustomizationAllowed());
        settings.put("presets", getThemePresets());
        return settings;
    }

    // ============================================
    // Inner Classes (existing)
    // ============================================

    public static class PasswordPolicy {
        private final int minLength;
        private final boolean requireUppercase;
        private final boolean requireLowercase;
        private final boolean requireNumber;
        private final boolean requireSpecial;
        private final int expiryDays;
        private final int historyCount;
        private final int maxAttempts;

        public PasswordPolicy(int minLength, boolean requireUppercase, boolean requireLowercase,
                              boolean requireNumber, boolean requireSpecial, int expiryDays,
                              int historyCount, int maxAttempts) {
            this.minLength = minLength;
            this.requireUppercase = requireUppercase;
            this.requireLowercase = requireLowercase;
            this.requireNumber = requireNumber;
            this.requireSpecial = requireSpecial;
            this.expiryDays = expiryDays;
            this.historyCount = historyCount;
            this.maxAttempts = maxAttempts;
        }

        public int getMinLength() { return minLength; }
        public boolean isRequireUppercase() { return requireUppercase; }
        public boolean isRequireLowercase() { return requireLowercase; }
        public boolean isRequireNumber() { return requireNumber; }
        public boolean isRequireSpecial() { return requireSpecial; }
        public int getExpiryDays() { return expiryDays; }
        public int getHistoryCount() { return historyCount; }
        public int getMaxAttempts() { return maxAttempts; }
    }

    public static class TwoFactorAuthConfig {
        private final boolean enabled;
        private final String method;

        public TwoFactorAuthConfig(boolean enabled, String method) {
            this.enabled = enabled;
            this.method = method;
        }

        public boolean isEnabled() { return enabled; }
        public String getMethod() { return method; }
    }

    public static class EmailConfig {
        private String host;
        private int port;
        private String username;
        private String password;
        private String from;
        private boolean sslEnabled;
        private boolean tlsEnabled;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public boolean isSslEnabled() { return sslEnabled; }
        public void setSslEnabled(boolean sslEnabled) { this.sslEnabled = sslEnabled; }
        public boolean isTlsEnabled() { return tlsEnabled; }
        public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
    }
}