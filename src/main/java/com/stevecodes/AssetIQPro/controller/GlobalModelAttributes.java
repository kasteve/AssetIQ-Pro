package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/**
 * Injects theme settings into every Thymeleaf view's model, so layouts/default.html
 * (and any child template) can read them without each controller having to fetch
 * and add them manually.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final SystemSettingService settingService;

    @ModelAttribute
    public void addThemeSettings(Model model) {
        Map<String, String> themeSettings = settingService.getSettingsMapByCategory(SystemSettingService.CATEGORY_THEME);
        model.addAttribute("themeSettings", themeSettings);
    }
}