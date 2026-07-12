package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.InfraRequestDTO;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.entity.InfraRequest.RequestStatus;
import com.stevecodes.AssetIQPro.entity.Notification;
import com.stevecodes.AssetIQPro.repository.InfraRequestRepository;
import com.stevecodes.AssetIQPro.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InfraRequestService {

    private final InfraRequestRepository requestRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final PdfGenerationService pdfGenerationService;

    // ============================================
    // Query Methods
    // ============================================

    public InfraRequest getRequestById(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));
    }

    public List<InfraRequest> getRequestsForUser(Long userId) {
        return requestRepository.findRequestsForUser(userId);
    }

    public List<InfraRequest> getRequestsByStatus(RequestStatus status) {
        return requestRepository.findByStatus(status);
    }

    public List<InfraRequest> getRequestsForApproval(Long approverId, String role) {
        // Implement based on role
        return List.of();
    }

    // ============================================
    // Request Lifecycle Management
    // ============================================

    @Transactional
    public InfraRequest createRequest(InfraRequestDTO dto, Long requesterId) {
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

        // Send notification to line manager
        notifyLineManager(saved);

        auditService.logAction("INFRA_REQUEST_CREATED",
                "Request #" + saved.getRequestId() + " created by user " + requesterId,
                requesterId);

        return saved;
    }

    @Transactional
    public InfraRequest approveByLineManager(Long requestId, Long managerId, String comment) {
        log.info("Line manager {} approving request: {}", managerId, requestId);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PENDING_LM_APPROVAL);

        request.setStatus(RequestStatus.PENDING_INFRA_REVIEW);
        request.setLmApprovedAt(LocalDateTime.now());
        request.setLmApprovedBy(managerId);
        request.setLmComment(comment);

        InfraRequest saved = requestRepository.save(request);

        // Notify requester and infra team
        notifyRequester(saved, "Your request has been approved by your line manager.");
        notifyInfraTeam(saved);

        auditService.logAction("INFRA_REQUEST_LM_APPROVED",
                "Request #" + requestId + " approved by line manager " + managerId,
                managerId);

        return saved;
    }

    @Transactional
    public InfraRequest rejectByLineManager(Long requestId, Long managerId, String reason) {
        log.info("Line manager {} rejecting request: {}", managerId, requestId);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PENDING_LM_APPROVAL);

        request.setStatus(RequestStatus.LM_REJECTED);
        request.setLmApprovedAt(LocalDateTime.now());
        request.setLmApprovedBy(managerId);
        request.setLmComment(reason);

        InfraRequest saved = requestRepository.save(request);

        notifyRequester(saved, "Your request has been rejected by your line manager. Reason: " + reason);

        auditService.logAction("INFRA_REQUEST_LM_REJECTED",
                "Request #" + requestId + " rejected by line manager " + managerId,
                managerId);

        return saved;
    }

    @Transactional
    public InfraRequest reviewByInfra(Long requestId, Long infraId, String comment, boolean approved) {
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

        auditService.logAction("INFRA_REQUEST_REVIEWED",
                "Request #" + requestId + " reviewed by infra " + infraId + " (approved: " + approved + ")",
                infraId);

        return saved;
    }

    @Transactional
    public InfraRequest approveByFinance(Long requestId, Long financeId, String comment) {
        log.info("Finance {} approving request: {}", financeId, requestId);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PENDING_FINANCE_APPROVAL);

        request.setStatus(RequestStatus.PROCUREMENT);
        request.setFinanceApprovedAt(LocalDateTime.now());
        request.setFinanceApprovedBy(financeId);
        request.setFinanceComment(comment);

        InfraRequest saved = requestRepository.save(request);

        notifyRequester(saved, "Your request has been approved by Finance and is now in procurement.");
        notifyProcurementTeam(saved);

        auditService.logAction("INFRA_REQUEST_FINANCE_APPROVED",
                "Request #" + requestId + " approved by finance " + financeId,
                financeId);

        return saved;
    }

    @Transactional
    public InfraRequest rejectByFinance(Long requestId, Long financeId, String reason) {
        log.info("Finance {} rejecting request: {}", financeId, requestId);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PENDING_FINANCE_APPROVAL);

        request.setStatus(RequestStatus.FINANCE_REJECTED);
        request.setFinanceApprovedAt(LocalDateTime.now());
        request.setFinanceApprovedBy(financeId);
        request.setFinanceComment(reason);

        InfraRequest saved = requestRepository.save(request);

        notifyRequester(saved, "Your request has been rejected by Finance. Reason: " + reason);

        auditService.logAction("INFRA_REQUEST_FINANCE_REJECTED",
                "Request #" + requestId + " rejected by finance " + financeId,
                financeId);

        return saved;
    }

    @Transactional
    public InfraRequest markDelivered(Long requestId, Long deliveredBy, String deliveryNotes) {
        log.info("Marking request {} as delivered by: {}", requestId, deliveredBy);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.PROCUREMENT);

        request.setStatus(RequestStatus.DELIVERED);
        request.setDeliveredAt(LocalDateTime.now());
        request.setDeliveredBy(deliveredBy);

        InfraRequest saved = requestRepository.save(request);

        // Generate PDF report
        try {
            byte[] pdfBytes = pdfGenerationService.generateInfraRequestReport(saved);
            // Save PDF and send email
        } catch (Exception e) {
            log.error("Failed to generate PDF for request: {}", requestId, e);
        }

        notifyRequester(saved, "Your requested items have been delivered. Please acknowledge receipt.");
        notifyFinanceTeamDelivery(saved);

        auditService.logAction("INFRA_REQUEST_DELIVERED",
                "Request #" + requestId + " marked as delivered by " + deliveredBy,
                deliveredBy);

        return saved;
    }

    @Transactional
    public InfraRequest acknowledgeReceipt(Long requestId, Long acknowledgedBy) {
        log.info("User {} acknowledging receipt for request: {}", acknowledgedBy, requestId);

        InfraRequest request = validateRequest(requestId);
        validateStatus(request, RequestStatus.DELIVERED);

        request.setStatus(RequestStatus.COMPLETED);
        request.setAcknowledgedAt(LocalDateTime.now());
        request.setAcknowledgedBy(acknowledgedBy);

        InfraRequest saved = requestRepository.save(request);

        // Send final completion email with PDF
        sendCompletionReport(saved);

        auditService.logAction("INFRA_REQUEST_COMPLETED",
                "Request #" + requestId + " completed - acknowledged by " + acknowledgedBy,
                acknowledgedBy);

        return saved;
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
        // Send email to line manager
        String approvalLink = "https://assetiq.company.com/infra-requests/" + request.getRequestId() + "/approve";
        emailService.sendInfraRequestApproval(
                "line_manager@company.com",  // Get from user service
                "Line Manager",
                request.getRequestId().toString(),
                "Requester Name",  // Get from user service
                request.getResourceType(),
                approvalLink
        );

        // Create notification
        createNotification(
                request.getLineManagerId(),
                Notification.NotificationType.REQUEST_STATUS,
                "Infrastructure Request Pending Approval",
                "Request #" + request.getRequestId() + " for " + request.getResourceType() + " requires your approval.",
                approvalLink
        );
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
        // Get all users with INFRA permission
        String reviewLink = "https://assetiq.company.com/infra-requests/" + request.getRequestId() + "/review";
        // Send email to infra team
        createNotification(
                null,  // Will be handled by permission-based notification
                Notification.NotificationType.REQUEST_STATUS,
                "New Infrastructure Request for Review",
                "Request #" + request.getRequestId() + " for " + request.getResourceType() + " is ready for review.",
                reviewLink
        );
    }

    private void notifyFinanceTeam(InfraRequest request) {
        // Similar to notifyInfraTeam
    }

    private void notifyProcurementTeam(InfraRequest request) {
        // Similar to notifyInfraTeam
    }

    private void notifyFinanceTeamDelivery(InfraRequest request) {
        // Notify finance that items have been delivered
    }

    private void sendCompletionReport(InfraRequest request) {
        // Generate and send final PDF report to all parties
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
}