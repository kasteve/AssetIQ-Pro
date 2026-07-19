package com.stevecodes.AssetIQPro.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.entity.ResourceRequest;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import com.stevecodes.AssetIQPro.repository.DepartmentRepository;
import com.stevecodes.AssetIQPro.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGenerationService {

    // Premium color palette
    private static final DeviceRgb NAVY = new DeviceRgb(21, 48, 77);
    private static final DeviceRgb NAVY_2 = new DeviceRgb(31, 67, 104);
    private static final DeviceRgb INK = new DeviceRgb(28, 43, 58);
    private static final DeviceRgb INK_SOFT = new DeviceRgb(84, 98, 111);
    private static final DeviceRgb MUTED = new DeviceRgb(154, 165, 173);
    private static final DeviceRgb LINE_SOFT = new DeviceRgb(228, 233, 237);
    private static final DeviceRgb WASH = new DeviceRgb(244, 246, 248);
    private static final DeviceRgb ACCENT = new DeviceRgb(181, 132, 42);
    private static final DeviceRgb GOOD = new DeviceRgb(46, 125, 79);
    private static final DeviceRgb GOOD_BG = new DeviceRgb(234, 245, 239);
    private static final DeviceRgb WARN = new DeviceRgb(180, 95, 6);
    private static final DeviceRgb WARN_BG = new DeviceRgb(253, 243, 224);
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb WATERMARK = new DeviceRgb(197, 205, 212);

    private final AppUserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;

    // ============================================
    // Infrastructure Request PDF - Compact Version
    // ============================================

    public byte[] generateInfraRequestReport(InfraRequest request) throws Exception {
        log.info("Generating infrastructure request report for request {}", request.getRequestId());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(20, 20, 16, 20);

        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont monoFont = PdfFontFactory.createFont(StandardFonts.COURIER);

        // Header
        addCompactHeader(document, "INFRASTRUCTURE REQUEST REPORT",
                "Request #: " + request.getRequestId(),
                "Status: " + (request.getStatus() != null ? request.getStatus().name() : "PENDING"),
                formatDateTime(request.getCreatedAt()), boldFont, regularFont);

        // Request Details - Compact 3-column grid
        addCompactSectionHeading(document, "Request Details", boldFont);
        Table detailsTable = createCompactInfraDetailsTable(request, boldFont, regularFont, monoFont);
        document.add(detailsTable);

        // Approval Timeline - Compact
        addCompactSectionHeading(document, "Approval Timeline", boldFont);
        Table timelineTable = createCompactInfraTimelineTable(request, boldFont, regularFont);
        document.add(timelineTable);

        // Signatures
        addCompactSectionHeading(document, "Signatures", boldFont);
        Table signatureTable = createCompactInfraSignatureTable(request, boldFont, regularFont);
        document.add(signatureTable);

        addCompactFooter(document, "Request #" + request.getRequestId(), regularFont);

        document.close();
        log.info("Generated infrastructure request report for request {}", request.getRequestId());
        return outputStream.toByteArray();
    }

    // ============================================
    // Resource Request PDF - Compact Version
    // ============================================

    public byte[] generateResourceRequestReport(ResourceRequest request) throws Exception {
        log.info("Generating resource request report for request {}", request.getRequestId());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(20, 20, 16, 20);

        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont monoFont = PdfFontFactory.createFont(StandardFonts.COURIER);

        // Header
        addCompactHeader(document, "RESOURCE REQUEST REPORT",
                "Request #: " + request.getRequestId(),
                "Status: " + (request.getStatus() != null ? request.getStatus() : "PENDING"),
                formatDateTime(request.getRequestTime()), boldFont, regularFont);

        // Request Details
        addCompactSectionHeading(document, "Request Details", boldFont);
        Table detailsTable = createCompactResourceDetailsTable(request, boldFont, regularFont, monoFont);
        document.add(detailsTable);

        // Approval Timeline
        addCompactSectionHeading(document, "Approval Timeline", boldFont);
        Table timelineTable = createCompactResourceTimelineTable(request, boldFont, regularFont);
        document.add(timelineTable);

        // Signatures
        addCompactSectionHeading(document, "Signatures", boldFont);
        Table signatureTable = createCompactResourceSignatureTable(request, boldFont, regularFont);
        document.add(signatureTable);

        addCompactFooter(document, "Request #" + request.getRequestId(), regularFont);

        document.close();
        log.info("Generated resource request report for request {}", request.getRequestId());
        return outputStream.toByteArray();
    }

    // ============================================
    // Transfer Certificate PDF - Complete Version with All Fields
    // ============================================

    public byte[] generateTransferCertificatePdf(Transfer transfer, List<Transfer> relatedTransfers) throws Exception {
        log.info("Generating complete asset transfer certificate for transfer {}", transfer.getTransferId());

        // Fetch department names and employee names
        populateTransferDetails(transfer);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(20, 20, 16, 20);

        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont monoFont = PdfFontFactory.createFont(StandardFonts.COURIER);

        String transferIdText = transfer.getTransferId() != null ? String.valueOf(transfer.getTransferId()) : "N/A";
        String dateText = transfer.getTransferDate() != null
                ? transfer.getTransferDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                : "N/A";
        String statusText = Boolean.TRUE.equals(transfer.getIsFullySigned()) ? "FULLY SIGNED" : "PENDING";

        // Header
        addTransferHeader(document, "ASSET TRANSFER CERTIFICATE",
                "Transfer #: " + transferIdText,
                statusText,
                "Transfer Date: " + dateText, boldFont, regularFont);

        // Asset Information
        addCompactSectionHeading(document, "Asset Information", boldFont);
        Table assetTable = createCompleteAssetInfoTable(transfer, boldFont, regularFont, monoFont);
        document.add(assetTable);

        // Condition
        addCompactSectionHeading(document, "Condition", boldFont);
        Table conditionTable = createCompleteConditionTable(transfer, boldFont, regularFont);
        document.add(conditionTable);

        // Transfer Information - From & To
        addCompactSectionHeading(document, "Transfer Information", boldFont);
        Table transferInfoTable = createCompleteTransferInfoTable(transfer, boldFont, regularFont);
        document.add(transferInfoTable);

        // Representatives
        addCompactSectionHeading(document, "Representatives", boldFont);
        Table repTable = createCompleteRepresentativesTable(transfer, boldFont, regularFont);
        document.add(repTable);

        // Additional Information
        addCompactSectionHeading(document, "Additional Information", boldFont);
        Table additionalTable = createCompleteAdditionalInfoTable(transfer, boldFont, regularFont);
        document.add(additionalTable);

        // Signatures - Side by Side (3 columns)
        addCompactSectionHeading(document, "Signatures", boldFont);
        Table signatureTable = createCompleteSignatureTable(transfer, boldFont, regularFont);
        document.add(signatureTable);

        addCompactFooter(document, "Transfer #" + transferIdText, regularFont);

        document.close();
        log.info("Generated complete PDF for transfer {}", transfer.getTransferId());
        return outputStream.toByteArray();
    }

    // ============================================
    // Helper Methods to Populate Transfer Details
    // ============================================

    private void populateTransferDetails(Transfer transfer) {
        // Populate Department Names
        if (transfer.getOldDepartmentId() != null) {
            departmentRepository.findById(transfer.getOldDepartmentId())
                    .ifPresent(dept -> transfer.setOldDepartmentName(dept.getName()));
        }

        if (transfer.getNewDepartmentId() != null) {
            departmentRepository.findById(transfer.getNewDepartmentId())
                    .ifPresent(dept -> transfer.setNewDepartmentName(dept.getName()));
        }

        // Populate Employee Names
        if (transfer.getOldEmployeeId() != null) {
            employeeRepository.findById(transfer.getOldEmployeeId())
                    .ifPresent(emp -> transfer.setOldEmployeeName(emp.getFullName()));
        }

        if (transfer.getNewEmployeeId() != null) {
            employeeRepository.findById(transfer.getNewEmployeeId())
                    .ifPresent(emp -> transfer.setNewEmployeeName(emp.getFullName()));
        }

        // Populate signer names if not already set
        if (transfer.getOldHandoverById() != null && (transfer.getOldHandoverByName() == null || transfer.getOldHandoverByName().isEmpty())) {
            employeeRepository.findById(transfer.getOldHandoverById())
                    .ifPresent(emp -> transfer.setOldHandoverByName(emp.getFullName()));
        }
        if (transfer.getOldReceivedById() != null && (transfer.getOldReceivedByName() == null || transfer.getOldReceivedByName().isEmpty())) {
            employeeRepository.findById(transfer.getOldReceivedById())
                    .ifPresent(emp -> transfer.setOldReceivedByName(emp.getFullName()));
        }
        if (transfer.getNewHandoverById() != null && (transfer.getNewHandoverByName() == null || transfer.getNewHandoverByName().isEmpty())) {
            employeeRepository.findById(transfer.getNewHandoverById())
                    .ifPresent(emp -> transfer.setNewHandoverByName(emp.getFullName()));
        }
        if (transfer.getNewReceivedById() != null && (transfer.getNewReceivedByName() == null || transfer.getNewReceivedByName().isEmpty())) {
            employeeRepository.findById(transfer.getNewReceivedById())
                    .ifPresent(emp -> transfer.setNewReceivedByName(emp.getFullName()));
        }
        if (transfer.getConfiguredById() != null && (transfer.getConfiguredByName() == null || transfer.getConfiguredByName().isEmpty())) {
            employeeRepository.findById(transfer.getConfiguredById())
                    .ifPresent(emp -> transfer.setConfiguredByName(emp.getFullName()));
        }
        if (transfer.getInfraRepresentativeId() != null && (transfer.getInfraRepresentativeName() == null || transfer.getInfraRepresentativeName().isEmpty())) {
            employeeRepository.findById(transfer.getInfraRepresentativeId())
                    .ifPresent(emp -> transfer.setInfraRepresentativeName(emp.getFullName()));
        }
        if (transfer.getFinanceRepresentativeId() != null && (transfer.getFinanceRepresentativeName() == null || transfer.getFinanceRepresentativeName().isEmpty())) {
            employeeRepository.findById(transfer.getFinanceRepresentativeId())
                    .ifPresent(emp -> transfer.setFinanceRepresentativeName(emp.getFullName()));
        }
    }

    // ============================================
    // Compact Helper Methods - Infrastructure
    // ============================================

    private void addCompactHeader(Document document, String title, String ref, String status, String date,
                                  PdfFont boldFont, PdfFont regularFont) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1, 4, 2.5f}));
        header.setWidth(UnitValue.createPercentValue(100));
        header.setMarginBottom(4);

        // Brand
        Cell brandCell = new Cell().setBorder(Border.NO_BORDER).setPadding(3)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        brandCell.add(new Paragraph("IQ")
                .setFont(boldFont).setFontSize(11).setFontColor(WHITE)
                .setBackgroundColor(NAVY).setPadding(4)
                .setTextAlignment(TextAlignment.CENTER));
        header.addCell(brandCell);

        // Title
        Cell titleCell = new Cell().setBorder(Border.NO_BORDER).setPadding(3)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        titleCell.add(new Paragraph(title)
                .setFont(boldFont).setFontSize(12).setFontColor(NAVY).setCharacterSpacing(0.6f));
        titleCell.add(new Paragraph("AssetIQ-Pro — Asset Management System")
                .setFont(regularFont).setFontSize(7).setFontColor(INK_SOFT));
        header.addCell(titleCell);

        // Meta
        Cell metaCell = new Cell().setBorder(Border.NO_BORDER).setPadding(3)
                .setTextAlignment(TextAlignment.RIGHT).setVerticalAlignment(VerticalAlignment.MIDDLE);
        metaCell.add(new Paragraph(ref).setFont(regularFont).setFontSize(8).setFontColor(INK_SOFT));
        DeviceRgb statusColor = status.contains("COMPLETED") ? GOOD : WARN;
        metaCell.add(new Paragraph(status).setFont(boldFont).setFontSize(8).setFontColor(statusColor));
        metaCell.add(new Paragraph(date).setFont(regularFont).setFontSize(8).setFontColor(INK_SOFT));
        header.addCell(metaCell);

        document.add(header);

        LineSeparator line = new LineSeparator(new SolidLine(1.5f));
        line.setStrokeColor(NAVY);
        line.setMarginTop(2);
        line.setMarginBottom(4);
        document.add(line);
    }

    private void addTransferHeader(Document document, String title, String ref, String status, String date,
                                   PdfFont boldFont, PdfFont regularFont) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1, 4, 2.5f}));
        header.setWidth(UnitValue.createPercentValue(100));
        header.setMarginBottom(4);

        // Brand
        Cell brandCell = new Cell().setBorder(Border.NO_BORDER).setPadding(3)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        brandCell.add(new Paragraph("IQ")
                .setFont(boldFont).setFontSize(11).setFontColor(WHITE)
                .setBackgroundColor(NAVY).setPadding(4)
                .setTextAlignment(TextAlignment.CENTER));
        header.addCell(brandCell);

        // Title
        Cell titleCell = new Cell().setBorder(Border.NO_BORDER).setPadding(3)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        titleCell.add(new Paragraph(title)
                .setFont(boldFont).setFontSize(12).setFontColor(NAVY).setCharacterSpacing(0.6f));
        titleCell.add(new Paragraph("AssetIQ-Pro — Asset Management System")
                .setFont(regularFont).setFontSize(7).setFontColor(INK_SOFT));
        header.addCell(titleCell);

        // Meta
        Cell metaCell = new Cell().setBorder(Border.NO_BORDER).setPadding(3)
                .setTextAlignment(TextAlignment.RIGHT).setVerticalAlignment(VerticalAlignment.MIDDLE);
        metaCell.add(new Paragraph(ref).setFont(regularFont).setFontSize(8).setFontColor(INK_SOFT));
        DeviceRgb statusColor = status.contains("FULLY SIGNED") ? GOOD : WARN;
        metaCell.add(new Paragraph(status).setFont(boldFont).setFontSize(8).setFontColor(statusColor));
        metaCell.add(new Paragraph(date).setFont(regularFont).setFontSize(8).setFontColor(INK_SOFT));
        header.addCell(metaCell);

        document.add(header);

        LineSeparator line = new LineSeparator(new SolidLine(1.5f));
        line.setStrokeColor(NAVY);
        line.setMarginTop(2);
        line.setMarginBottom(4);
        document.add(line);
    }

    private void addCompactSectionHeading(Document document, String title, PdfFont boldFont) {
        Paragraph heading = new Paragraph(title)
                .setFont(boldFont).setFontSize(9).setFontColor(NAVY)
                .setCharacterSpacing(0.4f)
                .setBorderBottom(new SolidBorder(NAVY, 1))
                .setPaddingBottom(2).setMarginTop(6).setMarginBottom(4);
        document.add(heading);
    }

    private Table createCompactInfraDetailsTable(InfraRequest request, PdfFont boldFont,
                                                 PdfFont regularFont, PdfFont monoFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{20, 30, 20, 30}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        String requesterName = userRepository.findById(request.getRequesterId())
                .map(user -> user.getFullName())
                .orElse("Unknown");

        table.addCell(compactLabelCell("Request ID", boldFont));
        table.addCell(compactValueCell(String.valueOf(request.getRequestId()), monoFont));
        table.addCell(compactLabelCell("Status", boldFont));
        table.addCell(compactValueCell(request.getStatus() != null ? request.getStatus().name() : "N/A", boldFont));

        table.addCell(compactLabelCell("Resource Type", boldFont));
        table.addCell(compactValueCell(request.getResourceType(), regularFont));
        table.addCell(compactLabelCell("Quantity", boldFont));
        table.addCell(compactValueCell(String.valueOf(request.getQuantity()), regularFont));

        table.addCell(compactLabelCell("Created At", boldFont));
        table.addCell(compactValueCell(formatDateTime(request.getCreatedAt()), regularFont));
        table.addCell(compactLabelCell("Requested By", boldFont));
        table.addCell(compactValueCell(requesterName, regularFont));

        table.addCell(compactLabelCell("Specification", boldFont));
        table.addCell(compactValueCell(val(request.getSpecification()), regularFont));
        table.addCell(compactLabelCell("Justification", boldFont));
        table.addCell(compactValueCell(val(request.getJustification()), regularFont));

        if (request.getPurchaseCost() != null) {
            table.addCell(compactLabelCell("Purchase Cost", boldFont));
            table.addCell(compactValueCell("Ugx " + request.getPurchaseCost().toString(), regularFont));
            table.addCell(compactLabelCell("", boldFont));
            table.addCell(compactValueCell("", regularFont));
        }

        return table;
    }

    private Table createCompactInfraTimelineTable(InfraRequest request, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{20, 40, 40}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        table.addHeaderCell(compactHeaderCell("Stage", boldFont));
        table.addHeaderCell(compactHeaderCell("Approved By", boldFont));
        table.addHeaderCell(compactHeaderCell("Timestamp", boldFont));

        String lmName = "Pending";
        if (request.getLmApprovedBy() != null) {
            lmName = userRepository.findById(request.getLmApprovedBy())
                    .map(user -> user.getFullName())
                    .orElse("Unknown");
        }
        table.addCell(compactValueCell("Line Manager", regularFont));
        table.addCell(compactValueCell(lmName, regularFont));
        table.addCell(compactValueCell(request.getLmApprovedAt() != null ? formatDateTime(request.getLmApprovedAt()) : "-", regularFont));

        String infraName = "Pending";
        if (request.getInfraReviewedBy() != null) {
            infraName = userRepository.findById(request.getInfraReviewedBy())
                    .map(user -> user.getFullName())
                    .orElse("Unknown");
        }
        table.addCell(compactValueCell("Infrastructure", regularFont));
        table.addCell(compactValueCell(infraName, regularFont));
        table.addCell(compactValueCell(request.getInfraReviewedAt() != null ? formatDateTime(request.getInfraReviewedAt()) : "-", regularFont));

        String financeName = "Pending";
        if (request.getFinanceApprovedBy() != null) {
            financeName = userRepository.findById(request.getFinanceApprovedBy())
                    .map(user -> user.getFullName())
                    .orElse("Unknown");
        }
        table.addCell(compactValueCell("Finance", regularFont));
        table.addCell(compactValueCell(financeName, regularFont));
        table.addCell(compactValueCell(request.getFinanceApprovedAt() != null ? formatDateTime(request.getFinanceApprovedAt()) : "-", regularFont));

        if (request.getLmComment() != null) {
            table.addCell(compactValueCell("LM Comment", regularFont));
            table.addCell(compactValueCell(request.getLmComment(), regularFont));
            table.addCell(compactValueCell("", regularFont));
        }
        if (request.getInfraComment() != null) {
            table.addCell(compactValueCell("Infra Comment", regularFont));
            table.addCell(compactValueCell(request.getInfraComment(), regularFont));
            table.addCell(compactValueCell("", regularFont));
        }
        if (request.getFinanceComment() != null) {
            table.addCell(compactValueCell("Finance Comment", regularFont));
            table.addCell(compactValueCell(request.getFinanceComment(), regularFont));
            table.addCell(compactValueCell("", regularFont));
        }

        return table;
    }

    private Table createCompactInfraSignatureTable(InfraRequest request, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 75}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        table.addHeaderCell(compactHeaderCell("Role", boldFont));
        table.addHeaderCell(compactHeaderCell("Signature", boldFont));

        String requesterName = userRepository.findById(request.getRequesterId())
                .map(user -> user.getFullName())
                .orElse("Unknown");

        boolean isSigned = request.getRequesterSignature() != null && !request.getRequesterSignature().isEmpty();

        Cell nameCell = new Cell().setPadding(4)
                .setBorder(new SolidBorder(LINE_SOFT, 0.5f));
        nameCell.add(new Paragraph("Requester: " + requesterName)
                .setFont(regularFont).setFontSize(8).setFontColor(INK));
        table.addCell(nameCell);

        Cell sigCell = new Cell().setPadding(4)
                .setBorder(new SolidBorder(LINE_SOFT, 0.5f));

        Div sigDiv = new Div().setHeight(25)
                .setBorderBottom(new SolidBorder(INK, 0.5f));

        if (isSigned) {
            try {
                String clean = request.getRequesterSignature().startsWith("data:image")
                        ? request.getRequesterSignature().substring(request.getRequesterSignature().indexOf(",") + 1)
                        : request.getRequesterSignature();
                byte[] sigBytes = Base64.getDecoder().decode(clean);
                ImageData sigData = ImageDataFactory.create(sigBytes);
                Image sigImage = new Image(sigData);
                sigImage.setMaxHeight(22);
                sigImage.setMaxWidth(150);
                sigDiv.add(sigImage);
            } catch (Exception e) {
                log.warn("Could not decode signature: {}", e.getMessage());
                sigDiv.add(new Paragraph("✓ Signed").setFont(boldFont).setFontSize(10).setFontColor(GOOD));
            }
        } else {
            sigDiv.add(new Paragraph("________________________")
                    .setFont(regularFont).setFontSize(8).setFontColor(MUTED));
        }
        sigCell.add(sigDiv);

        if (isSigned && request.getRequesterSignedAt() != null) {
            sigCell.add(new Paragraph("Signed on: " + formatDateTime(request.getRequesterSignedAt()))
                    .setFont(regularFont).setFontSize(7).setFontColor(INK_SOFT));
        } else {
            sigCell.add(new Paragraph("Not yet signed")
                    .setFont(regularFont).setFontSize(7).setFontColor(MUTED));
        }

        table.addCell(sigCell);

        return table;
    }

    // ============================================
    // Compact Helper Methods - Resource Request
    // ============================================

    private Table createCompactResourceDetailsTable(ResourceRequest request, PdfFont boldFont,
                                                    PdfFont regularFont, PdfFont monoFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{20, 30, 20, 30}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        table.addCell(compactLabelCell("Request ID", boldFont));
        table.addCell(compactValueCell(String.valueOf(request.getRequestId()), monoFont));
        table.addCell(compactLabelCell("Status", boldFont));
        table.addCell(compactValueCell(request.getStatus() != null ? request.getStatus() : "N/A", boldFont));

        table.addCell(compactLabelCell("Resource Type", boldFont));
        table.addCell(compactValueCell(request.getResourceType(), regularFont));
        table.addCell(compactLabelCell("Quantity", boldFont));
        table.addCell(compactValueCell(String.valueOf(request.getQuantity()), regularFont));

        table.addCell(compactLabelCell("Requested At", boldFont));
        table.addCell(compactValueCell(formatDateTime(request.getRequestTime()), regularFont));
        table.addCell(compactLabelCell("Requested By", boldFont));
        table.addCell(compactValueCell(request.getRequestedBy(), regularFont));

        table.addCell(compactLabelCell("Description", boldFont));
        table.addCell(compactValueCell(val(request.getDescription()), regularFont));
        table.addCell(compactLabelCell("Justification", boldFont));
        table.addCell(compactValueCell(val(request.getJustification()), regularFont));

        if (request.getAdminComment() != null) {
            table.addCell(compactLabelCell("Admin Comment", boldFont));
            table.addCell(compactValueCell(request.getAdminComment(), regularFont));
            table.addCell(compactLabelCell("", boldFont));
            table.addCell(compactValueCell("", regularFont));
        }

        return table;
    }

    private Table createCompactResourceTimelineTable(ResourceRequest request, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{20, 40, 40}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        table.addHeaderCell(compactHeaderCell("Stage", boldFont));
        table.addHeaderCell(compactHeaderCell("Action By", boldFont));
        table.addHeaderCell(compactHeaderCell("Timestamp", boldFont));

        table.addCell(compactValueCell("Request Created", regularFont));
        table.addCell(compactValueCell(request.getRequestedBy(), regularFont));
        table.addCell(compactValueCell(formatDateTime(request.getRequestTime()), regularFont));

        if (request.getAcceptedAt() != null) {
            table.addCell(compactValueCell("Accepted", regularFont));
            table.addCell(compactValueCell("Administrator", regularFont));
            table.addCell(compactValueCell(formatDateTime(request.getAcceptedAt()), regularFont));
        } else {
            table.addCell(compactValueCell("Accepted", regularFont));
            table.addCell(compactValueCell("Pending", regularFont));
            table.addCell(compactValueCell("-", regularFont));
        }

        if (request.getCompletedAt() != null) {
            table.addCell(compactValueCell("Completed", regularFont));
            table.addCell(compactValueCell("Administrator", regularFont));
            table.addCell(compactValueCell(formatDateTime(request.getCompletedAt()), regularFont));
        } else {
            table.addCell(compactValueCell("Completed", regularFont));
            table.addCell(compactValueCell("Pending", regularFont));
            table.addCell(compactValueCell("-", regularFont));
        }

        if (request.getAcknowledgedAt() != null) {
            table.addCell(compactValueCell("Signed", regularFont));
            table.addCell(compactValueCell(request.getSignatoryName(), regularFont));
            table.addCell(compactValueCell(formatDateTime(request.getAcknowledgedAt()), regularFont));
        } else if ("COMPLETED".equals(request.getStatus())) {
            table.addCell(compactValueCell("Signed", regularFont));
            table.addCell(compactValueCell("Awaiting Signature", regularFont));
            table.addCell(compactValueCell("-", regularFont));
        } else {
            table.addCell(compactValueCell("Signed", regularFont));
            table.addCell(compactValueCell("N/A", regularFont));
            table.addCell(compactValueCell("-", regularFont));
        }

        return table;
    }

    private Table createCompactResourceSignatureTable(ResourceRequest request, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 75}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        table.addHeaderCell(compactHeaderCell("Role", boldFont));
        table.addHeaderCell(compactHeaderCell("Signature", boldFont));

        boolean isSigned = request.isSigned();

        Cell nameCell = new Cell().setPadding(4)
                .setBorder(new SolidBorder(LINE_SOFT, 0.5f));
        nameCell.add(new Paragraph("Requester: " + request.getRequestedBy())
                .setFont(regularFont).setFontSize(8).setFontColor(INK));
        table.addCell(nameCell);

        Cell sigCell = new Cell().setPadding(4)
                .setBorder(new SolidBorder(LINE_SOFT, 0.5f));

        Div sigDiv = new Div().setHeight(25)
                .setBorderBottom(new SolidBorder(INK, 0.5f));

        if (isSigned && request.getRequesterSignature() != null) {
            try {
                String clean = request.getRequesterSignature().startsWith("data:image")
                        ? request.getRequesterSignature().substring(request.getRequesterSignature().indexOf(",") + 1)
                        : request.getRequesterSignature();
                byte[] sigBytes = Base64.getDecoder().decode(clean);
                ImageData sigData = ImageDataFactory.create(sigBytes);
                Image sigImage = new Image(sigData);
                sigImage.setMaxHeight(22);
                sigImage.setMaxWidth(150);
                sigDiv.add(sigImage);
            } catch (Exception e) {
                log.warn("Could not decode signature: {}", e.getMessage());
                sigDiv.add(new Paragraph("✓ Signed").setFont(boldFont).setFontSize(10).setFontColor(GOOD));
            }
        } else if ("COMPLETED".equals(request.getStatus())) {
            sigDiv.add(new Paragraph("________________________")
                    .setFont(regularFont).setFontSize(8).setFontColor(MUTED));
        } else {
            sigDiv.add(new Paragraph("Not required")
                    .setFont(regularFont).setFontSize(8).setFontColor(MUTED));
        }
        sigCell.add(sigDiv);

        if (isSigned && request.getAcknowledgedAt() != null) {
            sigCell.add(new Paragraph("Signed by: " + request.getSignatoryName())
                    .setFont(regularFont).setFontSize(7).setFontColor(INK_SOFT));
            sigCell.add(new Paragraph("Signed on: " + formatDateTime(request.getAcknowledgedAt()))
                    .setFont(regularFont).setFontSize(7).setFontColor(INK_SOFT));
        } else if ("COMPLETED".equals(request.getStatus())) {
            sigCell.add(new Paragraph("Awaiting signature")
                    .setFont(regularFont).setFontSize(7).setFontColor(MUTED));
        } else {
            sigCell.add(new Paragraph("N/A")
                    .setFont(regularFont).setFontSize(7).setFontColor(MUTED));
        }

        table.addCell(sigCell);

        return table;
    }

    // ============================================
    // Complete Transfer PDF Tables
    // ============================================

    private Table createCompleteAssetInfoTable(Transfer transfer, PdfFont boldFont,
                                               PdfFont regularFont, PdfFont monoFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{18, 32, 18, 32}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        table.addCell(compactLabelCell("Asset Tag", boldFont));
        table.addCell(compactValueCell(val(transfer.getAssetTag()), monoFont));
        table.addCell(compactLabelCell("Serial Number", boldFont));
        table.addCell(compactValueCell(val(transfer.getSerialNumber()), monoFont));

        table.addCell(compactLabelCell("Version / Make", boldFont));
        table.addCell(compactValueCell(val(transfer.getVersionMake()), regularFont));
        table.addCell(compactLabelCell("Model / Build", boldFont));
        table.addCell(compactValueCell(val(transfer.getModelBuild()), regularFont));

        table.addCell(compactLabelCell("Category", boldFont));
        table.addCell(compactValueCell(transfer.getCategoryId() != null ? String.valueOf(transfer.getCategoryId()) : "N/A", regularFont));
        table.addCell(compactLabelCell("Company", boldFont));
        table.addCell(compactValueCell(transfer.getCompanyId() != null ? String.valueOf(transfer.getCompanyId()) : "N/A", regularFont));

        table.addCell(compactLabelCell("Configured By", boldFont));
        table.addCell(compactValueCell(val(transfer.getConfiguredByName()), regularFont));
        table.addCell(compactLabelCell("", boldFont));
        table.addCell(compactValueCell("", regularFont));

        return table;
    }

    private Table createCompleteConditionTable(Transfer transfer, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{20, 40, 40}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        table.addHeaderCell(compactHeaderCell("", boldFont));
        table.addHeaderCell(compactHeaderCell("Previous", boldFont));
        table.addHeaderCell(compactHeaderCell("New", boldFont));

        table.addCell(compactLabelCell("Condition", boldFont));
        table.addCell(compactValueCell(val(transfer.getConditionOld()), regularFont));
        table.addCell(compactValueCell(val(transfer.getConditionNew()), regularFont));

        table.addCell(compactLabelCell("Accessories", boldFont));
        table.addCell(compactValueCell(val(transfer.getAccessoriesOld()), regularFont));
        table.addCell(compactValueCell(val(transfer.getAccessoriesNew()), regularFont));

        return table;
    }

    private Table createCompleteTransferInfoTable(Transfer transfer, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        // From (Outgoing)
        Cell fromCard = new Cell().setBorder(new SolidBorder(LINE_SOFT, 0.5f)).setPadding(4);
        fromCard.add(new Paragraph("FROM (OUTGOING)")
                .setFont(boldFont).setFontSize(7).setFontColor(NAVY_2).setCharacterSpacing(0.4f));
        fromCard.add(new Paragraph("Department: " + val(transfer.getOldDepartmentName()))
                .setFont(regularFont).setFontSize(8).setFontColor(INK));
        fromCard.add(new Paragraph("Employee: " + val(transfer.getOldEmployeeName()))
                .setFont(regularFont).setFontSize(8).setFontColor(INK));
        fromCard.add(new Paragraph("Staff ID: " + val(transfer.getOldEmployeeStaffId()))
                .setFont(regularFont).setFontSize(8).setFontColor(INK));
        fromCard.add(new Paragraph("Old Handover By: " + val(transfer.getOldHandoverByName()))
                .setFont(regularFont).setFontSize(8).setFontColor(INK));
        table.addCell(fromCard);

        // To (Incoming)
        Cell toCard = new Cell().setBorder(new SolidBorder(LINE_SOFT, 0.5f)).setPadding(4);
        toCard.add(new Paragraph("TO (INCOMING)")
                .setFont(boldFont).setFontSize(7).setFontColor(ACCENT).setCharacterSpacing(0.4f));
        toCard.add(new Paragraph("Department: " + val(transfer.getNewDepartmentName()))
                .setFont(regularFont).setFontSize(8).setFontColor(INK));
        toCard.add(new Paragraph("Employee: " + val(transfer.getNewEmployeeName()))
                .setFont(regularFont).setFontSize(8).setFontColor(INK));
        toCard.add(new Paragraph("Staff ID: " + val(transfer.getNewEmployeeStaffId()))
                .setFont(regularFont).setFontSize(8).setFontColor(INK));
        toCard.add(new Paragraph("New Handover By: " + val(transfer.getNewHandoverByName()))
                .setFont(regularFont).setFontSize(8).setFontColor(INK));
        table.addCell(toCard);

        return table;
    }

    private Table createCompleteRepresentativesTable(Transfer transfer, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 75}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        table.addCell(compactLabelCell("Infrastructure Rep", boldFont));
        table.addCell(compactValueCell(val(transfer.getInfraRepresentativeName()), regularFont));

        table.addCell(compactLabelCell("Finance Representative", boldFont));
        table.addCell(compactValueCell(val(transfer.getFinanceRepresentativeName()), regularFont));

        return table;
    }

    private Table createCompleteAdditionalInfoTable(Transfer transfer, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{20, 80}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        table.addCell(compactLabelCell("Software Installed", boldFont));
        table.addCell(compactValueCell(val(transfer.getSoftwareInstalled()), regularFont));

        table.addCell(compactLabelCell("Comments", boldFont));
        table.addCell(compactValueCell(val(transfer.getComments()), regularFont));

        return table;
    }

    // ============================================
    // Complete Signature Table - 3 Columns Side by Side
    // ============================================

    private Table createCompleteSignatureTable(Transfer transfer, PdfFont boldFont, PdfFont regularFont) {
        // 3 columns for signatures side by side
        Table table = new Table(UnitValue.createPercentArray(new float[]{33.3f, 33.3f, 33.3f}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(4);

        // Create signature boxes for each signer
        List<SignatureBox> signatureBoxes = Arrays.asList(
                createSignatureBoxData("Old Handover", transfer.getOldHandoverByName(),
                        transfer.getOldHandoverByStaffId(), transfer.getOldHandoverBySignature(),
                        transfer.getOldHandoverBySignedAt(), boldFont, regularFont),
                createSignatureBoxData("Old Received", transfer.getOldReceivedByName(),
                        transfer.getOldReceivedByStaffId(), transfer.getOldReceivedBySignature(),
                        transfer.getOldReceivedBySignedAt(), boldFont, regularFont),
                createSignatureBoxData("New Handover", transfer.getNewHandoverByName(),
                        transfer.getNewHandoverByStaffId(), transfer.getNewHandoverBySignature(),
                        transfer.getNewHandoverBySignedAt(), boldFont, regularFont),
                createSignatureBoxData("New Received", transfer.getNewReceivedByName(),
                        transfer.getNewReceivedByStaffId(), transfer.getNewReceivedBySignature(),
                        transfer.getNewReceivedBySignedAt(), boldFont, regularFont),
                createSignatureBoxData("Configured By", transfer.getConfiguredByName(),
                        transfer.getConfiguredByStaffId(), transfer.getConfiguredBySignature(),
                        transfer.getConfiguredBySignedAt(), boldFont, regularFont),
                createSignatureBoxData("Infrastructure Rep", transfer.getInfraRepresentativeName(),
                        transfer.getInfraRepresentativeStaffId(), transfer.getInfraRepSignature(),
                        transfer.getInfraRepSignedAt(), boldFont, regularFont),
                createSignatureBoxData("Finance Rep", transfer.getFinanceRepresentativeName(),
                        transfer.getFinanceRepresentativeStaffId(), transfer.getFinanceRepSignature(),
                        transfer.getFinanceRepSignedAt(), boldFont, regularFont)
        );

        // Add to table in 3-column layout
        for (SignatureBox sig : signatureBoxes) {
            table.addCell(sig.cell);
        }

        return table;
    }

    private SignatureBox createSignatureBoxData(String role, String name, String staffId,
                                                String signature, LocalDateTime signedAt,
                                                PdfFont boldFont, PdfFont regularFont) {
        Cell box = new Cell()
                .setBorder(new SolidBorder(LINE_SOFT, 0.5f))
                .setPadding(4)
                .setBackgroundColor(WHITE);

        // Role
        box.add(new Paragraph(role.toUpperCase())
                .setFont(boldFont)
                .setFontSize(6.5f)
                .setFontColor(ACCENT)
                .setCharacterSpacing(0.4f)
                .setMarginBottom(2));

        // Name
        box.add(new Paragraph(val(name))
                .setFont(boldFont)
                .setFontSize(8)
                .setFontColor(INK)
                .setMarginBottom(1));

        // Staff ID
        box.add(new Paragraph("Staff ID: " + val(staffId))
                .setFont(regularFont)
                .setFontSize(6.5f)
                .setFontColor(INK_SOFT)
                .setMarginBottom(2));

        // Signature line
        Div sigLine = new Div()
                .setHeight(20)
                .setBorderBottom(new SolidBorder(INK, 0.5f))
                .setMarginBottom(2);

        boolean isSigned = signature != null && !signature.isBlank();
        if (isSigned) {
            try {
                String clean = signature.startsWith("data:image")
                        ? signature.substring(signature.indexOf(",") + 1)
                        : signature;
                byte[] sigBytes = Base64.getDecoder().decode(clean);
                ImageData sigData = ImageDataFactory.create(sigBytes);
                Image sigImage = new Image(sigData);
                sigImage.setMaxHeight(18);
                sigImage.setMaxWidth(80);
                sigLine.add(sigImage);
            } catch (Exception e) {
                log.warn("Could not decode signature for {}: {}", role, e.getMessage());
                sigLine.add(new Paragraph("✓").setFont(boldFont).setFontSize(12).setFontColor(GOOD));
            }
        } else {
            sigLine.add(new Paragraph("___________")
                    .setFont(regularFont).setFontSize(7).setFontColor(MUTED));
        }
        box.add(sigLine);

        // Status
        String status = isSigned ? "✓ Signed" : "⎯ Pending";
        DeviceRgb statusColor = isSigned ? GOOD : MUTED;
        box.add(new Paragraph(status)
                .setFont(regularFont)
                .setFontSize(6)
                .setFontColor(statusColor)
                .setItalic());

        if (isSigned && signedAt != null) {
            box.add(new Paragraph(signedAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")))
                    .setFont(regularFont)
                    .setFontSize(5.5f)
                    .setFontColor(INK_SOFT));
        }

        return new SignatureBox(box);
    }

    // ============================================
    // Compact Cell Helpers
    // ============================================

    private Cell compactLabelCell(String text, PdfFont boldFont) {
        return new Cell()
                .setBackgroundColor(WASH)
                .setBorder(new SolidBorder(LINE_SOFT, 0.5f))
                .setPadding(3)
                .add(new Paragraph(text)
                        .setFont(boldFont)
                        .setFontSize(7)
                        .setFontColor(INK_SOFT)
                        .setCharacterSpacing(0.3f));
    }

    private Cell compactValueCell(String text, PdfFont font) {
        return new Cell()
                .setBorder(new SolidBorder(LINE_SOFT, 0.5f))
                .setPadding(3)
                .add(new Paragraph(text)
                        .setFont(font)
                        .setFontSize(8)
                        .setFontColor(INK));
    }

    private Cell compactHeaderCell(String text, PdfFont boldFont) {
        return new Cell()
                .setBackgroundColor(NAVY)
                .setPadding(3)
                .add(new Paragraph(text)
                        .setFont(boldFont)
                        .setFontSize(7)
                        .setFontColor(WHITE)
                        .setCharacterSpacing(0.3f));
    }

    private void addCompactFooter(Document document, String ref, PdfFont regularFont) {
        LineSeparator line = new LineSeparator(new SolidLine(0.5f));
        line.setStrokeColor(LINE_SOFT);
        line.setMarginTop(6);
        line.setMarginBottom(2);
        document.add(line);

        Table footer = new Table(UnitValue.createPercentArray(new float[]{1, 1}));
        footer.setWidth(UnitValue.createPercentValue(100));

        Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(2);
        left.add(new Paragraph(ref + "  ·  Generated " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")))
                .setFont(regularFont).setFontSize(6.5f).setFontColor(INK_SOFT));
        footer.addCell(left);

        Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(2).setTextAlignment(TextAlignment.RIGHT);
        right.add(new Paragraph("AssetIQ-Pro").setFont(regularFont).setFontSize(6.5f).setFontColor(INK_SOFT));
        footer.addCell(right);

        document.add(footer);
    }

    // ============================================
    // Common Helper Methods
    // ============================================

    private String val(String s) {
        return (s == null || s.isBlank()) ? "N/A" : s;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "-";
        return dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
    }

    // ============================================
    // Inner Classes
    // ============================================

    private static class SignatureBox {
        final Cell cell;
        SignatureBox(Cell cell) {
            this.cell = cell;
        }
    }
}