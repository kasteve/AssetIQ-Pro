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
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/transfers")
public class TransferViewController {

    private final TransferService transferService;
    private final TransferSigningService transferSigningService;
    private final TransferTokenRepository transferTokenRepository;
    private final TransferTokenService transferTokenService;
    private final CompanyService companyService;
    private final CategoryService categoryService;
    private final DepartmentService departmentService;
    private final EmployeeService employeeService;
    private final AssetService assetService;

    // ============================================
    // TRANSFERS LIST PAGE
    // ============================================

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

    // ============================================
    // POPULATE EMPLOYEE DETAILS
    // ============================================

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

    // ============================================
    // GET ROLE DISPLAY NAME
    // ============================================

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

    // ============================================
    // CREATE TRANSFER PAGE
    // ============================================

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

    // ============================================
    // SIGN PAGE - MULTI-ROLE SUPPORT
    // ============================================

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

            // ============================================
            // MULTI-ROLE SIGNING - Get all roles for this signer
            // ============================================
            List<String> signerRoles = transferTokenService.getRolesFromToken(transferToken);
            log.info("Signer roles: {}", signerRoles);

            // Get current role (first unsigned role, or first role if all signed)
            String currentRole = null;
            Map<String, Boolean> roleSignedStatus = new LinkedHashMap<>();
            int signedCount = 0;

            for (String role : signerRoles) {
                boolean isSigned = transfer.isSignedForRole(role);
                roleSignedStatus.put(role, isSigned);
                if (isSigned) {
                    signedCount++;
                } else if (currentRole == null) {
                    currentRole = role;
                }
            }

            // If all roles are signed, set currentRole to the last role
            if (currentRole == null && !signerRoles.isEmpty()) {
                currentRole = signerRoles.get(signerRoles.size() - 1);
            }

            boolean allSlotsSigned = signedCount == signerRoles.size() && !signerRoles.isEmpty();

            String roleDisplay = currentRole != null ? getRoleDisplayName(currentRole) : "No roles assigned";

            AtomicReference<String> signerName = new AtomicReference<>(transferToken.getSignerEmail());
            // Try to get the employee name
            if (transferToken.getSignerEmployeeId() != null) {
                Long employeeId = transferToken.getSignerEmployeeId();
                employeeService.getEmployeeById(employeeId)
                        .ifPresent(emp -> signerName.set(emp.getFullName()));
            }

            // ============================================
            // ADD ALL ATTRIBUTES TO MODEL
            // ============================================
            model.addAttribute("transfer", transfer);
            model.addAttribute("token", token);
            model.addAttribute("signerRole", transferToken.getSignerRole());
            model.addAttribute("signerRoleDisplay", roleDisplay);
            model.addAttribute("signerName", signerName);
            model.addAttribute("alreadySigned", transferToken.getIsUsed());

            // Multi-role attributes
            model.addAttribute("signerRoles", signerRoles);
            model.addAttribute("currentRole", currentRole);
            model.addAttribute("currentRoleDisplay", roleDisplay);
            model.addAttribute("roleSignedStatus", roleSignedStatus);
            model.addAttribute("totalSlots", signerRoles.size());
            model.addAttribute("signedSlotsCount", signedCount);
            model.addAttribute("allSlotsSigned", allSlotsSigned);

            // Transfer date formatting
            if (transfer.getTransferDate() != null) {
                model.addAttribute("transferDateFormatted",
                        transfer.getTransferDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            }

            // Related transfers
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

    // ============================================
    // SUBMIT SIGNATURE - FIXED
    // ============================================

    @PostMapping("/sign")
    public String submitSignature(@RequestParam Long transferId,
                                  @RequestParam String token,
                                  @RequestParam String signature,
                                  @RequestParam(required = false) String role,
                                  RedirectAttributes redirectAttributes) {
        try {
            log.info("Submitting signature for transfer: {}, token: {}, role: {}", transferId, token, role);

            TransferToken transferToken = transferSigningService.validateToken(token);

            // Determine which role to sign (using helper method to avoid lambda issues)
            String signerRole = determineSignerRole(transferId, transferToken, role);
            log.info("Signing role: {}", signerRole);

            // Save the signature for the specific role
            transferSigningService.saveSignature(transferId, signerRole, signature);

            // Check if all roles for this signer are now signed
            Transfer transfer = transferService.getTransferById(transferId);
            boolean allSlotsSigned = transferTokenService.areAllSlotsSigned(transfer, transferToken);

            if (allSlotsSigned) {
                transferToken.setIsUsed(true);
                transferTokenRepository.save(transferToken);
                log.info("✅ Signer {} has completed all roles for transfer {}", transferToken.getSignerEmail(), transferId);
            }

            boolean fullySigned = transferSigningService.isTransferFullySigned(transferId);

            if (fullySigned) {
                transferSigningService.generateFullySignedPdf(transferId);
                redirectAttributes.addFlashAttribute("message", "Transfer fully signed! PDF certificate emailed to all parties.");
                redirectAttributes.addFlashAttribute("fullySigned", true);
            } else {
                redirectAttributes.addFlashAttribute("message",
                        "Your signature for " + getRoleDisplayName(signerRole) + " has been submitted successfully." +
                                (allSlotsSigned ? " All your roles are now signed!" :
                                        " Please sign your remaining roles if any."));
                redirectAttributes.addFlashAttribute("fullySigned", false);
            }

            if (allSlotsSigned) {
                return "redirect:/transfers/thankyou";
            } else {
                // Redirect back to signing page with same token to sign remaining roles
                return "redirect:/transfers/sign?token=" + token;
            }

        } catch (Exception e) {
            log.error("Error submitting signature: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to submit signature: " + e.getMessage());
            return "redirect:/transfers/sign-error";
        }
    }

    // ============================================
    // DETERMINE SIGNER ROLE - HELPER METHOD
    // ============================================

    private String determineSignerRole(Long transferId, TransferToken transferToken, String requestedRole) {
        // If a specific role was requested, use it
        if (requestedRole != null && !requestedRole.isEmpty()) {
            return requestedRole;
        }

        String signerRole = transferToken.getSignerRole();

        // Single role - use it directly
        if (signerRole == null || !signerRole.contains(",")) {
            return signerRole != null ? signerRole : "UNKNOWN";
        }

        // Multiple roles - find the first unsigned one
        List<String> roles = transferTokenService.getRolesFromToken(transferToken);
        Transfer transfer = transferService.getTransferById(transferId);

        for (String r : roles) {
            if (!transfer.isSignedForRole(r)) {
                return r;
            }
        }

        // All roles are signed, return the first one
        return roles.isEmpty() ? "UNKNOWN" : roles.get(0);
    }

    // ============================================
    // THANK YOU PAGE
    // ============================================

    @GetMapping("/thankyou")
    public String thankyou() {
        return "transfers/thankyou";
    }

    // ============================================
    // SIGN ERROR PAGE
    // ============================================

    @GetMapping("/sign-error")
    public String signError() {
        return "transfers/sign-error";
    }

    // ============================================
    // CREATE TRANSFER
    // ============================================

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

    // ============================================
    // PROCESS RE-TRANSFER
    // ============================================

    @PostMapping("/retransfer")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public String processRetransfer(
            @RequestParam Long sourceTransferId,
            @RequestParam Long newEmployeeId,
            @RequestParam(required = false) Integer newDepartmentId,
            @RequestParam(required = false) String conditionOld,
            @RequestParam(required = false) String conditionNew,
            @RequestParam(required = false) String accessoriesOld,
            @RequestParam(required = false) String accessoriesNew,
            @RequestParam(required = false) String softwareInstalled,
            @RequestParam(required = false) String comments,
            RedirectAttributes redirectAttributes) {

        log.info("=========================================");
        log.info("📝 Processing re-transfer for source transfer: {}", sourceTransferId);
        log.info("   New Employee ID: {}", newEmployeeId);
        log.info("   New Department ID: {}", newDepartmentId);
        log.info("=========================================");

        AppUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("error", "You must be logged in.");
            return "redirect:/login";
        }

        try {
            Transfer sourceTransfer = transferService.getTransferById(sourceTransferId);

            Transfer retransferData = new Transfer();

            retransferData.setAssetTag(sourceTransfer.getAssetTag());
            retransferData.setSerialNumber(sourceTransfer.getSerialNumber());
            retransferData.setVersionMake(sourceTransfer.getVersionMake());
            retransferData.setModelBuild(sourceTransfer.getModelBuild());

            if (sourceTransfer.getCategory() != null) {
                retransferData.setCategory(sourceTransfer.getCategory());
            }

            retransferData.setCompanyId(sourceTransfer.getCompanyId());
            retransferData.setPreviousTransferId(sourceTransferId);
            retransferData.setNewEmployeeId(newEmployeeId);

            if (newDepartmentId != null) {
                retransferData.setNewDepartmentId(newDepartmentId);
            }

            retransferData.setConditionOld(conditionOld);
            retransferData.setConditionNew(conditionNew);
            retransferData.setAccessoriesOld(accessoriesOld);
            retransferData.setAccessoriesNew(accessoriesNew);
            retransferData.setSoftwareInstalled(softwareInstalled);
            retransferData.setComments(comments);

            Transfer newTransfer = transferService.retransferAsset(retransferData);

            redirectAttributes.addFlashAttribute("success",
                    "✅ Asset re-transferred successfully! Transfer #" + newTransfer.getTransferId() +
                            " (Sequence: " + newTransfer.getTransferSequence() + ") created. Signing emails sent to all parties.");

            return "redirect:/transfers";

        } catch (Exception e) {
            log.error("❌ Error processing re-transfer: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to re-transfer asset: " + e.getMessage());
            return "redirect:/transfers";
        }
    }

    // ============================================
    // INITIATE SIGNING
    // ============================================

    @PostMapping("/{transferId}/initiate-signing")
    @PreAuthorize("hasAnyAuthority('TRANSFER_CREATE', 'EDIT_ASSETS', 'ADMIN', 'SUPER_ADMIN')")
    public String initiateSigning(@PathVariable Long transferId, RedirectAttributes redirectAttributes) {
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

    // ============================================
    // DOWNLOAD PDF
    // ============================================

    @GetMapping("/{transferId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long transferId) {
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