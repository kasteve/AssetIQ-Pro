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

        ResourceRequest request = new ResourceRequest();
        request.setUserId(dto.getUserId());
        request.setRequestedBy(dto.getRequestedBy());
        request.setDescription(dto.getDescription());
        request.setResourceType(dto.getResourceType());
        request.setRequestTime(LocalDateTime.now());
        request.setStatus("PENDING");

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Send notification to admin
        emailService.sendResourceRequestNotification(
                "admin@company.com",  // Get from config
                "Resource Request Created",
                "User " + dto.getRequestedBy() + " requested: " + dto.getDescription()
        );

        auditService.logAction("RESOURCE_REQUEST_CREATED",
                "Resource request created by user: " + dto.getUserId() + ", type: " + dto.getResourceType(),
                dto.getUserId());

        return convertToDTO(saved);
    }

    @Transactional
    public ResourceRequestDTO acceptResourceRequest(Long requestId, String adminComment) {
        log.info("Accepting resource request: {}", requestId);

        ResourceRequest request = resourceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Resource request not found: " + requestId));

        request.setStatus("ACCEPTED");
        request.setFinalStatus("ACCEPTED");
        request.setAcceptedAt(LocalDateTime.now());
        request.setAdminComment(adminComment);

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Notify requester
        emailService.sendResourceRequestStatusUpdate(
                getUserEmail(request.getUserId()),
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

        ResourceRequest request = resourceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Resource request not found: " + requestId));

        request.setStatus("DECLINED");
        request.setFinalStatus("DECLINED");
        request.setDeclinedAt(LocalDateTime.now());
        request.setDeclinedReason(reason);

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Notify requester
        emailService.sendResourceRequestStatusUpdate(
                getUserEmail(request.getUserId()),
                "Resource Request Declined",
                "Your request for " + request.getResourceType() + " has been declined. Reason: " + reason
        );

        auditService.logAction("RESOURCE_REQUEST_DECLINED",
                "Resource request declined: " + requestId + " by admin",
                request.getUserId());

        return convertToDTO(saved);
    }

    // ============================================
    // Helper Methods
    // ============================================

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
        return dto;
    }

    private String getUserEmail(Long userId) {
        // TODO: Implement user email lookup
        return "user@company.com";
    }
}