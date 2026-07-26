package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.DashboardStatsDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard API", description = "Dashboard statistics and analytics API")
public class DashboardApiController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics")
    @PreAuthorize("hasAnyAuthority('VIEW_REPORTS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        return ResponseEntity.ok(dashboardService.getDashboardStats());
    }

    @GetMapping("/trends")
    @Operation(summary = "Get monthly trends")
    @PreAuthorize("hasAnyAuthority('VIEW_REPORTS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<DashboardStatsDTO.TrendDTO>> getMonthlyTrends() {
        return ResponseEntity.ok(dashboardService.getMonthlyTrends());
    }

    @GetMapping("/asset-distribution")
    @Operation(summary = "Get asset distribution by category")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'VIEW_REPORTS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Long>> getAssetDistribution() {
        return ResponseEntity.ok(dashboardService.getAssetDistribution());
    }

    @GetMapping("/request-trends")
    @Operation(summary = "Get request trends")
    @PreAuthorize("hasAnyAuthority('INFRA_REQUEST_VIEW', 'VIEW_REPORTS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> getRequestTrends() {
        return ResponseEntity.ok(dashboardService.getRequestTrends());
    }

    @GetMapping("/trends/daily")
    @Operation(summary = "Get daily trends (30 days)")
    @PreAuthorize("hasAnyAuthority('VIEW_REPORTS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<DashboardStatsDTO.TrendDTO>> getDailyTrends() {
        return ResponseEntity.ok(dashboardService.getDailyTrends(30));
    }

    @GetMapping("/trends/weekly")
    @Operation(summary = "Get weekly trends (12 weeks)")
    @PreAuthorize("hasAnyAuthority('VIEW_REPORTS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<DashboardStatsDTO.TrendDTO>> getWeeklyTrends() {
        return ResponseEntity.ok(dashboardService.getWeeklyTrends(12));
    }

    @GetMapping("/distribution/department")
    @Operation(summary = "Get asset distribution by department")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'VIEW_REPORTS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Long>> getByDepartment() {
        return ResponseEntity.ok(dashboardService.getAssetDistributionByDepartment());
    }

    @GetMapping("/distribution/status")
    @Operation(summary = "Get asset distribution by status")
    @PreAuthorize("hasAnyAuthority('ASSET_VIEW', 'VIEW_REPORTS', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Long>> getByStatus() {
        return ResponseEntity.ok(dashboardService.getAssetStatusDistribution());
    }
}