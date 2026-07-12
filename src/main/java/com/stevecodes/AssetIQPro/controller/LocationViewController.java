package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Location;
import com.stevecodes.AssetIQPro.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/locations")
public class LocationViewController {

    private final LocationService locationService;

    @GetMapping
    public String locations(Model model) {
        model.addAttribute("locations", locationService.getAllLocations());
        return "admin/locations";
    }

    @PostMapping
    public String createLocation(@RequestParam String name, @RequestParam(required = false) String address,
                                 @RequestParam(required = false) String city, @RequestParam(required = false) String country,
                                 RedirectAttributes redirectAttributes) {
        try {
            Location location = new Location(name, address, city, country);
            locationService.createLocation(location);
            redirectAttributes.addFlashAttribute("success", "Location created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create location: " + e.getMessage());
        }
        return "redirect:/admin/locations";
    }

    @GetMapping("/{id}/delete")
    public String deleteLocation(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
        try {
            locationService.deleteLocation(id);
            redirectAttributes.addFlashAttribute("success", "Location deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete location: " + e.getMessage());
        }
        return "redirect:/admin/locations";
    }
}