package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.entity.InfraRequest.RequestStatus;
import com.stevecodes.AssetIQPro.entity.Notification;
import com.stevecodes.AssetIQPro.repository.InfraRequestRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InfraRequestService {

    private final InfraRequestRepository requestRepository;
    private final NotificationRepository notificationRepository;
    private final AppUserRepository userRepository;  // ADD THIS
    private final EmailService emailService;
    private final AuditService auditService;
    private final PdfGenerationService pdfGenerationService;

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

        InfraRequest request = new InfraRequest();
        request.setRequesterId(requesterId);
        request.setLineManagerId(dto.getLineManagerId());
        request.setResourceType(dto.getResourceType());
        request.setSpecification(dto.getSpecification());
        request.setQuantity(dto.getQuantity());
        request.setJustification(dto.getJustification());
        request.setStatus(RequestStatus.PENDING_LM_APPROVAL);

        InfraRequest saved = requestRepository.save(request);
        InfraRequestDTO result = convertToDTO(saved);

        notifyLineManager(saved);
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

        notifyRequester(saved, "Your request has been approved by your line manager.");
        notifyInfraTeam(saved);

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

        notifyRequester(saved, "Your request has been rejected by your line manager. Reason: " + reason);

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
            notifyRequester(request, "Your request has been rejected by Infrastructure. Reason: " + comment);
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

        notifyRequester(saved, "Your request has been approved by Finance and is now in procurement.");
        notifyProcurementTeam(saved);

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

        notifyRequester(saved, "Your request has been rejected by Finance. Reason: " + reason);

        auditService.logAction("INFRA_REQUEST_FINANCE_REJECTED",
                "Request #" + requestId + " rejected by finance " + financeId,
                financeId);

        return result;
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

        notifyRequester(saved, "Your requested items have been delivered. Please acknowledge receipt.");
        notifyFinanceTeamDelivery(saved);

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

        sendCompletionReport(saved);

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

        // Get requester name
        userRepository.findById(request.getRequesterId()).ifPresent(user -> {
            dto.setRequesterName(user.getFullName());
            dto.setRequesterDepartment(user.getDepartment());
        });

        return dto;
    }
}