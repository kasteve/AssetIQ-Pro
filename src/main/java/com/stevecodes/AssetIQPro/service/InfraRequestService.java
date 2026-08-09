package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Employee;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.entity.InfraRequest.RequestStatus;
import com.stevecodes.AssetIQPro.entity.Notification;
import com.stevecodes.AssetIQPro.repository.EmployeeRepository;
import com.stevecodes.AssetIQPro.repository.InfraRequestRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.entity.RequestSLATracking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InfraRequestService {

    private final InfraRequestRepository requestRepository;
    private final NotificationRepository notificationRepository;
    private final AppUserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final PdfGenerationService pdfGenerationService;
    private final BaseUrlService baseUrlService;
    private final SLAService slaService;  // ✅ ADDED

    private static final String UPLOAD_DIR = "uploads/infra/quotations/";
    private static final String REPORT_DIR = "uploads/infra/reports/";

    // ============================================
    // Query Methods
    // ============================================

    public InfraRequestDTO getRequestById(Long requestId) {
        InfraRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));
        return convertToDTO(request);
    }

    public InfraRequest getRequestEntityById(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));
    }

    public InfraRequest getRequestBySigningToken(String token) {
        return requestRepository.findBySigningToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));
    }

    public List<InfraRequestDTO> getRequestsForUser(Long userId) {
        log.info("Getting infrastructure requests for user: {}", userId);
        return requestRepository.findRequestsForUser(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<InfraRequestDTO> getRequestsByStatus(RequestStatus status) {
        log.info("Getting infrastructure requests by status: {}", status);
        return requestRepository.findByStatus(status).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<InfraRequestDTO> getAllRequests() {
        log.info("Getting all infrastructure requests");
        return requestRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<InfraRequestDTO> getRequestsByRequesterId(Long userId) {
        log.info("Getting infrastructure requests for requester: {}", userId);
        return requestRepository.findByRequesterId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<InfraRequestDTO> getFinanceRequests() {
        log.info("Getting finance requests - all finance-related statuses");
        return requestRepository.findByStatusIn(List.of(
                        RequestStatus.PENDING_FINANCE_APPROVAL,
                        RequestStatus.PROCUREMENT,
                        RequestStatus.DELIVERED
                )).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ============================================
    // Request Lifecycle Management
    // ============================================

    @Transactional
    public InfraRequestDTO createRequest(InfraRequestDTO dto, Long requesterId) {
        log.info("Creating infrastructure request for user: {}", requesterId);

        // ✅ Get employee for the requester
        Employee employee = employeeRepository.findByUserId(requesterId)
                .orElseThrow(() -> new RuntimeException("Employee not found for user ID: " + requesterId));

        Long lineManagerId = null;

        if (employee.getLineManager() != null) {
            Employee lineManager = employee.getLineManager();

            // ✅ Try to get user ID from the employee's user_id field
            // Use reflection or a custom query to get the user_id directly
            try {
                // First try: check if the employee has a user_id through the user relationship
                if (lineManager.getUser() != null) {
                    lineManagerId = lineManager.getUser().getUserId();
                    log.info("Line manager found via user relationship: {}", lineManagerId);
                }
            } catch (Exception e) {
                log.warn("Could not get user from relationship: {}", e.getMessage());
            }

            // ✅ If that fails, use a direct query to get the user_id
            if (lineManagerId == null) {
                try {
                    // Use a direct query to get the user_id from the employee
                    Long employeeUserId = employeeRepository.findUserIdByEmployeeId(lineManager.getEmployeeId());
                    if (employeeUserId != null) {
                        lineManagerId = employeeUserId;
                        log.info("Line manager user_id found via direct query: {}", lineManagerId);
                    }
                } catch (Exception e) {
                    log.warn("Could not get user_id via direct query: {}", e.getMessage());
                }
            }

            // ✅ If still null, fallback to admin
            if (lineManagerId == null) {
                log.warn("Line manager {} does not have a user account. Using admin fallback.",
                        lineManager.getFirstName() + " " + lineManager.getSurName());

                // Fallback: Find an admin user
                AppUser adminUser = userRepository.findByRole("ADMIN").stream().findFirst()
                        .orElse(null);
                if (adminUser != null) {
                    lineManagerId = adminUser.getUserId();
                    log.info("Using admin user {} as fallback line manager", adminUser.getUsername());
                } else {
                    // Final fallback: use the first user with INFRA role
                    adminUser = userRepository.findByRole("INFRA").stream().findFirst().orElse(null);
                    if (adminUser != null) {
                        lineManagerId = adminUser.getUserId();
                        log.info("Using INFRA user {} as fallback line manager", adminUser.getUsername());
                    } else {
                        throw new RuntimeException("No line manager or admin found to approve request");
                    }
                }
            }
        }

        if (lineManagerId == null) {
            // Final fallback: assign to the first admin
            AppUser adminUser = userRepository.findByRole("ADMIN").stream().findFirst()
                    .orElseThrow(() -> new RuntimeException("No admin user found in system"));
            lineManagerId = adminUser.getUserId();
            log.info("Using admin user {} as final fallback line manager", adminUser.getUsername());
        }

        log.info("Final line manager ID: {}", lineManagerId);

        // Create the request
        InfraRequest request = new InfraRequest();
        request.setRequesterId(requesterId);
        request.setLineManagerId(lineManagerId);
        request.setResourceType(dto.getResourceType());
        request.setSpecification(dto.getSpecification());
        request.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
        request.setJustification(dto.getJustification());
        request.setStatus(RequestStatus.PENDING_LM_APPROVAL);

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        // ✅ START SLA TRACKING FOR INFRA REQUEST
        try {
            slaService.startSLATracking(saved.getRequestId(), "INFRA_REQUEST", requesterId);
            log.info("SLA tracking started for infra request: {}", saved.getRequestId());
        } catch (Exception e) {
            log.error("Failed to start SLA tracking for infra request: {}", e.getMessage());
        }

        // Notify line manager
        try {
            notifyLineManager(saved);
            sendEmailNotification(lineManagerId,
                    "Infrastructure Request Pending Approval",
                    "Request #" + saved.getRequestId() + " for " + saved.getResourceType() + " requires your approval.");
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_CREATED",
                "Request #" + saved.getRequestId() + " created by user " + requesterId,
                requesterId);

        return result;
    }

    @Transactional
    public InfraRequestDTO approveByLineManager(Long requestId, Long managerId, String comment) {
        log.info("Line manager {} approving request: {}", managerId, requestId);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PENDING_LM_APPROVAL);

        request.setStatus(RequestStatus.PENDING_INFRA_REVIEW);
        request.setLmApprovedAt(LocalDateTime.now());
        request.setLmApprovedBy(managerId);
        request.setLmComment(comment);

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        try {
            notifyRequester(saved, "Your request has been approved by your line manager.");
            sendEmailNotification(saved.getRequesterId(),
                    "Infrastructure Request Approved by Line Manager",
                    "Your request #" + saved.getRequestId() + " has been approved by your line manager and is now pending infrastructure review.");
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_LM_APPROVED",
                "Request #" + requestId + " approved by line manager " + managerId,
                managerId);

        return result;
    }

    @Transactional
    public InfraRequestDTO rejectByLineManager(Long requestId, Long managerId, String reason) {
        log.info("Line manager {} rejecting request: {}", managerId, requestId);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PENDING_LM_APPROVAL);

        request.setStatus(RequestStatus.LM_REJECTED);
        request.setLmApprovedAt(LocalDateTime.now());
        request.setLmApprovedBy(managerId);
        request.setLmComment(reason);

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        try {
            notifyRequester(saved, "Your request has been rejected by your line manager. Reason: " + reason);
            sendEmailNotification(saved.getRequesterId(),
                    "Infrastructure Request Rejected by Line Manager",
                    "Your request #" + saved.getRequestId() + " has been rejected by your line manager. Reason: " + reason);
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_LM_REJECTED",
                "Request #" + requestId + " rejected by line manager " + managerId,
                managerId);

        return result;
    }

    @Transactional
    public InfraRequestDTO reviewByInfra(Long requestId, Long infraId, String comment, boolean approved) {
        log.info("Infra {} reviewing request: {} (approved: {})", infraId, requestId, approved);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PENDING_INFRA_REVIEW);

        request.setInfraReviewedAt(LocalDateTime.now());
        request.setInfraReviewedBy(infraId);
        request.setInfraComment(comment);

        if (approved) {
            request.setStatus(RequestStatus.PENDING_FINANCE_APPROVAL);
            try {
                notifyFinanceTeam(request);
                sendEmailNotification(infraId,
                        "Infrastructure Request Approved",
                        "Request #" + requestId + " has been approved and is now pending finance approval.");
            } catch (Exception e) {
                log.error("Failed to send email: {}", e.getMessage());
            }
        } else {
            request.setStatus(RequestStatus.INFRA_REJECTED);
            try {
                notifyRequester(request, "Your request has been rejected by Infrastructure. Reason: " + comment);
                sendEmailNotification(request.getRequesterId(),
                        "Infrastructure Request Rejected by Infrastructure",
                        "Your request #" + request.getRequestId() + " has been rejected by Infrastructure. Reason: " + comment);
            } catch (Exception e) {
                log.error("Failed to send email: {}", e.getMessage());
            }
        }

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        auditService.logAction("INFRA_REQUEST_REVIEWED",
                "Request #" + requestId + " reviewed by infra " + infraId + " (approved: " + approved + ")",
                infraId);

        return result;
    }

    // ============================================
    // Finance Sequential Workflow
    // ============================================

    @Transactional
    public InfraRequestDTO approveByFinance(Long requestId, Long financeId, String comment) {
        log.info("Finance {} approving request: {}", financeId, requestId);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PENDING_FINANCE_APPROVAL);

        request.setStatus(RequestStatus.PROCUREMENT);
        request.setFinanceApprovedAt(LocalDateTime.now());
        request.setFinanceApprovedBy(financeId);
        request.setFinanceComment(comment);

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        try {
            notifyRequester(saved, "Your request has been approved by Finance and is now in procurement.");
            sendEmailNotification(saved.getRequesterId(),
                    "Infrastructure Request Approved by Finance",
                    "Your request #" + saved.getRequestId() + " has been approved by Finance and is now in procurement.");
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_FINANCE_APPROVED",
                "Request #" + requestId + " approved by finance " + financeId,
                financeId);

        return result;
    }

    @Transactional
    public InfraRequestDTO rejectByFinance(Long requestId, Long financeId, String reason) {
        log.info("Finance {} rejecting request: {}", financeId, requestId);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PENDING_FINANCE_APPROVAL);

        request.setStatus(RequestStatus.FINANCE_REJECTED);
        request.setFinanceApprovedAt(LocalDateTime.now());
        request.setFinanceApprovedBy(financeId);
        request.setFinanceComment(reason);

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        try {
            notifyRequester(saved, "Your request has been rejected by Finance. Reason: " + reason);
            sendEmailNotification(saved.getRequesterId(),
                    "Infrastructure Request Rejected by Finance",
                    "Your request #" + saved.getRequestId() + " has been rejected by Finance. Reason: " + reason);
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_FINANCE_REJECTED",
                "Request #" + requestId + " rejected by finance " + financeId,
                financeId);

        return result;
    }

    @Transactional
    public InfraRequestDTO markProcurement(Long requestId, Long financeId, String procurementOrderRef,
                                           BigDecimal purchaseCost, Integer supplierId) {
        log.info("Finance {} marking request {} for procurement", financeId, requestId);

        InfraRequest request = validateRequest(requestId);

        if (request.getStatus() != RequestStatus.PENDING_FINANCE_APPROVAL &&
                request.getStatus() != RequestStatus.PROCUREMENT) {
            throw new IllegalStateException("Request must be in PENDING_FINANCE_APPROVAL or PROCUREMENT status. Current: " + request.getStatus());
        }

        request.setStatus(RequestStatus.PROCUREMENT);
        if (request.getFinanceApprovedAt() == null) {
            request.setFinanceApprovedAt(LocalDateTime.now());
            request.setFinanceApprovedBy(financeId);
        }
        request.setProcurementOrderRef(procurementOrderRef);
        request.setPurchaseCost(purchaseCost);
        request.setSupplierId(supplierId);

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        try {
            notifyRequester(saved, "Your request has been moved to procurement.");
            sendEmailNotification(saved.getRequesterId(),
                    "Infrastructure Request - Procurement",
                    "Your request #" + saved.getRequestId() + " is now in procurement.");
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_PROCUREMENT",
                "Request #" + requestId + " moved to procurement by " + financeId,
                financeId);

        return result;
    }

    @Transactional
    public InfraRequestDTO markDelivered(Long requestId, Long deliveredBy, String deliveryNotes) {
        log.info("Marking request {} as delivered by: {}", requestId, deliveredBy);

        InfraRequest request = validateRequest(requestId);

        if (request.getStatus() != RequestStatus.PROCUREMENT) {
            throw new IllegalStateException("Request must be in PROCUREMENT status. Current: " + request.getStatus());
        }

        request.setStatus(RequestStatus.DELIVERED);
        request.setDeliveredAt(LocalDateTime.now());
        request.setDeliveredBy(deliveredBy);
        requestRepository.save(request);

        // Generate signing link for requester
        try {
            String signingLink = generateSigningLink(requestId);
            log.info("Signing link generated for request {}: {}", requestId, signingLink);
        } catch (Exception e) {
            log.error("Failed to generate signing link: {}", e.getMessage());
        }

        try {
            notifyRequester(request, "Your requested items have been delivered. Please sign to complete.");
            sendEmailNotification(request.getRequesterId(),
                    "Infrastructure Request Delivered - Please Sign",
                    "Your request #" + request.getRequestId() + " has been delivered. Please sign to complete.");
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_DELIVERED",
                "Request #" + requestId + " marked as delivered by " + deliveredBy,
                deliveredBy);

        return convertToDTO(request);
    }

    @Transactional
    public String generateSigningLink(Long requestId) {
        InfraRequest request = validateRequest(requestId);

        if (request.getStatus() != RequestStatus.DELIVERED) {
            throw new IllegalStateException("Request must be in DELIVERED status to sign. Current: " + request.getStatus());
        }

        String token = UUID.randomUUID().toString();
        request.setSigningToken(token);
        request.setSigningTokenExpiry(LocalDateTime.now().plusHours(48));
        requestRepository.save(request);

        String signingLink = baseUrlService.buildUrl("/infra-requests/sign?token=%s", token);

        userRepository.findById(request.getRequesterId()).ifPresent(user -> {
            emailService.sendSimpleEmail(
                    user.getEmail(),
                    "Infrastructure Request - Sign to Complete",
                    "Please sign to acknowledge receipt of your request #" + requestId + ":\n\n" + signingLink
            );
        });

        return signingLink;
    }

    @Transactional
    public void saveRequesterSignature(Long requestId, String token, String signature) {
        InfraRequest request = validateRequest(requestId);

        if (!token.equals(request.getSigningToken())) {
            throw new RuntimeException("Invalid token");
        }
        if (request.getSigningTokenExpiry() == null ||
                request.getSigningTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token has expired");
        }

        request.setRequesterSignature(signature);
        request.setRequesterSignedAt(LocalDateTime.now());
        request.setSigningToken(null);
        request.setSigningTokenExpiry(null);
        request.setStatus(RequestStatus.COMPLETED);
        request.setCompletedAt(LocalDateTime.now());

        // ✅ Complete SLA tracking
        try {
            slaService.completeSLATracking(requestId, "INFRA_REQUEST");
            log.info("SLA tracking completed for infra request: {}", requestId);
        } catch (Exception e) {
            log.error("Failed to complete SLA tracking for infra request: {}", e.getMessage());
        }

        // Generate PDF with signature
        try {
            byte[] pdfBytes = pdfGenerationService.generateInfraRequestReport(request);
            String pdfPath = savePdfToFile(pdfBytes, requestId);
            request.setPdfReportPath(pdfPath);
            log.info("PDF generated for request: {}", requestId);
        } catch (Exception e) {
            log.error("Failed to generate PDF for request {}: {}", requestId, e.getMessage());
        }

        requestRepository.save(request);
    }

    @Transactional
    public InfraRequestDTO completeRequest(Long requestId, Long completedBy) {
        log.info("User {} completing request: {}", completedBy, requestId);

        InfraRequest request = validateRequest(requestId);

        if (request.getStatus() != RequestStatus.DELIVERED) {
            throw new IllegalStateException("Request must be in DELIVERED status. Current: " + request.getStatus());
        }

        request.setStatus(RequestStatus.COMPLETED);
        request.setCompletedAt(LocalDateTime.now());

        // ✅ Complete SLA tracking
        try {
            slaService.completeSLATracking(requestId, "INFRA_REQUEST");
            log.info("SLA tracking completed for infra request: {}", requestId);
        } catch (Exception e) {
            log.error("Failed to complete SLA tracking for infra request: {}", e.getMessage());
        }

        // Generate PDF on completion
        try {
            byte[] pdfBytes = pdfGenerationService.generateInfraRequestReport(request);
            String pdfPath = savePdfToFile(pdfBytes, requestId);
            request.setPdfReportPath(pdfPath);
            log.info("PDF generated for request: {}", requestId);
        } catch (Exception e) {
            log.error("Failed to generate PDF for request {}: {}", requestId, e.getMessage());
        }

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        try {
            sendCompletionReport(saved);
            sendEmailNotification(saved.getRequesterId(),
                    "Infrastructure Request Completed",
                    "Your request #" + saved.getRequestId() + " has been completed.");
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_COMPLETED",
                "Request #" + requestId + " completed by " + completedBy,
                completedBy);

        return result;
    }
    @Transactional
    public InfraRequestDTO acknowledgeReceipt(Long requestId, Long acknowledgedBy) {
        log.info("User {} acknowledging receipt for request: {}", acknowledgedBy, requestId);

        InfraRequest request = validateRequest(requestId);

        if (request.getStatus() != RequestStatus.DELIVERED) {
            throw new IllegalStateException("Request must be in DELIVERED status. Current: " + request.getStatus());
        }

        request.setStatus(RequestStatus.COMPLETED);
        request.setAcknowledgedAt(LocalDateTime.now());
        request.setAcknowledgedBy(acknowledgedBy);

        // Generate PDF on completion
        try {
            byte[] pdfBytes = pdfGenerationService.generateInfraRequestReport(request);
            String pdfPath = savePdfToFile(pdfBytes, requestId);
            request.setPdfReportPath(pdfPath);
            log.info("PDF generated for request: {}", requestId);
        } catch (Exception e) {
            log.error("Failed to generate PDF for request {}: {}", requestId, e.getMessage());
        }

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        try {
            sendCompletionReport(saved);
            sendEmailNotification(saved.getRequesterId(),
                    "Infrastructure Request Completed",
                    "Your request #" + saved.getRequestId() + " has been completed. Thank you for using AssetIQ-Pro.");
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_COMPLETED",
                "Request #" + requestId + " completed - acknowledged by " + acknowledgedBy,
                acknowledgedBy);

        return result;
    }

    @Transactional
    public String uploadQuotation(Long requestId, MultipartFile file) {
        log.info("Uploading quotation for request: {}", requestId);

        InfraRequest request = validateRequest(requestId);

        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);
            Files.write(filePath, file.getBytes());

            request.setQuotationPath(filePath.toString());
            requestRepository.save(request);

            auditService.logAction("QUOTATION_UPLOADED",
                    "Quotation uploaded for request #" + requestId,
                    null);

            return filePath.toString();

        } catch (IOException e) {
            log.error("Failed to upload quotation: {}", e.getMessage());
            throw new RuntimeException("Failed to upload quotation", e);
        }
    }

    public List<AppUser> getUsersWithPermission(String permissionName) {
        return userRepository.findUsersWithPermission(permissionName);
    }

    // ============================================
    // Helper Methods
    // ============================================

    private InfraRequest validateRequest(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));
    }

    private void validateStatus(InfraRequest request, RequestStatus expectedStatus) {
        if (request.getStatus() != expectedStatus) {
            throw new IllegalStateException(
                    "Invalid status. Expected: " + expectedStatus + ", Actual: " + request.getStatus()
            );
        }
    }

    private String savePdfToFile(byte[] pdfBytes, Long requestId) throws IOException {
        Path uploadPath = Paths.get(REPORT_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String filename = "infra_request_" + requestId + "_" + System.currentTimeMillis() + ".pdf";
        Path filePath = uploadPath.resolve(filename);
        Files.write(filePath, pdfBytes);
        return filePath.toString();
    }

    private void notifyLineManager(InfraRequest request) {
        userRepository.findById(request.getLineManagerId()).ifPresent(manager -> {
            String approvalLink = baseUrlService.buildUrl("/infra-requests/%s", request.getRequestId());
            createNotification(
                    request.getLineManagerId(),
                    Notification.NotificationType.REQUEST_STATUS,
                    "Infrastructure Request Pending Approval",
                    "Request #" + request.getRequestId() + " for " + request.getResourceType() + " requires your approval.",
                    approvalLink
            );
        });
    }

    private void notifyRequester(InfraRequest request, String message) {
        String requestLink = baseUrlService.buildUrl("/infra-requests/%s", request.getRequestId());
        createNotification(
                request.getRequesterId(),
                Notification.NotificationType.REQUEST_STATUS,
                "Infrastructure Request Update",
                message,
                requestLink
        );
    }

    private void notifyFinanceTeam(InfraRequest request) {
        log.info("Notifying finance team about request: {}", request.getRequestId());
        // Get finance users and notify them
        List<AppUser> financeUsers = userRepository.findUsersWithPermission("APPROVE_FINANCE");
        for (AppUser user : financeUsers) {
            String requestLink = baseUrlService.buildUrl("/infra-requests/%s", request.getRequestId());
            createNotification(
                    user.getUserId(),
                    Notification.NotificationType.REQUEST_STATUS,
                    "Infrastructure Request Pending Finance Approval",
                    "Request #" + request.getRequestId() + " for " + request.getResourceType() + " requires your approval.",
                    requestLink
            );
        }
    }

    private void sendEmailNotification(Long userId, String subject, String body) {
        userRepository.findById(userId).ifPresent(user -> {
            emailService.sendSimpleEmail(user.getEmail(), subject, body);
        });
    }

    private void sendCompletionReport(InfraRequest request) {
        log.info("Sending completion report for request: {}", request.getRequestId());
        // Implement PDF generation and email sending if needed
    }

    private void createNotification(Long userId, Notification.NotificationType type,
                                    String title, String message, String link) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type.name());
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLink(link != null ? link : "/infra-requests");
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    // ============================================
    // Conversion Methods
    // ============================================

    private InfraRequestDTO convertToDTO(InfraRequest request) {
        InfraRequestDTO dto = new InfraRequestDTO();
        dto.setRequestId(request.getRequestId());
        dto.setResourceType(request.getResourceType());
        dto.setSpecification(request.getSpecification());
        dto.setQuantity(request.getQuantity());
        dto.setJustification(request.getJustification());
        dto.setStatus(request.getStatus() != null ? request.getStatus().name() : null);
        dto.setCreatedAt(request.getCreatedAt());
        dto.setUpdatedAt(request.getUpdatedAt());
        dto.setRequesterId(request.getRequesterId());
        dto.setLineManagerId(request.getLineManagerId());
        dto.setLmApprovedAt(request.getLmApprovedAt());
        dto.setLmApprovedBy(request.getLmApprovedBy());
        dto.setLmComment(request.getLmComment());
        dto.setInfraReviewedAt(request.getInfraReviewedAt());
        dto.setInfraReviewedBy(request.getInfraReviewedBy());
        dto.setInfraComment(request.getInfraComment());
        dto.setFinanceApprovedAt(request.getFinanceApprovedAt());
        dto.setFinanceApprovedBy(request.getFinanceApprovedBy());
        dto.setFinanceComment(request.getFinanceComment());
        dto.setProcurementOrderRef(request.getProcurementOrderRef());
        dto.setSupplierId(request.getSupplierId());
        dto.setPurchaseCost(request.getPurchaseCost());
        dto.setDeliveredAt(request.getDeliveredAt());
        dto.setDeliveredBy(request.getDeliveredBy());
        dto.setAcknowledgedAt(request.getAcknowledgedAt());
        dto.setAcknowledgedBy(request.getAcknowledgedBy());
        dto.setCompletedAt(request.getCompletedAt());
        dto.setPdfReportPath(request.getPdfReportPath());
        dto.setAssetId(request.getAssetId());
        dto.setQuotationPath(request.getQuotationPath());
        dto.setRequesterSignature(request.getRequesterSignature());
        dto.setRequesterSignedAt(request.getRequesterSignedAt());
        dto.setSigningToken(request.getSigningToken());
        dto.setSigningTokenExpiry(request.getSigningTokenExpiry());

        // Get requester details with staff ID
        userRepository.findById(request.getRequesterId()).ifPresent(user -> {
            dto.setRequesterName(user.getFullName());
            dto.setRequesterDepartment(user.getDepartment());
            dto.setRequesterStaffId(user.getStaffId());
        });

        // Get line manager staff ID
        userRepository.findById(request.getLineManagerId()).ifPresent(user -> {
            dto.setLineManagerStaffId(user.getStaffId());
        });

        // Get approver staff IDs
        if (request.getLmApprovedBy() != null) {
            userRepository.findById(request.getLmApprovedBy()).ifPresent(user -> {
                dto.setLmApprovedByName(user.getFullName());
                dto.setLmApprovedByStaffId(user.getStaffId());
            });
        }

        if (request.getInfraReviewedBy() != null) {
            userRepository.findById(request.getInfraReviewedBy()).ifPresent(user -> {
                dto.setInfraReviewedByName(user.getFullName());
                dto.setInfraReviewedByStaffId(user.getStaffId());
            });
        }

        if (request.getFinanceApprovedBy() != null) {
            userRepository.findById(request.getFinanceApprovedBy()).ifPresent(user -> {
                dto.setFinanceApprovedByName(user.getFullName());
                dto.setFinanceApprovedByStaffId(user.getStaffId());
            });
        }

        // ============================================
        // ✅ ADD SLA TRACKING DATA
        // ============================================
        try {
            RequestSLATracking slaTracking = slaService.getSLAStatus(request.getRequestId(), "INFRA_REQUEST");
            if (slaTracking != null) {
                dto.setSlaStatus(slaTracking.getStatus());
                dto.setSlaStatusDisplay(slaTracking.getStatusDisplay());
                dto.setSlaPercentage(slaTracking.getPercentageComplete());
            } else {
                dto.setSlaStatus("N/A");
                dto.setSlaStatusDisplay("N/A");
                dto.setSlaPercentage(0.0);
            }
        } catch (Exception e) {
            log.warn("Could not fetch SLA status for request {}: {}", request.getRequestId(), e.getMessage());
            dto.setSlaStatus("N/A");
            dto.setSlaStatusDisplay("N/A");
            dto.setSlaPercentage(0.0);
        }

        return dto;
    }
}