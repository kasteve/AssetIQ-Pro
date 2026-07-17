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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceRequestService {

    private final ResourceRequestRepository resourceRequestRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppUserService appUserService;
    private final PdfGenerationService pdfGenerationService;

    private static final String REPORT_DIR = "uploads/resources/reports/";

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

    public List<ResourceRequestDTO> getAcceptedResourceRequests() {
        log.info("Getting accepted resource requests");
        return resourceRequestRepository.findByStatus("ACCEPTED")
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ResourceRequestDTO> getCompletedResourceRequests() {
        log.info("Getting completed resource requests");
        return resourceRequestRepository.findByStatus("COMPLETED")
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ResourceRequestDTO> getDeclinedResourceRequests() {
        log.info("Getting declined resource requests");
        return resourceRequestRepository.findByStatus("REJECTED")
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ResourceRequestDTO getResourceRequestById(Long requestId) {
        log.info("Getting resource request by id: {}", requestId);
        ResourceRequest request = validateRequest(requestId);
        return convertToDTO(request);
    }

    public ResourceRequest getResourceRequestEntityById(Long requestId) {
        return validateRequest(requestId);
    }

    public ResourceRequest getResourceRequestBySigningToken(String token) {
        return resourceRequestRepository.findBySigningToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));
    }

    public long countPendingRequests() {
        return resourceRequestRepository.countByStatus("PENDING");
    }

    public long countAcceptedRequests() {
        return resourceRequestRepository.countByStatus("ACCEPTED");
    }

    public long countCompletedRequests() {
        return resourceRequestRepository.countByStatus("COMPLETED");
    }

    public long countDeclinedRequests() {
        return resourceRequestRepository.countByStatus("REJECTED");
    }

    // ============================================
    // Request Management - Direct Admin Approval
    // ============================================

    @Transactional
    public ResourceRequestDTO createResourceRequest(ResourceRequestDTO dto) {
        log.info("Creating resource request for user: {}", dto.getUserId());

        ResourceRequest request = new ResourceRequest();
        request.setUserId(dto.getUserId());
        request.setRequestedBy(dto.getRequestedBy());
        request.setDescription(dto.getDescription());
        request.setResourceType(dto.getResourceType());
        request.setQuantity(dto.getQuantity() != null ? dto.getQuantity() : 1);
        request.setJustification(dto.getJustification());
        request.setRequestTime(LocalDateTime.now());
        request.setStatus("PENDING");

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Notify admin
        emailService.sendResourceRequestNotification(
                "admin@company.com",
                "New Resource Request Pending Approval",
                "User " + dto.getRequestedBy() + " requested: " + dto.getResourceType() +
                        " - " + dto.getDescription()
        );

        auditService.logAction("RESOURCE_REQUEST_CREATED",
                "Resource request created by user: " + dto.getUserId() + ", type: " + dto.getResourceType(),
                dto.getUserId());

        return convertToDTO(saved);
    }

    @Transactional
    public ResourceRequestDTO createResourceRequest(Long userId, String requestedBy, String description,
                                                    String resourceType, Integer quantity, String justification) {
        ResourceRequestDTO dto = new ResourceRequestDTO();
        dto.setUserId(userId);
        dto.setRequestedBy(requestedBy);
        dto.setDescription(description);
        dto.setResourceType(resourceType);
        dto.setQuantity(quantity != null ? quantity : 1);
        dto.setJustification(justification);
        return createResourceRequest(dto);
    }

    @Transactional
    public ResourceRequestDTO acceptResourceRequest(Long requestId, String adminComment) {
        log.info("Admin accepting resource request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not pending approval. Current status: " + request.getStatus());
        }

        request.setStatus("ACCEPTED");
        request.setAcceptedAt(LocalDateTime.now());
        request.setAdminComment(adminComment);

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Notify requester
        String requesterEmail = getEmailForUser(request.getUserId());
        emailService.sendResourceRequestStatusUpdate(
                requesterEmail,
                "Resource Request Accepted",
                "Your request for " + request.getResourceType() + " has been accepted by the administrator."
        );

        auditService.logAction("RESOURCE_REQUEST_ACCEPTED",
                "Resource request accepted: " + requestId + " by admin",
                request.getUserId());

        return convertToDTO(saved);
    }

    @Transactional
    public ResourceRequestDTO declineResourceRequest(Long requestId, String reason) {
        log.info("Admin declining resource request: {} - Reason: {}", requestId, reason);

        ResourceRequest request = validateRequest(requestId);
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not pending approval. Current status: " + request.getStatus());
        }

        request.setStatus("REJECTED");
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
    public ResourceRequestDTO completeResourceRequest(Long requestId, String deliveryNotes) {
        log.info("Admin completing resource request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);
        if (!"ACCEPTED".equals(request.getStatus())) {
            throw new IllegalStateException("Request must be ACCEPTED to complete. Current status: " + request.getStatus());
        }

        request.setStatus("COMPLETED");
        request.setCompletedAt(LocalDateTime.now());
        request.setDeliveryNotes(deliveryNotes);

        // Generate signing token
        String token = UUID.randomUUID().toString();
        request.setSigningToken(token);
        request.setSigningTokenExpiry(LocalDateTime.now().plusHours(48));

        ResourceRequest saved = resourceRequestRepository.save(request);

        // Generate PDF
        try {
            byte[] pdfBytes = pdfGenerationService.generateResourceRequestReport(saved);
            String pdfPath = savePdfToFile(pdfBytes, requestId);
            saved.setPdfReportPath(pdfPath);
            resourceRequestRepository.save(saved);
            log.info("PDF generated for resource request: {}", requestId);
        } catch (Exception e) {
            log.error("Failed to generate PDF for resource request {}: {}", requestId, e.getMessage());
        }

        // Notify requester with signature link
        String requesterEmail = getEmailForUser(request.getUserId());
        String signatureLink = "http://localhost:8091/assetIQ-pro/resources/sign?token=" + token;
        emailService.sendResourceRequestStatusUpdate(
                requesterEmail,
                "Resource Request Completed - Please Sign",
                "Your request for " + request.getResourceType() + " has been completed.\n\n" +
                        "Please sign to acknowledge receipt: " + signatureLink
        );

        auditService.logAction("RESOURCE_REQUEST_COMPLETED",
                "Resource request completed: " + requestId + " by admin",
                request.getUserId());

        return convertToDTO(saved);
    }

    @Transactional
    public void saveRequesterSignature(Long requestId, String token, String signature, String signatoryName) {
        log.info("Saving signature for resource request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);

        if (!token.equals(request.getSigningToken())) {
            throw new RuntimeException("Invalid token");
        }
        if (request.getSigningTokenExpiry() == null ||
                request.getSigningTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token has expired");
        }

        request.setRequesterSignature(signature);
        request.setSignatoryName(signatoryName);
        request.setAcknowledgedAt(LocalDateTime.now());
        request.setSigningToken(null);
        request.setSigningTokenExpiry(null);

        // Regenerate PDF with signature
        try {
            byte[] pdfBytes = pdfGenerationService.generateResourceRequestReport(request);
            String pdfPath = savePdfToFile(pdfBytes, requestId);
            request.setPdfReportPath(pdfPath);
            log.info("PDF regenerated with signature for request: {}", requestId);
        } catch (Exception e) {
            log.error("Failed to regenerate PDF for request {}: {}", requestId, e.getMessage());
        }

        resourceRequestRepository.save(request);

        auditService.logAction("RESOURCE_REQUEST_SIGNED",
                "Resource request signed by: " + signatoryName,
                request.getUserId());
    }

    @Transactional
    public void acknowledgeReceipt(Long requestId, Long userId, String signature, String signatoryName) {
        log.info("User {} acknowledging receipt for resource request: {}", userId, requestId);

        ResourceRequest request = validateRequest(requestId);

        if (!"COMPLETED".equals(request.getStatus())) {
            throw new IllegalStateException("Request must be COMPLETED to acknowledge. Current status: " + request.getStatus());
        }

        if (request.getRequesterSignature() != null) {
            throw new IllegalStateException("Request has already been acknowledged");
        }

        if (!request.getUserId().equals(userId)) {
            throw new IllegalStateException("You are not authorized to sign this request");
        }

        request.setRequesterSignature(signature);
        request.setSignatoryName(signatoryName);
        request.setAcknowledgedAt(LocalDateTime.now());
        request.setAcknowledgedBy(userId);
        request.setSigningToken(null);
        request.setSigningTokenExpiry(null);

        try {
            byte[] pdfBytes = pdfGenerationService.generateResourceRequestReport(request);
            String pdfPath = savePdfToFile(pdfBytes, requestId);
            request.setPdfReportPath(pdfPath);
            log.info("PDF regenerated with signature for request: {}", requestId);
        } catch (Exception e) {
            log.error("Failed to regenerate PDF for request {}: {}", requestId, e.getMessage());
        }

        resourceRequestRepository.save(request);

        String requesterEmail = getEmailForUser(userId);
        emailService.sendResourceRequestStatusUpdate(
                requesterEmail,
                "Resource Request Signed - Completed",
                "Thank you for signing the receipt for your resource request #" + requestId +
                        ".\n\nA PDF report has been generated and is available for download."
        );

        auditService.logAction("RESOURCE_REQUEST_ACKNOWLEDGED",
                "Resource request acknowledged by user: " + userId + ", signatory: " + signatoryName,
                userId);
    }

    @Transactional
    public void recallRequest(Long requestId) {
        log.info("Recalling resource request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);
        String status = request.getStatus();

        if (!"PENDING".equals(status) && !"ACCEPTED".equals(status)) {
            throw new IllegalStateException("Cannot recall - request already processed");
        }

        request.setStatus("RECALLED");
        resourceRequestRepository.save(request);

        auditService.logAction("RESOURCE_REQUEST_RECALLED",
                "Resource request recalled: " + requestId,
                request.getUserId());
    }

    // ============================================
    // PDF Generation Methods
    // ============================================

    private String savePdfToFile(byte[] pdfBytes, Long requestId) throws java.io.IOException {
        java.nio.file.Path uploadPath = java.nio.file.Paths.get(REPORT_DIR);
        if (!java.nio.file.Files.exists(uploadPath)) {
            java.nio.file.Files.createDirectories(uploadPath);
        }

        String filename = "resource_request_" + requestId + "_" + System.currentTimeMillis() + ".pdf";
        java.nio.file.Path filePath = uploadPath.resolve(filename);
        java.nio.file.Files.write(filePath, pdfBytes);
        return filePath.toString();
    }

    // ============================================
    // Helper Methods
    // ============================================

    private ResourceRequest validateRequest(Long requestId) {
        return resourceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Resource request not found: " + requestId));
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
        dto.setQuantity(request.getQuantity());
        dto.setJustification(request.getJustification());
        dto.setRequestTime(request.getRequestTime());
        dto.setStatus(request.getStatus());
        dto.setAcceptedAt(request.getAcceptedAt());
        dto.setDeclinedAt(request.getDeclinedAt());
        dto.setDeclinedReason(request.getDeclinedReason());
        dto.setAdminComment(request.getAdminComment());
        dto.setAcknowledgedAt(request.getAcknowledgedAt());
        dto.setAcknowledgedBy(request.getAcknowledgedBy());
        dto.setRequesterSignature(request.getRequesterSignature());
        dto.setSignatoryName(request.getSignatoryName());
        dto.setDeliveryNotes(request.getDeliveryNotes());
        dto.setCompletedAt(request.getCompletedAt());
        dto.setSigningToken(request.getSigningToken());
        dto.setSigningTokenExpiry(request.getSigningTokenExpiry());
        dto.setPdfReportPath(request.getPdfReportPath());
        return dto;
    }
}