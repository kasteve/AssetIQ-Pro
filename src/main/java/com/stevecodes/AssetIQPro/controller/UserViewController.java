package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.DashboardStatsDTO;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.DashboardService;
import com.stevecodes.AssetIQPro.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class UserViewController {

    private final AppUserService userService;
    private final DashboardService dashboardService;
    private final PermissionService permissionService;

    @GetMapping
    public String users(Model model) {
        log.info("Loading user management page");

        // Get dashboard stats
        DashboardStatsDTO stats = dashboardService.getDashboardStats();

        // Calculate pending password change users
        long pendingPasswordChange = userService.getAllUsers().stream()
                .filter(user -> user.isMustChangePassword())
                .count();
        stats.setPendingPasswordChange(pendingPasswordChange);

        model.addAttribute("users", userService.getAllUsers());
        model.addAttribute("stats", stats);
        model.addAttribute("allPermissions", permissionService.getAllPermissions());

        return "admin/users";
    }
}