package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
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

    private final InfraRequestService requestService;
    private final AppUserService userService;

    @GetMapping
    public String infraRequests(Model model) {
        log.info("Loading infrastructure requests page");

        // Get all requests as DTOs
        List<InfraRequestDTO> requests = requestService.getAllRequests();
        model.addAttribute("requests", requests);

        // Get users who can be line managers
        List<AppUser> lineManagers = userService.getUsersWithPermission("APPROVE_LM");
        model.addAttribute("lineManagers", lineManagers);

        return "infra-requests/list";
    }
}