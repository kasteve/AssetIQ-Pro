package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.UserDTO;
import com.stevecodes.AssetIQPro.exception.PasswordReuseException;
import com.stevecodes.AssetIQPro.repository.SystemSettingRepository;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.SystemSettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final AppUserService userService;
    private final SystemSettingService settingService;
    private final SystemSettingRepository systemSettingRepository;

    private static final String KEY_SESSION_TIMEOUT = "SESSION_TIMEOUT";
    private static final int DEFAULT_SESSION_TIMEOUT_SECONDS = 300; // 5 minutes

    @GetMapping("/login")
    public String showLoginPage() {
        log.info("Displaying login page.");
        return "login";
    }

    @PostMapping("/login")
    public String processLogin(@RequestParam("username") String username,
                               @RequestParam("password") String password,
                               Model model,
                               HttpServletRequest request) {
        try {
            log.info("Login attempt for username: {}", username);

            UserDTO user = userService.authenticateUser(username, password);

            if (user == null) {
                log.warn("Authentication failed for username: {}", username);
                model.addAttribute("error", "Invalid username or password.");
                return "login";
            }

            if (user.isBlocked()) {
                log.warn("User is blocked: {}", username);
                model.addAttribute("error", "Your account has been blocked. Please contact administrator.");
                return "login";
            }

            if (!user.isActive()) {
                log.warn("User is inactive: {}", username);
                model.addAttribute("error", "Your account is inactive. Please contact administrator.");
                return "login";
            }

            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
                log.info("Old session invalidated for user: {}", username);
            }

            HttpSession session = request.getSession(true);
            session.setAttribute("userId", user.getUserId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("fullName", user.getFullName());
            session.setAttribute("userType", user.getUserType());
            session.setAttribute("permissions", user.getPermissions());
            session.setAttribute("isFirstLogin", user.isFirstLogin());
            session.setAttribute("mustChangePassword", user.isMustChangePassword());
            session.setAttribute("sessionCreatedAt", System.currentTimeMillis());

            if (user.getPermissions() != null) {
                session.setAttribute("permissionNames", user.getPermissions());
                log.info("User permissions: {}", user.getPermissions());
            }

            // Get session timeout from system settings using the repository
            int timeoutSeconds = getSessionTimeout();
            session.setMaxInactiveInterval(timeoutSeconds);
            session.setAttribute("sessionTimeoutSet", true);
            session.setAttribute("sessionTimeoutSeconds", timeoutSeconds);

            log.info("=========================================");
            log.info("SESSION DETAILS FOR USER: {}", user.getUsername());
            log.info("Session ID: {}", session.getId());
            log.info("MaxInactiveInterval: {} seconds ({} minutes)",
                    session.getMaxInactiveInterval(),
                    session.getMaxInactiveInterval() / 60);
            log.info("=========================================");

            log.info("Authenticated user: {}, userType: {}", user.getUsername(), user.getUserType());

            if (user.isMustChangePassword() || user.isFirstLogin()) {
                log.info("Password change required for user: {}", username);
                return "redirect:/change-password?firstLogin=true";
            }

            log.info("Session created successfully for user: {}", username);
            return "redirect:/dashboard";

        } catch (Exception ex) {
            log.error("Unexpected error during login for username: {}", username, ex);
            model.addAttribute("error", "An unexpected error occurred. Please try again.");
            return "login";
        }
    }

    /**
     * Get session timeout from system settings
     */
    private int getSessionTimeout() {
        try {
            Optional<String> timeoutValue = systemSettingRepository.findSettingValueByKey(KEY_SESSION_TIMEOUT);
            if (timeoutValue.isPresent()) {
                int timeoutMinutes = Integer.parseInt(timeoutValue.get());
                return timeoutMinutes * 60;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid session timeout value, using default: {} seconds", DEFAULT_SESSION_TIMEOUT_SECONDS);
        }
        return DEFAULT_SESSION_TIMEOUT_SECONDS;
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
            log.info("User logged out successfully.");
        }
        return "redirect:/login?logout=true";
    }

    // ================================================================
    // SESSION MANAGEMENT API ENDPOINTS
    // ================================================================

    /**
     * Get current session status - used by client-side session manager
     * IMPORTANT: This method DOES NOT modify the session or reset the timeout
     */
    @GetMapping(value = "/api/session/status", produces = "application/json")
    @ResponseBody
    public Map<String, Object> getSessionStatus(HttpServletRequest request) {
        // Use getSession(false) - DO NOT create a new session
        HttpSession session = request.getSession(false);
        Map<String, Object> status = new HashMap<>();

        if (session != null) {
            try {
                // Get attributes WITHOUT modifying the session
                String username = (String) session.getAttribute("username");
                Long userId = (Long) session.getAttribute("userId");

                if (username != null && userId != null) {
                    // Get the timeout - this doesn't modify the session
                    int maxInactiveInterval = session.getMaxInactiveInterval();
                    long lastAccessTime = session.getLastAccessedTime();
                    long currentTime = System.currentTimeMillis();
                    long elapsedSeconds = (currentTime - lastAccessTime) / 1000;
                    long remainingSeconds = Math.max(0, maxInactiveInterval - elapsedSeconds);

                    status.put("authenticated", true);
                    status.put("username", username);
                    status.put("userId", userId);
                    status.put("remainingSeconds", remainingSeconds);
                    status.put("maxInactiveInterval", maxInactiveInterval);
                    status.put("lastAccessTime", Instant.ofEpochMilli(lastAccessTime).toString());

                    // Determine warning level (adjusted for 5-minute timeout)
                    if (remainingSeconds < 15) {
                        status.put("status", "critical");
                    } else if (remainingSeconds < 60) {
                        status.put("status", "warning");
                    } else {
                        status.put("status", "active");
                    }

                    log.debug("Session status for user {}: {} seconds remaining (not modifying session)",
                            username, remainingSeconds);
                } else {
                    status.put("authenticated", false);
                    status.put("message", "Session incomplete or invalid");
                }
            } catch (Exception e) {
                log.error("Error checking session status: {}", e.getMessage());
                status.put("authenticated", false);
                status.put("message", "Error checking session");
            }
        } else {
            status.put("authenticated", false);
            status.put("message", "No active session found");
        }

        return status;
    }

    /**
     * Extend current session - called when user clicks "Extend Session"
     * This is the ONLY endpoint that should touch/modify the session
     */
    @PostMapping(value = "/api/session/extend", produces = "application/json")
    @ResponseBody
    public Map<String, Object> extendSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session != null) {
            try {
                // ONLY extend the session when user explicitly requests it
                // This resets the timeout
                session.setAttribute("lastActivity", System.currentTimeMillis());
                session.setAttribute("sessionExtendedAt", System.currentTimeMillis());

                // Touch the session to reset timeout
                int maxInactiveInterval = session.getMaxInactiveInterval();

                response.put("success", true);
                response.put("remainingSeconds", maxInactiveInterval);
                response.put("message", "Session extended successfully");
                response.put("extendedAt", Instant.now().toString());

                String username = (String) session.getAttribute("username");
                log.info("Session EXTENDED for user: {}", username);

            } catch (Exception e) {
                log.error("Error extending session: {}", e.getMessage());
                response.put("success", false);
                response.put("message", "Failed to extend session: " + e.getMessage());
            }
        } else {
            response.put("success", false);
            response.put("message", "No active session found");
        }

        return response;
    }

    /**
     * Ping session - simple keep-alive endpoint
     * IMPORTANT: This method DOES NOT modify the session or reset the timeout
     */
    @GetMapping(value = "/api/session/ping", produces = "application/json")
    @ResponseBody
    public Map<String, Object> pingSession(HttpServletRequest request) {
        // Use getSession(false) - DO NOT create or modify the session
        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session != null) {
            // Just check if session exists - don't modify it
            response.put("active", true);
            response.put("sessionId", session.getId());
            // Get the timeout without modifying
            response.put("timeoutSeconds", session.getMaxInactiveInterval());
            response.put("lastAccessTime", Instant.ofEpochMilli(session.getLastAccessedTime()).toString());

            String username = (String) session.getAttribute("username");
            if (username != null) {
                response.put("username", username);
            }
        } else {
            response.put("active", false);
        }

        return response;
    }

    /**
     * Logout via API - used by client-side session manager
     */
    @PostMapping(value = "/api/session/logout", produces = "application/json")
    @ResponseBody
    public Map<String, Object> logoutViaApi(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session != null) {
            String username = (String) session.getAttribute("username");
            session.invalidate();
            log.info("User {} logged out via session API", username);
            response.put("success", true);
            response.put("message", "Logged out successfully");
        } else {
            response.put("success", false);
            response.put("message", "No active session to log out");
        }

        return response;
    }

    // ================================================================
    // EXISTING METHODS BELOW (unchanged)
    // ================================================================

    @GetMapping("/change-password")
    public String showChangePasswordForm(@RequestParam(required = false) boolean firstLogin,
                                         HttpSession session,
                                         Model model) {
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            log.warn("No userId in session, redirecting to login");
            return "redirect:/login";
        }

        SystemSettingService.PasswordPolicy policy = settingService.getPasswordPolicy();
        model.addAttribute("userId", userId);
        model.addAttribute("firstLogin", firstLogin);
        model.addAttribute("passwordPolicy", policy);

        return "change-password";
    }

    @PostMapping("/change-password")
    public String processPasswordChange(@RequestParam("userId") Long userId,
                                        @RequestParam("newPassword") String newPassword,
                                        @RequestParam("confirmPassword") String confirmPassword,
                                        @RequestParam(required = false) boolean firstLogin,
                                        RedirectAttributes redirectAttributes,
                                        HttpSession session) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match.");
            return "redirect:/change-password?firstLogin=" + firstLogin;
        }

        String policyError = validatePasswordPolicy(newPassword);
        if (policyError != null) {
            redirectAttributes.addFlashAttribute("error", policyError);
            return "redirect:/change-password?firstLogin=" + firstLogin;
        }

        try {
            userService.changePassword(userId, newPassword, firstLogin);

            session.setAttribute("mustChangePassword", false);
            session.setAttribute("isFirstLogin", false);

            redirectAttributes.addFlashAttribute("success", "Password changed successfully!");
            log.info("Password changed for user ID: {}", userId);

            if (firstLogin) {
                return "redirect:/login?success=true";
            }
            return "redirect:/dashboard";

        } catch (PasswordReuseException e) {
            log.warn("Password reuse attempt for user ID: {}", userId);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/change-password?firstLogin=" + firstLogin;
        } catch (Exception e) {
            log.error("Error changing password for user ID: {}", userId, e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/change-password?firstLogin=" + firstLogin;
        }
    }

    @PostMapping("/forgot-password")
    @ResponseBody
    public Map<String, Object> forgotPassword(@RequestParam("username") String username) {
        Map<String, Object> response = new HashMap<>();
        try {
            log.info("Forgot password request for: {}", username);

            var userOpt = userService.getUserByUsername(username);
            if (userOpt.isEmpty()) {
                userOpt = userService.getUserByEmail(username);
            }

            if (userOpt.isEmpty()) {
                log.warn("User not found for forgot password: {}", username);
                response.put("success", false);
                response.put("message", "User not found with the provided username or email.");
                return response;
            }

            var user = userOpt.get();

            if (!user.isActive()) {
                response.put("success", false);
                response.put("message", "Your account is inactive. Please contact administrator.");
                return response;
            }

            String token = userService.generatePasswordResetToken(user.getEmail());

            response.put("success", true);
            response.put("message", "A password reset link has been sent to your email. Please check your inbox.");
            log.info("Password reset email sent to: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Error processing forgot password: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "An error occurred. Please try again later.");
        }
        return response;
    }

    @PostMapping("/reset-password")
    @ResponseBody
    public Map<String, Object> resetPassword(@RequestParam("token") String token,
                                             @RequestParam("newPassword") String newPassword,
                                             @RequestParam("confirmPassword") String confirmPassword) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!newPassword.equals(confirmPassword)) {
                response.put("success", false);
                response.put("message", "Passwords do not match.");
                return response;
            }

            String policyError = validatePasswordPolicy(newPassword);
            if (policyError != null) {
                response.put("success", false);
                response.put("message", policyError);
                return response;
            }

            userService.resetPasswordWithToken(token, newPassword);

            response.put("success", true);
            response.put("message", "Password reset successfully! You can now login with your new password.");
            log.info("Password reset successfully with token");

        } catch (Exception e) {
            log.error("Error resetting password: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    private String validatePasswordPolicy(String password) {
        SystemSettingService.PasswordPolicy policy = settingService.getPasswordPolicy();

        if (password == null || password.length() < policy.getMinLength()) {
            return "Password must be at least " + policy.getMinLength() + " characters.";
        }
        if (policy.isRequireUppercase() && password.chars().noneMatch(Character::isUpperCase)) {
            return "Password must include an uppercase letter.";
        }
        if (policy.isRequireLowercase() && password.chars().noneMatch(Character::isLowerCase)) {
            return "Password must include a lowercase letter.";
        }
        if (policy.isRequireNumber() && password.chars().noneMatch(Character::isDigit)) {
            return "Password must include a number.";
        }
        if (policy.isRequireSpecial() && password.chars().noneMatch(c -> "!@#$%^&*()-_+=".indexOf(c) >= 0)) {
            return "Password must include a special character.";
        }
        return null;
    }
}