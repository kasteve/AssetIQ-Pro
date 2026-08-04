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
import org.springframework.web.multipart.MultipartFile;
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
        log.info("=== LOADING DISPOSAL DASHBOARD ===");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        try {
            // Get all requests by status
            List<AssetDisposalRequest> pendingFinance = disposalService.getPendingFinanceRequests();
            List<AssetDisposalRequest> pendingInfra = disposalService.getPendingInfraRequests();
            List<AssetDisposalRequest> pendingCompliance = disposalService.getPendingComplianceRequests();
            List<AssetDisposalRequest> pendingExecution = disposalService.getPendingExecutionRequests();
            List<AssetDisposalRequest> completedRequests = disposalService.getRequestsByStatus("COMPLETED");
            List<AssetDisposalRequest> rejectedRequests = disposalService.getRequestsByStatus("REJECTED");

            // Calculate total pending
            int totalPending = pendingFinance.size() + pendingInfra.size() +
                    pendingCompliance.size() + pendingExecution.size();

            // Get assets ready for disposal
            List<Asset> readyForDisposal = disposalService.getAssetsReadyForDisposal();

            // Get user's requests
            List<AssetDisposalRequest> myRequests = disposalService.getRequestsByRequester(currentUser.getUserId());

            // Add to model
            model.addAttribute("pendingFinance", pendingFinance);
            model.addAttribute("pendingInfra", pendingInfra);
            model.addAttribute("pendingCompliance", pendingCompliance);
            model.addAttribute("pendingExecution", pendingExecution);
            model.addAttribute("completedRequests", completedRequests);
            model.addAttribute("rejectedRequests", rejectedRequests);
            model.addAttribute("totalPending", totalPending);
            model.addAttribute("readyForDisposal", readyForDisposal);
            model.addAttribute("myRequests", myRequests);

            // Permissions
            model.addAttribute("canApproveFinance",
                    currentUser.hasPermission("DISPOSAL_FINANCE_APPROVE") || currentUser.isAdmin());
            model.addAttribute("canApproveInfra",
                    currentUser.hasPermission("DISPOSAL_INFRA_APPROVE") || currentUser.isAdmin());
            model.addAttribute("canApproveCompliance",
                    currentUser.hasPermission("DISPOSAL_COMPLIANCE_APPROVE") || currentUser.isAdmin());
            model.addAttribute("canExecute",
                    currentUser.hasPermission("DISPOSAL_EXECUTE") || currentUser.isAdmin());
            model.addAttribute("canRequest",
                    currentUser.hasPermission("DISPOSAL_REQUEST") || currentUser.isAdmin());

            model.addAttribute("currentPage", "disposal-dashboard");
            model.addAttribute("pageTitle", "Disposal Management");

            log.info("✅ Disposal dashboard loaded successfully");
            log.info("📊 Pending: Finance={}, Infra={}, Compliance={}, Execution={}",
                    pendingFinance.size(), pendingInfra.size(), pendingCompliance.size(), pendingExecution.size());

        } catch (Exception e) {
            log.error("❌ Error loading disposal dashboard: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load disposal dashboard");
        }

        return "admin/disposal-dashboard";
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String viewDisposalRequest(@PathVariable Long requestId, Model model) {
        log.info("📋 Viewing disposal request: {}", requestId);

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                return "redirect:/login";
            }

            AssetDisposalRequest request = disposalService.getRequestById(requestId);
            model.addAttribute("request", request);

            // Permissions
            model.addAttribute("canApproveFinance",
                    currentUser.hasPermission("DISPOSAL_FINANCE_APPROVE") || currentUser.isAdmin());
            model.addAttribute("canApproveInfra",
                    currentUser.hasPermission("DISPOSAL_INFRA_APPROVE") || currentUser.isAdmin());
            model.addAttribute("canApproveCompliance",
                    currentUser.hasPermission("DISPOSAL_COMPLIANCE_APPROVE") || currentUser.isAdmin());
            model.addAttribute("canExecute",
                    currentUser.hasPermission("DISPOSAL_EXECUTE") || currentUser.isAdmin());

            model.addAttribute("currentPage", "disposal-dashboard");
            model.addAttribute("pageTitle", "Disposal Request #" + requestId);

        } catch (Exception e) {
            log.error("❌ Error viewing disposal request: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load disposal request");
        }

        return "admin/disposal-request-detail";
    }

    // ============================================
    // CREATE DISPOSAL REQUEST
    // ============================================

    @PostMapping("/request")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_REQUEST', 'ADMIN', 'SUPER_ADMIN')")
    public String createDisposalRequest(@RequestParam Integer assetId,
                                        @RequestParam String disposalReason,
                                        @RequestParam String disposalMethod,
                                        @RequestParam(required = false) String priority,
                                        RedirectAttributes redirectAttributes) {
        log.info("=== CREATE DISPOSAL REQUEST ===");
        log.info("📝 Asset: {}, Reason: {}, Method: {}", assetId, disposalReason, disposalMethod);

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            Asset asset = assetService.getAssetEntityById(assetId);
            disposalService.createDisposalRequest(assetId, currentUser.getUserId(),
                    disposalReason, disposalMethod, priority);

            redirectAttributes.addFlashAttribute("success",
                    "Disposal request created successfully for asset: " + asset.getTag());

        } catch (IllegalStateException e) {
            log.error("❌ Error creating disposal request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error creating disposal request: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to create disposal request.");
        }

        return "redirect:/assets";
    }

    // ============================================
    // FINANCE APPROVAL ENDPOINTS
    // ============================================

    @PostMapping("/{requestId}/finance/approve")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_FINANCE_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String approveByFinance(@PathVariable Long requestId,
                                   @RequestParam(required = false) String comment,
                                   RedirectAttributes redirectAttributes) {
        log.info("=== FINANCE APPROVE ===");
        log.info("📝 Request: {}, Comment: {}", requestId, comment);

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            disposalService.approveByFinance(requestId, currentUser.getUserId(), comment);

            redirectAttributes.addFlashAttribute("success",
                    "Disposal request approved by Finance successfully.");

        } catch (IllegalStateException e) {
            log.error("❌ Error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error approving by Finance: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to approve by Finance.");
        }

        return "redirect:/admin/disposal";
    }

    @PostMapping("/{requestId}/finance/reject")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_FINANCE_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String rejectByFinance(@PathVariable Long requestId,
                                  @RequestParam String reason,
                                  RedirectAttributes redirectAttributes) {
        log.info("=== FINANCE REJECT ===");
        log.info("📝 Request: {}, Reason: {}", requestId, reason);

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            disposalService.rejectByFinance(requestId, currentUser.getUserId(), reason);

            redirectAttributes.addFlashAttribute("success",
                    "Disposal request rejected by Finance.");

        } catch (IllegalStateException e) {
            log.error("❌ Error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error rejecting by Finance: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to reject by Finance.");
        }

        return "redirect:/admin/disposal";
    }

    // ============================================
    // INFRASTRUCTURE APPROVAL ENDPOINTS
    // ============================================

    @PostMapping("/{requestId}/infra/approve")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_INFRA_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String approveByInfrastructure(@PathVariable Long requestId,
                                          @RequestParam(required = false) String comment,
                                          @RequestParam(required = false) MultipartFile policyFile,
                                          RedirectAttributes redirectAttributes) {
        log.info("=== INFRASTRUCTURE APPROVE ===");
        log.info("📝 Request: {}, Comment: {}, Policy File: {}",
                requestId, comment, policyFile != null ? policyFile.getOriginalFilename() : "None");

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            disposalService.approveByInfrastructure(requestId, currentUser.getUserId(), comment, policyFile);

            redirectAttributes.addFlashAttribute("success",
                    "Disposal request approved by Infrastructure successfully.");

        } catch (IllegalStateException e) {
            log.error("❌ Error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error approving by Infrastructure: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to approve by Infrastructure.");
        }

        return "redirect:/admin/disposal";
    }

    @PostMapping("/{requestId}/infra/reject")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_INFRA_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String rejectByInfrastructure(@PathVariable Long requestId,
                                         @RequestParam String reason,
                                         RedirectAttributes redirectAttributes) {
        log.info("=== INFRASTRUCTURE REJECT ===");
        log.info("📝 Request: {}, Reason: {}", requestId, reason);

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            disposalService.rejectByInfrastructure(requestId, currentUser.getUserId(), reason);

            redirectAttributes.addFlashAttribute("success",
                    "Disposal request rejected by Infrastructure.");

        } catch (IllegalStateException e) {
            log.error("❌ Error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error rejecting by Infrastructure: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to reject by Infrastructure.");
        }

        return "redirect:/admin/disposal";
    }

    // ============================================
    // RISK & COMPLIANCE APPROVAL ENDPOINTS
    // ============================================

    @PostMapping("/{requestId}/compliance/approve")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_COMPLIANCE_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String approveByCompliance(@PathVariable Long requestId,
                                      @RequestParam(required = false) String comment,
                                      RedirectAttributes redirectAttributes) {
        log.info("=== COMPLIANCE APPROVE ===");
        log.info("📝 Request: {}, Comment: {}", requestId, comment);

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            disposalService.approveByCompliance(requestId, currentUser.getUserId(), comment);

            redirectAttributes.addFlashAttribute("success",
                    "Disposal request approved by Risk & Compliance successfully.");

        } catch (IllegalStateException e) {
            log.error("❌ Error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error approving by Compliance: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to approve by Compliance.");
        }

        return "redirect:/admin/disposal";
    }

    @PostMapping("/{requestId}/compliance/reject")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_COMPLIANCE_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String rejectByCompliance(@PathVariable Long requestId,
                                     @RequestParam String reason,
                                     RedirectAttributes redirectAttributes) {
        log.info("=== COMPLIANCE REJECT ===");
        log.info("📝 Request: {}, Reason: {}", requestId, reason);

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            disposalService.rejectByCompliance(requestId, currentUser.getUserId(), reason);

            redirectAttributes.addFlashAttribute("success",
                    "Disposal request rejected by Risk & Compliance.");

        } catch (IllegalStateException e) {
            log.error("❌ Error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error rejecting by Compliance: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to reject by Compliance.");
        }

        return "redirect:/admin/disposal";
    }

    // ============================================
    // EXECUTION ENDPOINTS
    // ============================================

    @PostMapping("/{requestId}/execute")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_EXECUTE', 'ADMIN', 'SUPER_ADMIN')")
    public String executeDisposal(@PathVariable Long requestId,
                                  @RequestParam String completionNotes,
                                  @RequestParam(required = false) MultipartFile proofDocument,
                                  RedirectAttributes redirectAttributes) {
        log.info("=== EXECUTE DISPOSAL ===");
        log.info("📝 Request: {}, Notes: {}, Proof: {}",
                requestId, completionNotes, proofDocument != null ? proofDocument.getOriginalFilename() : "None");

        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in.");
                return "redirect:/login";
            }

            disposalService.executeDisposal(requestId, currentUser.getUserId(), completionNotes, proofDocument);

            redirectAttributes.addFlashAttribute("success",
                    "Disposal executed successfully! Asset has been marked as DISPOSED.");

        } catch (IllegalStateException e) {
            log.error("❌ Error: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error executing disposal: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to execute disposal.");
        }

        return "redirect:/admin/disposal";
    }

    // ============================================
    // LEGACY ENDPOINTS (Keep for backward compatibility)
    // ============================================

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String approveDisposalRequest(@PathVariable Long requestId,
                                         @RequestParam(required = false) String comment,
                                         @RequestParam(required = false) String approvalReference,
                                         RedirectAttributes redirectAttributes) {
        log.info("⚠️ Legacy approve endpoint called for request: {}", requestId);
        // Redirect to the new workflow
        redirectAttributes.addFlashAttribute("warning",
                "Please use the new approval workflow. This request will be sent to Finance.");
        return "redirect:/admin/disposal/" + requestId;
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_APPROVE', 'ADMIN', 'SUPER_ADMIN')")
    public String rejectDisposalRequest(@PathVariable Long requestId,
                                        @RequestParam String reason,
                                        RedirectAttributes redirectAttributes) {
        log.info("⚠️ Legacy reject endpoint called for request: {}", requestId);
        redirectAttributes.addFlashAttribute("warning",
                "Please use the new approval workflow. This request will be sent to Finance.");
        return "redirect:/admin/disposal/" + requestId;
    }

    @PostMapping("/{requestId}/start")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_EXECUTE', 'ADMIN', 'SUPER_ADMIN')")
    public String startDisposalProcess(@PathVariable Long requestId,
                                       RedirectAttributes redirectAttributes) {
        log.info("⚠️ Legacy start endpoint called for request: {}", requestId);
        redirectAttributes.addFlashAttribute("warning",
                "Please use the new approval workflow.");
        return "redirect:/admin/disposal/" + requestId;
    }

    @PostMapping("/{requestId}/complete")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_EXECUTE', 'ADMIN', 'SUPER_ADMIN')")
    public String completeDisposalProcess(@PathVariable Long requestId,
                                          @RequestParam String completionNotes,
                                          @RequestParam String disposalMethod,
                                          @RequestParam(defaultValue = "false") boolean dataWipeConfirmed,
                                          RedirectAttributes redirectAttributes) {
        log.info("⚠️ Legacy complete endpoint called for request: {}", requestId);
        redirectAttributes.addFlashAttribute("warning",
                "Please use the new approval workflow.");
        return "redirect:/admin/disposal/" + requestId;
    }

    @PostMapping("/{requestId}/confirm-data-wipe")
    @PreAuthorize("hasAnyAuthority('DISPOSAL_EXECUTE', 'ADMIN', 'SUPER_ADMIN')")
    public String confirmDataWipe(@PathVariable Long requestId,
                                  RedirectAttributes redirectAttributes) {
        log.info("⚠️ Legacy data wipe endpoint called for request: {}", requestId);
        redirectAttributes.addFlashAttribute("warning",
                "Please use the new approval workflow.");
        return "redirect:/admin/disposal/" + requestId;
    }

    // ============================================
    // REST API ENDPOINTS
    // ============================================

    @GetMapping("/api/pending")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('DISPOSAL_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, List<AssetDisposalRequest>>> getPendingRequests() {
        Map<String, List<AssetDisposalRequest>> response = new HashMap<>();
        response.put("finance", disposalService.getPendingFinanceRequests());
        response.put("infrastructure", disposalService.getPendingInfraRequests());
        response.put("compliance", disposalService.getPendingComplianceRequests());
        response.put("execution", disposalService.getPendingExecutionRequests());
        return ResponseEntity.ok(response);
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
        log.info("📋 Checking pending disposal request for asset: {}", assetId);

        boolean hasPending = disposalService.hasPendingRequest(assetId);
        Map<String, Object> response = new HashMap<>();
        response.put("hasPending", hasPending);

        if (hasPending) {
            AssetDisposalRequest pendingRequest = disposalService.getPendingRequestByAssetId(assetId);
            if (pendingRequest != null) {
                response.put("requestId", pendingRequest.getDisposalRequestId());
                response.put("status", pendingRequest.getStatus());
                response.put("currentStep", pendingRequest.getCurrentApprovalStep());
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

    @GetMapping("/api/status/{status}")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('DISPOSAL_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<AssetDisposalRequest>> getRequestsByStatus(@PathVariable String status) {
        return ResponseEntity.ok(disposalService.getRequestsByStatus(status));
    }
}