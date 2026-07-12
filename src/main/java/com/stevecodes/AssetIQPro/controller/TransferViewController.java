package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/transfers")
public class TransferViewController {

    private final TransferService transferService;
    private final CompanyService companyService;
    private final CategoryService categoryService;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;

    @GetMapping
    public String transfers(Model model) {
        log.info("Loading transfers page");

        List<Transfer> transfers = transferService.getAllTransfers();

        // Populate department names
        for (Transfer transfer : transfers) {
            if (transfer.getOldDepartmentId() != null) {
                departmentService.getDepartmentById(transfer.getOldDepartmentId())
                        .ifPresent(dept -> transfer.setOldDepartmentName(dept.getName()));
            }
            if (transfer.getNewDepartmentId() != null) {
                departmentService.getDepartmentById(transfer.getNewDepartmentId())
                        .ifPresent(dept -> transfer.setNewDepartmentName(dept.getName()));
            }
        }

        model.addAttribute("transfers", transfers);
        model.addAttribute("companies", companyService.getAllCompanies());
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("employees", employeeService.getAllEmployees());
        return "transfers/list";
    }

    @PostMapping("/create")
    public String createTransfer(Transfer transfer,
                                 @RequestParam(required = false) MultipartFile file,
                                 RedirectAttributes redirectAttributes) {
        try {
            if (transfer.getTransferDate() == null) {
                transfer.setTransferDate(LocalDate.now());
            }
            transfer.setIsFullySigned(false);
            transferService.createTransfer(transfer);
            redirectAttributes.addFlashAttribute("success", "Transfer created successfully!");
            return "redirect:/transfers";
        } catch (Exception e) {
            log.error("Error creating transfer: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create transfer: " + e.getMessage());
            return "redirect:/transfers";
        }
    }
}