package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.UserGroup;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.PermissionService;
import com.stevecodes.AssetIQPro.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/groups")
public class GroupViewController {

    private final UserGroupService groupService;
    private final AppUserService userService;
    private final PermissionService permissionService;

    @GetMapping
    public String groups(Model model) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (!currentUser.hasAnyPermission("MANAGE_ROLES", "ADMIN")) {
            throw new AccessDeniedException("You don't have permission to manage groups.");
        }

        log.info("Loading user groups page");

        List<UserGroup> groups = groupService.getAllGroups();

        // Calculate stats in controller to avoid Thymeleaf expression issues
        long totalGroups = groups.size();
        long activeGroups = groups.stream().filter(UserGroup::isActive).count();
        long systemGroups = groups.stream().filter(UserGroup::isSystemGroup).count();
        long customGroups = groups.stream().filter(g -> !g.isSystemGroup() && g.isActive()).count();

        model.addAttribute("groups", groups);
        model.addAttribute("totalGroups", totalGroups);
        model.addAttribute("activeGroups", activeGroups);
        model.addAttribute("systemGroups", systemGroups);
        model.addAttribute("customGroups", customGroups);
        model.addAttribute("allPermissions", permissionService.getAllPermissions());
        model.addAttribute("users", userService.getAllUsers());
        model.addAttribute("canManage", currentUser.hasAnyPermission("MANAGE_ROLES", "ADMIN"));

        return "admin/groups";
    }

    @PostMapping("/create")
    public String createGroup(@RequestParam String groupName,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) List<String> permissions,
                              RedirectAttributes redirectAttributes) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            log.info("Creating group: {}", groupName);
            UserGroup group = groupService.createGroup(groupName, description, currentUser.getUserId());

            if (permissions != null && !permissions.isEmpty()) {
                groupService.syncGroupPermissions(group.getGroupId(), permissions);
            }

            redirectAttributes.addAttribute("success", "Group '" + groupName + "' created successfully!");
        } catch (Exception e) {
            log.error("Error creating group: {}", e.getMessage(), e);
            redirectAttributes.addAttribute("error", "Failed to create group: " + e.getMessage());
        }
        return "redirect:/admin/groups";
    }

    @PostMapping("/update")
    public String updateGroup(@RequestParam Long groupId,
                              @RequestParam String groupName,
                              @RequestParam(required = false) String description,
                              @RequestParam(required = false) List<String> permissions,
                              RedirectAttributes redirectAttributes) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            log.info("Updating group: {}", groupId);
            groupService.updateGroup(groupId, groupName, description, currentUser.getUserId());

            if (permissions != null) {
                groupService.syncGroupPermissions(groupId, permissions);
            }

            redirectAttributes.addAttribute("success", "Group updated successfully!");
        } catch (Exception e) {
            log.error("Error updating group: {}", e.getMessage(), e);
            redirectAttributes.addAttribute("error", "Failed to update group: " + e.getMessage());
        }
        return "redirect:/admin/groups";
    }

    @PostMapping("/{groupId}/delete")
    public String deleteGroup(@PathVariable Long groupId, RedirectAttributes redirectAttributes) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            log.info("Deleting group: {}", groupId);
            groupService.deleteGroup(groupId, currentUser.getUserId());
            redirectAttributes.addAttribute("success", "Group deleted successfully!");
        } catch (Exception e) {
            log.error("Error deleting group: {}", e.getMessage(), e);
            redirectAttributes.addAttribute("error", "Failed to delete group: " + e.getMessage());
        }
        return "redirect:/admin/groups";
    }

    @PostMapping("/{groupId}/toggle")
    public String toggleGroup(@PathVariable Long groupId, RedirectAttributes redirectAttributes) {
        try {
            log.info("Toggling group status: {}", groupId);
            groupService.toggleGroupStatus(groupId);
            redirectAttributes.addAttribute("success", "Group status updated successfully!");
        } catch (Exception e) {
            log.error("Error toggling group: {}", e.getMessage(), e);
            redirectAttributes.addAttribute("error", "Failed to toggle group status: " + e.getMessage());
        }
        return "redirect:/admin/groups";
    }

    @PostMapping("/add-member")
    public String addMember(@RequestParam Long groupId,
                            @RequestParam Long userId,
                            RedirectAttributes redirectAttributes) {
        try {
            log.info("Adding user {} to group {}", userId, groupId);
            groupService.addMemberToGroup(groupId, userId);
            redirectAttributes.addAttribute("success", "Member added successfully!");
        } catch (Exception e) {
            log.error("Error adding member: {}", e.getMessage(), e);
            redirectAttributes.addAttribute("error", "Failed to add member: " + e.getMessage());
        }
        return "redirect:/admin/groups";
    }

    @PostMapping("/{groupId}/remove-member")
    public String removeMember(@PathVariable Long groupId,
                               @RequestParam Long userId,
                               RedirectAttributes redirectAttributes) {
        try {
            log.info("Removing user {} from group {}", userId, groupId);
            groupService.removeMemberFromGroup(groupId, userId);
            redirectAttributes.addAttribute("success", "Member removed successfully!");
        } catch (Exception e) {
            log.error("Error removing member: {}", e.getMessage(), e);
            redirectAttributes.addAttribute("error", "Failed to remove member: " + e.getMessage());
        }
        return "redirect:/admin/groups";
    }
}