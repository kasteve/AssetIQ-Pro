package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.ResourceRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/resources")
public class ResourceViewController {

    private final ResourceRequestService resourceRequestService;
    private final AppUserService userService;

    @GetMapping
    public String resources(Model model) {
        log.info("Loading administration resources page");
        model.addAttribute("resourceRequests", resourceRequestService.getAllResourceRequests());
        return "resources/list";
    }

    @PostMapping("/request")
    public String requestResource(@RequestParam Long userId,
                                  @RequestParam String resourceType,
                                  @RequestParam String description,
                                  @RequestParam(required = false) Integer quantity,
                                  RedirectAttributes redirectAttributes) {
        try {
            String requestedBy = userService.getUserById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"))
                    .getUsername();

            resourceRequestService.createResourceRequest(userId, requestedBy, description, resourceType, quantity);
            redirectAttributes.addFlashAttribute("success", "Resource requested successfully!");
        } catch (Exception e) {
            log.error("Error requesting resource: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to request resource.");
        }
        return "redirect:/bookings/my-requests";
    }

    @PostMapping("/{requestId}/accept")
    public String acceptResourceRequest(@PathVariable Long requestId,
                                        @RequestParam(required = false) String adminComment,
                                        RedirectAttributes redirectAttributes) {
        try {
            resourceRequestService.acceptResourceRequest(requestId, adminComment);
            redirectAttributes.addFlashAttribute("success", "Resource request accepted.");
        } catch (Exception e) {
            log.error("Error accepting resource request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to accept request.");
        }
        return "redirect:/resources";
    }

    @PostMapping("/{requestId}/decline")
    public String declineResourceRequest(@PathVariable Long requestId,
                                         @RequestParam String declinedReason,
                                         RedirectAttributes redirectAttributes) {
        try {
            resourceRequestService.declineResourceRequest(requestId, declinedReason);
            redirectAttributes.addFlashAttribute("success", "Resource request declined.");
        } catch (Exception e) {
            log.error("Error declining resource request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to decline request.");
        }
        return "redirect:/resources";
    }
}