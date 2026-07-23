package com.stevecodes.AssetIQPro.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasAnyAuthority('ADMIN', 'SUPER_ADMIN', 'VIEW_REPORTS')")
public class AdminViewController {

    @GetMapping
    public String adminDashboard() {
        log.info("Loading admin dashboard page");
        return "admin/dashboard";
    }
}