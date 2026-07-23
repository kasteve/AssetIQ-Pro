package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.UserDTO;
import com.stevecodes.AssetIQPro.security.JwtUtil;
import com.stevecodes.AssetIQPro.service.AppUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and user management APIs")
public class AuthenticationController {

    private final AppUserService userService;
    private final JwtUtil jwtUtil;

    // ============================================
    // Login (Public - No Auth Required)
    // ============================================

    @PostMapping("/login")
    @Operation(summary = "Login to get JWT token")
    public ResponseEntity<Map<String, Object>> login(@RequestParam String username,
                                                     @RequestParam String password) {
        Map<String, Object> response = new HashMap<>();

        try {
            UserDTO user = userService.authenticateUser(username, password);

            if (user == null) {
                response.put("success", false);
                response.put("message", "Invalid username or password");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

            if (user.isBlocked()) {
                response.put("success", false);
                response.put("message", "Your account has been blocked. Please contact administrator.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            if (!user.isActive()) {
                response.put("success", false);
                response.put("message", "Your account is inactive. Please contact administrator.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            String token = jwtUtil.generateToken(user.getUsername(), user.getUserType());

            response.put("success", true);
            response.put("message", "Authentication successful");
            response.put("token", token);
            response.put("username", user.getUsername());
            response.put("fullName", user.getFullName());
            response.put("userType", user.getUserType());
            response.put("department", user.getDepartment());
            response.put("permissions", user.getPermissions());
            response.put("mustChangePassword", user.isMustChangePassword());
            response.put("firstLogin", user.isFirstLogin());
            response.put("tokenType", "Bearer");
            response.put("expiresIn", "24 hours");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Authentication failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping("/web-login")
    @Operation(summary = "Login for web application (session-based)")
    public String webLogin(@RequestParam String username,
                           @RequestParam String password,
                           HttpSession session) {
        UserDTO user = userService.authenticateUser(username, password);

        if (user == null) {
            return "redirect:/login?error=true";
        }

        if (user.isBlocked() || !user.isActive()) {
            return "redirect:/login?error=blocked";
        }

        session.setAttribute("userId", user.getUserId());
        session.setAttribute("username", user.getUsername());
        session.setAttribute("fullName", user.getFullName());
        session.setAttribute("userType", user.getUserType());
        session.setAttribute("permissions", user.getPermissions());
        session.setAttribute("isFirstLogin", user.isFirstLogin());
        session.setAttribute("mustChangePassword", user.isMustChangePassword());

        if (user.isMustChangePassword()) {
            return "redirect:/change-password?firstLogin=true";
        }

        return "redirect:/dashboard";
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user")
    public ResponseEntity<Map<String, String>> logout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/web-logout")
    public String webLogout(HttpSession session) {
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login?logout=true";
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change user password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestParam Long userId,
                                                              @RequestParam String currentPassword,
                                                              @RequestParam String newPassword,
                                                              @RequestParam(required = false) String confirmPassword) {
        Map<String, String> response = new HashMap<>();

        if (!newPassword.equals(confirmPassword)) {
            response.put("success", "false");
            response.put("message", "Passwords do not match");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            userService.changePassword(userId, newPassword, false);
            response.put("success", "true");
            response.put("message", "Password changed successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", "false");
            response.put("message", "Failed to change password: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/change-password-first-login")
    @Operation(summary = "Change password on first login")
    public ResponseEntity<Map<String, String>> changePasswordFirstLogin(@RequestParam Long userId,
                                                                        @RequestParam String newPassword,
                                                                        @RequestParam String confirmPassword) {
        Map<String, String> response = new HashMap<>();

        if (!newPassword.equals(confirmPassword)) {
            response.put("success", "false");
            response.put("message", "Passwords do not match");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            userService.changePassword(userId, newPassword, true);
            response.put("success", "true");
            response.put("message", "Password changed successfully. You can now login.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", "false");
            response.put("message", "Failed to change password: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestParam String email) {
        Map<String, String> response = new HashMap<>();

        try {
            String token = userService.generatePasswordResetToken(email);
            response.put("success", "true");
            response.put("message", "Password reset link sent to your email");
            response.put("resetToken", token);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", "false");
            response.put("message", "Failed to send reset link: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using token")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestParam String token,
                                                             @RequestParam String newPassword,
                                                             @RequestParam String confirmPassword) {
        Map<String, String> response = new HashMap<>();

        if (!newPassword.equals(confirmPassword)) {
            response.put("success", "false");
            response.put("message", "Passwords do not match");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            userService.resetPasswordWithToken(token, newPassword);
            response.put("success", "true");
            response.put("message", "Password reset successfully. You can now login.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", "false");
            response.put("message", "Failed to reset password: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate JWT token")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.put("valid", false);
                response.put("message", "Invalid token format");
                return ResponseEntity.ok(response);
            }

            String token = authHeader.substring(7);
            String username = jwtUtil.extractUsername(token);
            String userType = jwtUtil.extractUserType(token);
            boolean isValid = jwtUtil.validateToken(token, username);

            response.put("valid", isValid);
            response.put("username", username);
            response.put("userType", userType);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("valid", false);
            response.put("message", "Token validation failed");
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/session")
    @Operation(summary = "Get current session information")
    public ResponseEntity<Map<String, Object>> getSessionInfo(HttpSession session) {
        Map<String, Object> response = new HashMap<>();

        response.put("userId", session.getAttribute("userId"));
        response.put("username", session.getAttribute("username"));
        response.put("fullName", session.getAttribute("fullName"));
        response.put("userType", session.getAttribute("userType"));
        response.put("isFirstLogin", session.getAttribute("isFirstLogin"));
        response.put("mustChangePassword", session.getAttribute("mustChangePassword"));
        response.put("sessionId", session.getId());
        response.put("creationTime", session.getCreationTime());
        response.put("lastAccessedTime", session.getLastAccessedTime());

        return ResponseEntity.ok(response);
    }
}