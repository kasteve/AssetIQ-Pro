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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    private static final String UPLOAD_DIR = "./uploads/infra/quotations/";

    // ============================================
    // Query Methods
    // ============================================

    public InfraRequestDTO getRequestById(Long requestId) {
        InfraRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));
        return convertToDTO(request);
    }

    public List<InfraRequestDTO> getRequestsForUser(Long userId) {
        return requestRepository.findRequestsForUser(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<InfraRequestDTO> getRequestsByStatus(RequestStatus status) {
        return requestRepository.findByStatus(status).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<InfraRequestDTO> getAllRequests() {
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

    // ============================================
    // Request Lifecycle Management
    // ============================================

    @Transactional
    public InfraRequestDTO createRequest(InfraRequestDTO dto, Long requesterId) {
        log.info("Creating infrastructure request for user: {}", requesterId);

        // Get requester's line manager from Employee table
        Employee employee = employeeRepository.findByUserId(requesterId)
                .orElseThrow(() -> new RuntimeException("Employee not found for user ID: " + requesterId));

        Long lineManagerId = null;
        if (employee.getLineManager() != null) {
            Employee lineManager = employee.getLineManager();
            if (lineManager.getUser() != null) {
                lineManagerId = lineManager.getUser().getUserId();
            }
        }

        if (lineManagerId == null) {
            throw new RuntimeException("Employee has no line manager assigned");
        }

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

        // Notify line manager - catch email errors so request creation doesn't fail
        try {
            notifyLineManager(saved);
            sendEmailNotification(lineManagerId,
                    "Infrastructure Request Pending Approval",
                    "Request #" + saved.getRequestId() + " for " + saved.getResourceType() + " requires your approval.");
        } catch (Exception e) {
            log.error("Failed to send email notification for request {}: {}", saved.getRequestId(), e.getMessage());
            // In-app notification is already created in notifyLineManager
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
            log.error("Failed to send email for approval: {}", e.getMessage());
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
            log.error("Failed to send email for rejection: {}", e.getMessage());
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
            notifyFinanceTeam(request);
        } else {
            request.setStatus(RequestStatus.INFRA_REJECTED);
            try {
                notifyRequester(request, "Your request has been rejected by Infrastructure. Reason: " + comment);
                sendEmailNotification(request.getRequesterId(),
                        "Infrastructure Request Rejected by Infrastructure",
                        "Your request #" + request.getRequestId() + " has been rejected by Infrastructure. Reason: " + comment);
            } catch (Exception e) {
                log.error("Failed to send email for rejection: {}", e.getMessage());
            }
        }

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        auditService.logAction("INFRA_REQUEST_REVIEWED",
                "Request #" + requestId + " reviewed by infra " + infraId + " (approved: " + approved + ")",
                infraId);

        return result;
    }

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
            log.error("Failed to send email for approval: {}", e.getMessage());
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
            log.error("Failed to send email for rejection: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_FINANCE_REJECTED",
                "Request #" + requestId + " rejected by finance " + financeId,
                financeId);

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

    @Transactional
    public InfraRequestDTO markDelivered(Long requestId, Long deliveredBy, String deliveryNotes) {
        log.info("Marking request {} as delivered by: {}", requestId, deliveredBy);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PROCUREMENT);

        request.setStatus(RequestStatus.DELIVERED);
        request.setDeliveredAt(LocalDateTime.now());
        request.setDeliveredBy(deliveredBy);

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        try {
            notifyRequester(saved, "Your requested items have been delivered. Please acknowledge receipt.");
            sendEmailNotification(saved.getRequesterId(),
                    "Infrastructure Request Delivered",
                    "Your request #" + saved.getRequestId() + " has been delivered. Please acknowledge receipt.");
        } catch (Exception e) {
            log.error("Failed to send email for delivery: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_DELIVERED",
                "Request #" + requestId + " marked as delivered by " + deliveredBy,
                deliveredBy);

        return result;
    }

    @Transactional
    public InfraRequestDTO acknowledgeReceipt(Long requestId, Long acknowledgedBy) {
        log.info("User {} acknowledging receipt for request: {}", acknowledgedBy, requestId);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.DELIVERED);

        request.setStatus(RequestStatus.COMPLETED);
        request.setAcknowledgedAt(LocalDateTime.now());
        request.setAcknowledgedBy(acknowledgedBy);

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        try {
            sendCompletionReport(saved);
            sendEmailNotification(saved.getRequesterId(),
                    "Infrastructure Request Completed",
                    "Your request #" + saved.getRequestId() + " has been completed. Thank you for using AssetIQ-Pro.");
        } catch (Exception e) {
            log.error("Failed to send email for completion: {}", e.getMessage());
        }

        auditService.logAction("INFRA_REQUEST_COMPLETED",
                "Request #" + requestId + " completed - acknowledged by " + acknowledgedBy,
                acknowledgedBy);

        return result;
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

    private void notifyLineManager(InfraRequest request) {
        userRepository.findById(request.getLineManagerId()).ifPresent(manager -> {
            String approvalLink = "/infra-requests/" + request.getRequestId();
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
        createNotification(
                request.getRequesterId(),
                Notification.NotificationType.REQUEST_STATUS,
                "Infrastructure Request Update",
                message,
                "/infra-requests/" + request.getRequestId()
        );
    }

    private void notifyInfraTeam(InfraRequest request) {
        log.info("Notifying infra team about request: {}", request.getRequestId());
    }

    private void notifyFinanceTeam(InfraRequest request) {
        log.info("Notifying finance team about request: {}", request.getRequestId());
    }

    private void notifyProcurementTeam(InfraRequest request) {
        log.info("Notifying procurement team about request: {}", request.getRequestId());
    }

    private void sendEmailNotification(Long userId, String subject, String body) {
        userRepository.findById(userId).ifPresent(user -> {
            emailService.sendSimpleEmail(user.getEmail(), subject, body);
        });
    }

    private void notifyFinanceTeamDelivery(InfraRequest request) {
        log.info("Notifying finance team about delivery: {}", request.getRequestId());
    }

    private void sendCompletionReport(InfraRequest request) {
        log.info("Sending completion report for request: {}", request.getRequestId());
    }

    private void createNotification(Long userId, Notification.NotificationType type,
                                    String title, String message, String link) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type.name());
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setLink(link);
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

        return dto;
    }
}