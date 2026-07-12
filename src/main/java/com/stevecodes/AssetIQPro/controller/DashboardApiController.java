package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.DashboardStatsDTO;
import com.stevecodes.AssetIQPro.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        return ResponseEntity.ok(dashboardService.getDashboardStats());
    }

    @GetMapping("/trends")
    @Operation(summary = "Get monthly trends")
    public ResponseEntity<List<DashboardStatsDTO.MonthlyTrendDTO>> getMonthlyTrends() {
        return ResponseEntity.ok(dashboardService.getMonthlyTrends());
    }

    @GetMapping("/asset-distribution")
    @Operation(summary = "Get asset distribution by category")
    public ResponseEntity<Map<String, Long>> getAssetDistribution() {
        return ResponseEntity.ok(dashboardService.getAssetDistribution());
    }

    @GetMapping("/request-trends")
    @Operation(summary = "Get request trends")
    public ResponseEntity<Map<String, Object>> getRequestTrends() {
        return ResponseEntity.ok(dashboardService.getRequestTrends());
    }
}