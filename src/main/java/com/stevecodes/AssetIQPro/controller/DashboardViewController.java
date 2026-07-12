package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.DashboardStatsDTO;
import com.stevecodes.AssetIQPro.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DashboardViewController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        log.info("Loading dashboard page");

        try {
            DashboardStatsDTO stats = dashboardService.getDashboardStats();
            model.addAttribute("stats", stats);
            model.addAttribute("recentTransfers", dashboardService.getRecentTransfers());
            model.addAttribute("recentRequests", dashboardService.getRecentRequests());
        } catch (Exception e) {
            log.error("Error loading dashboard stats: {}", e.getMessage());
            model.addAttribute("error", "Could not load dashboard data");
        }

        return "dashboard/index";
    }
}