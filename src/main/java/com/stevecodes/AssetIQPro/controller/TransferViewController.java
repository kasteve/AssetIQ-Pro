package com.stevecodes.AssetIQPro.controller;

import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.entity.TransferToken;
import com.stevecodes.AssetIQPro.repository.TransferTokenRepository;
import com.stevecodes.AssetIQPro.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/sign")
    public String showSignPage(@RequestParam String token, Model model) {
        log.info("Sign page accessed with token: {}", token);

        try {
            TransferToken transferToken = transferSigningService.validateToken(token);
            Transfer transfer = transferService.getTransferById(transferToken.getTransferId());

            // Populate department names
            if (transfer.getOldDepartmentId() != null) {
                departmentService.getDepartmentById(transfer.getOldDepartmentId())
                        .ifPresent(dept -> transfer.setOldDepartmentName(dept.getName()));
            }
            if (transfer.getNewDepartmentId() != null) {
                departmentService.getDepartmentById(transfer.getNewDepartmentId())
                        .ifPresent(dept -> transfer.setNewDepartmentName(dept.getName()));
            }

            // Populate employee names
            if (transfer.getOldEmployeeId() != null) {
                employeeService.getEmployeeById(transfer.getOldEmployeeId())
                        .ifPresent(emp -> transfer.setOldEmployeeName(emp.getFullName()));
            }
            if (transfer.getNewEmployeeId() != null) {
                employeeService.getEmployeeById(transfer.getNewEmployeeId())
                        .ifPresent(emp -> transfer.setNewEmployeeName(emp.getFullName()));
            }

            // Get role display name
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

    // POST endpoint - Submit signature
    @PostMapping("/sign")
    public String submitSignature(@RequestParam Integer transferId,
                                  @RequestParam String token,
                                  @RequestParam String signature,
                                  RedirectAttributes redirectAttributes) {
        try {
            log.info("Submitting signature for transfer: {}, token: {}", transferId, token);

            // Validate token
            TransferToken transferToken = transferSigningService.validateToken(token);

            // Save signature
            transferSigningService.saveSignature(transferId, transferToken.getSignerRole(), signature);

            // Mark token as used
            transferToken.setIsUsed(true);
            transferTokenRepository.save(transferToken);

            // Check if fully signed
            boolean fullySigned = transferSigningService.isTransferFullySigned(transferId);

            if (fullySigned) {
                // Generate and send PDF to all signers
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
    public String createTransfer(Transfer transfer,
                                 @RequestParam(required = false) MultipartFile file,
                                 RedirectAttributes redirectAttributes) {
        try {
            if (transfer.getTransferDate() == null) {
                transfer.setTransferDate(LocalDate.now());
            }
            transfer.setIsFullySigned(false);

            Transfer saved = transferService.createTransfer(transfer);

            // Auto-initiate signing
            transferSigningService.initiateTransferSigning(saved.getTransferId());

            redirectAttributes.addFlashAttribute("success", "Transfer created successfully! Signing emails sent to all signers.");
            return "redirect:/transfers";
        } catch (Exception e) {
            log.error("Error creating transfer: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Failed to create transfer: " + e.getMessage());
            return "redirect:/transfers";
        }
    }

    @PostMapping("/{transferId}/initiate-signing")
    public String initiateSigning(@PathVariable Integer transferId, RedirectAttributes redirectAttributes) {
        try {
            transferSigningService.initiateTransferSigning(transferId);
            redirectAttributes.addFlashAttribute("success", "Signing emails sent successfully!");
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
        } catch (Exception e) {
            log.error("Error generating PDF for transfer {}: {}", transferId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}