package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Permission;
import com.stevecodes.AssetIQPro.entity.UserGroup;
import com.stevecodes.AssetIQPro.exception.ResourceNotFoundException;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.repository.PermissionRepository;
import com.stevecodes.AssetIQPro.repository.UserGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final UserGroupRepository groupRepository;
    private final AppUserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    private static final Set<String> SYSTEM_GROUP_NAMES = Set.of(
            "ADMINS", "MANAGERS", "INFRASTRUCTURE", "FINANCE", "DRIVERS"
    );

    @Transactional
    public UserGroup createGroup(String groupName, String description, Long createdBy) {
        log.info("Creating user group: {}", groupName);

        if (groupRepository.existsByGroupName(groupName)) {
            throw new RuntimeException("Group name already exists: " + groupName);
        }

        UserGroup group = new UserGroup(groupName, description);
        group.setCreatedBy(createdBy);
        group.setSystemGroup(SYSTEM_GROUP_NAMES.contains(groupName.toUpperCase()));

        UserGroup saved = groupRepository.save(group);

        auditService.logAction("GROUP_CREATED",
                "Group created: " + groupName + " (System: " + group.isSystemGroup() + ")",
                createdBy);

        return saved;
    }

    @Transactional
    public UserGroup updateGroup(Long groupId, String groupName, String description, Long updatedBy) {
        log.info("Updating user group: {}", groupId);

        UserGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        if (group.isSystemGroup()) {
            throw new RuntimeException("Cannot modify system group: " + group.getGroupName());
        }

        if (!group.getGroupName().equals(groupName) && groupRepository.existsByGroupName(groupName)) {
            throw new RuntimeException("Group name already exists: " + groupName);
        }

        group.setGroupName(groupName);
        group.setDescription(description);
        group.setUpdatedBy(updatedBy);

        UserGroup saved = groupRepository.save(group);

        auditService.logAction("GROUP_UPDATED",
                "Group updated: " + groupName + " (ID: " + groupId + ")",
                updatedBy);

        return saved;
    }

    @Transactional
    public void deleteGroup(Long groupId, Long deletedBy) {
        log.info("Deleting user group: {}", groupId);

        UserGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        if (group.isSystemGroup()) {
            throw new RuntimeException("Cannot delete system group: " + group.getGroupName());
        }

        // Remove all members first
        group.getMembers().clear();
        groupRepository.save(group);

        groupRepository.delete(group);

        auditService.logAction("GROUP_DELETED",
                "Group deleted: " + group.getGroupName() + " (ID: " + groupId + ")",
                deletedBy);
    }

    @Transactional
    public void addMemberToGroup(Long groupId, Long userId) {
        log.info("Adding user {} to group {}", userId, groupId);

        UserGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        group.addMember(user);
        groupRepository.save(group);

        auditService.logAction("GROUP_MEMBER_ADDED",
                "User " + user.getUsername() + " added to group " + group.getGroupName(),
                null);
    }

    @Transactional
    public void removeMemberFromGroup(Long groupId, Long userId) {
        log.info("Removing user {} from group {}", userId, groupId);

        UserGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        group.removeMember(user);
        groupRepository.save(group);

        auditService.logAction("GROUP_MEMBER_REMOVED",
                "User " + user.getUsername() + " removed from group " + group.getGroupName(),
                null);
    }

    @Transactional
    public void addPermissionToGroup(Long groupId, String permissionName) {
        log.info("Adding permission {} to group {}", permissionName, groupId);

        UserGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        Permission permission = permissionRepository.findByPermissionName(permissionName)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + permissionName));

        group.addPermission(permission);
        groupRepository.save(group);
    }

    @Transactional
    public void removePermissionFromGroup(Long groupId, String permissionName) {
        log.info("Removing permission {} from group {}", permissionName, groupId);

        UserGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        group.getPermissions().removeIf(p -> p.getPermissionName().equals(permissionName));
        groupRepository.save(group);
    }

    @Transactional
    public void syncGroupPermissions(Long groupId, List<String> permissionNames) {
        log.info("Syncing permissions for group: {}", groupId);

        UserGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        Set<Permission> newPermissions = new HashSet<>();
        for (String permName : permissionNames) {
            permissionRepository.findByPermissionName(permName)
                    .ifPresent(newPermissions::add);
        }

        group.setPermissions(newPermissions);
        groupRepository.save(group);

        auditService.logAction("GROUP_PERMISSIONS_SYNCED",
                "Permissions synced for group: " + group.getGroupName(),
                null);
    }

    @Transactional
    public void toggleGroupStatus(Long groupId) {
        UserGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        if (group.isSystemGroup()) {
            throw new RuntimeException("Cannot deactivate system group: " + group.getGroupName());
        }

        group.setActive(!group.isActive());
        groupRepository.save(group);
    }

    // ============================================
    // Query Methods - FIXED to load eagerly
    // ============================================

    public List<UserGroup> getAllGroups() {
        return groupRepository.findAllWithMembersAndPermissions();
    }

    public List<UserGroup> getActiveGroups() {
        return groupRepository.findByActiveTrue();
    }

    public List<UserGroup> getSystemGroups() {
        return groupRepository.findSystemGroups();
    }

    public List<UserGroup> getCustomGroups() {
        return groupRepository.findCustomGroups();
    }

    public Optional<UserGroup> getGroupById(Long groupId) {
        return groupRepository.findByIdWithMembersAndPermissions(groupId);
    }

    public Optional<UserGroup> getGroupByName(String groupName) {
        return groupRepository.findByGroupName(groupName);
    }

    public List<UserGroup> getGroupsByMember(Long userId) {
        return groupRepository.findGroupsByMemberId(userId);
    }

    public List<UserGroup> getGroupsWithPermission(String permissionName) {
        return groupRepository.findGroupsWithPermission(permissionName);
    }

    public long getMemberCount(Long groupId) {
        return groupRepository.countMembers(groupId);
    }

    /**
     * Get all permissions for a user from their groups
     */
    public Set<String> getGroupPermissionsForUser(Long userId) {
        List<UserGroup> groups = groupRepository.findGroupsByMemberId(userId);
        return groups.stream()
                .filter(UserGroup::isActive)
                .flatMap(g -> g.getPermissions().stream())
                .map(Permission::getPermissionName)
                .collect(Collectors.toSet());
    }

    /**
     * Initialize default system groups with permissions
     */
    @Transactional
    public void initializeDefaultGroups() {
        log.info("Initializing default system groups...");

        // Create default groups if they don't exist
        createDefaultGroupIfNotExists("ADMINS", "System administrators with full access");
        createDefaultGroupIfNotExists("MANAGERS", "Line managers with approval authority");
        createDefaultGroupIfNotExists("INFRASTRUCTURE", "Infrastructure team members");
        createDefaultGroupIfNotExists("FINANCE", "Finance team members");
        createDefaultGroupIfNotExists("DRIVERS", "Driver team members");

        // Assign default permissions to groups
        assignDefaultPermissionsToGroup("ADMINS", List.of(
                "ADMIN", "MANAGE_ROLES", "CREATE_USERS", "RESET_PASSWORDS",
                "MANAGE_CONFIG", "VIEW_AUDIT", "VIEW_ALL_TRANSACTIONS",
                "MANAGE_GROUPS", "VIEW_GROUPS"
        ));

        assignDefaultPermissionsToGroup("MANAGERS", List.of(
                "APPROVE_LM", "VIEW_REPORTS", "EMPLOYEE_VIEW"
        ));

        assignDefaultPermissionsToGroup("INFRASTRUCTURE", List.of(
                "APPROVE_INFRA", "REVIEW_INFRA", "INFRA_REQUEST_VIEW", "INFRA_REQUEST_APPROVE"
        ));

        assignDefaultPermissionsToGroup("FINANCE", List.of(
                "APPROVE_FINANCE", "VIEW_REPORTS", "DOWNLOAD_REPORTS"
        ));

        assignDefaultPermissionsToGroup("DRIVERS", List.of(
                "DRIVER_VIEW", "DRIVER_REQUEST", "VIEW_OWN_TRANSACTIONS"
        ));

        log.info("Default system groups initialized successfully");
    }

    private void createDefaultGroupIfNotExists(String groupName, String description) {
        if (!groupRepository.existsByGroupName(groupName)) {
            UserGroup group = new UserGroup(groupName, description);
            group.setSystemGroup(true);
            group.setActive(true);
            groupRepository.save(group);
            log.info("Created system group: {}", groupName);
        }
    }

    private void assignDefaultPermissionsToGroup(String groupName, List<String> permissionNames) {
        UserGroup group = groupRepository.findByGroupName(groupName).orElse(null);
        if (group == null) return;

        for (String permName : permissionNames) {
            Permission permission = permissionRepository.findByPermissionName(permName).orElse(null);
            if (permission != null) {
                group.addPermission(permission);
            }
        }
        groupRepository.save(group);
        log.info("Assigned {} permissions to group: {}", permissionNames.size(), groupName);
    }
}