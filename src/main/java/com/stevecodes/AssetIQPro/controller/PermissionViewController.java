package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.service.AppUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/permissions")
public class PermissionViewController {

    private final AppUserService userService;

    @GetMapping
    public String permissions(Model model) {
        log.info("Loading permissions page");
        model.addAttribute("users", userService.getAllUsers());
        return "admin/permissions";
    }
}