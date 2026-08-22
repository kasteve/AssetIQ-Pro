package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.entity.ResourceRequest;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.repository.InfraRequestRepository;
import com.stevecodes.AssetIQPro.repository.ResourceRequestRepository;
import com.stevecodes.AssetIQPro.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncPdfService {

    private final PdfGenerationService pdfGenerationService;
    private final InfraRequestRepository infraRequestRepository;
    private final ResourceRequestRepository resourceRequestRepository;
    private final TransferRepository transferRepository;

    private static final String REPORT_DIR = "uploads/reports/";

    @Async("pdfTaskExecutor")
    public void generateInfraRequestPdfAsync(Long requestId) {
        try {
            log.info("📄 Generating PDF asynchronously for infra request: {}", requestId);
            InfraRequest request = infraRequestRepository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));

            byte[] pdfBytes = pdfGenerationService.generateInfraRequestReport(request);
            String pdfPath = savePdfToFile(pdfBytes, "infra_request_" + requestId);

            request.setPdfReportPath(pdfPath);
            infraRequestRepository.save(request);
            log.info("✅ PDF generated for infra request: {}", requestId);
        } catch (Exception e) {
            log.error("❌ Failed to generate PDF for infra request {}: {}", requestId, e.getMessage());
        }
    }

    @Async("pdfTaskExecutor")
    public void generateResourceRequestPdfAsync(Long requestId) {
        try {
            log.info("📄 Generating PDF asynchronously for resource request: {}", requestId);
            ResourceRequest request = resourceRequestRepository.findById(requestId)
                    .orElseThrow(() -> new RuntimeException("Request not found: " + requestId));

            byte[] pdfBytes = pdfGenerationService.generateResourceRequestReport(request);
            String pdfPath = savePdfToFile(pdfBytes, "resource_request_" + requestId);

            request.setPdfReportPath(pdfPath);
            resourceRequestRepository.save(request);
            log.info("✅ PDF generated for resource request: {}", requestId);
        } catch (Exception e) {
            log.error("❌ Failed to generate PDF for resource request {}: {}", requestId, e.getMessage());
        }
    }

    @Async("pdfTaskExecutor")
    public void generateTransferPdfAsync(Long transferId) {
        try {
            log.info("📄 Generating PDF asynchronously for transfer: {}", transferId);
            Transfer transfer = transferRepository.findById(transferId)
                    .orElseThrow(() -> new RuntimeException("Transfer not found: " + transferId));

            List<Transfer> related = transferRepository.findRelatedTransfers(
                    transfer.getAssetTag(), transfer.getSerialNumber(), transferId);

            byte[] pdfBytes = pdfGenerationService.generateTransferCertificatePdf(transfer, related);
            String pdfPath = savePdfToFile(pdfBytes, "transfer_" + transferId);
            log.info("✅ PDF generated for transfer: {}", transferId);
        } catch (Exception e) {
            log.error("❌ Failed to generate PDF for transfer {}: {}", transferId, e.getMessage());
        }
    }

    private String savePdfToFile(byte[] pdfBytes, String prefix) throws Exception {
        Path uploadPath = Paths.get(REPORT_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String filename = prefix + "_" + System.currentTimeMillis() + ".pdf";
        Path filePath = uploadPath.resolve(filename);
        Files.write(filePath, pdfBytes);
        return filePath.toString();
    }
}