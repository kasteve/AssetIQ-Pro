package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.InfraRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/infra-requests")
public class InfraRequestViewController {

    private final InfraRequestService requestService;
    private final AppUserService userService;

    @GetMapping
    public String infraRequests(Model model) {
        log.info("Loading infrastructure requests page");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        Long userId = currentUser.getUserId();
        log.info("Current user ID: {}, Role: {}", userId, currentUser.getRole());

        List<InfraRequestDTO> allRequests = requestService.getAllRequests();
        List<InfraRequestDTO> filteredRequests;

        if (currentUser.isAdmin()) {
            filteredRequests = allRequests;
            log.info("Admin user viewing all {} infra requests", filteredRequests.size());
        } else {
            filteredRequests = allRequests.stream()
                    .filter(r -> {
                        if (r.getRequesterId() != null && r.getRequesterId().equals(userId)) {
                            return true;
                        }
                        if (r.getLineManagerId() != null && r.getLineManagerId().equals(userId)) {
                            return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            log.info("Regular user {} viewing {} infra requests (out of {} total)",
                    currentUser.getUsername(), filteredRequests.size(), allRequests.size());
        }

        model.addAttribute("requests", filteredRequests);

        List<InfraRequestDTO> pendingLMRequests = filteredRequests.stream()
                .filter(r -> InfraRequest.RequestStatus.PENDING_LM_APPROVAL.name().equals(r.getStatus()))
                .collect(Collectors.toList());

        List<InfraRequestDTO> pendingRequests = filteredRequests.stream()
                .filter(r -> InfraRequest.RequestStatus.PENDING_INFRA_REVIEW.name().equals(r.getStatus()))
                .collect(Collectors.toList());

        List<InfraRequestDTO> pendingFinanceRequests = filteredRequests.stream()
                .filter(r -> InfraRequest.RequestStatus.PENDING_FINANCE_APPROVAL.name().equals(r.getStatus()) ||
                        InfraRequest.RequestStatus.PROCUREMENT.name().equals(r.getStatus()) ||
                        InfraRequest.RequestStatus.DELIVERED.name().equals(r.getStatus()))
                .collect(Collectors.toList());

        model.addAttribute("pendingLMRequests", pendingLMRequests);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingFinanceRequests", pendingFinanceRequests);

        model.addAttribute("canApproveLM", currentUser.hasAnyPermission("APPROVE_LM", "ADMIN"));
        model.addAttribute("canApproveInfra", currentUser.hasAnyPermission("APPROVE_INFRA", "REVIEW_INFRA", "ADMIN"));
        model.addAttribute("canApproveFinance", currentUser.hasAnyPermission("APPROVE_FINANCE", "ADMIN"));
        model.addAttribute("canCreate", currentUser.hasAnyPermission("INFRA_REQUEST_CREATE", "ADMIN"));

        return "infra-requests/list";
    }

    @GetMapping("/{id}")
    @ResponseBody
    public InfraRequestDTO viewRequest(@PathVariable Long id) {
        log.info("Fetching infrastructure request: {}", id);
        return requestService.getRequestById(id);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        try {
            log.info("Downloading PDF for infrastructure request: {}", id);

            InfraRequestDTO request = requestService.getRequestById(id);

            if (request.getPdfReportPath() == null) {
                log.warn("PDF not found for request: {}", id);
                return ResponseEntity.notFound().build();
            }

            Path pdfPath = Paths.get(request.getPdfReportPath());
            byte[] pdfBytes = Files.readAllBytes(pdfPath);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "infra-request-" + id + ".pdf");

            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            log.error("Error downloading PDF for request {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Show the signing page
     */
    @GetMapping("/sign")
    public String showSignPage(@RequestParam String token, Model model) {
        try {
            log.info("=== SIGN PAGE ACCESSED ===");
            log.info("Token: {}", token);

            InfraRequest request = requestService.getRequestBySigningToken(token);
            log.info("Request found: ID={}, Status={}", request.getRequestId(), request.getStatus());

            // Check if already signed
            if (request.isSigned()) {
                log.info("Request {} already signed on {}", request.getRequestId(), request.getRequesterSignedAt());
                model.addAttribute("alreadySigned", true);
                model.addAttribute("signedAt", request.getRequesterSignedAt());
            }

            String requesterName = userService.getUserById(request.getRequesterId())
                    .map(user -> user.getFullName())
                    .orElse("Unknown");

            model.addAttribute("request", request);
            model.addAttribute("requesterName", requesterName);
            model.addAttribute("token", token);
            model.addAttribute("alreadySigned", request.isSigned());

            return "infra-requests/sign";

        } catch (Exception e) {
            log.error("❌ Error validating token: {}", e.getMessage(), e);
            model.addAttribute("error", "Invalid or expired signing link: " + e.getMessage());
            return "infra-requests/sign-error";
        }
    }

    /**
     * Submit signature - FIXED: Better handling of signature data
     */
    @PostMapping("/sign")
    public String submitSignature(@RequestParam Long requestId,
                                  @RequestParam String token,
                                  @RequestParam String signature,
                                  RedirectAttributes redirectAttributes) {
        try {
            log.info("=== SUBMIT SIGNATURE START ===");
            log.info("Request ID: {}, Token: {}", requestId, token);
            log.info("Signature length: {}", signature != null ? signature.length() : 0);

            // Validate signature is not empty
            if (signature == null || signature.trim().isEmpty()) {
                log.error("❌ Signature is empty!");
                redirectAttributes.addFlashAttribute("error", "Please provide a signature.");
                return "redirect:/infra-requests/sign-error";
            }

            // Validate signature is not the empty canvas
            if (signature.length() < 100) {
                log.error("❌ Signature too short! Length: {}", signature.length());
                redirectAttributes.addFlashAttribute("error", "Invalid signature. Please draw your signature again.");
                return "redirect:/infra-requests/sign-error";
            }

            // Save the signature
            requestService.saveRequesterSignature(requestId, token, signature);

            log.info("✅ Signature saved successfully for request: {}", requestId);
            redirectAttributes.addFlashAttribute("message", "Thank you! Your signature has been recorded and the request is complete.");
            return "redirect:/infra-requests/sign-thankyou";

        } catch (IllegalStateException e) {
            log.warn("⚠️ {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/infra-requests/sign-error";
        } catch (Exception e) {
            log.error("❌ Error submitting signature: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to submit signature: " + e.getMessage());
            return "redirect:/infra-requests/sign-error";
        }
    }

    @GetMapping("/sign-thankyou")
    public String signThankyou() {
        return "infra-requests/sign-thankyou";
    }

    @GetMapping("/sign-error")
    public String signError() {
        return "infra-requests/sign-error";
    }

    @GetMapping("/dashboard")
    public String infraDashboard(Model model) {
        log.info("Loading infrastructure dashboard");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        Long userId = currentUser.getUserId();

        List<InfraRequestDTO> allRequests = requestService.getAllRequests();
        List<InfraRequestDTO> filteredRequests;

        if (currentUser.isAdmin()) {
            filteredRequests = allRequests;
        } else {
            filteredRequests = allRequests.stream()
                    .filter(r -> {
                        if (r.getRequesterId() != null && r.getRequesterId().equals(userId)) {
                            return true;
                        }
                        if (r.getLineManagerId() != null && r.getLineManagerId().equals(userId)) {
                            return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }

        List<InfraRequestDTO> pendingRequests = filteredRequests.stream()
                .filter(r -> InfraRequest.RequestStatus.PENDING_INFRA_REVIEW.name().equals(r.getStatus()))
                .collect(Collectors.toList());

        List<InfraRequestDTO> pendingLMRequests = filteredRequests.stream()
                .filter(r -> InfraRequest.RequestStatus.PENDING_LM_APPROVAL.name().equals(r.getStatus()))
                .collect(Collectors.toList());

        List<InfraRequestDTO> pendingFinanceRequests = filteredRequests.stream()
                .filter(r -> InfraRequest.RequestStatus.PENDING_FINANCE_APPROVAL.name().equals(r.getStatus()) ||
                        InfraRequest.RequestStatus.PROCUREMENT.name().equals(r.getStatus()) ||
                        InfraRequest.RequestStatus.DELIVERED.name().equals(r.getStatus()))
                .collect(Collectors.toList());

        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingLMRequests", pendingLMRequests);
        model.addAttribute("pendingFinanceRequests", pendingFinanceRequests);

        model.addAttribute("canApproveLM", currentUser.hasAnyPermission("APPROVE_LM", "ADMIN"));
        model.addAttribute("canApproveInfra", currentUser.hasAnyPermission("APPROVE_INFRA", "REVIEW_INFRA", "ADMIN"));
        model.addAttribute("canApproveFinance", currentUser.hasAnyPermission("APPROVE_FINANCE", "ADMIN"));

        return "infra-requests/dashboard";
    }
}