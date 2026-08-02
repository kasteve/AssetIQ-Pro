package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Asset;
import com.stevecodes.AssetIQPro.entity.AssetDisposalRequest;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.AssetDisposalService;
import com.stevecodes.AssetIQPro.service.AssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/disposal")
public class AssetDisposalController {

    private final AssetDisposalService disposalService;
    private final AssetService assetService;

    // ============================================
    // VIEWS
    // ============================================

    @GetMapping
    @PreAuthorize("hasAnyAuthority('DISPOSAL_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String disposalDashboard(Model model) {
        log.info("Loading disposal dashboard");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("currentPage", "disposal-dashboard");
        model.addAttribute("pageTitle", "Disposal Mgt");

        List<AssetDisposalRequest> pendingRequests = disposalService.getPendingRequests();
        model.addAttribute("pendingRequests", pendingRequests);

        model.addAttribute("approvedRequests", disposalService.getRequestsByStatus("APPROVED"));
        model.addAttribute("inProgressRequests", disposalService.getRequestsByStatus("IN_PROGRESS"));
        model.addAttribute("completedRequests", disposalService.getRequestsByStatus("COMPLETED"));
        model.addAttribute("rejectedRequests", disposalService.getRequestsByStatus("REJECTED"));

        List<Asset> readyForDisposal = disposalService.getAssetsReadyForDisposal();
        model.addAttribute("readyForDisposal", readyForDisposal);

        List<AssetDisposalRequest> myRequests = disposalService.getRequestsByRequester(currentUser.getUserId());
        model.addAttribute("myRequests", myRequests);

        model.addAttribute("canApprove", currentUser.hasPermission("DISPOSAL_APPROVE") || currentUser.isAdmin());
        model.addAttribute("canExecute", currentUser.hasPermission("DISPOSAL_EXECUTE") || currentUser.isAdmin());
        model.addAttribute("canRequest", currentUser.hasPermission("DISPOSAL_REQUEST") || currentUser.isAdmin());

        return "admin/disposal-dashboard";
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String disposalRequests(Model model) {
        log.info("Loading disposal requests page");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("currentPage", "disposal-dashboard");
        model.addAttribute("pageTitle", "Disposal Mgt");

        List<AssetDisposalRequest> allRequests = disposalService.getRequestsByStatus("PENDING");
        model.addAttribute("requests", allRequests);

        model.addAttribute("canApprove", currentUser.hasPermission("DISPOSAL_APPROVE") || currentUser.isAdmin());
        model.addAttribute("canExecute", currentUser.hasPermission("DISPOSAL_EXECUTE") || currentUser.isAdmin());

        return "admin/disposal-requests";
    }

    // ============================================
    // API ENDPOINTS
    // ============================================

    @PostMapping("/request")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_REQUEST', 'ADMIN', 'SUPER_ADMIN')")
    public String createDisposalRequest(@RequestParam Integer assetId,
                                        @RequestParam String disposalReason,
                                        @RequestParam String disposalMethod,
                                        @RequestParam(required = false) String priority,
                                        RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            log.info("Creating disposal request for asset: {} by user: {}", assetId, currentUser.getUserId());

            Asset asset = assetService.getAssetEntityById(assetId);
            disposalService.createDisposalRequest(assetId, currentUser.getUserId(), disposalReason, disposalMethod, priority);

            redirectAttributes.addFlashAttribute("success", "Disposal request created successfully for asset: " + asset.getTag());

        } catch (IllegalStateException e) {
            log.error("Error creating disposal request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error creating disposal request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create disposal request.");
        }
        return "redirect:/assets";
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String approveDisposalRequest(@PathVariable Long requestId,
                                         @RequestParam(required = false) String comment,
                                         @RequestParam(required = false) String approvalReference,
                                         RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            log.info("Approving disposal request: {} by user: {}", requestId, currentUser.getUserId());

            disposalService.approveDisposalRequest(requestId, currentUser.getUserId(), comment, approvalReference);

            redirectAttributes.addFlashAttribute("success", "Disposal request approved successfully.");

        } catch (IllegalStateException e) {
            log.error("Error approving disposal request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error approving disposal request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to approve disposal request.");
        }
        return "redirect:/admin/disposal";
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String rejectDisposalRequest(@PathVariable Long requestId,
                                        @RequestParam String reason,
                                        RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            log.info("Rejecting disposal request: {} by user: {}", requestId, currentUser.getUserId());

            disposalService.rejectDisposalRequest(requestId, currentUser.getUserId(), reason);

            redirectAttributes.addFlashAttribute("success", "Disposal request rejected.");

        } catch (IllegalStateException e) {
            log.error("Error rejecting disposal request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error rejecting disposal request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to reject disposal request.");
        }
        return "redirect:/admin/disposal";
    }

    @PostMapping("/{requestId}/start")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_EXECUTE', 'ADMIN', 'SUPER_ADMIN')")
    public String startDisposalProcess(@PathVariable Long requestId,
                                       RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            log.info("Starting disposal process for request: {} by user: {}", requestId, currentUser.getUserId());

            disposalService.startDisposalProcess(requestId, currentUser.getUserId());

            redirectAttributes.addFlashAttribute("success", "Disposal process started.");

        } catch (Exception e) {
            log.error("Error starting disposal process: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to start disposal process.");
        }
        return "redirect:/admin/disposal";
    }

    @PostMapping("/{requestId}/complete")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_EXECUTE', 'ADMIN', 'SUPER_ADMIN')")
    public String completeDisposalProcess(@PathVariable Long requestId,
                                          @RequestParam String completionNotes,
                                          @RequestParam String disposalMethod,
                                          @RequestParam(defaultValue = "false") boolean dataWipeConfirmed,
                                          RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            log.info("Completing disposal process for request: {} by user: {}", requestId, currentUser.getUserId());

            disposalService.completeDisposalProcess(requestId, currentUser.getUserId(), completionNotes, disposalMethod, dataWipeConfirmed);

            redirectAttributes.addFlashAttribute("success", "Disposal process completed successfully.");

        } catch (Exception e) {
            log.error("Error completing disposal process: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to complete disposal process.");
        }
        return "redirect:/admin/disposal";
    }

    @PostMapping("/{requestId}/confirm-data-wipe")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_EXECUTE', 'ADMIN', 'SUPER_ADMIN')")
    public String confirmDataWipe(@PathVariable Long requestId,
                                  RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            log.info("Confirming data wipe for request: {} by user: {}", requestId, currentUser.getUserId());

            disposalService.confirmDataWipe(requestId, currentUser.getUserId());

            redirectAttributes.addFlashAttribute("success", "Data wipe confirmed.");

        } catch (Exception e) {
            log.error("Error confirming data wipe: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to confirm data wipe.");
        }
        return "redirect:/admin/disposal";
    }

    // ============================================
    // REST API ENDPOINTS
    // ============================================

    @GetMapping("/api/pending")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('DISPOSAL_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<AssetDisposalRequest>> getPendingRequests() {
        return ResponseEntity.ok(disposalService.getPendingRequests());
    }

    @GetMapping("/api/ready-for-disposal")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('DISPOSAL_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<Asset>> getAssetsReadyForDisposal() {
        return ResponseEntity.ok(disposalService.getAssetsReadyForDisposal());
    }

    @GetMapping("/api/asset/{assetId}/pending")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('DISPOSAL_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> checkPendingRequest(@PathVariable Integer assetId) {
        log.info("Checking pending disposal request for asset: {}", assetId);

        boolean hasPending = disposalService.hasPendingRequest(assetId);
        Map<String, Object> response = new HashMap<>();
        response.put("hasPending", hasPending);

        if (hasPending) {
            AssetDisposalRequest pendingRequest = disposalService.getPendingRequestByAssetId(assetId);
            if (pendingRequest != null) {
                response.put("requestId", pendingRequest.getDisposalRequestId());
                response.put("status", pendingRequest.getStatus());
                response.put("requestedAt", pendingRequest.getRequestedAt());
            }
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/{requestId}")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('DISPOSAL_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<AssetDisposalRequest> getRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(disposalService.getRequestById(requestId));
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String viewDisposalRequest(@PathVariable Long requestId, Model model) {
        log.info("Viewing disposal request: {}", requestId);

        model.addAttribute("currentPage", "disposal-dashboard");
        model.addAttribute("pageTitle", "Disposal Mgt");

        AssetDisposalRequest request = disposalService.getRequestById(requestId);
        model.addAttribute("request", request);

        AppUser currentUser = SecurityUtils.getCurrentUser();
        model.addAttribute("canApprove", currentUser.hasPermission("DISPOSAL_APPROVE") || currentUser.isAdmin());
        model.addAttribute("canExecute", currentUser.hasPermission("DISPOSAL_EXECUTE") || currentUser.isAdmin());

        return "admin/disposal-request-detail";
    }
}