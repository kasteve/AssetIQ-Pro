package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.UserDTO;
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

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final AppUserService userService;
    private final SystemSettingService settingService;

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

            // Apply configured session timeout
            int timeoutMinutes = settingService.getInt(SystemSettingService.KEY_SESSION_TIMEOUT);
            if (timeoutMinutes > 0) {
                session.setMaxInactiveInterval(timeoutMinutes * 60);
            }

            log.info("Authenticated user: {}, userType: {}", user.getUsername(), user.getUserType());

            if (user.isMustChangePassword() || user.isFirstLogin()) {
                log.info("First-time login detected, redirecting to change-password for user: {}", username);
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

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
            log.info("User logged out successfully.");
        }
        return "redirect:/login?logout=true";
    }

    @GetMapping("/change-password")
    public String showChangePasswordForm(@RequestParam(required = false) boolean firstLogin,
                                         HttpSession session,
                                         Model model) {
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            return "redirect:/login";
        }

        model.addAttribute("userId", userId);
        model.addAttribute("firstLogin", firstLogin);
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

            // Clear session flags after password change
            session.setAttribute("mustChangePassword", false);
            session.setAttribute("isFirstLogin", false);

            redirectAttributes.addFlashAttribute("success", "Password changed successfully!");
            log.info("Password changed for user ID: {}", userId);

            if (firstLogin) {
                return "redirect:/login";
            }
            return "redirect:/dashboard";

        } catch (Exception e) {
            log.error("Error changing password for user ID: {}", userId, e);
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/change-password?firstLogin=" + firstLogin;
        }
    }

    // ============================================
    // FORGOT PASSWORD ENDPOINTS
    // ============================================

    @PostMapping("/forgot-password")
    @ResponseBody
    public Map<String, Object> forgotPassword(@RequestParam("username") String username) {
        Map<String, Object> response = new HashMap<>();
        try {
            log.info("Forgot password request for: {}", username);

            // Check if user exists
            var userOpt = userService.getUserByUsername(username);
            if (userOpt.isEmpty()) {
                // Check by email as well
                userOpt = userService.getUserByEmail(username);
            }

            if (userOpt.isEmpty()) {
                log.warn("User not found for forgot password: {}", username);
                response.put("success", false);
                response.put("message", "User not found with the provided username or email.");
                return response;
            }

            var user = userOpt.get();

            // Generate reset token
            String token = userService.generatePasswordResetToken(user.getEmail());

            // Send reset email with temporary password
            String tempPassword = userService.generateTemporaryPassword();
            userService.resetPassword(user.getUserId());

            response.put("success", true);
            response.put("message", "A temporary password has been sent to your email. Please check your inbox.");
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
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }

    /**
     * Validates a candidate password against the live password policy settings
     * (SystemSettingService / SECURITY category), rather than a hardcoded rule.
     * Returns null if valid, or a user-facing error message if not.
     */
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