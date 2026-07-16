package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.InfraRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

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

        // All requests
        List<InfraRequestDTO> requests = requestService.getAllRequests();
        model.addAttribute("requests", requests);

        // Pending by status
        List<InfraRequestDTO> pendingLMRequests = requestService.getRequestsByStatus(InfraRequest.RequestStatus.PENDING_LM_APPROVAL);
        List<InfraRequestDTO> pendingRequests = requestService.getRequestsByStatus(InfraRequest.RequestStatus.PENDING_INFRA_REVIEW);
        List<InfraRequestDTO> pendingFinanceRequests = requestService.getRequestsByStatus(InfraRequest.RequestStatus.PENDING_FINANCE_APPROVAL);

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
}