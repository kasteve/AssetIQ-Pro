package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.UserDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Permission;
import com.stevecodes.AssetIQPro.exception.ResourceNotFoundException;
import com.stevecodes.AssetIQPro.exception.UserAlreadyExistsException;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.repository.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AppUserService {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuditService auditService;

    // ============================================
    // Authentication
    // ============================================

    public UserDTO authenticateUser(String username, String rawPassword) {
        log.info("Authenticating user: {}", username);

        Optional<AppUser> userOpt = userRepository.findByUsernameOrEmail(username, username);
        if (userOpt.isEmpty()) {
            log.warn("User not found: {}", username);
            return null;
        }

        AppUser user = userOpt.get();

        if (!user.isActive() || user.isBlocked()) {
            log.warn("User is inactive or blocked: {}", username);
            return null;
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            log.warn("Invalid password for user: {}", username);
            return null;
        }

        log.info("User authenticated successfully: {}", username);
        return convertToDTO(user);
    }

    // ============================================
    // User Management
    // ============================================

    @Transactional
    public AppUser createUser(UserDTO userDTO) {
        log.info("Creating new user: {}", userDTO.getUsername());

        if (userRepository.findByUsername(userDTO.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException("Username '" + userDTO.getUsername() + "' is already taken.");
        }
        if (userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email '" + userDTO.getEmail() + "' is already registered.");
        }

        String tempPassword = generateTemporaryPassword();

        AppUser user = new AppUser();
        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setFullName(userDTO.getFullName());

        // Handle department - store as string directly
        if (userDTO.getDepartment() != null && !userDTO.getDepartment().isEmpty()) {
            user.setDepartment(userDTO.getDepartment());
        }

        user.setActive(true);
        user.setBlocked(false);
        user.setMustChangePassword(true);
        user.setFirstLogin(true);
        user.setCreatedAt(LocalDateTime.now());

        // Assign permissions
        if (userDTO.getPermissions() != null && !userDTO.getPermissions().isEmpty()) {
            for (String permName : userDTO.getPermissions()) {
                permissionRepository.findByPermissionName(permName)
                        .ifPresent(user::addPermission);
            }
        }

        AppUser saved = userRepository.save(user);
        log.info("User created successfully: {}", saved.getUsername());

        // LOG CREDENTIALS BEFORE RETURNING
        log.info("=========================================");
        log.info("👤 NEW USER CREATED");
        log.info("Username: {}", saved.getUsername());
        log.info("Temporary Password: {}", tempPassword);
        log.info("Email: {}", saved.getEmail());
        log.info("=========================================");

        // Send welcome email
        try {
            emailService.sendWelcomeEmail(
                    saved.getEmail(),
                    saved.getFullName(),
                    saved.getUsername(),
                    tempPassword
            );
            log.info("Welcome email sent to: {}", saved.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", saved.getEmail(), e.getMessage());
        }

        // Audit log
        auditService.logAction("USER_CREATED", "User created: " + saved.getUsername(), saved.getUserId());

        return saved;
    }

    @Transactional
    public AppUser updateUser(Long userId, UserDTO userDTO) {
        log.info("Updating user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (userDTO.getFullName() != null) user.setFullName(userDTO.getFullName());
        if (userDTO.getDepartment() != null) user.setDepartment(userDTO.getDepartment());
        if (userDTO.isActive() != user.isActive()) user.setActive(userDTO.isActive());
        if (userDTO.isBlocked() != user.isBlocked()) user.setBlocked(userDTO.isBlocked());
        if (userDTO.getPasswordHash() != null) user.setPasswordHash(userDTO.getPasswordHash());
        if (userDTO.isMustChangePassword() != user.isMustChangePassword()) user.setMustChangePassword(userDTO.isMustChangePassword());
        if (userDTO.isFirstLogin() != user.isFirstLogin()) user.setFirstLogin(userDTO.isFirstLogin());

        // Update permissions
        if (userDTO.getPermissions() != null) {
            user.getPermissions().clear();
            for (String permName : userDTO.getPermissions()) {
                permissionRepository.findByPermissionName(permName)
                        .ifPresent(user::addPermission);
            }
        }

        user.setUpdatedAt(LocalDateTime.now());
        AppUser updated = userRepository.save(user);

        auditService.logAction("USER_UPDATED", "User updated: " + updated.getUsername(), updated.getUserId());

        return updated;
    }

    @Transactional
    public void changePassword(Long userId, String newPassword, boolean isFirstLogin) {
        log.info("Changing password for user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);
        user.setMustChangePassword(false);
        user.setLastPasswordChanged(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        log.info("Password changed for user: {}", userId);

        try {
            emailService.sendPasswordChangeConfirmation(user.getEmail(), user.getFullName());
        } catch (Exception e) {
            log.error("Failed to send password change confirmation: {}", e.getMessage());
        }

        auditService.logAction("PASSWORD_CHANGED", "Password changed for user: " + user.getUsername(), user.getUserId());
    }

    @Transactional
    public void resetPassword(Long userId) {
        log.info("Resetting password for user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String tempPassword = generateTemporaryPassword();

        log.info("=========================================");
        log.info("🔑 PASSWORD RESET FOR USER: {}", user.getUsername());
        log.info("Temporary Password: {}", tempPassword);
        log.info("=========================================");

        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        user.setFirstLogin(true);
        user.setLastPasswordChanged(LocalDateTime.now());
        userRepository.save(user);

        // Send email with new password
        try {
            emailService.sendWelcomeEmail(
                    user.getEmail(),
                    user.getFullName(),
                    user.getUsername(),
                    tempPassword
            );
            log.info("Reset email sent to: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send reset email: {}", e.getMessage());
            throw new RuntimeException("Failed to send reset email");
        }
    }

    @Transactional
    public String generatePasswordResetToken(String email) {
        log.info("Generating password reset token for: {}", email);

        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), token);
            log.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage());
            throw new RuntimeException("Failed to send password reset email");
        }

        return token;
    }

    @Transactional
    public void resetPasswordWithToken(String token, String newPassword) {
        log.info("Resetting password with token");

        AppUser user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid reset token"));

        if (user.getPasswordResetExpiry() == null ||
                user.getPasswordResetExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFirstLogin(false);
        user.setMustChangePassword(false);
        user.setLastPasswordChanged(LocalDateTime.now());
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        log.info("Password reset successfully for user: {}", user.getUsername());

        try {
            emailService.sendPasswordChangeConfirmation(user.getEmail(), user.getFullName());
        } catch (Exception e) {
            log.error("Failed to send password change confirmation: {}", e.getMessage());
        }
    }

    // ============================================
    // Permission Management
    // ============================================

    @Transactional
    public void addPermissionToUser(Long userId, String permissionName) {
        log.info("Adding permission '{}' to user: {}", permissionName, userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Permission permission = permissionRepository.findByPermissionName(permissionName)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionName));

        if (!user.getPermissions().contains(permission)) {
            user.addPermission(permission);
            userRepository.save(user);
            auditService.logAction("PERMISSION_ADDED",
                    "Permission '" + permissionName + "' added to user: " + user.getUsername(),
                    user.getUserId());
            log.info("Permission '{}' added to user: {}", permissionName, userId);
        }
    }

    @Transactional
    public void removePermissionFromUser(Long userId, String permissionName) {
        log.info("Removing permission '{}' from user: {}", permissionName, userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.getPermissions().removeIf(p -> p.getPermissionName().equals(permissionName));
        userRepository.save(user);

        auditService.logAction("PERMISSION_REMOVED",
                "Permission '" + permissionName + "' removed from user: " + user.getUsername(),
                user.getUserId());
        log.info("Permission '{}' removed from user: {}", permissionName, userId);
    }

    @Transactional
    public void syncPermissions(Long userId, List<String> permissionNames) {
        log.info("Syncing permissions for user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.getPermissions().clear();

        for (String permName : permissionNames) {
            permissionRepository.findByPermissionName(permName)
                    .ifPresent(user::addPermission);
        }

        userRepository.save(user);
        auditService.logAction("PERMISSIONS_SYNCED",
                "Permissions synced for user: " + user.getUsername(),
                user.getUserId());
        log.info("Permissions synced for user: {}", userId);
    }

    // ============================================
    // Query Methods
    // ============================================

    public List<AppUser> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<AppUser> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<AppUser> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<AppUser> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<AppUser> getUsersByDepartment(String department) {
        return userRepository.findByDepartment(department);
    }

    public List<AppUser> getActiveUsers() {
        return userRepository.findByActiveTrue();
    }

    public List<AppUser> getUsersWithPermission(String permissionName) {
        return userRepository.findUsersWithPermission(permissionName);
    }

    public boolean hasPermission(Long userId, String permissionName) {
        return userRepository.hasPermission(userId, permissionName);
    }

    public long getTotalUsers() {
        return userRepository.count();
    }

    public long getActiveUsersCount() {
        return userRepository.countByActiveTrue();
    }

    @Transactional
    public void toggleUserStatus(Long userId) {
        log.info("Toggling user status: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setActive(!user.isActive());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.logAction("USER_STATUS_TOGGLED",
                "User " + user.getUsername() + " status changed to: " + (user.isActive() ? "ACTIVE" : "INACTIVE"),
                user.getUserId());
        log.info("User status toggled: {} -> {}", userId, user.isActive() ? "ACTIVE" : "INACTIVE");
    }

    @Transactional
    public void blockUser(Long userId) {
        log.info("Blocking user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setBlocked(true);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.logAction("USER_BLOCKED", "User blocked: " + user.getUsername(), user.getUserId());
    }

    @Transactional
    public void unblockUser(Long userId) {
        log.info("Unblocking user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setBlocked(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.logAction("USER_UNBLOCKED", "User unblocked: " + user.getUsername(), user.getUserId());
    }

    @Transactional
    public void deleteUser(Long userId) {
        log.info("Deleting user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        auditService.logAction("USER_DELETED", "User deleted: " + user.getUsername(), userId);
        userRepository.deleteById(userId);
        log.info("User deleted: {}", userId);
    }

    // ============================================
    // Utility Methods
    // ============================================

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(12);

        // Ensure at least one of each character type
        password.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(random.nextInt(26)));
        password.append("abcdefghijklmnopqrstuvwxyz".charAt(random.nextInt(26)));
        password.append("0123456789".charAt(random.nextInt(10)));
        password.append("!@#$%^&*".charAt(random.nextInt(9)));

        // Fill remaining characters
        for (int i = 4; i < 12; i++) {
            password.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        return shuffleString(password.toString());
    }

    private String shuffleString(String input) {
        char[] chars = input.toCharArray();
        SecureRandom random = new SecureRandom();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    private UserDTO convertToDTO(AppUser user) {
        UserDTO dto = new UserDTO();
        dto.setUserId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setDepartment(user.getDepartment());
        dto.setActive(user.isActive());
        dto.setBlocked(user.isBlocked());
        dto.setFirstLogin(user.isFirstLogin());
        dto.setMustChangePassword(user.isMustChangePassword());
        dto.setPasswordHash(user.getPasswordHash());
        dto.setUserType(user.getPermissions().stream()
                .map(Permission::getPermissionName)
                .collect(Collectors.toList())
                .contains("ADMIN") ? "ADMIN" : "USER");
        dto.setPermissions(user.getPermissions().stream()
                .map(Permission::getPermissionName)
                .collect(Collectors.toList()));
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}