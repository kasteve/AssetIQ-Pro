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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    @Autowired
    private UserGroupService userGroupService;

    @Autowired
    private RolePermissionService rolePermissionService;

    // ============================================
    // Default Permissions for ALL New Users
    // ============================================
    private List<String> getDefaultPermissions() {
        return List.of(
                "GENERATE_VOUCHERS",
                "VIEW_OWN_TRANSACTIONS",
                "MANAGE_BOOKINGS",
                "INFRA_REQUEST_VIEW",
                "INFRA_REQUEST_CREATE",
                "RESOURCE_REQUEST_VIEW",
                "RESOURCE_REQUEST_CREATE",
                "ROOM_VIEW_ALL",
                "ROOM_BOOK",
                "ROOM_CANCEL",
                "DRIVER_VIEW"
        );
    }

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

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutesLeft = ChronoUnit.MINUTES.between(LocalDateTime.now(), user.getLockedUntil()) + 1;
            log.warn("Account locked for user: {} ({} minute(s) remaining)", username, minutesLeft);
            throw new AccountLockedException(
                    "Account locked due to too many failed login attempts. Try again in " + minutesLeft + " minute(s).");
        }

        if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(LocalDateTime.now())) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            int maxAttempts = settingService.getInt(SystemSettingService.KEY_PASSWORD_MAX_ATTEMPTS);
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (maxAttempts > 0 && attempts >= maxAttempts) {
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

        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        log.info("User authenticated successfully: {}", username);
        UserDTO dto = convertToDTO(user);

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

        // Create Employee
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

        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee created with ID: {}", savedEmployee.getEmployeeId());

        // Create User
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
        user.setEmployee(savedEmployee);

        Set<Permission> permissions = new HashSet<>();

        List<String> defaultPermissions = getDefaultPermissions();
        for (String permName : defaultPermissions) {
            permissionRepository.findByPermissionName(permName)
                    .ifPresent(permissions::add);
        }

        if (userDTO.getPermissions() != null && !userDTO.getPermissions().isEmpty()) {
            for (String permName : userDTO.getPermissions()) {
                permissionRepository.findByPermissionName(permName)
                        .ifPresent(permissions::add);
            }
        }

        user.setPermissions(permissions);

        AppUser savedUser = userRepository.save(user);
        log.info("User created successfully: {}", savedUser.getUsername());

        passwordHistoryRepository.save(new PasswordHistory(savedUser.getUserId(), savedUser.getPasswordHash()));

        log.info("=========================================");
        log.info("👤 NEW USER CREATED");
        log.info("Staff ID: {}", savedUser.getStaffId());
        log.info("Username: {}", savedUser.getUsername());
        log.info("Role: {}", savedUser.getRole());
        log.info("Department: {}", savedUser.getDepartment());
        log.info("Temporary Password: {}", tempPassword);
        log.info("Default Permissions: {}", defaultPermissions);
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

    // ============================================
    // Update User - FIXED with duplicate checks
    // ============================================
    @Transactional
    public AppUser updateUser(Long userId, UserDTO userDTO) {
        log.info("Updating user: {}", userId);

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // ✅ CHECK FOR DUPLICATE EMAIL (excluding current user)
        if (userDTO.getEmail() != null && !userDTO.getEmail().equals(user.getEmail())) {
            Optional<AppUser> existingUser = userRepository.findByEmail(userDTO.getEmail());
            if (existingUser.isPresent() && !existingUser.get().getUserId().equals(userId)) {
                throw new UserAlreadyExistsException("Email '" + userDTO.getEmail() + "' is already registered to another user.");
            }
            user.setEmail(userDTO.getEmail());
        }

        // ✅ CHECK FOR DUPLICATE USERNAME (excluding current user)
        if (userDTO.getUsername() != null && !userDTO.getUsername().equals(user.getUsername())) {
            Optional<AppUser> existingUser = userRepository.findByUsername(userDTO.getUsername());
            if (existingUser.isPresent() && !existingUser.get().getUserId().equals(userId)) {
                throw new UserAlreadyExistsException("Username '" + userDTO.getUsername() + "' is already taken.");
            }
            user.setUsername(userDTO.getUsername());
        }

        // ✅ CHECK FOR DUPLICATE STAFF ID (excluding current user)
        if (userDTO.getStaffId() != null && !userDTO.getStaffId().equals(user.getStaffId())) {
            Optional<AppUser> existingUser = userRepository.findByStaffId(userDTO.getStaffId());
            if (existingUser.isPresent() && !existingUser.get().getUserId().equals(userId)) {
                throw new UserAlreadyExistsException("Staff ID '" + userDTO.getStaffId() + "' is already registered.");
            }
            user.setStaffId(userDTO.getStaffId());
        }

        // Update other fields
        if (userDTO.getFullName() != null) {
            user.setFullName(userDTO.getFullName());
        }
        if (userDTO.getRole() != null) {
            user.setRole(userDTO.getRole());
        }

        user.setActive(userDTO.isActive());
        user.setBlocked(userDTO.isBlocked());

        if (userDTO.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(userDTO.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            user.setDepartmentEntity(dept);
            user.setDepartment(dept.getName());
        } else {
            user.setDepartmentEntity(null);
            user.setDepartment(null);
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

        // Update permissions
        if (userDTO.getPermissions() != null) {
            Set<Permission> newPermissions = new HashSet<>();
            for (String permName : userDTO.getPermissions()) {
                permissionRepository.findByPermissionName(permName)
                        .ifPresent(newPermissions::add);
            }
            user.setPermissions(newPermissions);
        }

        user.setUpdatedAt(LocalDateTime.now());

        AppUser updated = userRepository.save(user);

        auditService.logAction("USER_UPDATED",
                "User updated: " + updated.getUsername() + " role: " + updated.getRole(),
                updated.getUserId());

        return updated;
    }

    // ============================================
    // Password Helper Methods
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

    private void recordPasswordHistory(Long userId, String newHash) {
        passwordHistoryRepository.save(new PasswordHistory(userId, newHash));

        int historyCount = settingService.getInt(SystemSettingService.KEY_PASSWORD_HISTORY_COUNT);
        int keep = Math.max(historyCount, 1) + 5;
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

        Set<Permission> permissions = new HashSet<>();
        List<String> defaultPermissions = getDefaultPermissions();
        for (String permName : defaultPermissions) {
            permissionRepository.findByPermissionName(permName)
                    .ifPresent(permissions::add);
        }
        user.setPermissions(permissions);

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

    /**
     * Get all effective permissions for a user
     * Priority: Direct > Group > Role
     */
    public Set<String> getEffectivePermissions(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Set<String> allPermissions = new HashSet<>();

        // 1. Add role-based permissions
        if (user.getRole() != null) {
            allPermissions.addAll(rolePermissionService.getPermissionsForRole(user.getRole()));
        }

        // 2. Add group-based permissions
        Set<String> groupPermissions = userGroupService.getGroupPermissionsForUser(userId);
        allPermissions.addAll(groupPermissions);

        // 3. Add direct permissions (highest priority - can override)
        if (user.getPermissions() != null) {
            user.getPermissions().stream()
                    .map(Permission::getPermissionName)
                    .forEach(allPermissions::add);
        }

        return allPermissions;
    }

    /**
     * Check if a user has a permission (considering role, groups, and direct)
     */
    public boolean userHasPermission(Long userId, String permissionName) {
        return getEffectivePermissions(userId).contains(permissionName);
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
        } else {
            log.info("Permission '{}' already exists for user: {}", permissionName, userId);
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

        Set<Permission> newPermissions = new HashSet<>();
        for (String permName : permissionNames) {
            permissionRepository.findByPermissionName(permName)
                    .ifPresent(newPermissions::add);
        }
        user.setPermissions(newPermissions);
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

    public Optional<AppUser> getUserByEmployeeId(Long employeeId) {
        log.info("Getting user by employee ID: {}", employeeId);
        return userRepository.findByEmployeeId(employeeId);
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