package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.DashboardStatsDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardViewController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("isAuthenticated()")
    public String dashboard(Model model) {
        log.info("Loading dashboard page");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            DashboardStatsDTO stats = dashboardService.getDashboardStats();
            model.addAttribute("stats", stats);
            model.addAttribute("recentTransfers", dashboardService.getRecentTransfers());
            model.addAttribute("recentRequests", dashboardService.getRecentRequests());
            model.addAttribute("canViewReports", currentUser.hasAnyPermission("VIEW_REPORTS", "ADMIN"));
        } catch (Exception e) {
            log.error("Error loading dashboard stats: {}", e.getMessage());
            model.addAttribute("error", "Could not load dashboard data");
        }

        return "dashboard/index";
    }
}