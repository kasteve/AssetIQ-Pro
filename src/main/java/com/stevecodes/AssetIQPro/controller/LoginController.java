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

            // ✅ Proper session management - invalidate old session if exists
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
                log.info("Old session invalidated for user: {}", username);
            }

            // ✅ Create new session
            HttpSession session = request.getSession(true);
            session.setAttribute("userId", user.getUserId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("fullName", user.getFullName());
            session.setAttribute("userType", user.getUserType());
            session.setAttribute("permissions", user.getPermissions());
            session.setAttribute("isFirstLogin", user.isFirstLogin());
            session.setAttribute("mustChangePassword", user.isMustChangePassword());

            // Store permission names
            if (user.getPermissions() != null) {
                session.setAttribute("permissionNames", user.getPermissions());
                log.info("User permissions: {}", user.getPermissions());
            }

            // Apply configured session timeout
            int timeoutMinutes = settingService.getInt(SystemSettingService.KEY_SESSION_TIMEOUT);
            if (timeoutMinutes > 0) {
                session.setMaxInactiveInterval(timeoutMinutes * 60);
                log.info("Session timeout set to {} minutes", timeoutMinutes);
            }

            log.info("Authenticated user: {}, userType: {}", user.getUsername(), user.getUserType());
            log.info("Session ID: {}", session.getId());

            // ✅ Check if user needs to change password
            if (user.isMustChangePassword() || user.isFirstLogin()) {
                log.info("Password change required for user: {}", username);
                return "redirect:/assetIQ-pro/change-password?firstLogin=true";  // ✅ Added context path
            }

            log.info("Session created successfully for user: {}", username);
            return "redirect:/assetIQ-pro/dashboard";  // ✅ Added context path

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
        return "redirect:/assetIQ-pro/login?logout=true";  // ✅ Added context path
    }

    @GetMapping("/change-password")
    public String showChangePasswordForm(@RequestParam(required = false) boolean firstLogin,
                                         HttpSession session,
                                         Model model) {
        Long userId = (Long) session.getAttribute("userId");

        if (userId == null) {
            log.warn("No userId in session, redirecting to login");
            return "redirect:/assetIQ-pro/login";  // ✅ Added context path
        }

        // Get password policy for display
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
        // Validate passwords match
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match.");
            return "redirect:/assetIQ-pro/change-password?firstLogin=" + firstLogin;  // ✅ Added context path
        }

        // Validate against password policy
        String policyError = validatePasswordPolicy(newPassword);
        if (policyError != null) {
            redirectAttributes.addFlashAttribute("error", policyError);
            return "redirect:/assetIQ-pro/change-password?firstLogin=" + firstLogin;  // ✅ Added context path
        }

        try {
            // Change password
            userService.changePassword(userId, newPassword, firstLogin);

            // Clear session flags after password change
            session.setAttribute("mustChangePassword", false);
            session.setAttribute("isFirstLogin", false);

            redirectAttributes.addFlashAttribute("success", "Password changed successfully!");
            log.info("Password changed for user ID: {}", userId);

            if (firstLogin) {
                return "redirect:/assetIQ-pro/login?success=true";  // ✅ Added context path
            }
            return "redirect:/assetIQ-pro/dashboard";  // ✅ Added context path

        } catch (Exception e) {
            log.error("Error changing password for user ID: {}", userId, e);
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/assetIQ-pro/change-password?firstLogin=" + firstLogin;  // ✅ Added context path
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

            // Check if user exists by username or email
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

            // Check if user is active
            if (!user.isActive()) {
                response.put("success", false);
                response.put("message", "Your account is inactive. Please contact administrator.");
                return response;
            }

            // Generate reset token
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
            // Validate passwords match
            if (!newPassword.equals(confirmPassword)) {
                response.put("success", false);
                response.put("message", "Passwords do not match.");
                return response;
            }

            // Validate against password policy
            String policyError = validatePasswordPolicy(newPassword);
            if (policyError != null) {
                response.put("success", false);
                response.put("message", policyError);
                return response;
            }

            // Reset password with token
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