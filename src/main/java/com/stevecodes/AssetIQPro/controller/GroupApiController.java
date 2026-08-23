package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.UserGroup;
import com.stevecodes.AssetIQPro.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/admin/groups")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('MANAGE_ROLES', 'ADMIN')")
public class GroupApiController {

    private final UserGroupService groupService;

    @GetMapping
    public ResponseEntity<List<UserGroup>> getAllGroups() {
        log.info("API: Getting all groups");
        List<UserGroup> groups = groupService.getAllGroups();
        log.info("Found {} groups", groups.size());
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<UserGroup> getGroup(@PathVariable Long groupId) {
        log.info("API: Getting group with ID: {}", groupId);
        return groupService.getGroupById(groupId)
                .map(group -> {
                    log.info("Found group: {} with {} members and {} permissions",
                            group.getGroupName(),
                            group.getMembers().size(),
                            group.getPermissions().size()
                    );
                    return ResponseEntity.ok(group);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<AppUser>> getGroupMembers(@PathVariable Long groupId) {
        log.info("API: Getting members for group ID: {}", groupId);
        return groupService.getGroupById(groupId)
                .map(group -> {
                    List<AppUser> members = group.getMembers().stream().collect(Collectors.toList());
                    log.info("Found {} members for group: {}", members.size(), group.getGroupName());
                    return ResponseEntity.ok(members);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{groupId}/permissions")
    public ResponseEntity<Set<String>> getGroupPermissions(@PathVariable Long groupId) {
        log.info("API: Getting permissions for group ID: {}", groupId);
        return groupService.getGroupById(groupId)
                .map(group -> {
                    Set<String> permissions = group.getPermissions().stream()
                            .map(p -> p.getPermissionName())
                            .collect(Collectors.toSet());
                    log.info("Found {} permissions for group: {}", permissions.size(), group.getGroupName());
                    return ResponseEntity.ok(permissions);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}