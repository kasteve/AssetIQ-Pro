package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.dto.ResourceRequestDTO;
import com.stevecodes.AssetIQPro.entity.RequestSLATracking;
import com.stevecodes.AssetIQPro.entity.ResourceRequest;
import com.stevecodes.AssetIQPro.entity.StockItem;
import com.stevecodes.AssetIQPro.repository.ResourceRequestRepository;
import com.stevecodes.AssetIQPro.repository.StockItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceRequestService {

    private final ResourceRequestRepository resourceRequestRepository;
    private final StockItemRepository stockItemRepository;
    private final StockService stockService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final AppUserService appUserService;
    private final PdfGenerationService pdfGenerationService;
    private final BaseUrlService baseUrlService;
    private final SLAService slaService;
    private final AsyncNotificationService asyncNotificationService;
    private final AsyncAuditService asyncAuditService;
    private final AsyncPdfService asyncPdfService;

    private static final String REPORT_DIR = "uploads/resources/reports/";

    // ============================================
    // Query Methods (unchanged - synchronous)
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
        return resourceRequestRepository.findByUserIdOrderByRequestTimeDesc(userId)
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
    // Request Management (Async-optimized)
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

        if (dto.getStockItemId() != null) {
            request.setStockItemId(dto.getStockItemId());
            StockItem stockItem = stockItemRepository.findById(dto.getStockItemId()).orElse(null);
            if (stockItem != null) {
                request.setStockItemName(stockItem.getName());
                log.info("Linked request to stock item: {} (ID: {})", stockItem.getName(), stockItem.getId());
            }
        }

        // ✅ IMMEDIATE: Save to database
        ResourceRequest saved = resourceRequestRepository.save(request);
        log.info("✅ Resource request created with ID: {}", saved.getRequestId());

        // ✅ ASYNC: Start SLA tracking
        CompletableFuture.runAsync(() -> {
            try {
                slaService.startSLATracking(saved.getRequestId(), "RESOURCE_REQUEST", dto.getUserId());
                log.info("SLA tracking started for resource request: {}", saved.getRequestId());
            } catch (Exception e) {
                log.error("Failed to start SLA tracking: {}", e.getMessage());
            }
        });

        // ✅ ASYNC: Send email notification
        String stockInfo = request.getStockItemName() != null ?
                "\nStock Item: " + request.getStockItemName() + "\n" : "";
        emailService.sendResourceRequestNotification(
                "admin@company.com",
                "New Resource Request Pending Approval",
                "User " + dto.getRequestedBy() + " requested: " + dto.getResourceType() +
                        " - " + dto.getDescription() + "\n" +
                        "Quantity: " + request.getQuantity() + "\n" +
                        stockInfo
        );

        // ✅ ASYNC: Create notification
        asyncNotificationService.createNotificationAsync(
                dto.getUserId(),
                "RESOURCE_REQUEST_CREATED",
                "Resource Request Created",
                "Your resource request for " + dto.getResourceType() + " has been submitted.",
                "/resources"
        );

        // ✅ ASYNC: Audit log
        asyncAuditService.logActionAsync(
                "RESOURCE_REQUEST_CREATED",
                "Resource request created by user: " + dto.getUserId() + ", type: " + dto.getResourceType() +
                        (request.getStockItemId() != null ? ", stock item: " + request.getStockItemName() : ""),
                dto.getUserId()
        );

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
    public ResourceRequestDTO createResourceRequest(Long userId, String requestedBy, String description,
                                                    String resourceType, Integer quantity, String justification,
                                                    Long stockItemId) {
        ResourceRequestDTO dto = new ResourceRequestDTO();
        dto.setUserId(userId);
        dto.setRequestedBy(requestedBy);
        dto.setDescription(description);
        dto.setResourceType(resourceType);
        dto.setQuantity(quantity != null ? quantity : 1);
        dto.setJustification(justification);
        dto.setStockItemId(stockItemId);
        return createResourceRequest(dto);
    }

    @Transactional
    public ResourceRequestDTO acceptResourceRequest(Long requestId, String adminComment) {
        log.info("Admin accepting resource request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not pending approval. Current status: " + request.getStatus());
        }

        if (request.getStockItemId() != null && request.getQuantity() != null && request.getQuantity() > 0) {
            StockItem stockItem = stockItemRepository.findById(request.getStockItemId()).orElse(null);
            if (stockItem != null) {
                if (stockItem.getQuantity() < request.getQuantity()) {
                    throw new IllegalStateException(
                            "Insufficient stock for '" + stockItem.getName() + "'. " +
                                    "Available: " + stockItem.getQuantity() + ", Requested: " + request.getQuantity()
                    );
                }
                log.info("Stock check passed. Available: {}, Requested: {}", stockItem.getQuantity(), request.getQuantity());
            }
        }

        // ✅ IMMEDIATE: Update request status
        request.setStatus("ACCEPTED");
        request.setAcceptedAt(LocalDateTime.now());
        request.setAdminComment(adminComment);

        ResourceRequest saved = resourceRequestRepository.save(request);

        // ✅ ASYNC: Send email
        String requesterEmail = getEmailForUser(request.getUserId());
        String stockInfo = request.getStockItemName() != null ?
                "\nStock Item: " + request.getStockItemName() + "\n" : "";
        emailService.sendResourceRequestStatusUpdate(
                requesterEmail,
                "Resource Request Accepted",
                "Your request for " + request.getResourceType() + " has been accepted by the administrator.\n" +
                        stockInfo +
                        "Quantity: " + request.getQuantity()
        );

        // ✅ ASYNC: Audit log
        asyncAuditService.logActionAsync(
                "RESOURCE_REQUEST_ACCEPTED",
                "Resource request accepted: " + requestId + " by admin",
                request.getUserId()
        );

        return convertToDTO(saved);
    }

    @Transactional
    public ResourceRequestDTO declineResourceRequest(Long requestId, String reason) {
        log.info("Admin declining resource request: {} - Reason: {}", requestId, reason);

        ResourceRequest request = validateRequest(requestId);
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalStateException("Request is not pending approval. Current status: " + request.getStatus());
        }

        // ✅ IMMEDIATE: Update request status
        request.setStatus("REJECTED");
        request.setDeclinedAt(LocalDateTime.now());
        request.setDeclinedReason(reason);

        ResourceRequest saved = resourceRequestRepository.save(request);

        // ✅ ASYNC: Send email
        String requesterEmail = getEmailForUser(request.getUserId());
        emailService.sendResourceRequestStatusUpdate(
                requesterEmail,
                "Resource Request Declined",
                "Your request for " + request.getResourceType() + " has been declined. Reason: " + reason
        );

        // ✅ ASYNC: Audit log
        asyncAuditService.logActionAsync(
                "RESOURCE_REQUEST_DECLINED",
                "Resource request declined: " + requestId + " by admin",
                request.getUserId()
        );

        return convertToDTO(saved);
    }

    @Transactional
    public ResourceRequestDTO completeResourceRequest(Long requestId, String deliveryNotes) {
        log.info("Admin completing resource request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);
        if (!"ACCEPTED".equals(request.getStatus())) {
            throw new IllegalStateException("Request must be ACCEPTED to complete. Current status: " + request.getStatus());
        }

        // ✅ IMMEDIATE: Deduct stock (must be synchronous)
        if (request.getStockItemId() != null && request.getQuantity() != null && request.getQuantity() > 0) {
            StockItem stockItem = stockItemRepository.findById(request.getStockItemId()).orElse(null);
            if (stockItem != null) {
                if (stockItem.getQuantity() < request.getQuantity()) {
                    throw new IllegalStateException(
                            "Insufficient stock to complete request. " +
                                    "Available: " + stockItem.getQuantity() + ", Requested: " + request.getQuantity() +
                                    ". Please restock first."
                    );
                }

                int newQuantity = stockItem.getQuantity() - request.getQuantity();
                stockItem.setQuantity(newQuantity);
                stockItem.setLastUpdated(LocalDateTime.now());

                if (newQuantity > stockItem.getLowStockThreshold()) {
                    stockItem.setAlertSent(false);
                }

                stockItemRepository.save(stockItem);
                log.info("Stock deducted: {} - {} (new quantity: {})",
                        stockItem.getName(), request.getQuantity(), newQuantity);

                if (newQuantity <= stockItem.getLowStockThreshold()) {
                    stockService.checkAndSendLowStockAlert(stockItem);
                }
            } else {
                log.warn("Stock item not found for ID: {}, skipping stock deduction", request.getStockItemId());
            }
        }

        // ✅ IMMEDIATE: Update request status
        request.setStatus("COMPLETED");
        request.setCompletedAt(LocalDateTime.now());
        request.setDeliveryNotes(deliveryNotes);

        String token = UUID.randomUUID().toString();
        request.setSigningToken(token);
        request.setSigningTokenExpiry(LocalDateTime.now().plusHours(48));

        ResourceRequest saved = resourceRequestRepository.save(request);

        // ✅ ASYNC: Complete SLA tracking
        CompletableFuture.runAsync(() -> {
            try {
                slaService.completeSLATracking(requestId, "RESOURCE_REQUEST");
                log.info("SLA tracking completed for resource request: {}", requestId);
            } catch (Exception e) {
                log.error("Failed to complete SLA tracking: {}", e.getMessage());
            }
        });

        // ✅ ASYNC: Generate PDF
        asyncPdfService.generateResourceRequestPdfAsync(requestId);

        // ✅ ASYNC: Send email
        String signatureLink = baseUrlService.buildUrl("/resources/sign?token=%s", token);
        String requesterEmail = getEmailForUser(request.getUserId());
        String stockInfo = request.getStockItemName() != null ?
                "\nStock Item: " + request.getStockItemName() + "\n" : "";
        emailService.sendResourceRequestStatusUpdate(
                requesterEmail,
                "Resource Request Completed - Please Sign",
                "Your request for " + request.getResourceType() + " has been completed.\n" +
                        stockInfo +
                        "Quantity: " + request.getQuantity() + "\n\n" +
                        "Please sign to acknowledge receipt: " + signatureLink
        );

        // ✅ ASYNC: Audit log
        asyncAuditService.logActionAsync(
                "RESOURCE_REQUEST_COMPLETED",
                "Resource request completed: " + requestId + " by admin" +
                        (request.getStockItemId() != null ? " (Stock deducted)" : ""),
                request.getUserId()
        );

        return convertToDTO(saved);
    }

    @Transactional
    public void saveRequesterSignature(Long requestId, String token, String signature, String signatoryName) {
        log.info("Saving signature for resource request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);

        if (request.getSigningToken() == null || !request.getSigningToken().equals(token)) {
            log.error("Invalid token. Expected: {}, Got: {}", request.getSigningToken(), token);
            throw new RuntimeException("Invalid token");
        }

        if (request.getSigningTokenExpiry() == null ||
                request.getSigningTokenExpiry().isBefore(LocalDateTime.now())) {
            log.error("Token expired. Expiry: {}, Now: {}", request.getSigningTokenExpiry(), LocalDateTime.now());
            throw new RuntimeException("Token has expired");
        }

        if (request.getRequesterSignature() != null && !request.getRequesterSignature().isEmpty()) {
            log.warn("Request {} already signed", requestId);
            throw new RuntimeException("Request has already been signed");
        }

        // ✅ IMMEDIATE: Update request
        request.setRequesterSignature(signature);
        request.setSignatoryName(signatoryName);
        request.setAcknowledgedAt(LocalDateTime.now());
        request.setSigningToken(null);
        request.setSigningTokenExpiry(null);

        resourceRequestRepository.save(request);

        // ✅ ASYNC: Regenerate PDF with signature
        asyncPdfService.generateResourceRequestPdfAsync(requestId);

        // ✅ ASYNC: Send email with PDF attachment
        CompletableFuture.runAsync(() -> {
            try {
                sendSignedConfirmationEmail(request);
            } catch (Exception e) {
                log.error("Failed to send confirmation email with PDF: {}", e.getMessage());
            }
        });

        // ✅ ASYNC: Audit log
        asyncAuditService.logActionAsync(
                "RESOURCE_REQUEST_SIGNED",
                "Resource request signed by: " + signatoryName + " for request: " + requestId,
                request.getUserId()
        );
    }

    @Transactional
    public void resendSigningLink(Long requestId) {
        log.info("Resending signing link for request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);

        if (!"COMPLETED".equals(request.getStatus())) {
            throw new IllegalStateException("Request must be COMPLETED to resend link. Current status: " + request.getStatus());
        }

        if (request.getRequesterSignature() != null && !request.getRequesterSignature().isEmpty()) {
            throw new IllegalStateException("Request has already been signed");
        }

        // ✅ IMMEDIATE: Generate new token
        String token = UUID.randomUUID().toString();
        request.setSigningToken(token);
        request.setSigningTokenExpiry(LocalDateTime.now().plusHours(48));
        resourceRequestRepository.save(request);

        // ✅ ASYNC: Resend email
        String requesterEmail = getEmailForUser(request.getUserId());
        String signatureLink = baseUrlService.buildUrl("/resources/sign?token=%s", token);
        emailService.sendResourceRequestStatusUpdate(
                requesterEmail,
                "REMINDER: Resource Request - Please Sign",
                "This is a reminder that your request for " + request.getResourceType() + " is awaiting your signature.\n\n" +
                        "Please sign to acknowledge receipt: " + signatureLink + "\n\n" +
                        "This link will expire in 48 hours."
        );

        // ✅ ASYNC: Audit log
        asyncAuditService.logActionAsync(
                "RESOURCE_REQUEST_LINK_RESENT",
                "Signing link resent for request: " + requestId,
                request.getUserId()
        );
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

        // ✅ IMMEDIATE: Update request
        request.setRequesterSignature(signature);
        request.setSignatoryName(signatoryName);
        request.setAcknowledgedAt(LocalDateTime.now());
        request.setAcknowledgedBy(userId);
        request.setSigningToken(null);
        request.setSigningTokenExpiry(null);

        resourceRequestRepository.save(request);

        // ✅ ASYNC: Regenerate PDF with signature
        asyncPdfService.generateResourceRequestPdfAsync(requestId);

        // ✅ ASYNC: Send confirmation email
        CompletableFuture.runAsync(() -> {
            try {
                sendSignedConfirmationEmail(request);
            } catch (Exception e) {
                log.error("Failed to send confirmation email: {}", e.getMessage());
            }
        });

        // ✅ ASYNC: Audit log
        asyncAuditService.logActionAsync(
                "RESOURCE_REQUEST_ACKNOWLEDGED",
                "Resource request acknowledged by user: " + userId + ", signatory: " + signatoryName,
                userId
        );
    }

    @Transactional
    public void recallRequest(Long requestId) {
        log.info("Recalling resource request: {}", requestId);

        ResourceRequest request = validateRequest(requestId);
        String status = request.getStatus();

        if (!"PENDING".equals(status) && !"ACCEPTED".equals(status)) {
            throw new IllegalStateException("Cannot recall - request already processed");
        }

        // ✅ IMMEDIATE: Update status
        request.setStatus("RECALLED");
        resourceRequestRepository.save(request);

        // ✅ ASYNC: Audit log
        asyncAuditService.logActionAsync(
                "RESOURCE_REQUEST_RECALLED",
                "Resource request recalled: " + requestId,
                request.getUserId()
        );
    }

    // ============================================
    // Stock Check Helper (synchronous - fast)
    // ============================================

    public boolean isStockAvailable(Long requestId) {
        ResourceRequest request = validateRequest(requestId);
        if (request.getStockItemId() == null || request.getQuantity() == null) {
            return true;
        }

        StockItem stockItem = stockItemRepository.findById(request.getStockItemId()).orElse(null);
        if (stockItem == null) {
            return false;
        }

        return stockItem.getQuantity() >= request.getQuantity();
    }

    public Integer getAvailableStockForRequest(Long requestId) {
        ResourceRequest request = validateRequest(requestId);
        if (request.getStockItemId() == null) {
            return null;
        }

        StockItem stockItem = stockItemRepository.findById(request.getStockItemId()).orElse(null);
        return stockItem != null ? stockItem.getQuantity() : 0;
    }

    // ============================================
    // Email with PDF Attachment (unchanged)
    // ============================================
    private void sendSignedConfirmationEmail(ResourceRequest request) {
        try {
            String requesterEmail = getEmailForUser(request.getUserId());
            String subject = "Resource Request Signed - Completed #" + request.getRequestId();
            String body = "Thank you for signing the receipt for your resource request #" + request.getRequestId() +
                    ".\n\nA signed PDF report is attached for your records.\n\n" +
                    "Request Details:\n" +
                    "- Resource Type: " + request.getResourceType() + "\n" +
                    "- Description: " + request.getDescription() + "\n" +
                    "- Quantity: " + request.getQuantity() + "\n" +
                    (request.getStockItemName() != null ? "- Stock Item: " + request.getStockItemName() + "\n" : "") +
                    "- Signed by: " + request.getSignatoryName() + "\n" +
                    "- Signed on: " + request.getAcknowledgedAt() + "\n\n" +
                    "You can also download the PDF from the portal at any time.";

            if (request.getPdfReportPath() != null) {
                Path pdfPath = Paths.get(request.getPdfReportPath());
                if (Files.exists(pdfPath)) {
                    byte[] pdfBytes = Files.readAllBytes(pdfPath);
                    String fileName = "resource-request-" + request.getRequestId() + "-signed.pdf";

                    emailService.sendEmailWithAttachment(
                            requesterEmail,
                            subject,
                            body,
                            pdfBytes,
                            fileName
                    );
                    log.info("Signed confirmation email with PDF attachment sent to: {}", requesterEmail);
                } else {
                    emailService.sendResourceRequestStatusUpdate(requesterEmail, subject, body);
                    log.warn("PDF file not found, sent email without attachment");
                }
            } else {
                emailService.sendResourceRequestStatusUpdate(requesterEmail, subject, body);
                log.warn("PDF path is null, sent email without attachment");
            }
        } catch (Exception e) {
            log.error("Failed to send signed confirmation email: {}", e.getMessage());
            try {
                String requesterEmail = getEmailForUser(request.getUserId());
                String subject = "Resource Request Signed - Completed #" + request.getRequestId();
                String body = "Thank you for signing the receipt for your resource request #" + request.getRequestId() +
                        ".\n\nYou can download the signed PDF from the portal at any time.";
                emailService.sendResourceRequestStatusUpdate(requesterEmail, subject, body);
            } catch (Exception fallbackError) {
                log.error("Fallback email also failed: {}", fallbackError.getMessage());
            }
        }
    }

    // ============================================
    // PDF Generation Methods (synchronous - called from async service)
    // ============================================

    private String savePdfToFile(byte[] pdfBytes, Long requestId) throws java.io.IOException {
        Path uploadPath = Paths.get(REPORT_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String filename = "resource_request_" + requestId + "_" + System.currentTimeMillis() + ".pdf";
        Path filePath = uploadPath.resolve(filename);
        Files.write(filePath, pdfBytes);
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

        dto.setStockItemId(request.getStockItemId());
        dto.setStockItemName(request.getStockItemName());

        if (request.getStockItemId() != null) {
            stockItemRepository.findById(request.getStockItemId())
                    .ifPresent(item -> dto.setCurrentStockQuantity(item.getQuantity()));
        }

        try {
            RequestSLATracking slaTracking = slaService.getSLAStatus(request.getRequestId(), "RESOURCE_REQUEST");
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
            log.warn("Could not fetch SLA status for resource request {}: {}", request.getRequestId(), e.getMessage());
            dto.setSlaStatus("N/A");
            dto.setSlaStatusDisplay("N/A");
            dto.setSlaPercentage(0.0);
        }

        return dto;
    }
}