package com.stevecodes.AssetIQPro.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BaseUrlService {

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.context-path:/assetIQ-pro}")
    private String contextPath;

    public String getFullBaseUrl() {
        return baseUrl + contextPath;
    }

    public String buildUrl(String path) {
        return getFullBaseUrl() + (path.startsWith("/") ? path : "/" + path);
    }

    // ✅ Fixed: Supports multiple parameters with varargs
    public String buildUrl(String path, Object... params) {
        String fullPath = path.startsWith("/") ? path : "/" + path;
        String formattedPath = String.format(fullPath, params);
        return getFullBaseUrl() + formattedPath;
    }
}