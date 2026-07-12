package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Company;
import com.stevecodes.AssetIQPro.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/companies")
public class CompanyViewController {

    private final CompanyService companyService;

    @GetMapping
    public String companies(Model model) {
        model.addAttribute("companies", companyService.getAllCompanies());
        return "admin/companies";
    }

    @PostMapping
    public String createCompany(@RequestParam String name, @RequestParam(required = false) String description,
                                RedirectAttributes redirectAttributes) {
        try {
            Company company = new Company(name, description);
            companyService.createCompany(company);
            redirectAttributes.addFlashAttribute("success", "Company created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create company: " + e.getMessage());
        }
        return "redirect:/admin/companies";
    }

    @GetMapping("/{id}/delete")
    public String deleteCompany(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            companyService.deleteCompany(id);
            redirectAttributes.addFlashAttribute("success", "Company deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete company: " + e.getMessage());
        }
        return "redirect:/admin/companies";
    }
}