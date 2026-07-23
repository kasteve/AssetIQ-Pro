package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Company;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/companies")
@PreAuthorize("hasAnyAuthority('COMPANY_VIEW', 'ADMIN', 'SUPER_ADMIN')")
public class CompanyViewController {

    private final CompanyService companyService;

    @GetMapping
    public String companies(Model model) {
        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }
        model.addAttribute("companies", companyService.getAllCompanies());
        model.addAttribute("canEdit", currentUser.hasAnyPermission("COMPANY_VIEW", "ADMIN"));
        model.addAttribute("canDelete", currentUser.hasAnyPermission("COMPANY_VIEW", "ADMIN"));
        return "admin/companies";
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('COMPANY_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String createCompany(@RequestParam String name, @RequestParam(required = false) String description,
                                RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }
            Company company = new Company(name, description);
            companyService.createCompany(company);
            redirectAttributes.addFlashAttribute("success", "Company created successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create companies.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create company: " + e.getMessage());
        }
        return "redirect:/admin/companies";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyAuthority('COMPANY_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String updateCompany(@PathVariable Long id, @RequestParam String name,
                                @RequestParam(required = false) String description,
                                RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }
            companyService.updateCompany(id, name, description);
            redirectAttributes.addFlashAttribute("success", "Company updated successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to update companies.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update company: " + e.getMessage());
        }
        return "redirect:/admin/companies";
    }

    @GetMapping("/{id}/delete")
    @PreAuthorize("hasAnyAuthority('COMPANY_VIEW', 'ADMIN', 'SUPER_ADMIN')")
    public String deleteCompany(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }
            companyService.deleteCompany(id);
            redirectAttributes.addFlashAttribute("success", "Company deleted successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "You don't have permission to delete companies.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete company: " + e.getMessage());
        }
        return "redirect:/admin/companies";
    }
}