package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.entity.TransferToken;
import com.stevecodes.AssetIQPro.repository.TransferTokenRepository;
import com.stevecodes.AssetIQPro.security.SecurityUtils;
import com.stevecodes.AssetIQPro.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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
    private final TransferSigningService transferSigningService;
    private final TransferTokenRepository transferTokenRepository;
    private final CompanyService companyService;
    private final CategoryService categoryService;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;
    private final AssetService assetService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TRANSFER_VIEW', 'VIEW_ALL_TRANSACTIONS', 'ADMIN', 'SUPER_ADMIN')")
    public String transfers(Model model) {
        log.info("Loading transfers page");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        List<Transfer> transfers = transferService.getAllTransfers();

        for (Transfer transfer : transfers) {
            populateEmployeeDetails(transfer);

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
        model.addAttribute("canCreate", currentUser.hasAnyPermission("TRANSFER_CREATE", "EDIT_ASSETS", "ADMIN"));
        model.addAttribute("canInitiateSigning", currentUser.hasAnyPermission("TRANSFER_CREATE", "EDIT_ASSETS", "ADMIN"));
        model.addAttribute("canViewAll", currentUser.hasAnyPermission("TRANSFER_VIEW", "VIEW_ALL_TRANSACTIONS", "ADMIN"));

        return "transfers/list";
    }

    private void populateEmployeeDetails(Transfer transfer) {
        if (transfer.getOldEmployeeId() != null) {
            employeeService.getEmployeeById(transfer.getOldEmployeeId())
                    .ifPresent(emp -> {
                        transfer.setOldEmployeeName(emp.getFullName());
                        if (emp.getUser() != null) {
                            transfer.setOldEmployeeStaffId(emp.getUser().getStaffId());
                        }
                    });
        }

        if (transfer.getNewEmployeeId() != null) {
            employeeService.getEmployeeById(transfer.getNewEmployeeId())
                    .ifPresent(emp -> {
                        transfer.setNewEmployeeName(emp.getFullName());
                        if (emp.getUser() != null) {
                            transfer.setNewEmployeeStaffId(emp.getUser().getStaffId());
                        }
                    });
        }

        if (transfer.getConfiguredById() != null) {
            employeeService.getEmployeeById(transfer.getConfiguredById())
                    .ifPresent(emp -> {
                        transfer.setConfiguredByName(emp.getFullName());
                        if (emp.getUser() != null) {
                            transfer.setConfiguredByStaffId(emp.getUser().getStaffId());
                        }
                    });
        }

        if (transfer.getOldHandoverById() != null) {
            employeeService.getEmployeeById(transfer.getOldHandoverById())
                    .ifPresent(emp -> {
                        transfer.setOldHandoverByName(emp.getFullName());
                        if (emp.getUser() != null) {
                            transfer.setOldHandoverByStaffId(emp.getUser().getStaffId());
                        }
                    });
        }

        if (transfer.getOldReceivedById() != null) {
            employeeService.getEmployeeById(transfer.getOldReceivedById())
                    .ifPresent(emp -> {
                        transfer.setOldReceivedByName(emp.getFullName());
                        if (emp.getUser() != null) {
                            transfer.setOldReceivedByStaffId(emp.getUser().getStaffId());
                        }
                    });
        }

        if (transfer.getNewHandoverById() != null) {
            employeeService.getEmployeeById(transfer.getNewHandoverById())
                    .ifPresent(emp -> {
                        transfer.setNewHandoverByName(emp.getFullName());
                        if (emp.getUser() != null) {
                            transfer.setNewHandoverByStaffId(emp.getUser().getStaffId());
                        }
                    });
        }

        if (transfer.getNewReceivedById() != null) {
            employeeService.getEmployeeById(transfer.getNewReceivedById())
                    .ifPresent(emp -> {
                        transfer.setNewReceivedByName(emp.getFullName());
                        if (emp.getUser() != null) {
                            transfer.setNewReceivedByStaffId(emp.getUser().getStaffId());
                        }
                    });
        }

        if (transfer.getInfraRepresentativeId() != null) {
            employeeService.getEmployeeById(transfer.getInfraRepresentativeId())
                    .ifPresent(emp -> {
                        transfer.setInfraRepresentativeName(emp.getFullName());
                        if (emp.getUser() != null) {
                            transfer.setInfraRepresentativeStaffId(emp.getUser().getStaffId());
                        }
                    });
        }

        if (transfer.getFinanceRepresentativeId() != null) {
            employeeService.getEmployeeById(transfer.getFinanceRepresentativeId())
                    .ifPresent(emp -> {
                        transfer.setFinanceRepresentativeName(emp.getFullName());
                        if (emp.getUser() != null) {
                            transfer.setFinanceRepresentativeStaffId(emp.getUser().getStaffId());
                        }
                    });
        }
    }

    @GetMapping("/create")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public String createTransfer(@RequestParam(required = false) String assetTag, Model model) {
        log.info("Create transfer page accessed with assetTag: {}", assetTag);

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        model.addAttribute("assetTag", assetTag);
        model.addAttribute("companies", companyService.getAllCompanies());
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("employees", employeeService.getAllEmployees());

        if (assetTag != null && !assetTag.isEmpty()) {
            try {
                var asset = assetService.getAssetByTag(assetTag);
                model.addAttribute("asset", asset);
            } catch (Exception e) {
                log.warn("Asset not found with tag: {}", assetTag);
            }
        }

        return "transfers/create";
    }

    @GetMapping("/sign")
    public String showSignPage(@RequestParam String token, Model model) {
        log.info("Sign page accessed with token: {}", token);

        try {
            TransferToken transferToken = transferSigningService.validateToken(token);
            Transfer transfer = transferService.getTransferById(transferToken.getTransferId());

            populateEmployeeDetails(transfer);

            if (transfer.getOldDepartmentId() != null) {
                departmentService.getDepartmentById(transfer.getOldDepartmentId())
                        .ifPresent(dept -> transfer.setOldDepartmentName(dept.getName()));
            }
            if (transfer.getNewDepartmentId() != null) {
                departmentService.getDepartmentById(transfer.getNewDepartmentId())
                        .ifPresent(dept -> transfer.setNewDepartmentName(dept.getName()));
            }

            String roleDisplay = getRoleDisplayName(transferToken.getSignerRole());

            model.addAttribute("transfer", transfer);
            model.addAttribute("token", token);
            model.addAttribute("signerRole", transferToken.getSignerRole());
            model.addAttribute("signerRoleDisplay", roleDisplay);
            model.addAttribute("signerName", transferToken.getSignerEmail());
            model.addAttribute("alreadySigned", transferToken.getIsUsed());

            List<Transfer> relatedTransfers = transferService.getRelatedTransfers(
                    transfer.getTransferId(), transfer.getAssetTag(), transfer.getSerialNumber());
            model.addAttribute("relatedTransfers", relatedTransfers);

            return "transfers/sign";
        } catch (Exception e) {
            log.error("Error validating token: {}", e.getMessage(), e);
            model.addAttribute("error", "Invalid or expired signing link: " + e.getMessage());
            return "transfers/sign-error";
        }
    }

    private String getRoleDisplayName(String role) {
        if (role == null) return "Unknown";
        switch (role) {
            case "OLD_HANDOVER": return "Old Handover By";
            case "OLD_RECEIVED": return "Old Received By";
            case "NEW_HANDOVER": return "New Handover By";
            case "NEW_RECEIVED": return "New Received By";
            case "CONFIGURED_BY": return "Configured By";
            case "INFRA_REP": return "Infrastructure Representative";
            case "FINANCE_REP": return "Finance Representative";
            default: return role;
        }
    }

    @PostMapping("/sign")
    public String submitSignature(@RequestParam Integer transferId,
                                  @RequestParam String token,
                                  @RequestParam String signature,
                                  RedirectAttributes redirectAttributes) {
        try {
            log.info("Submitting signature for transfer: {}, token: {}", transferId, token);

            TransferToken transferToken = transferSigningService.validateToken(token);
            transferSigningService.saveSignature(transferId, transferToken.getSignerRole(), signature);

            transferToken.setIsUsed(true);
            transferTokenRepository.save(transferToken);

            boolean fullySigned = transferSigningService.isTransferFullySigned(transferId);

            if (fullySigned) {
                transferSigningService.generateFullySignedPdf(transferId);
                redirectAttributes.addFlashAttribute("message", "Transfer fully signed! PDF certificate emailed to all parties.");
                redirectAttributes.addFlashAttribute("fullySigned", true);
            } else {
                redirectAttributes.addFlashAttribute("message", "Your signature has been submitted successfully.");
                redirectAttributes.addFlashAttribute("fullySigned", false);
            }

            return "redirect:/transfers/thankyou";
        } catch (Exception e) {
            log.error("Error submitting signature: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to submit signature: " + e.getMessage());
            return "redirect:/transfers/sign-error";
        }
    }

    @GetMapping("/thankyou")
    public String thankyou() {
        return "transfers/thankyou";
    }

    @GetMapping("/sign-error")
    public String signError() {
        return "transfers/sign-error";
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public String createTransfer(Transfer transfer,
                                 @RequestParam(required = false) MultipartFile file,
                                 RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            if (transfer.getTransferDate() == null) {
                transfer.setTransferDate(LocalDate.now());
            }
            transfer.setIsFullySigned(false);

            Transfer saved = transferService.createTransfer(transfer);
            transferSigningService.initiateTransferSigning(saved.getTransferId());

            redirectAttributes.addFlashAttribute("success", "Transfer created successfully! Signing emails sent to all signers.");
            return "redirect:/transfers";
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to create transfers.");
            return "redirect:/transfers";
        } catch (Exception e) {
            log.error("Error creating transfer: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create transfer: " + e.getMessage());
            return "redirect:/transfers";
        }
    }

    @PostMapping("/{transferId}/initiate-signing")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public String initiateSigning(@PathVariable Integer transferId, RedirectAttributes redirectAttributes) {
        try {
            AppUser currentUser = SecurityUtils.getCurrentUser();
            if (currentUser == null) {
                redirectAttributes.addFlashAttribute("error", "You must be logged in to perform this action.");
                return "redirect:/login";
            }

            transferSigningService.initiateTransferSigning(transferId);
            redirectAttributes.addFlashAttribute("success", "Signing emails sent successfully!");
        } catch (AccessDeniedException e) {
            log.warn("Access denied: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "You don't have permission to initiate signing.");
        } catch (Exception e) {
            log.error("Error initiating signing: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to send signing emails: " + e.getMessage());
        }
        return "redirect:/transfers";
    }

    @GetMapping("/{transferId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Integer transferId) {
        try {
            byte[] pdf = transferSigningService.getFullySignedPdf(transferId);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=transfer_" + transferId + ".pdf")
                    .body(pdf);
        } catch (IllegalStateException e) {
            log.warn("PDF not available: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error generating PDF for transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}