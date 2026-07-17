package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
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

        List<InfraRequestDTO> requests = requestService.getAllRequests();
        model.addAttribute("requests", requests);

        List<InfraRequestDTO> pendingLMRequests = requestService.getRequestsByStatus(InfraRequest.RequestStatus.PENDING_LM_APPROVAL);
        List<InfraRequestDTO> pendingRequests = requestService.getRequestsByStatus(InfraRequest.RequestStatus.PENDING_INFRA_REVIEW);
        List<InfraRequestDTO> pendingFinanceRequests = requestService.getFinanceRequests();

        model.addAttribute("pendingLMRequests", pendingLMRequests);
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingFinanceRequests", pendingFinanceRequests);

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

    @GetMapping("/sign")
    public String showSignPage(@RequestParam String token, Model model) {
        try {
            log.info("Sign page accessed with token: {}", token);
            InfraRequest request = requestService.getRequestBySigningToken(token);

            // Get requester name
            String requesterName = userService.getUserById(request.getRequesterId())
                    .map(user -> user.getFullName())
                    .orElse("Unknown");

            model.addAttribute("request", request);
            model.addAttribute("requesterName", requesterName);
            model.addAttribute("token", token);
            model.addAttribute("alreadySigned", request.isSigned());

            return "infra-requests/sign";
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage(), e);
            model.addAttribute("error", "Invalid or expired signing link: " + e.getMessage());
            return "infra-requests/sign-error";
        }
    }

    @PostMapping("/sign")
    public String submitSignature(@RequestParam Long requestId,
                                  @RequestParam String token,
                                  @RequestParam String signature,
                                  RedirectAttributes redirectAttributes) {
        try {
            log.info("Submitting signature for request: {}, token: {}", requestId, token);
            requestService.saveRequesterSignature(requestId, token, signature);
            redirectAttributes.addFlashAttribute("message", "Thank you! Request completed successfully.");
            return "redirect:/infra-requests/sign-thankyou";
        } catch (Exception e) {
            log.error("Error submitting signature: {}", e.getMessage());
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

        List<InfraRequestDTO> pendingRequests = requestService.getRequestsByStatus(InfraRequest.RequestStatus.PENDING_INFRA_REVIEW);
        List<InfraRequestDTO> pendingLMRequests = requestService.getRequestsByStatus(InfraRequest.RequestStatus.PENDING_LM_APPROVAL);
        List<InfraRequestDTO> pendingFinanceRequests = requestService.getFinanceRequests();

        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingLMRequests", pendingLMRequests);
        model.addAttribute("pendingFinanceRequests", pendingFinanceRequests);

        return "infra-requests/dashboard";
    }
}