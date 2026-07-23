package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final SystemSettingService settingService;

    @ModelAttribute
    public void addThemeSettings(Model model) {
        Map<String, String> themeSettings = settingService.getSettingsMapByCategory(SystemSettingService.CATEGORY_THEME);
        model.addAttribute("themeSettings", themeSettings);
    }

    @ModelAttribute
    public void addUserPermissions(Model model) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null) {
            model.addAttribute("currentUser", currentUser);
            model.addAttribute("isAdmin", currentUser.isAdmin());
            model.addAttribute("canManageUsers", currentUser.canManageUsers());
            model.addAttribute("canManageBookings", currentUser.canManageBookings());
            model.addAttribute("canManageConfig", currentUser.canManageConfig());
            model.addAttribute("canManageRoles", currentUser.canManageRoles());
            model.addAttribute("canViewAudit", currentUser.canViewAudit());
            model.addAttribute("canManageAssets", currentUser.canManageAssets());
            model.addAttribute("canViewTransfers", currentUser.canViewTransfers());
            model.addAttribute("canCreateTransfers", currentUser.canCreateTransfers());
            model.addAttribute("isDriver", currentUser.isDriver());
            model.addAttribute("isInfrastructure", currentUser.isInfrastructure());
            model.addAttribute("isFinance", currentUser.isFinance());
        }
    }
}