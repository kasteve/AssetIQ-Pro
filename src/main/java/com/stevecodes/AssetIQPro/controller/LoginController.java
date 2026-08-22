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
            session.setAttribute("sessionLastResetAt", System.currentTimeMillis());

            if (user.getPermissions() != null) {
                session.setAttribute("permissionNames", user.getPermissions());
                log.info("User permissions: {}", user.getPermissions());
            }

            // Get session timeout from system settings using the repository
            int timeoutSeconds = getSessionTimeout();
            session.setMaxInactiveInterval(timeoutSeconds);
            session.setAttribute("sessionTimeoutSet", true);
            session.setAttribute("sessionTimeoutSeconds", timeoutSeconds);
            // Store the session creation time for reference
            session.setAttribute("sessionCreationTime", System.currentTimeMillis());

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
                log.info("✅ SESSION_TIMEOUT from database: {} minutes", timeoutMinutes);
                return timeoutMinutes * 60;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid session timeout value, using default: {} seconds", DEFAULT_SESSION_TIMEOUT_SECONDS);
        }
        log.info("⚠️ Using default session timeout: {} seconds ({} minutes)",
                DEFAULT_SESSION_TIMEOUT_SECONDS, DEFAULT_SESSION_TIMEOUT_SECONDS / 60);
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
     *
     * CRITICAL: This method uses a special approach to get session info
     * WITHOUT resetting the timeout. We store the session creation time
     * and calculate remaining time based on that, not on lastAccessedTime.
     */
    @GetMapping(value = "/api/session/status", produces = "application/json")
    @ResponseBody
    public Map<String, Object> getSessionStatus(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, Object> status = new HashMap<>();

        if (session != null) {
            try {
                String username = (String) session.getAttribute("username");
                Long userId = (Long) session.getAttribute("userId");

                if (username != null && userId != null) {
                    // Get the max inactive interval (timeout)
                    int maxInactiveInterval = session.getMaxInactiveInterval();

                    // Get the session creation time we stored during login
                    Long sessionCreationTime = (Long) session.getAttribute("sessionCreationTime");
                    if (sessionCreationTime == null) {
                        // Fallback to actual creation time
                        sessionCreationTime = session.getCreationTime();
                    }

                    // Calculate elapsed time since session creation
                    long currentTime = System.currentTimeMillis();
                    long elapsedSeconds = (currentTime - sessionCreationTime) / 1000;

                    // Calculate remaining time based on creation time, NOT lastAccessedTime
                    long remainingSeconds = Math.max(0, maxInactiveInterval - elapsedSeconds);

                    status.put("authenticated", true);
                    status.put("username", username);
                    status.put("userId", userId);
                    status.put("remainingSeconds", remainingSeconds);
                    status.put("maxInactiveInterval", maxInactiveInterval);
                    status.put("sessionCreationTime", Instant.ofEpochMilli(sessionCreationTime).toString());
                    status.put("lastAccessTime", Instant.ofEpochMilli(session.getLastAccessedTime()).toString());

                    // Determine warning level
                    if (remainingSeconds < 15) {
                        status.put("status", "critical");
                    } else if (remainingSeconds < 60) {
                        status.put("status", "warning");
                    } else {
                        status.put("status", "active");
                    }

                    // Log only when significant
                    if (remainingSeconds < 60 || remainingSeconds % 30 == 0) {
                        log.info("⏱️ Session for {}: {}s remaining ({}m {}s) - using creation time",
                                username, remainingSeconds, remainingSeconds / 60, remainingSeconds % 60);
                    }
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
     * REFRESH SESSION - Called on user activity to reset the session timer
     * This is the primary endpoint for resetting the session on user interaction
     */
    @PostMapping(value = "/api/session/refresh", produces = "application/json")
    @ResponseBody
    public Map<String, Object> refreshSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session != null) {
            try {
                // Reset the session creation time to now - this effectively resets the timer
                long currentTime = System.currentTimeMillis();
                session.setAttribute("sessionCreationTime", currentTime);
                session.setAttribute("sessionLastResetAt", currentTime);
                session.setAttribute("sessionRefreshCount",
                        ((Integer) session.getAttribute("sessionRefreshCount") != null ?
                                (Integer) session.getAttribute("sessionRefreshCount") + 1 : 1));

                int maxInactiveInterval = session.getMaxInactiveInterval();

                response.put("success", true);
                response.put("remainingSeconds", maxInactiveInterval);
                response.put("message", "Session refreshed successfully");
                response.put("refreshedAt", Instant.now().toString());

                String username = (String) session.getAttribute("username");
                int refreshCount = (Integer) session.getAttribute("sessionRefreshCount");
                log.info("🔄 Session REFRESHED for user: {} (refresh #{}, reset to {} seconds)",
                        username, refreshCount, maxInactiveInterval);

            } catch (Exception e) {
                log.error("Error refreshing session: {}", e.getMessage());
                response.put("success", false);
                response.put("message", "Failed to refresh session: " + e.getMessage());
            }
        } else {
            response.put("success", false);
            response.put("message", "No active session found");
        }

        return response;
    }

    /**
     * Extend current session - called when user clicks "Extend Session" in the modal
     * This is similar to refresh but with an explicit user action
     */
    @PostMapping(value = "/api/session/extend", produces = "application/json")
    @ResponseBody
    public Map<String, Object> extendSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session != null) {
            try {
                // Reset the session creation time to now
                long currentTime = System.currentTimeMillis();
                session.setAttribute("sessionCreationTime", currentTime);
                session.setAttribute("sessionLastResetAt", currentTime);
                session.setAttribute("sessionExtendedAt", currentTime);
                session.setAttribute("sessionExtendCount",
                        ((Integer) session.getAttribute("sessionExtendCount") != null ?
                                (Integer) session.getAttribute("sessionExtendCount") + 1 : 1));

                int maxInactiveInterval = session.getMaxInactiveInterval();

                response.put("success", true);
                response.put("remainingSeconds", maxInactiveInterval);
                response.put("message", "Session extended successfully");
                response.put("extendedAt", Instant.now().toString());

                String username = (String) session.getAttribute("username");
                int extendCount = (Integer) session.getAttribute("sessionExtendCount");
                log.info("✅ Session EXTENDED for user: {} (extend #{}, reset to {} seconds)",
                        username, extendCount, maxInactiveInterval);

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
     * This uses the same creation-time approach as status
     */
    @GetMapping(value = "/api/session/ping", produces = "application/json")
    @ResponseBody
    public Map<String, Object> pingSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Map<String, Object> response = new HashMap<>();

        if (session != null) {
            response.put("active", true);
            response.put("sessionId", session.getId());
            response.put("timeoutSeconds", session.getMaxInactiveInterval());

            // Get remaining time using creation time
            Long sessionCreationTime = (Long) session.getAttribute("sessionCreationTime");
            if (sessionCreationTime != null) {
                long elapsedSeconds = (System.currentTimeMillis() - sessionCreationTime) / 1000;
                long remainingSeconds = Math.max(0, session.getMaxInactiveInterval() - elapsedSeconds);
                response.put("remainingSeconds", remainingSeconds);
            }

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