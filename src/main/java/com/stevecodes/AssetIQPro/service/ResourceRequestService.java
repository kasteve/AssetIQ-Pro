package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.ResourceRequestDTO;
import com.stevecodes.AssetIQPro.entity.ResourceRequest;
import com.stevecodes.AssetIQPro.repository.ResourceRequestRepository;
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
public class ResourceRequestService {

    private final ResourceRequestRepository resourceRequestRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppUserService appUserService;

    // ============================================
    // Query Methods
    // ============================================

    public List<ResourceRequestDTO> getAllResourceRequests() {
        log.info("Getting all resource requests");
        return resourceRequestRepository.findAllByOrderByRequestTimeDesc()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ResourceRequestDTO> getResourceRequestsByUserId(Long userId) {
        log.info("Getting resource requests for user: {}", userId);
        return resourceRequestRepository.findByUserId(userId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ResourceRequestDTO> getPendingResourceRequests() {
        log.info("Getting pending resource requests");
        return resourceRequestRepository.findByStatus("PENDING")
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ResourceRequestDTO> getResourceRequestsByLineManager(Long lmId) {
        log.info("Getting resource requests for line manager: {}", lmId);
        return resourceRequestRepository.findByLineManagerId(lmId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ResourceRequestDTO getResourceRequestById(Long requestId) {
        log.info("Getting resource request by id: {}", requestId);
        ResourceRequest request = resourceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Resource request not found: " + requestId));
        return convertToDTO(request);
    }

    public long countPendingRequests() {
        return resourceRequestRepository.countByStatus("PENDING");
    }

    // ============================================
    // Request Management
    // ============================================

    @Transactional
    public ResourceRequestDTO createResourceRequest(ResourceRequestDTO dto) {
        log.info("Creating resource request for user: {}", dto.getUserId());

        // Get requester's line manager
        Long lineManagerId = getLineManagerId(dto.getUserId());

        ResourceRequest request = new ResourceRequest();
        request.setUserId(dto.getUserId());
        request.setRequestedBy(dto.getRequestedBy());
        request.setDescription(dto.getDescription());
        request.setResourceType(dto.getResourceType());
        request.setRequestTime(LocalDateTime.now());
        request.setStatus("PENDING_LM_APPROVAL");
        request.setLineManagerId(lineManagerId);

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Notify line manager
        String lmEmail = getEmailForUser(lineManagerId);
        emailService.sendResourceRequestNotification(
                lmEmail,
                "Resource Request Pending Approval",
                "User " + dto.getRequestedBy() + " requested: " + dto.getDescription()
        );

        auditService.logAction("RESOURCE_REQUEST_CREATED",
                "Resource request created by user: " + dto.getUserId() + ", type: " + dto.getResourceType(),
                dto.getUserId());

        return convertToDTO(saved);
    }

    @Transactional
    public ResourceRequestDTO createResourceRequest(Long userId, String requestedBy, String description,
                                                    String resourceType, Integer quantity) {
        ResourceRequestDTO dto = new ResourceRequestDTO();
        dto.setUserId(userId);
        dto.setRequestedBy(requestedBy);
        dto.setDescription(description);
        dto.setResourceType(resourceType);
        return createResourceRequest(dto);
    }

    @Transactional
    public ResourceRequestDTO approveByLineManager(Long requestId, Long lmId, String comment) {
        log.info("Line manager {} approving resource request: {}", lmId, requestId);

        ResourceRequest request = validateRequest(requestId);
        if (!"PENDING_LM_APPROVAL".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not pending LM approval");
        }

        request.setStatus("PENDING_ADMIN_APPROVAL");
        request.setLmApprovedBy(lmId);
        request.setLmApprovedAt(LocalDateTime.now());
        request.setLmComment(comment);

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Notify admin
        emailService.sendResourceRequestNotification(
                "admin@company.com",
                "Resource Request Pending Admin Approval",
                "Request from " + request.getRequestedBy() + " for " + request.getResourceType()
        );

        auditService.logAction("RESOURCE_REQUEST_LM_APPROVED",
                "Resource request approved by LM: " + requestId,
                lmId);

        return convertToDTO(saved);
    }

    @Transactional
    public ResourceRequestDTO rejectByLineManager(Long requestId, Long lmId, String reason) {
        log.info("Line manager {} rejecting resource request: {}", lmId, requestId);

        ResourceRequest request = validateRequest(requestId);
        if (!"PENDING_LM_APPROVAL".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not pending LM approval");
        }

        request.setStatus("REJECTED");
        request.setLmApprovedBy(lmId);
        request.setLmApprovedAt(LocalDateTime.now());
        request.setDeclinedReason(reason);

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Notify requester
        String requesterEmail = getEmailForUser(request.getUserId());
        emailService.sendResourceRequestStatusUpdate(
                requesterEmail,
                "Resource Request Rejected by Line Manager",
                "Your request for " + request.getResourceType() + " has been rejected. Reason: " + reason
        );

        auditService.logAction("RESOURCE_REQUEST_LM_REJECTED",
                "Resource request rejected by LM: " + requestId,
                lmId);

        return convertToDTO(saved);
    }

    @Transactional
    public ResourceRequestDTO acceptResourceRequest(Long requestId, String adminComment) {
        log.info("Accepting resource request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);
        if (!"PENDING_ADMIN_APPROVAL".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not pending admin approval");
        }

        request.setStatus("ACCEPTED");
        request.setFinalStatus("ACCEPTED");
        request.setAcceptedAt(LocalDateTime.now());
        request.setAdminComment(adminComment);

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Notify requester
        String requesterEmail = getEmailForUser(request.getUserId());
        emailService.sendResourceRequestStatusUpdate(
                requesterEmail,
                "Resource Request Accepted",
                "Your request for " + request.getResourceType() + " has been accepted."
        );

        auditService.logAction("RESOURCE_REQUEST_ACCEPTED",
                "Resource request accepted: " + requestId + " by admin",
                request.getUserId());

        return convertToDTO(saved);
    }

    @Transactional
    public ResourceRequestDTO declineResourceRequest(Long requestId, String reason) {
        log.info("Declining resource request: {} - Reason: {}", requestId, reason);

        ResourceRequest request = validateRequest(requestId);
        if (!"PENDING_ADMIN_APPROVAL".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not pending admin approval");
        }

        request.setStatus("REJECTED");
        request.setFinalStatus("REJECTED");
        request.setDeclinedAt(LocalDateTime.now());
        request.setDeclinedReason(reason);

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Notify requester
        String requesterEmail = getEmailForUser(request.getUserId());
        emailService.sendResourceRequestStatusUpdate(
                requesterEmail,
                "Resource Request Declined",
                "Your request for " + request.getResourceType() + " has been declined. Reason: " + reason
        );

        auditService.logAction("RESOURCE_REQUEST_DECLINED",
                "Resource request declined: " + requestId + " by admin",
                request.getUserId());

        return convertToDTO(saved);
    }

    @Transactional
    public void acknowledgeReceipt(Long requestId, Long userId, String signature) {
        log.info("User {} acknowledging receipt for resource request: {}", userId, requestId);

        ResourceRequest request = validateRequest(requestId);
        if (!"ACCEPTED".equals(request.getStatus())) {
            throw new IllegalStateException("Request must be accepted to acknowledge");
        }

        request.setAcknowledgedAt(LocalDateTime.now());
        request.setAcknowledgedBy(userId);
        request.setRequesterSignature(signature);
        request.setStatus("COMPLETED");

        resourceRequestRepository.save(request);

        auditService.logAction("RESOURCE_REQUEST_ACKNOWLEDGED",
                "Resource request acknowledged: " + requestId + " by user: " + userId,
                userId);
    }

    @Transactional
    public void recallRequest(Long requestId) {
        log.info("Recalling resource request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);
        String status = request.getStatus();

        if (!"PENDING_LM_APPROVAL".equals(status) && !"PENDING_ADMIN_APPROVAL".equals(status)) {
            throw new IllegalStateException("Cannot recall - request already processed");
        }

        request.setStatus("RECALLED");
        resourceRequestRepository.save(request);

        auditService.logAction("RESOURCE_REQUEST_RECALLED",
                "Resource request recalled: " + requestId,
                request.getUserId());
    }

    // ============================================
    // Helper Methods
    // ============================================

    private ResourceRequest validateRequest(Long requestId) {
        return resourceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Resource request not found: " + requestId));
    }

    private Long getLineManagerId(Long userId) {
        // Get employee for user, then get their line manager
        // This is a placeholder - implement based on your Employee-LineManager relationship
        return 1L; // Placeholder
    }

    private String getEmailForUser(Long userId) {
        return appUserService.getUserById(userId)
                .map(user -> user.getEmail())
                .orElse("user@company.com");
    }

    private ResourceRequestDTO convertToDTO(ResourceRequest request) {
        ResourceRequestDTO dto = new ResourceRequestDTO();
        dto.setRequestId(request.getRequestId());
        dto.setUserId(request.getUserId());
        dto.setRequestedBy(request.getRequestedBy());
        dto.setDescription(request.getDescription());
        dto.setResourceType(request.getResourceType());
        dto.setRequestTime(request.getRequestTime());
        dto.setStatus(request.getStatus());
        dto.setFinalStatus(request.getFinalStatus());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setAcceptedAt(request.getAcceptedAt());
        dto.setDeclinedAt(request.getDeclinedAt());
        dto.setDeclinedReason(request.getDeclinedReason());
        dto.setAdminComment(request.getAdminComment());
        dto.setLmComment(request.getLmComment());
        dto.setLmApprovedAt(request.getLmApprovedAt());
        dto.setLmApprovedBy(request.getLmApprovedBy());
        dto.setAcknowledgedAt(request.getAcknowledgedAt());
        dto.setAcknowledgedBy(request.getAcknowledgedBy());
        dto.setRequesterSignature(request.getRequesterSignature());
        return dto;
    }
}