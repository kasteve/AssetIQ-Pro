package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.UserDTO;
import com.stevecodes.AssetIQPro.service.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final AppUserService userService;

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

        if (!isPasswordStrong(newPassword)) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 8 characters and include uppercase, lowercase, and number.");
            return "redirect:/change-password?firstLogin=" + firstLogin;
        }

        try {
            userService.changePassword(userId, newPassword, firstLogin);
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

    private boolean isPasswordStrong(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasUpper && hasLower && hasDigit;
    }
}