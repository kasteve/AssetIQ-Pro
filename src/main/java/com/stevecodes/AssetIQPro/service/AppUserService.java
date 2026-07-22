package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.UserDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Department;
import com.stevecodes.AssetIQPro.entity.Employee;
import com.stevecodes.AssetIQPro.entity.Permission;
import com.stevecodes.AssetIQPro.entity.PasswordHistory;
import com.stevecodes.AssetIQPro.exception.AccountLockedException;
import com.stevecodes.AssetIQPro.exception.PasswordReuseException;
import com.stevecodes.AssetIQPro.exception.ResourceNotFoundException;
import com.stevecodes.AssetIQPro.exception.UserAlreadyExistsException;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.repository.DepartmentRepository;
import com.stevecodes.AssetIQPro.repository.EmployeeRepository;
import com.stevecodes.AssetIQPro.repository.PasswordHistoryRepository;
import com.stevecodes.AssetIQPro.repository.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordHistoryRepository passwordHistoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private SystemSettingService settingService;

    // ============================================
    // Authentication
    // ============================================

    @Transactional
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

        // ---- Lockout check ----
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutesLeft = ChronoUnit.MINUTES.between(LocalDateTime.now(), user.getLockedUntil()) + 1;
            log.warn("Account locked for user: {} ({} minute(s) remaining)", username, minutesLeft);
            throw new AccountLockedException(
                    "Account locked due to too many failed login attempts. Try again in " + minutesLeft + " minute(s).");
        }

        // Lock window has passed -> clear stale lock/attempts
        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(LocalDateTime.now())) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            int maxAttempts = settingService.getInt(SystemSettingService.KEY_PASSWORD_MAX_ATTEMPTS);
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (maxAttempts > 0 && attempts >= maxAttempts) {
                // Lock for 30 minutes (adjust as desired)
                user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
                userRepository.save(user);
                log.warn("Account locked after {} failed attempts: {}", attempts, username);
                throw new AccountLockedException(
                        "Account locked due to too many failed login attempts. Try again in 30 minute(s).");
            }

            userRepository.save(user);
            log.warn("Invalid password for user: {} (attempt {}/{})", username, attempts, maxAttempts);
            return null;
        }

        // Successful login -> reset failed attempt counter
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        log.info("User authenticated successfully: {}", username);
        UserDTO dto = convertToDTO(user);

        // ---- Password expiry check (transient flag only, not persisted) ----
        int expiryDays = settingService.getInt(SystemSettingService.KEY_PASSWORD_EXPIRY_DAYS);
        if (expiryDays > 0 && user.getLastPasswordChanged() != null) {
            long daysSinceChange = ChronoUnit.DAYS.between(user.getLastPasswordChanged(), LocalDateTime.now());
            if (daysSinceChange >= expiryDays) {
                log.info("Password expired for user: {} ({} days since last change)", username, daysSinceChange);
                dto.setMustChangePassword(true);
            }
        }

        return dto;
    }

    // ============================================
    // User Management
    // ============================================

    @Transactional
    public AppUser createUser(UserDTO userDTO) {
        log.info("Creating new user: {}", userDTO.getUsername());

        // Validation
        if (userRepository.findByUsername(userDTO.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException("Username '" + userDTO.getUsername() + "' is already taken.");
        }
        if (userRepository.findByEmail(userDTO.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException("Email '" + userDTO.getEmail() + "' is already registered.");
        }
        if (userDTO.getStaffId() != null && userRepository.findByStaffId(userDTO.getStaffId()).isPresent()) {
            throw new UserAlreadyExistsException("Staff ID '" + userDTO.getStaffId() + "' is already registered.");
        }

        String tempPassword = generateTemporaryPassword();

        // 1. Create and save Employee
        Employee employee = new Employee();
        employee.setStaffId(userDTO.getStaffId());
        employee.setFirstName(getFirstName(userDTO.getFullName()));
        employee.setSurName(getLastName(userDTO.getFullName()));
        employee.setEmailAddress(userDTO.getEmail());
        employee.setPhoneNumber(userDTO.getPhoneNumber());

        if (userDTO.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(userDTO.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            employee.setDepartment(dept);
        }

        if (userDTO.getLineManagerId() != null) {
            Employee lineManager = employeeRepository.findById(userDTO.getLineManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Line Manager not found"));
            employee.setLineManager(lineManager);
        }

        // Save Employee – it is now persistent
        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee created with ID: {}", savedEmployee.getEmployeeId());

        // 2. Create AppUser
        AppUser user = new AppUser();
        user.setStaffId(userDTO.getStaffId());
        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setFullName(userDTO.getFullName());
        user.setRole(userDTO.getRole() != null ? userDTO.getRole() : "EMPLOYEE");

        if (userDTO.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(userDTO.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            user.setDepartmentEntity(dept);
            user.setDepartment(dept.getName());
        }

        user.setActive(true);
        user.setBlocked(false);
        user.setMustChangePassword(true);
        user.setFirstLogin(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setLastPasswordChanged(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        // ✅ Link the persistent Employee (owning side)
        user.setEmployee(savedEmployee);

        // Assign permissions
        if (userDTO.getPermissions() != null && !userDTO.getPermissions().isEmpty()) {
            for (String permName : userDTO.getPermissions()) {
                permissionRepository.findByPermissionName(permName)
                        .ifPresent(user::addPermission);
            }
        } else {
            List<String> defaultPermissions = getDefaultPermissions(userDTO.getRole());
            for (String permName : defaultPermissions) {
                permissionRepository.findByPermissionName(permName)
                        .ifPresent(user::addPermission);
            }
        }

        // ✅ Save AppUser – this will set the foreign key (employee_id)
        AppUser savedUser = userRepository.save(user);
        log.info("User created successfully: {}", savedUser.getUsername());

        // Record initial password in history so it counts against future reuse checks
        passwordHistoryRepository.save(new PasswordHistory(savedUser.getUserId(), savedUser.getPasswordHash()));

        log.info("=========================================");
        log.info("👤 NEW USER CREATED");
        log.info("Staff ID: {}", savedUser.getStaffId());
        log.info("Username: {}", savedUser.getUsername());
        log.info("Role: {}", savedUser.getRole());
        log.info("Department: {}", savedUser.getDepartment());
        log.info("Temporary Password: {}", tempPassword);
        log.info("Email: {}", savedUser.getEmail());
        log.info("=========================================");

        try {
            emailService.sendWelcomeEmail(
                    savedUser.getEmail(),
                    savedUser.getFullName(),
                    savedUser.getUsername(),
                    tempPassword
            );
            log.info("Welcome email sent to: {}", savedUser.getEmail());
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", savedUser.getEmail(), e.getMessage());
        }

        auditService.logAction("USER_CREATED",
                "User created: " + savedUser.getUsername() + " with role: " + savedUser.getRole(),
                savedUser.getUserId());

        return savedUser;
    }

    private List<String> getDefaultPermissions(String role) {
        if (role == null) return List.of();

        return switch(role.toUpperCase()) {
            case "SUPERADMIN" -> List.of(
                    "VIEW_REPORTS", "DOWNLOAD_REPORTS", "VIEW_ALL_TRANSACTIONS",
                    "APPROVE_INFRA", "APPROVE_INFRA_REQUESTS", "APPROVE_LM", "APPROVE_FINANCE",
                    "CREATE_USERS", "RESET_PASSWORDS", "MANAGE_ROLES", "MANAGE_CONFIG",
                    "EDIT_ASSETS", "DELETE_ASSETS", "MANAGE_WARRANTY", "MANAGE_EOL",
                    "GENERATE_VOUCHERS", "GENERATE_MULTIPLE_VOUCHERS", "MANAGE_BOOKINGS",
                    "VIEW_AUDIT", "ASSET_VIEW", "ASSET_CREATE", "ASSET_EDIT", "ASSET_MOVE",
                    "ASSET_ASSIGN", "TRANSFER_CREATE", "TRANSFER_VIEW", "TRANSFER_BULK_EXPORT",
                    "USER_VIEW", "USER_EDIT", "USER_DISABLE", "USER_LOCK",
                    "INFRA_REQUEST_VIEW", "INFRA_REQUEST_APPROVE", "INFRA_REQUEST_ATTACH_QUOTATION",
                    "RESOURCE_REQUEST_VIEW", "RESOURCE_REQUEST_APPROVE",
                    "ROOM_VIEW_ALL", "DRIVER_VIEW", "DRIVER_APPROVE",
                    "CAB_REQUEST_APPROVE", "CAB_REQUEST_VIEW",
                    "EMPLOYEE_VIEW", "EMPLOYEE_CREATE", "EMPLOYEE_EDIT",
                    "DEPARTMENT_VIEW", "LOCATION_VIEW", "CATEGORY_VIEW", "SUPPLIER_VIEW", "COMPANY_VIEW"
            );
            case "ADMIN" -> List.of(
                    "VIEW_REPORTS", "DOWNLOAD_REPORTS", "VIEW_ALL_TRANSACTIONS",
                    "APPROVE_INFRA", "APPROVE_INFRA_REQUESTS", "APPROVE_LM", "APPROVE_FINANCE",
                    "CREATE_USERS", "RESET_PASSWORDS", "MANAGE_ROLES", "MANAGE_CONFIG",
                    "EDIT_ASSETS", "DELETE_ASSETS", "MANAGE_WARRANTY", "MANAGE_EOL",
                    "GENERATE_VOUCHERS", "GENERATE_MULTIPLE_VOUCHERS", "MANAGE_BOOKINGS",
                    "VIEW_AUDIT", "ASSET_VIEW", "ASSET_CREATE", "ASSET_EDIT", "ASSET_MOVE",
                    "ASSET_ASSIGN", "TRANSFER_CREATE", "TRANSFER_VIEW", "TRANSFER_BULK_EXPORT",
                    "USER_VIEW", "USER_EDIT", "USER_DISABLE", "USER_LOCK",
                    "INFRA_REQUEST_VIEW", "INFRA_REQUEST_APPROVE", "INFRA_REQUEST_ATTACH_QUOTATION",
                    "RESOURCE_REQUEST_VIEW", "RESOURCE_REQUEST_APPROVE",
                    "ROOM_VIEW_ALL", "DRIVER_VIEW", "DRIVER_APPROVE",
                    "CAB_REQUEST_APPROVE", "CAB_REQUEST_VIEW",
                    "EMPLOYEE_VIEW", "EMPLOYEE_CREATE", "EMPLOYEE_EDIT"
            );
            case "DRIVER" -> List.of(
                    "VIEW_REPORTS", "VIEW_OWN_TRANSACTIONS", "MANAGE_BOOKINGS",
                    "ROOM_BOOK", "ROOM_CANCEL", "ROOM_VIEW_ALL",
                    "DRIVER_APPROVE", "DRIVER_VIEW",
                    "INFRA_REQUEST_CREATE", "RESOURCE_REQUEST_CREATE",
                    "INFRA_REQUEST_VIEW", "RESOURCE_REQUEST_VIEW"
            );
            case "INFRA" -> List.of(
                    "VIEW_REPORTS", "VIEW_OWN_TRANSACTIONS", "DOWNLOAD_REPORTS",
                    "APPROVE_INFRA", "APPROVE_INFRA_REQUESTS", "MANAGE_BOOKINGS",
                    "VIEW_AUDIT", "ROOM_BOOK", "ROOM_CANCEL", "ROOM_VIEW_ALL",
                    "DRIVER_REQUEST", "DRIVER_CANCEL",
                    "INFRA_REQUEST_VIEW", "INFRA_REQUEST_APPROVE", "INFRA_REQUEST_ATTACH_QUOTATION",
                    "RESOURCE_REQUEST_CREATE", "RESOURCE_REQUEST_VIEW",
                    "SUPPLIER_VIEW", "COMPANY_VIEW"
            );
            case "FINANCE" -> List.of(
                    "VIEW_REPORTS", "VIEW_OWN_TRANSACTIONS", "DOWNLOAD_REPORTS",
                    "APPROVE_FINANCE", "VIEW_ALL_TRANSACTIONS",
                    "ROOM_BOOK", "ROOM_CANCEL", "ROOM_VIEW_ALL",
                    "DRIVER_REQUEST", "DRIVER_CANCEL",
                    "RESOURCE_REQUEST_VIEW", "RESOURCE_REQUEST_APPROVE",
                    "SUPPLIER_VIEW", "COMPANY_VIEW"
            );
            case "MANAGER" -> List.of(
                    "VIEW_REPORTS", "VIEW_OWN_TRANSACTIONS", "DOWNLOAD_REPORTS",
                    "APPROVE_LM", "MANAGE_BOOKINGS",
                    "ROOM_BOOK", "ROOM_CANCEL", "ROOM_VIEW_ALL",
                    "DRIVER_REQUEST", "DRIVER_CANCEL",
                    "INFRA_REQUEST_VIEW", "RESOURCE_REQUEST_VIEW",
                    "EMPLOYEE_VIEW"
            );
            default -> List.of(
                    "VIEW_REPORTS", "VIEW_OWN_TRANSACTIONS",
                    "ROOM_BOOK", "ROOM_CANCEL", "ROOM_VIEW_ALL",
                    "DRIVER_REQUEST", "DRIVER_CANCEL",
                    "INFRA_REQUEST_CREATE", "RESOURCE_REQUEST_CREATE",
                    "INFRA_REQUEST_VIEW", "RESOURCE_REQUEST_VIEW"
            );
        };
    }

    @Transactional
    public AppUser updateUser(Long userId, UserDTO userDTO) {
        log.info("Updating user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (userDTO.getFullName() != null) user.setFullName(userDTO.getFullName());
        if (userDTO.getRole() != null) user.setRole(userDTO.getRole());
        if (userDTO.isActive() != user.isActive()) user.setActive(userDTO.isActive());
        if (userDTO.isBlocked() != user.isBlocked()) user.setBlocked(userDTO.isBlocked());
        if (userDTO.getPasswordHash() != null) user.setPasswordHash(userDTO.getPasswordHash());
        if (userDTO.isMustChangePassword() != user.isMustChangePassword()) user.setMustChangePassword(userDTO.isMustChangePassword());
        if (userDTO.isFirstLogin() != user.isFirstLogin()) user.setFirstLogin(userDTO.isFirstLogin());

        // Update department
        if (userDTO.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(userDTO.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            user.setDepartmentEntity(dept);
            user.setDepartment(dept.getName());
        }

        // Update employee
        Employee employee = user.getEmployee();
        if (employee != null) {
            employee.setFirstName(getFirstName(userDTO.getFullName()));
            employee.setSurName(getLastName(userDTO.getFullName()));
            employee.setEmailAddress(userDTO.getEmail());
            employee.setPhoneNumber(userDTO.getPhoneNumber());

            if (userDTO.getDepartmentId() != null) {
                Department dept = departmentRepository.findById(userDTO.getDepartmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
                employee.setDepartment(dept);
            }

            if (userDTO.getLineManagerId() != null) {
                Employee lineManager = employeeRepository.findById(userDTO.getLineManagerId())
                        .orElseThrow(() -> new ResourceNotFoundException("Line Manager not found"));
                employee.setLineManager(lineManager);
            }

            employeeRepository.save(employee);
        }

        if (userDTO.getPermissions() != null) {
            user.getPermissions().clear();
            for (String permName : userDTO.getPermissions()) {
                permissionRepository.findByPermissionName(permName)
                        .ifPresent(user::addPermission);
            }
        }

        user.setUpdatedAt(LocalDateTime.now());
        AppUser updated = userRepository.save(user);

        auditService.logAction("USER_UPDATED",
                "User updated: " + updated.getUsername() + " role: " + updated.getRole(),
                updated.getUserId());

        return updated;
    }

    // ============================================
    // Password Helper Methods (SINGLE DEFINITION)
    // ============================================

    public String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(12);

        password.append("ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(random.nextInt(26)));
        password.append("abcdefghijklmnopqrstuvwxyz".charAt(random.nextInt(26)));
        password.append("0123456789".charAt(random.nextInt(10)));
        password.append("!@#$%^&*".charAt(random.nextInt(8)));

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

    /**
     * Checks the candidate new password against the user's stored password history,
     * per the configured "History Count" policy setting. Throws PasswordReuseException
     * if the candidate matches one of the last N passwords.
     */
    private void enforcePasswordHistory(Long userId, String rawNewPassword) {
        int historyCount = settingService.getInt(SystemSettingService.KEY_PASSWORD_HISTORY_COUNT);
        if (historyCount <= 0) return;

        List<PasswordHistory> history = passwordHistoryRepository.findByUserIdOrderByChangedAtDesc(userId);
        int checkLimit = Math.min(historyCount, history.size());

        for (int i = 0; i < checkLimit; i++) {
            if (passwordEncoder.matches(rawNewPassword, history.get(i).getPasswordHash())) {
                throw new PasswordReuseException(
                        "You cannot reuse one of your last " + historyCount + " password(s). Please choose a different password.");
            }
        }
    }

    /**
     * Records the new password hash in history and trims older entries beyond
     * the configured history count (kept as a small buffer of +5 for safety).
     */
    private void recordPasswordHistory(Long userId, String newHash) {
        passwordHistoryRepository.save(new PasswordHistory(userId, newHash));

        int historyCount = settingService.getInt(SystemSettingService.KEY_PASSWORD_HISTORY_COUNT);
        int keep = Math.max(historyCount, 1) + 5; // small buffer
        List<PasswordHistory> all = passwordHistoryRepository.findByUserIdOrderByChangedAtDesc(userId);
        if (all.size() > keep) {
            List<Long> idsToKeep = all.stream().limit(keep).map(PasswordHistory::getHistoryId).collect(Collectors.toList());
            passwordHistoryRepository.deleteByUserIdAndHistoryIdNotIn(userId, idsToKeep);
        }
    }

    @Transactional
    public void updateUserRole(Long userId, String role) {
        log.info("Updating role for user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        user.setRole(role);
        user.getPermissions().clear();

        List<String> defaultPermissions = getDefaultPermissions(role);
        for (String permName : defaultPermissions) {
            permissionRepository.findByPermissionName(permName)
                    .ifPresent(user::addPermission);
        }

        userRepository.save(user);
        log.info("Role updated to {} for user: {}", role, user.getUsername());

        auditService.logAction("ROLE_UPDATED",
                "Role updated to " + role + " for user: " + user.getUsername(),
                user.getUserId());
    }

    @Transactional
    public void changePassword(Long userId, String newPassword, boolean isFirstLogin) {
        log.info("Changing password for user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        enforcePasswordHistory(userId, newPassword);

        String newHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newHash);
        user.setFirstLogin(false);
        user.setMustChangePassword(false);
        user.setLastPasswordChanged(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        userRepository.save(user);
        recordPasswordHistory(userId, newHash);
        log.info("Password changed for user: {}", userId);

        try {
            emailService.sendPasswordChangeConfirmation(user.getEmail(), user.getFullName());
        } catch (Exception e) {
            log.error("Failed to send password change confirmation: {}", e.getMessage());
        }

        auditService.logAction("PASSWORD_CHANGED",
                "Password changed for user: " + user.getUsername(),
                user.getUserId());
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

        String newHash = passwordEncoder.encode(tempPassword);
        user.setPasswordHash(newHash);
        user.setMustChangePassword(true);
        user.setFirstLogin(true);
        user.setLastPasswordChanged(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        recordPasswordHistory(userId, newHash);

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

        enforcePasswordHistory(user.getUserId(), newPassword);

        String newHash = passwordEncoder.encode(newPassword);
        user.setPasswordHash(newHash);
        user.setFirstLogin(false);
        user.setMustChangePassword(false);
        user.setLastPasswordChanged(LocalDateTime.now());
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);
        user.setUpdatedAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        userRepository.save(user);
        recordPasswordHistory(user.getUserId(), newHash);
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

    public Optional<AppUser> getUserByStaffId(String staffId) {
        return userRepository.findByStaffId(staffId);
    }

    public List<AppUser> getUsersByDepartment(Integer departmentId) {
        return userRepository.findByDepartmentEntity_DepartmentId(departmentId);
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

    public List<AppUser> getUsersByRole(String role) {
        return userRepository.findByRole(role);
    }

    public long getActiveUsersCount() {
        return userRepository.countByActiveTrue();
    }

    public long getBlockedUsersCount() {
        return userRepository.countByBlockedTrue();
    }

    public long getPendingPasswordChangeCount() {
        return userRepository.countByMustChangePasswordTrue();
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

    /**
     * Admin-triggered manual unlock (clears lockout independent of blocked/active flags).
     */
    @Transactional
    public void unlockUser(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        auditService.logAction("USER_UNLOCKED", "User unlocked: " + user.getUsername(), user.getUserId());
    }

    @Transactional
    public void deleteUser(Long userId) {
        log.info("Deleting user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        auditService.logAction("USER_DELETED", "User deleted: " + user.getUsername(), userId);

        // Delete employee if exists
        if (user.getEmployee() != null) {
            employeeRepository.delete(user.getEmployee());
        }

        userRepository.deleteById(userId);
        log.info("User deleted: {}", userId);
    }

    // ============================================
    // Utility Methods
    // ============================================

    private String getFirstName(String fullName) {
        if (fullName == null) return "";
        int lastSpace = fullName.lastIndexOf(' ');
        return lastSpace > 0 ? fullName.substring(0, lastSpace) : fullName;
    }

    private String getLastName(String fullName) {
        if (fullName == null) return "";
        int lastSpace = fullName.lastIndexOf(' ');
        return lastSpace > 0 ? fullName.substring(lastSpace + 1) : "";
    }

    private UserDTO convertToDTO(AppUser user) {
        UserDTO dto = new UserDTO();
        dto.setUserId(user.getUserId());
        dto.setStaffId(user.getStaffId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setRole(user.getRole());
        dto.setActive(user.isActive());
        dto.setBlocked(user.isBlocked());
        dto.setFirstLogin(user.isFirstLogin());
        dto.setMustChangePassword(user.isMustChangePassword());
        dto.setPasswordHash(user.getPasswordHash());

        if (user.getDepartmentEntity() != null) {
            dto.setDepartmentId(user.getDepartmentEntity().getDepartmentId());
            dto.setDepartment(user.getDepartmentEntity().getName());
        }

        if (user.getEmployee() != null) {
            dto.setEmployeeId(user.getEmployee().getEmployeeId());
            if (user.getEmployee().getLineManager() != null) {
                dto.setLineManagerId(user.getEmployee().getLineManager().getEmployeeId());
            }
            dto.setPhoneNumber(user.getEmployee().getPhoneNumber());
        }

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