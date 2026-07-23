package com.stevecodes.AssetIQPro.security;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    private static AppUserRepository userRepository;

    // This is a static utility class, so we need to inject via a setter or constructor
    public SecurityUtils(AppUserRepository userRepository) {
        SecurityUtils.userRepository = userRepository;
    }

    /**
     * Get the currently authenticated user
     */
    public static AppUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        // If it's already an AppUser, return it
        if (principal instanceof AppUser) {
            return (AppUser) principal;
        }

        // If it's a Spring Security User, get the username and fetch from DB
        if (principal instanceof User) {
            User user = (User) principal;
            String username = user.getUsername();
            if (userRepository != null) {
                return userRepository.findByUsernameOrEmail(username, username).orElse(null);
            }
        }

        // If principal is a String (username)
        if (principal instanceof String) {
            String username = (String) principal;
            if (userRepository != null) {
                return userRepository.findByUsernameOrEmail(username, username).orElse(null);
            }
        }

        return null;
    }

    /**
     * Check if current user has a specific permission
     */
    public static boolean hasPermission(String permission) {
        AppUser user = getCurrentUser();
        if (user == null) return false;
        return user.hasPermission(permission);
    }

    /**
     * Check if current user has any of the specified permissions
     */
    public static boolean hasAnyPermission(String... permissions) {
        AppUser user = getCurrentUser();
        if (user == null) return false;
        return user.hasAnyPermission(permissions);
    }

    /**
     * Check if current user has all of the specified permissions
     */
    public static boolean hasAllPermissions(String... permissions) {
        AppUser user = getCurrentUser();
        if (user == null) return false;
        return user.hasAllPermissions(permissions);
    }

    /**
     * Check if current user is an admin
     */
    public static boolean isAdmin() {
        AppUser user = getCurrentUser();
        if (user == null) return false;
        return user.isAdmin();
    }

    /**
     * Check if current user has a specific role
     */
    public static boolean hasRole(String role) {
        AppUser user = getCurrentUser();
        if (user == null) return false;
        return user.hasRole(role);
    }
}