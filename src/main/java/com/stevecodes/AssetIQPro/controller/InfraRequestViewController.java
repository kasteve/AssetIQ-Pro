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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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