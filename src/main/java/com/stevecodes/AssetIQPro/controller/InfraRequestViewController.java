package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.repository.InfraRequestRepository;
import com.stevecodes.AssetIQPro.service.AppUserService;
import com.stevecodes.AssetIQPro.service.InfraRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/infra-requests")
public class InfraRequestViewController {

    private final InfraRequestRepository requestRepository;
    private final AppUserService userService;
    private final InfraRequestService requestService;

    @GetMapping
    public String infraRequests(Model model) {
        log.info("Loading infrastructure requests page");

        // Get all requests
        List<InfraRequest> requests = requestRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("requests", requests);

        // Get users who can be line managers (have APPROVE_LM permission)
        List<AppUser> lineManagers = userService.getUsersWithPermission("APPROVE_LM");
        model.addAttribute("lineManagers", lineManagers);

        return "infra-requests/list";
    }

    @GetMapping("/dashboard")
    public String infraDashboard(Model model) {
        log.info("Loading infrastructure dashboard");

        // Get pending requests for review
        List<InfraRequest> pendingRequests = requestRepository.findByStatus(InfraRequest.RequestStatus.PENDING_INFRA_REVIEW);
        List<InfraRequest> pendingLMRequests = requestRepository.findByStatus(InfraRequest.RequestStatus.PENDING_LM_APPROVAL);
        List<InfraRequest> pendingFinanceRequests = requestRepository.findByStatus(InfraRequest.RequestStatus.PENDING_FINANCE_APPROVAL);

        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingLMRequests", pendingLMRequests);
        model.addAttribute("pendingFinanceRequests", pendingFinanceRequests);

        return "infra-requests/dashboard";
    }
}