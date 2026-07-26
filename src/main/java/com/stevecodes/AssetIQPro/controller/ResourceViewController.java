package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.ResourceRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.ResourceRequest;
import com.stevecodes.AssetIQPro.entity.StockCategory;
import com.stevecodes.AssetIQPro.entity.StockItem;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.ResourceRequestService;
import com.stevecodes.AssetIQPro.service.StockService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/resources")
public class ResourceViewController {

    private final ResourceRequestService resourceRequestService;
    private final AppUserService userService;
    private final StockService stockService;

    @PostMapping("/request")
    public String createResourceRequest(@RequestParam Long userId,
                                        @RequestParam String requestedBy,
                                        @RequestParam String resourceType,
                                        @RequestParam String description,
                                        @RequestParam(required = false, defaultValue = "1") Integer quantity,
                                        @RequestParam(required = false) String justification,
                                        @RequestParam(required = false) Long stockItemId,
                                        RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            if (!currentUser.hasAnyPermission("RESOURCE_REQUEST_CREATE", "ADMIN")) {
                throw new AccessDeniedException("You don't have permission to create resource requests.");
            }

            log.info("Creating resource request for user: {}, stockItemId: {}", userId, stockItemId);
            resourceRequestService.createResourceRequest(userId, requestedBy, description,
                    resourceType, quantity, justification, stockItemId);
            redirectAttributes.addFlashAttribute("success", "✅ Resource request created successfully!");
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create resource requests.");
        } catch (Exception e) {
            log.error("Error creating resource request: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "❌ Failed to create resource request: " + e.getMessage());
        }
        return "redirect:/resources/list";
    }

    @GetMapping("/list")
    public String listResourceRequests(Model model) {
        log.info("Displaying resource requests list");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        List<ResourceRequestDTO> allRequests = resourceRequestService.getAllResourceRequests();
        List<ResourceRequestDTO> pendingRequests = resourceRequestService.getPendingResourceRequests();
        List<ResourceRequestDTO> acceptedRequests = resourceRequestService.getAcceptedResourceRequests();
        List<ResourceRequestDTO> completedRequests = resourceRequestService.getCompletedResourceRequests();

        long pendingCount = resourceRequestService.countPendingRequests();
        long acceptedCount = resourceRequestService.countAcceptedRequests();
        long declinedCount = resourceRequestService.countDeclinedRequests();
        long completedCount = resourceRequestService.countCompletedRequests();

        model.addAttribute("resourceRequests", allRequests);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("acceptedRequests", acceptedRequests);
        model.addAttribute("completedRequests", completedRequests);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("acceptedCount", acceptedCount);
        model.addAttribute("declinedCount", declinedCount);
        model.addAttribute("completedCount", completedCount);

        model.addAttribute("canApprove", currentUser.hasAnyPermission("RESOURCE_REQUEST_APPROVE", "ADMIN"));
        model.addAttribute("canCreate", currentUser.hasAnyPermission("RESOURCE_REQUEST_CREATE", "ADMIN"));

        // ✅ Load categories and stock items for dropdowns
        List<StockCategory> categories = stockService.getAllCategories();
        log.info("Loaded {} categories for dropdown", categories != null ? categories.size() : 0);
        model.addAttribute("categories", categories);

        List<StockItem> stockItems = stockService.getAllStockItems();
        log.info("Loaded {} stock items for dropdown", stockItems != null ? stockItems.size() : 0);
        model.addAttribute("stockItems", stockItems);

        return "resources/list";
    }

    @GetMapping("/{id}")
    @ResponseBody
    public ResourceRequestDTO viewRequest(@PathVariable Long id) {
        log.info("Fetching resource request: {}", id);
        return resourceRequestService.getResourceRequestById(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        try {
            log.info("Downloading PDF for resource request: {}", id);

            ResourceRequestDTO request = resourceRequestService.getResourceRequestById(id);

            if (request.getPdfReportPath() == null) {
                log.warn("PDF not found for request: {}", id);
                return ResponseEntity.notFound().build();
            }

            Path pdfPath = Paths.get(request.getPdfReportPath());
            if (!Files.exists(pdfPath)) {
                log.warn("PDF file not found at path: {}", pdfPath);
                return ResponseEntity.notFound().build();
            }

            byte[] pdfBytes = Files.readAllBytes(pdfPath);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "resource-request-" + id + ".pdf");

            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            log.error("Error downloading PDF for request {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/sign")
    public String showSignPage(@RequestParam String token, Model model) {
        try {
            log.info("========== SIGN PAGE ACCESSED ==========");
            log.info("Token received: {}", token);

            ResourceRequest request = resourceRequestService.getResourceRequestBySigningToken(token);

            if (request == null) {
                log.error("No request found for token: {}", token);
                model.addAttribute("error", "Invalid signing link. Please contact your administrator.");
                return "resources/sign-error";
            }

            log.info("Request found: ID={}, Status={}", request.getRequestId(), request.getStatus());

            if (request.getSigningTokenExpiry() != null &&
                    request.getSigningTokenExpiry().isBefore(LocalDateTime.now())) {
                log.error("Token expired for request: {}", request.getRequestId());
                model.addAttribute("error", "This signing link has expired. Please request a new one from the administrator.");
                return "resources/sign-error";
            }

            if (request.getRequesterSignature() != null && !request.getRequesterSignature().isEmpty()) {
                log.info("Request {} already signed", request.getRequestId());
                model.addAttribute("alreadySigned", true);
                model.addAttribute("request", request);
                return "resources/sign";
            }

            String requesterName = userService.getUserById(request.getUserId())
                    .map(user -> user.getFullName())
                    .orElse("Unknown");

            model.addAttribute("request", request);
            model.addAttribute("requesterName", requesterName);
            model.addAttribute("token", token);
            model.addAttribute("alreadySigned", false);

            log.info("Sign page rendered successfully for request: {}", request.getRequestId());
            return "resources/sign";
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage(), e);
            model.addAttribute("error", "Invalid or expired signing link: " + e.getMessage());
            return "resources/sign-error";
        }
    }

    @PostMapping("/sign")
    public String submitSignature(@RequestParam Long requestId,
                                  @RequestParam String token,
                                  @RequestParam String signature,
                                  RedirectAttributes redirectAttributes) {
        try {
            log.info("Submitting signature for request: {}, token: {}", requestId, token);

            ResourceRequest request = resourceRequestService.getResourceRequestEntityById(requestId);
            String signatoryName = userService.getUserById(request.getUserId())
                    .map(user -> user.getFullName())
                    .orElse("Unknown");

            log.info("Signatory name auto-captured: {}", signatoryName);

            resourceRequestService.saveRequesterSignature(requestId, token, signature, signatoryName);

            redirectAttributes.addFlashAttribute("message", "Thank you! Request completed successfully.");
            return "redirect:/resources/sign-thankyou";
        } catch (Exception e) {
            log.error("Error submitting signature: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to submit signature: " + e.getMessage());
            return "redirect:/resources/sign-error";
        }
    }

    @PostMapping("/{id}/resend-link")
    public String resendSigningLink(@PathVariable Long id,
                                    RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            if (!currentUser.hasAnyPermission("RESOURCE_REQUEST_APPROVE", "ADMIN")) {
                throw new AccessDeniedException("You don't have permission to resend signing links.");
            }

            log.info("Resending signing link for request: {}", id);
            resourceRequestService.resendSigningLink(id);
            redirectAttributes.addFlashAttribute("success", "✅ Signing link resent successfully!");
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to resend signing links.");
        } catch (Exception e) {
            log.error("Error resending signing link: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "❌ Failed to resend signing link: " + e.getMessage());
        }
        return "redirect:/resources/list";
    }

    @GetMapping("/sign-thankyou")
    public String signThankyou() {
        return "resources/sign-thankyou";
    }

    @GetMapping("/sign-error")
    public String signError() {
        return "resources/sign-error";
    }
}