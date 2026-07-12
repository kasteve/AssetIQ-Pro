package com.stevecodes.AssetIQPro.config;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.service.AppUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private AppUserService userService;

    @ModelAttribute
    public void addUserAttributes(HttpSession session, org.springframework.ui.Model model) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId != null) {
            userService.getUserById(userId).ifPresent(user -> {
                model.addAttribute("isAdmin", user.isAdmin());
                model.addAttribute("canManageUsers", user.canManageUsers());
                model.addAttribute("canManageRoles", user.canManageRoles());
                model.addAttribute("canViewAudit", user.canViewAudit());
                model.addAttribute("canManageBookings", user.canManageBookings());
                model.addAttribute("canManageDriverRequests", user.canManageDriverRequests());
                model.addAttribute("canReviewInfra", user.canReviewInfra());
                model.addAttribute("canManageResources", user.canManageResources());
            });
        }
    }
}