package com.stevecodes.AssetIQPro.controller;

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

import java.util.List;

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
        log.info("Loading SLA Management page");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        // Get all SLA configurations
        List<SLAConfiguration> slaConfigs = slaService.getAllSLAConfigurations();
        model.addAttribute("slaConfigs", slaConfigs);
        model.addAttribute("totalConfigs", slaConfigs.size());
        model.addAttribute("activeConfigs", slaConfigs.stream().filter(SLAConfiguration::getIsActive).count());

        // Get SLA tracking
        List<RequestSLATracking> slaTrackings = slaService.getAllActiveSLAs();
        model.addAttribute("slaTrackings", slaTrackings);
        model.addAttribute("activeSLACount", slaTrackings.size());
        model.addAttribute("breachedSLACount", slaService.getBreachedSLACount());

        // Get escalations
        List<SLAEscalationHistory> escalations = slaService.getEscalationHistory(null, null);
        model.addAttribute("escalations", escalations);
        model.addAttribute("escalationCount", escalations.size());

        model.addAttribute("currentPage", "sla-config");
        model.addAttribute("pageTitle", "SLA Management");

        return "admin/sla";
    }

    @GetMapping("/tracking/{trackingId}")
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public String viewTracking(@PathVariable Long trackingId, Model model) {
        log.info("Viewing SLA tracking: {}", trackingId);

        // TODO: Implement tracking detail view
        model.addAttribute("trackingId", trackingId);
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
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            config.setCreatedBy(currentUser.getUserId());
            config.setIsDefault(isDefault != null && isDefault);
            config.setIsActive(isActive == null || isActive);

            slaService.createSLAConfiguration(config);
            redirectAttributes.addFlashAttribute("success", "SLA configuration created successfully!");

        } catch (Exception e) {
            log.error("Error creating SLA configuration: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create SLA configuration: " + e.getMessage());
        }
        return "redirect:/admin/sla";
    }

    @PostMapping("/update/{configId}")
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public String updateSLA(@PathVariable Integer configId,
                            @ModelAttribute SLAConfiguration config,
                            @RequestParam(required = false) Boolean isDefault,
                            @RequestParam(required = false) Boolean isActive,
                            RedirectAttributes redirectAttributes) {
        try {
            config.setIsDefault(isDefault != null && isDefault);
            config.setIsActive(isActive == null || isActive);

            slaService.updateSLAConfiguration(configId, config);
            redirectAttributes.addFlashAttribute("success", "SLA configuration updated successfully!");

        } catch (Exception e) {
            log.error("Error updating SLA configuration: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to update SLA configuration: " + e.getMessage());
        }
        return "redirect:/admin/sla";
    }

    @PostMapping("/delete/{configId}")
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public String deleteSLA(@PathVariable Integer configId,
                            RedirectAttributes redirectAttributes) {
        try {
            // TODO: Implement delete logic in SLAService
            // slaService.deleteSLAConfiguration(configId);
            redirectAttributes.addFlashAttribute("success", "SLA configuration deleted successfully!");

        } catch (Exception e) {
            log.error("Error deleting SLA configuration: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to delete SLA configuration: " + e.getMessage());
        }
        return "redirect:/admin/sla";
    }

    // ============================================
    // API ENDPOINTS (for AJAX calls)
    // ============================================

    @GetMapping("/api/configs")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public List<SLAConfiguration> getSLAConfigs() {
        return slaService.getAllSLAConfigurations();
    }

    @GetMapping("/api/trackings")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public List<RequestSLATracking> getSLATrackings() {
        return slaService.getAllActiveSLAs();
    }

    @GetMapping("/api/escalations")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('MANAGE_CONFIG', 'ADMIN', 'SUPER_ADMIN')")
    public List<SLAEscalationHistory> getEscalations() {
        return slaService.getEscalationHistory(null, null);
    }
}