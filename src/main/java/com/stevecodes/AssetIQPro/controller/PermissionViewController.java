package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.AppUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/permissions")
public class PermissionViewController {

    private final AppUserService userService;

    @GetMapping
    public String permissions(Model model) {
        log.info("Loading permissions page");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (!currentUser.hasAnyPermission("MANAGE_ROLES", "ADMIN")) {
            throw new AccessDeniedException("You don't have permission to manage permissions.");
        }

        model.addAttribute("users", userService.getAllUsers());
        model.addAttribute("canManageRoles", currentUser.hasAnyPermission("MANAGE_ROLES", "ADMIN"));

        return "admin/permissions";
    }
}