package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/theme")
@RequiredArgsConstructor
public class ThemeController {

    private final SystemSettingService settingService;

    @GetMapping("/default")
    public ResponseEntity<Map<String, String>> getDefaultTheme() {
        Map<String, String> response = new HashMap<>();
        String defaultTheme = settingService.getString(SystemSettingService.KEY_THEME_DEFAULT);
        response.put("theme", defaultTheme != null ? defaultTheme : "light");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/presets")
    public ResponseEntity<List<String>> getThemePresets() {
        List<String> presets = settingService.getStringList(SystemSettingService.KEY_THEME_PRESETS);
        if (presets.isEmpty()) {
            presets = List.of("light", "dark", "blue", "green", "purple", "system");
        }
        return ResponseEntity.ok(presets);
    }

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getThemeSettings() {
        Map<String, Object> response = new HashMap<>();
        response.put("defaultTheme", settingService.getString(SystemSettingService.KEY_THEME_DEFAULT));
        response.put("allowCustomization", settingService.getBoolean(SystemSettingService.KEY_THEME_ALLOW_CUSTOM));
        response.put("presets", settingService.getStringList(SystemSettingService.KEY_THEME_PRESETS));
        return ResponseEntity.ok(response);
    }
}