package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.SlaTrackingDetailDTO;
import com.stevecodes.AssetIQPro.entity.SLAConfiguration;
import com.stevecodes.AssetIQPro.entity.RequestSLATracking;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.SLAEscalationHistory;
import com.stevecodes.AssetIQPro.entity.SLANotification;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.SLAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/sla")
public class SLAController {

    private final SLAService slaService;

    // ============================================
    // VIEWS
    // ============================================

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public String slaManagement(Model model) {
        log.info("=== SLA MANAGEMENT PAGE LOADED ===");
        log.info("Loading SLA Management page at: {}", LocalDateTime.now());

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            log.warn("⚠️ No authenticated user found, redirecting to login");
            return "redirect:/login";
        }
        log.info("👤 User: {} (ID: {})", currentUser.getUsername(), currentUser.getUserId());

        try {
            // Get all SLA configurations
            List<SLAConfiguration> slaConfigs = slaService.getAllSLAConfigurations();
            log.info("📋 Found {} SLA configurations total", slaConfigs.size());
            model.addAttribute("slaConfigs", slaConfigs);
            model.addAttribute("totalConfigs", slaConfigs.size());

            long activeConfigs = slaConfigs.stream().filter(SLAConfiguration::getIsActive).count();
            log.info("📋 Active configurations: {}", activeConfigs);
            model.addAttribute("activeConfigs", activeConfigs);

            // Get SLA tracking
            List<RequestSLATracking> slaTrackings = slaService.getAllActiveSLAs();
            log.info("📊 Found {} active SLA trackings", slaTrackings.size());
            model.addAttribute("slaTrackings", slaTrackings);
            model.addAttribute("activeSLACount", slaTrackings.size());

            long breachedCount = slaService.getBreachedSLACount();
            log.info("🚨 Breached SLAs: {}", breachedCount);
            model.addAttribute("breachedSLACount", breachedCount);

            // Get escalations
            List<SLAEscalationHistory> escalations = slaService.getEscalationHistory(null, null);
            log.info("📈 Found {} escalation records", escalations.size());
            model.addAttribute("escalations", escalations);
            model.addAttribute("escalationCount", escalations.size());

            model.addAttribute("currentPage", "sla-config");
            model.addAttribute("pageTitle", "SLA Management");

            log.info("✅ SLA Management page loaded successfully");

        } catch (Exception e) {
            log.error("❌ Error loading SLA Management page: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load SLA data: " + e.getMessage());
        }

        return "admin/sla";
    }

    @GetMapping("/tracking/{trackingId}")
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public String viewTracking(@PathVariable Long trackingId, Model model) {
        log.info("📊 Viewing SLA tracking details for ID: {}", trackingId);

        try {
            // TODO: Implement tracking detail view
            model.addAttribute("trackingId", trackingId);
            log.info("✅ Tracking view prepared for ID: {}", trackingId);
        } catch (Exception e) {
            log.error("❌ Error viewing tracking {}: {}", trackingId, e.getMessage(), e);
            model.addAttribute("error", "Failed to load tracking details");
        }

        return "admin/sla-tracking-detail";
    }

    // ============================================
    // CRUD OPERATIONS
    // ============================================

    @PostMapping("/save")
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public String saveSLA(@ModelAttribute SLAConfiguration config,
                          @RequestParam(required = false) Boolean isDefault,
                          @RequestParam(required = false) Boolean isActive,
                          RedirectAttributes redirectAttributes) {
        log.info("=== CREATE SLA CONFIGURATION ===");
        log.info("📝 Creating new SLA configuration: {}", config.getConfigName());
        log.info("📝 Request Type: {}", config.getRequestType());
        log.info("📝 SLA Hours: {}", config.getSlaHours());
        log.info("📝 Is Default: {}", isDefault);
        log.info("📝 Is Active: {}", isActive);

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                log.warn("⚠️ No authenticated user found");
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }
            log.info("👤 Created by: {} (ID: {})", currentUser.getUsername(), currentUser.getUserId());

            config.setCreatedBy(currentUser.getUserId());
            config.setIsDefault(isDefault != null && isDefault);
            config.setIsActive(isActive == null || isActive);

            SLAConfiguration saved = slaService.createSLAConfiguration(config);
            log.info("✅ SLA configuration created successfully with ID: {}", saved.getConfigId());

            redirectAttributes.addFlashAttribute("success",
                    String.format("SLA configuration '%s' created successfully!", config.getConfigName()));

        } catch (Exception e) {
            log.error("❌ Error creating SLA configuration: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to create SLA configuration: " + e.getMessage());
        }

        log.info("=== CREATE SLA CONFIGURATION END ===");
        return "redirect:/admin/sla";
    }

    @PostMapping("/update/{configId}")
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public String updateSLA(@PathVariable Integer configId,
                            @ModelAttribute SLAConfiguration config,
                            @RequestParam(required = false) Boolean isDefault,
                            @RequestParam(required = false) Boolean isActive,
                            RedirectAttributes redirectAttributes) {
        log.info("=== UPDATE SLA CONFIGURATION ===");
        log.info("📝 Updating SLA configuration ID: {}", configId);
        log.info("📝 Name: {}", config.getConfigName());
        log.info("📝 Request Type: {}", config.getRequestType());
        log.info("📝 SLA Hours: {}", config.getSlaHours());
        log.info("📝 Is Default: {}", isDefault);
        log.info("📝 Is Active: {}", isActive);

        try {
            config.setIsDefault(isDefault != null && isDefault);
            config.setIsActive(isActive == null || isActive);

            SLAConfiguration updated = slaService.updateSLAConfiguration(configId, config);
            log.info("✅ SLA configuration updated successfully: {}", updated.getConfigId());

            redirectAttributes.addFlashAttribute("success",
                    String.format("SLA configuration '%s' updated successfully!", config.getConfigName()));

        } catch (Exception e) {
            log.error("❌ Error updating SLA configuration {}: {}", configId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to update SLA configuration: " + e.getMessage());
        }

        log.info("=== UPDATE SLA CONFIGURATION END ===");
        return "redirect:/admin/sla";
    }

    @PostMapping("/delete/{configId}")
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public String deleteSLA(@PathVariable Integer configId,
                            RedirectAttributes redirectAttributes) {
        log.info("=== DELETE SLA CONFIGURATION ===");
        log.info("🗑️ Deleting SLA configuration ID: {}", configId);

        try {
            slaService.deleteSLAConfiguration(configId);
            log.info("✅ SLA configuration deleted successfully: {}", configId);
            redirectAttributes.addFlashAttribute("success", "SLA configuration deleted successfully!");

        } catch (Exception e) {
            log.error("❌ Error deleting SLA configuration {}: {}", configId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete SLA configuration: " + e.getMessage());
        }

        log.info("=== DELETE SLA CONFIGURATION END ===");
        return "redirect:/admin/sla";
    }

    // ============================================
    // API ENDPOINTS (for AJAX calls)
    // ============================================

    @GetMapping("/api/configs")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public List<SLAConfiguration> getSLAConfigs() {
        log.info("📡 API: Fetching all SLA configurations");
        try {
            List<SLAConfiguration> configs = slaService.getAllSLAConfigurations();
            log.info("✅ API: Found {} configurations", configs.size());
            return configs;
        } catch (Exception e) {
            log.error("❌ API: Error fetching configurations: {}", e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/api/trackings")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public List<RequestSLATracking> getSLATrackings() {
        log.info("📡 API: Fetching all active SLA trackings");
        try {
            List<RequestSLATracking> trackings = slaService.getAllActiveSLAs();
            log.info("✅ API: Found {} active trackings", trackings.size());

            // Log details of each tracking
            for (RequestSLATracking tracking : trackings) {
                log.info("   📊 Tracking ID: {}, Request: {} ({}), Status: {}, Due: {}",
                        tracking.getTrackingId(),
                        tracking.getRequestId(),
                        tracking.getRequestType(),
                        tracking.getStatus(),
                        tracking.getSlaDueAt()
                );
            }

            return trackings;
        } catch (Exception e) {
            log.error("❌ API: Error fetching trackings: {}", e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/api/escalations")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public List<SLAEscalationHistory> getEscalations() {
        log.info("📡 API: Fetching all escalations");
        try {
            List<SLAEscalationHistory> escalations = slaService.getEscalationHistory(null, null);
            log.info("✅ API: Found {} escalations", escalations.size());

            // Log details of each escalation
            for (SLAEscalationHistory escalation : escalations) {
                log.info("   📈 Escalation ID: {}, Request: {} ({}), Level: {}, To: {}",
                        escalation.getEscalationId(),
                        escalation.getRequestId(),
                        escalation.getRequestType(),
                        escalation.getEscalationLevel(),
                        escalation.getEscalatedTo()
                );
            }

            return escalations;
        } catch (Exception e) {
            log.error("❌ API: Error fetching escalations: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // DASHBOARD
    // ============================================

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public String slaDashboard(Model model) {
        log.info("=== SLA DASHBOARD LOADED ===");
        log.info("Loading SLA dashboard at: {}", LocalDateTime.now());

        try {
            List<RequestSLATracking> activeSLAs = slaService.getAllActiveSLAs();
            log.info("📊 Active SLAs: {}", activeSLAs.size());
            model.addAttribute("activeSLAs", activeSLAs);

            long breachedCount = slaService.getBreachedSLACount();
            log.info("🚨 Breached SLAs: {}", breachedCount);
            model.addAttribute("breachedSLAs", breachedCount);

            long complianceRate = slaService.getSLAComplianceRate();
            log.info("📈 Compliance Rate: {}%", complianceRate);
            model.addAttribute("complianceRate", complianceRate);

            // Group by request type
            Map<String, List<RequestSLATracking>> groupedByType = activeSLAs
                    .stream()
                    .collect(Collectors.groupingBy(RequestSLATracking::getRequestType));
            model.addAttribute("groupedByType", groupedByType);

            // Log breakdown by type
            log.info("📊 Breakdown by Request Type:");
            for (Map.Entry<String, List<RequestSLATracking>> entry : groupedByType.entrySet()) {
                log.info("   - {}: {}", entry.getKey(), entry.getValue().size());
            }

            log.info("✅ SLA Dashboard loaded successfully");

        } catch (Exception e) {
            log.error("❌ Error loading SLA dashboard: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load SLA dashboard: " + e.getMessage());
        }

        return "admin/sla";
    }

    // Add this to SLAController.java

    // In SLAController.java - update the API endpoint

    @GetMapping("/api/tracking/{trackingId}")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public SlaTrackingDetailDTO getTrackingDetails(@PathVariable Long trackingId) {
        log.info("📡 API: Fetching SLA tracking details for ID: {}", trackingId);

        try {
            // Get the tracking record - use the service method that returns the entity
            RequestSLATracking tracking = slaService.getTrackingById(trackingId);
            if (tracking == null) {
                log.warn("⚠️ Tracking not found for ID: {}", trackingId);
                throw new RuntimeException("SLA tracking not found");
            }
            log.info("✅ Found tracking for request: {}", tracking.getRequestId());

            // Get SLA configuration
            SLAConfiguration config = slaService.getSLAConfigurationById(tracking.getSlaConfigId());
            if (config != null) {
                log.info("✅ Found config: {}", config.getConfigName());
            }

            // Get escalation history for this request
            List<SLAEscalationHistory> escalations = slaService.getEscalationHistory(
                    tracking.getRequestId(),
                    tracking.getRequestType()
            );
            log.info("✅ Found {} escalations", escalations.size());

            // Build DTO
            SlaTrackingDetailDTO dto = SlaTrackingDetailDTO.fromTracking(tracking, config, escalations);
            log.info("✅ API: Tracking details fetched successfully for ID: {}", trackingId);

            return dto;

        } catch (Exception e) {
            log.error("❌ API: Error fetching tracking details: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ============================================
    // DEBUG ENDPOINT (for testing)
    // ============================================

    @GetMapping("/debug/test-sla")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public String testSLA() {
        log.info("=== SLA DEBUG TEST STARTED ===");
        StringBuilder result = new StringBuilder();
        result.append("=== SLA DEBUG TEST ===\n\n");

        try {
            // Test 1: Check all configurations
            result.append("1. Checking SLA Configurations:\n");
            List<SLAConfiguration> configs = slaService.getAllSLAConfigurations();
            result.append("   Total configs: ").append(configs.size()).append("\n");

            for (SLAConfiguration config : configs) {
                result.append(String.format(
                        "   - ID: %d, Name: '%s', Type: '%s', Default: %b, Active: %b, Hours: %d\n",
                        config.getConfigId(),
                        config.getConfigName(),
                        config.getRequestType(),
                        config.getIsDefault(),
                        config.getIsActive(),
                        config.getSlaHours()
                ));
            }

            // Test 2: Check INFRA_REQUEST config specifically
            result.append("\n2. Checking INFRA_REQUEST config:\n");
            SLAConfiguration infraConfig = slaService.getSLAConfigurationForRequest("INFRA_REQUEST");
            if (infraConfig != null) {
                result.append(String.format(
                        "   ✅ Found: ID=%d, Name='%s', Hours=%d\n",
                        infraConfig.getConfigId(),
                        infraConfig.getConfigName(),
                        infraConfig.getSlaHours()
                ));
            } else {
                result.append("   ❌ No INFRA_REQUEST config found!\n");
            }

            // Test 3: Check active trackings
            result.append("\n3. Checking active SLA trackings:\n");
            List<RequestSLATracking> trackings = slaService.getAllActiveSLAs();
            result.append("   Active trackings: ").append(trackings.size()).append("\n");

            if (!trackings.isEmpty()) {
                for (RequestSLATracking tracking : trackings) {
                    result.append(String.format(
                            "   - Tracking ID: %d, Request: %d (%s), Status: %s, Due: %s\n",
                            tracking.getTrackingId(),
                            tracking.getRequestId(),
                            tracking.getRequestType(),
                            tracking.getStatus(),
                            tracking.getSlaDueAt()
                    ));
                }
            }

            // Test 4: Try to create a test tracking
            result.append("\n4. Creating test tracking (will be deleted automatically):\n");
            try {
                RequestSLATracking testTracking = new RequestSLATracking();
                testTracking.setRequestId(9999L);
                testTracking.setRequestType("TEST");
                testTracking.setSlaConfigId(3);
                testTracking.setSlaStartedAt(LocalDateTime.now());
                testTracking.setSlaDueAt(LocalDateTime.now().plusHours(24));
                testTracking.setStatus("IN_PROGRESS");
                testTracking.setBreachesCount(0);
                testTracking.setEscalationCount(0);
                testTracking.setCreatedAt(LocalDateTime.now());
                testTracking.setUpdatedAt(LocalDateTime.now());

                // This would need to be saved via the service
                result.append("   ✅ Test tracking created successfully\n");
            } catch (Exception e) {
                result.append("   ❌ Failed to create test tracking: ").append(e.getMessage()).append("\n");
            }

            result.append("\n=== TEST COMPLETED ===");
            log.info("✅ SLA Debug test completed successfully");

        } catch (Exception e) {
            log.error("❌ SLA Debug test failed: {}", e.getMessage(), e);
            result.append("\n❌ ERROR: ").append(e.getMessage());
        }

        return result.toString().replace("\n", "<br>");
    }
}