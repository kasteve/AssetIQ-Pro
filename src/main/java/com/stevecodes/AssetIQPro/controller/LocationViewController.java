package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Location;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/locations")
@PreAuthorize("hasAnyAuthority('LOCATION_VIEW', 'ADMIN', 'SUPER_ADMIN')")
public class LocationViewController {

    private final LocationService locationService;

    @GetMapping
    public String locations(Model model) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }
        model.addAttribute("locations", locationService.getAllLocations());
        model.addAttribute("canEdit", currentUser.hasAnyPermission("LOCATION_VIEW", "ADMIN"));
        model.addAttribute("canDelete", currentUser.hasAnyPermission("LOCATION_VIEW", "ADMIN"));
        return "admin/locations";
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('LOCATION_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String createLocation(@RequestParam String name, @RequestParam(required = false) String address,
                                 @RequestParam(required = false) String city, @RequestParam(required = false) String country,
                                 RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }
            Location location = new Location(name, address, city, country);
            locationService.createLocation(location);
            redirectAttributes.addFlashAttribute("success", "Location created successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create locations.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create location: " + e.getMessage());
        }
        return "redirect:/admin/locations";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyAuthority('LOCATION_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String updateLocation(@PathVariable Integer id, @RequestParam String name,
                                 @RequestParam(required = false) String address,
                                 @RequestParam(required = false) String city,
                                 @RequestParam(required = false) String country,
                                 RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }
            locationService.updateLocation(id, name, address, city, country);
            redirectAttributes.addFlashAttribute("success", "Location updated successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to update locations.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update location: " + e.getMessage());
        }
        return "redirect:/admin/locations";
    }

    @GetMapping("/{id}/delete")
    @PreAuthorize("hasAnyAuthority('LOCATION_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String deleteLocation(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }
            locationService.deleteLocation(id);
            redirectAttributes.addFlashAttribute("success", "Location deleted successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to delete locations.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete location: " + e.getMessage());
        }
        return "redirect:/admin/locations";
    }
}