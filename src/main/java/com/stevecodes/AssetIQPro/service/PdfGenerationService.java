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
import com.stevecodes.AssetIQPro.entity.Transfer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
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
    private static final DeviceRgb PRIMARY_BLUE = new DeviceRgb(0, 102, 204);

    public byte[] generateTransferCertificatePdf(Transfer transfer, List<Transfer> relatedTransfers) throws Exception {
        log.info("Generating premium asset transfer certificate for transfer {}", transfer.getTransferId());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(28, 28, 24, 28);

        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont monoFont = PdfFontFactory.createFont(StandardFonts.COURIER);

        addLetterhead(document, transfer, boldFont, regularFont);
        addStatusStrip(document, transfer, boldFont, regularFont);
        addAssetInformationSection(document, transfer, boldFont, regularFont, monoFont);
        addTransferPartiesSection(document, transfer, boldFont, regularFont);
        addConditionAccessoriesSection(document, transfer, boldFont, regularFont);
        addTransactionHistorySection(document, transfer, relatedTransfers, boldFont, regularFont);
        addTextBlockSection(document, "Software Installed", transfer.getSoftwareInstalled(), boldFont, regularFont);
        addTextBlockSection(document, "Comments", transfer.getComments(), boldFont, regularFont);
        addSignaturesSection(document, transfer, boldFont, regularFont);
        addFooter(document, transfer, regularFont);

        document.close();
        log.info("Generated premium PDF for transfer {}", transfer.getTransferId());
        return outputStream.toByteArray();
    }

    private void addLetterhead(Document document, Transfer transfer, PdfFont boldFont, PdfFont regularFont) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1.2f, 4.4f, 2.4f, 1.3f}));
        header.setWidth(UnitValue.createPercentValue(100));

        // Brand mark
        Cell brandCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        brandCell.add(new Paragraph("IQ")
                .setFont(boldFont).setFontSize(13).setFontColor(WHITE)
                .setBackgroundColor(NAVY).setPadding(6)
                .setTextAlignment(TextAlignment.CENTER));
        header.addCell(brandCell);

        // Title
        Cell titleCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        titleCell.add(new Paragraph("ASSET TRANSFER CERTIFICATE")
                .setFont(boldFont).setFontSize(15).setFontColor(NAVY).setCharacterSpacing(0.8f));
        titleCell.add(new Paragraph("AssetIQ-Pro — Asset Management System")
                .setFont(regularFont).setFontSize(8.5f).setFontColor(INK_SOFT));
        header.addCell(titleCell);

        // Transfer ID
        Cell refCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .setTextAlignment(TextAlignment.RIGHT).setVerticalAlignment(VerticalAlignment.MIDDLE);
        String transferIdText = transfer.getTransferId() != null ? String.valueOf(transfer.getTransferId()) : "N/A";
        String dateText = transfer.getTransferDate() != null
                ? transfer.getTransferDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                : "N/A";
        refCell.add(new Paragraph("Transfer #: " + transferIdText).setFont(regularFont).setFontSize(9).setFontColor(INK_SOFT));
        refCell.add(new Paragraph("Date: " + dateText).setFont(regularFont).setFontSize(9).setFontColor(INK_SOFT));
        header.addCell(refCell);

        // QR Code
        Cell qrCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        try {
            Image qrImage = generateQRCode(transfer);
            if (qrImage != null) {
                qrImage.setWidth(46);
                qrImage.setHeight(46);
                qrCell.add(qrImage);
            }
        } catch (Exception e) {
            log.warn("QR generation failed: {}", e.getMessage());
        }
        header.addCell(qrCell);

        document.add(header);

        LineSeparator line = new LineSeparator(new SolidLine(2f));
        line.setStrokeColor(NAVY);
        line.setMarginTop(4);
        line.setMarginBottom(6);
        document.add(line);
    }

    private void addStatusStrip(Document document, Transfer transfer, PdfFont boldFont, PdfFont regularFont) {
        boolean isSigned = Boolean.TRUE.equals(transfer.getIsFullySigned());

        Table strip = new Table(UnitValue.createPercentArray(new float[]{4f, 1.5f}));
        strip.setWidth(UnitValue.createPercentValue(100));
        strip.setMarginBottom(4);

        Cell textCell = new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(WASH)
                .setPadding(6).setVerticalAlignment(VerticalAlignment.MIDDLE);
        textCell.add(new Paragraph("This document certifies the handover of the IT asset described below.")
                .setFont(regularFont).setFontSize(8.5f).setFontColor(INK_SOFT));
        strip.addCell(textCell);

        Cell badgeCell = new Cell().setBorder(Border.NO_BORDER).setBackgroundColor(WASH)
                .setPadding(6).setTextAlignment(TextAlignment.RIGHT).setVerticalAlignment(VerticalAlignment.MIDDLE);

        String badgeText = isSigned ? "FULLY SIGNED" : "PENDING";
        DeviceRgb badgeColor = isSigned ? GOOD : WARN;
        DeviceRgb badgeBg = isSigned ? GOOD_BG : WARN_BG;

        badgeCell.add(new Paragraph(badgeText)
                .setFont(boldFont).setFontSize(8.5f).setFontColor(badgeColor)
                .setBackgroundColor(badgeBg).setPadding(4).setPaddingLeft(10).setPaddingRight(10)
                .setCharacterSpacing(0.4f).setTextAlignment(TextAlignment.CENTER));
        strip.addCell(badgeCell);
        document.add(strip);
    }

    private void addSectionHeading(Document document, String title, PdfFont boldFont) {
        Paragraph heading = new Paragraph(title.toUpperCase())
                .setFont(boldFont).setFontSize(10.5f).setFontColor(NAVY)
                .setCharacterSpacing(0.6f)
                .setBorderBottom(new SolidBorder(NAVY, 1))
                .setPaddingBottom(3).setMarginTop(12).setMarginBottom(7);
        document.add(heading);
    }

    private void addAssetInformationSection(Document document, Transfer transfer, PdfFont boldFont,
                                            PdfFont regularFont, PdfFont monoFont) {
        addSectionHeading(document, "Asset Information", boldFont);

        Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}));
        grid.setWidth(UnitValue.createPercentValue(100));
        grid.setMarginBottom(6);

        grid.addCell(fieldCell("Asset Tag", val(transfer.getAssetTag()), boldFont, monoFont));
        grid.addCell(fieldCell("Serial Number", val(transfer.getSerialNumber()), boldFont, monoFont));
        grid.addCell(fieldCell("Version / Make", val(transfer.getVersionMake()), boldFont, regularFont));
        grid.addCell(fieldCell("Model Build", val(transfer.getModelBuild()), boldFont, regularFont));
        grid.addCell(fieldCell("Transfer Date", transfer.getTransferDate() != null ?
                        transfer.getTransferDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "N/A",
                boldFont, regularFont));
        grid.addCell(fieldCell("Status", Boolean.TRUE.equals(transfer.getIsFullySigned()) ? "COMPLETED" : "PENDING",
                boldFont, regularFont));

        document.add(grid);
    }

    private Cell fieldCell(String label, String value, PdfFont boldFont, PdfFont valueFont) {
        Cell cell = new Cell().setBorder(new SolidBorder(LINE_SOFT, 0.75f)).setPadding(7);
        cell.add(new Paragraph(label.toUpperCase())
                .setFont(boldFont).setFontSize(7.5f).setFontColor(INK_SOFT).setCharacterSpacing(0.4f)
                .setMarginBottom(2));
        cell.add(new Paragraph(value)
                .setFont(valueFont).setFontSize(10.5f).setFontColor(INK).setMultipliedLeading(1.1f));
        return cell;
    }

    private void addTransferPartiesSection(Document document, Transfer transfer, PdfFont boldFont, PdfFont regularFont) {
        addSectionHeading(document, "Transfer Parties", boldFont);

        Table parties = new Table(UnitValue.createPercentArray(new float[]{1, 1}));
        parties.setWidth(UnitValue.createPercentValue(100));
        parties.setMarginBottom(6);

        String oldDept = transfer.getOldDepartmentId() != null ? "Dept ID: " + transfer.getOldDepartmentId() : "N/A";
        String newDept = transfer.getNewDepartmentId() != null ? "Dept ID: " + transfer.getNewDepartmentId() : "N/A";
        String oldEmp = transfer.getOldEmployeeId() != null ? "Emp ID: " + transfer.getOldEmployeeId() : "N/A";
        String newEmp = transfer.getNewEmployeeId() != null ? "Emp ID: " + transfer.getNewEmployeeId() : "N/A";

        parties.addCell(partyCard("From (Outgoing)", NAVY_2, oldDept, oldEmp, boldFont, regularFont));
        parties.addCell(partyCard("To (Incoming)", ACCENT, newDept, newEmp, boldFont, regularFont));

        document.add(parties);
    }

    private Cell partyCard(String headLabel, DeviceRgb accentColor, String dept, String emp,
                           PdfFont boldFont, PdfFont regularFont) {
        Cell card = new Cell().setBorder(new SolidBorder(LINE_SOFT, 0.75f)).setPadding(0);

        Paragraph head = new Paragraph(headLabel.toUpperCase())
                .setFont(boldFont).setFontSize(8.5f).setFontColor(accentColor).setCharacterSpacing(0.4f)
                .setBackgroundColor(WASH).setPadding(6).setMarginBottom(0)
                .setBorderBottom(new SolidBorder(LINE_SOFT, 0.75f));
        card.add(head);

        Paragraph deptP = new Paragraph()
                .add(new Text("DEPARTMENT\n").setFont(boldFont).setFontSize(7.5f).setFontColor(INK_SOFT).setCharacterSpacing(0.4f))
                .add(new Text(val(dept)).setFont(regularFont).setFontSize(10.5f).setFontColor(INK))
                .setPadding(7).setMarginBottom(0);
        card.add(deptP);

        Paragraph empP = new Paragraph()
                .add(new Text("EMPLOYEE\n").setFont(boldFont).setFontSize(7.5f).setFontColor(INK_SOFT).setCharacterSpacing(0.4f))
                .add(new Text(val(emp)).setFont(regularFont).setFontSize(10.5f).setFontColor(INK))
                .setPadding(7).setMarginBottom(0);
        card.add(empP);

        return card;
    }

    private void addConditionAccessoriesSection(Document document, Transfer transfer, PdfFont boldFont, PdfFont regularFont) {
        addSectionHeading(document, "Condition & Accessories", boldFont);

        Table table = new Table(UnitValue.createPercentArray(new float[]{1.3f, 2.5f, 2.5f}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(6);

        table.addHeaderCell(compareHeaderCell("", boldFont));
        table.addHeaderCell(compareHeaderCell("Old (Prior)", boldFont));
        table.addHeaderCell(compareHeaderCell("New (Handover)", boldFont));

        table.addCell(compareLabelCell("Condition", boldFont));
        table.addCell(compareValueCell(val(transfer.getConditionOld()), regularFont));
        table.addCell(compareValueCell(val(transfer.getConditionNew()), regularFont));

        table.addCell(compareLabelCell("Accessories", boldFont));
        table.addCell(compareValueCell(val(transfer.getAccessoriesOld()), regularFont));
        table.addCell(compareValueCell(val(transfer.getAccessoriesNew()), regularFont));

        document.add(table);
    }

    private Cell compareHeaderCell(String text, PdfFont boldFont) {
        return new Cell().setBackgroundColor(NAVY).setPadding(6)
                .setBorder(new SolidBorder(NAVY, 0.5f))
                .add(new Paragraph(text.toUpperCase())
                        .setFont(boldFont).setFontSize(8).setFontColor(WHITE).setCharacterSpacing(0.4f));
    }

    private Cell compareLabelCell(String text, PdfFont boldFont) {
        return new Cell().setBackgroundColor(WASH).setPadding(7)
                .setBorder(new SolidBorder(LINE_SOFT, 0.75f))
                .add(new Paragraph(text.toUpperCase())
                        .setFont(boldFont).setFontSize(8.5f).setFontColor(INK_SOFT).setCharacterSpacing(0.4f));
    }

    private Cell compareValueCell(String text, PdfFont regularFont) {
        return new Cell().setPadding(7)
                .setBorder(new SolidBorder(LINE_SOFT, 0.75f))
                .add(new Paragraph(text).setFont(regularFont).setFontSize(10).setFontColor(INK));
    }

    private void addTransactionHistorySection(Document document, Transfer currentTransfer,
                                              List<Transfer> relatedTransfers,
                                              PdfFont boldFont, PdfFont regularFont) {
        addSectionHeading(document, "Transaction History", boldFont);

        if (relatedTransfers == null || relatedTransfers.isEmpty()) {
            Paragraph none = new Paragraph("No prior transfers recorded for this asset.")
                    .setFont(regularFont).setFontSize(9.5f).setFontColor(MUTED).setItalic()
                    .setBorder(new SolidBorder(LINE_SOFT, 0.75f))
                    .setPadding(8).setMarginBottom(6);
            document.add(none);
            return;
        }

        Table table = new Table(UnitValue.createPercentArray(new float[]{0.9f, 1.3f, 2.2f, 2.2f, 1.4f}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.setMarginBottom(6);

        table.addHeaderCell(compareHeaderCell("ID", boldFont));
        table.addHeaderCell(compareHeaderCell("Date", boldFont));
        table.addHeaderCell(compareHeaderCell("From", boldFont));
        table.addHeaderCell(compareHeaderCell("To", boldFont));
        table.addHeaderCell(compareHeaderCell("Status", boldFont));

        for (Transfer h : relatedTransfers) {
            String dateText = h.getTransferDate() != null
                    ? h.getTransferDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    : "N/A";
            String fromDept = h.getOldDepartmentId() != null ? String.valueOf(h.getOldDepartmentId()) : "N/A";
            String toDept = h.getNewDepartmentId() != null ? String.valueOf(h.getNewDepartmentId()) : "N/A";
            boolean signed = Boolean.TRUE.equals(h.getIsFullySigned());

            table.addCell(compareValueCell(String.valueOf(h.getTransferId()), regularFont));
            table.addCell(compareValueCell(dateText, regularFont));
            table.addCell(compareValueCell(fromDept, regularFont));
            table.addCell(compareValueCell(toDept, regularFont));

            Cell statusCell = new Cell().setPadding(7).setBorder(new SolidBorder(LINE_SOFT, 0.75f));
            statusCell.add(new Paragraph(signed ? "COMPLETED" : "PENDING")
                    .setFont(boldFont).setFontSize(8)
                    .setFontColor(signed ? GOOD : WARN)
                    .setBackgroundColor(signed ? GOOD_BG : WARN_BG)
                    .setPadding(3).setPaddingLeft(6).setPaddingRight(6)
                    .setTextAlignment(TextAlignment.CENTER));
            table.addCell(statusCell);
        }

        document.add(table);
    }

    private void addTextBlockSection(Document document, String title, String content,
                                     PdfFont boldFont, PdfFont regularFont) {
        addSectionHeading(document, title, boldFont);

        boolean isEmpty = content == null || content.isBlank();
        Paragraph p = new Paragraph(isEmpty ? "No " + title.toLowerCase() + " recorded." : content)
                .setFont(regularFont).setFontSize(10)
                .setFontColor(isEmpty ? MUTED : INK)
                .setBorder(new SolidBorder(LINE_SOFT, 0.75f))
                .setPadding(8).setMarginBottom(6);
        if (isEmpty) p.setItalic();
        document.add(p);
    }

    private void addSignaturesSection(Document document, Transfer transfer, PdfFont boldFont, PdfFont regularFont) {
        addSectionHeading(document, "Signatures", boldFont);

        Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}));
        grid.setWidth(UnitValue.createPercentValue(100));
        grid.setMarginBottom(4);

        // REMOVED: From Employee and To Employee
        grid.addCell(buildSignatureBox("Configured By",
                transfer.getConfiguredByName(),  // name
                transfer.getConfiguredBySignature(),
                transfer.getConfiguredBySignedAt(), boldFont, regularFont));

        grid.addCell(buildSignatureBox("Old Handover",
                transfer.getOldHandoverByName(),  // name
                transfer.getOldHandoverBySignature(),
                transfer.getOldHandoverBySignedAt(), boldFont, regularFont));
        grid.addCell(buildSignatureBox("Old Received",
                transfer.getOldReceivedByName(),  // name
                transfer.getOldReceivedBySignature(),
                transfer.getOldReceivedBySignedAt(), boldFont, regularFont));

        grid.addCell(buildSignatureBox("New Handover",
                transfer.getNewHandoverByName(),  // name
                transfer.getNewHandoverBySignature(),
                transfer.getNewHandoverBySignedAt(), boldFont, regularFont));
        grid.addCell(buildSignatureBox("New Received",
                transfer.getNewReceivedByName(),  // name
                transfer.getNewReceivedBySignature(),
                transfer.getNewReceivedBySignedAt(), boldFont, regularFont));

        grid.addCell(buildSignatureBox("Infrastructure Rep",
                transfer.getInfraRepresentativeName(),  // name
                transfer.getInfraRepSignature(),
                transfer.getInfraRepSignedAt(), boldFont, regularFont));
        grid.addCell(buildSignatureBox("Finance Rep",
                transfer.getFinanceRepresentativeName(),  // name
                transfer.getFinanceRepSignature(),
                transfer.getFinanceRepSignedAt(), boldFont, regularFont));

        document.add(grid);
    }

    private Cell buildSignatureBox(String role, String name, String signature, LocalDateTime signedAt,
                                   PdfFont boldFont, PdfFont regularFont) {
        Cell box = new Cell().setBorder(new SolidBorder(LINE_SOFT, 0.75f)).setPadding(9);

        box.add(new Paragraph(role.toUpperCase())
                .setFont(boldFont).setFontSize(7.5f).setFontColor(ACCENT)
                .setCharacterSpacing(0.5f).setMarginBottom(14));

        Div sigLine = new Div().setHeight(28)
                .setBorderBottom(new SolidBorder(INK, 0.75f))
                .setMarginBottom(4);

        boolean isSigned = signature != null && !signature.isBlank();
        if (isSigned) {
            try {
                String clean = signature.startsWith("data:image")
                        ? signature.substring(signature.indexOf(",") + 1)
                        : signature;
                byte[] sigBytes = Base64.getDecoder().decode(clean);
                ImageData sigData = ImageDataFactory.create(sigBytes);
                Image sigImage = new Image(sigData);
                sigImage.setMaxHeight(24);
                sigLine.add(sigImage);
            } catch (Exception e) {
                log.warn("Could not decode signature for role {}: {}", role, e.getMessage());
                sigLine.add(new Paragraph("✓ Signed").setFont(boldFont).setFontSize(11).setFontColor(GOOD));
            }
        } else {
            sigLine.add(new Paragraph("________________________")
                    .setFont(regularFont).setFontSize(8.5f).setFontColor(MUTED));
        }
        box.add(sigLine);

        // Show signer name
        box.add(new Paragraph(val(name))
                .setFont(boldFont).setFontSize(9.5f).setFontColor(isSigned ? INK : MUTED).setMarginBottom(1));

        String metaText = (isSigned && signedAt != null)
                ? "Signed " + signedAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                : "Not yet signed";
        box.add(new Paragraph(metaText).setFont(regularFont).setFontSize(8).setFontColor(INK_SOFT));

        return box;
    }

    private void addFooter(Document document, Transfer transfer, PdfFont regularFont) {
        LineSeparator line = new LineSeparator(new SolidLine(0.75f));
        line.setStrokeColor(LINE_SOFT);
        line.setMarginTop(10);
        line.setMarginBottom(4);
        document.add(line);

        Table footer = new Table(UnitValue.createPercentArray(new float[]{1, 1}));
        footer.setWidth(UnitValue.createPercentValue(100));

        String transferIdText = transfer.getTransferId() != null ? String.valueOf(transfer.getTransferId()) : "N/A";

        Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(2);
        left.add(new Paragraph("Transfer #" + transferIdText + "  ·  Generated " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")))
                .setFont(regularFont).setFontSize(7.5f).setFontColor(INK_SOFT));
        footer.addCell(left);

        Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(2).setTextAlignment(TextAlignment.RIGHT);
        right.add(new Paragraph("AssetIQ-Pro").setFont(regularFont).setFontSize(7.5f).setFontColor(INK_SOFT));
        footer.addCell(right);

        document.add(footer);

        boolean isSigned = Boolean.TRUE.equals(transfer.getIsFullySigned());
        String watermark = isSigned ? "OFFICIAL DOCUMENT — AUTHENTICATED" : "OFFICIAL DOCUMENT — PENDING";
        document.add(new Paragraph(watermark)
                .setFont(regularFont).setFontSize(7)
                .setFontColor(WATERMARK).setCharacterSpacing(0.6f)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(3));
    }

    private Image generateQRCode(Transfer transfer) {
        try {
            StringBuilder content = new StringBuilder();
            content.append("Transfer: ").append(transfer.getTransferId())
                    .append("\nAsset: ").append(transfer.getAssetTag())
                    .append("\nStatus: ").append(Boolean.TRUE.equals(transfer.getIsFullySigned()) ? "COMPLETED" : "PENDING")
                    .append("\nDate: ").append(transfer.getTransferDate());

            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix matrix = qrWriter.encode(content.toString(), BarcodeFormat.QR_CODE, 150, 150);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);

            ImageData imgData = ImageDataFactory.create(baos.toByteArray());
            return new Image(imgData);

        } catch (WriterException | java.io.IOException e) {
            log.warn("QR generation failed: {}", e.getMessage());
            return null;
        }
    }

    private String val(String s) {
        return (s == null || s.isBlank()) ? "N/A" : s;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "-";
        return dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
    }

    public byte[] generateInfraRequestReport(InfraRequest request) throws Exception {
        log.info("Generating infrastructure request report for request {}", request.getRequestId());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(28, 28, 24, 28);

        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

        // Header
        addInfraHeader(document, request, boldFont, regularFont);

        // Request Details
        addSectionHeading(document, "Request Details", boldFont);
        Table detailsTable = createInfraDetailsTable(request, boldFont, regularFont);
        document.add(detailsTable);

        // Approval Timeline
        addSectionHeading(document, "Approval Timeline", boldFont);
        Table timelineTable = createInfraTimelineTable(request, boldFont, regularFont);
        document.add(timelineTable);

        // Signatures
        addSectionHeading(document, "Signatures", boldFont);
        Table signatureTable = createInfraSignatureTable(request, boldFont, regularFont);
        document.add(signatureTable);

        addInfraFooter(document, request, regularFont);

        document.close();
        log.info("Generated infrastructure request report for request {}", request.getRequestId());
        return outputStream.toByteArray();
    }

    private void addInfraHeader(Document document, InfraRequest request, PdfFont boldFont, PdfFont regularFont) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1.2f, 4.4f, 2.4f, 1.3f}));
        header.setWidth(UnitValue.createPercentValue(100));

        Cell brandCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        brandCell.add(new Paragraph("IQ")
                .setFont(boldFont).setFontSize(13).setFontColor(WHITE)
                .setBackgroundColor(NAVY).setPadding(6)
                .setTextAlignment(TextAlignment.CENTER));
        header.addCell(brandCell);

        Cell titleCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        titleCell.add(new Paragraph("INFRASTRUCTURE REQUEST REPORT")
                .setFont(boldFont).setFontSize(15).setFontColor(NAVY).setCharacterSpacing(0.8f));
        titleCell.add(new Paragraph("AssetIQ-Pro — Asset Management System")
                .setFont(regularFont).setFontSize(8.5f).setFontColor(INK_SOFT));
        header.addCell(titleCell);

        Cell refCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .setTextAlignment(TextAlignment.RIGHT).setVerticalAlignment(VerticalAlignment.MIDDLE);
        refCell.add(new Paragraph("Request #: " + request.getRequestId()).setFont(regularFont).setFontSize(9).setFontColor(INK_SOFT));
        refCell.add(new Paragraph("Date: " + formatDateTime(request.getCreatedAt())).setFont(regularFont).setFontSize(9).setFontColor(INK_SOFT));
        header.addCell(refCell);

        Cell qrCell = new Cell().setBorder(Border.NO_BORDER).setPadding(4)
                .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        try {
            Image qrImage = generateInfraQRCode(request);
            if (qrImage != null) {
                qrImage.setWidth(46);
                qrImage.setHeight(46);
                qrCell.add(qrImage);
            }
        } catch (Exception e) {
            log.warn("QR generation failed: {}", e.getMessage());
        }
        header.addCell(qrCell);

        document.add(header);

        LineSeparator line = new LineSeparator(new SolidLine(2f));
        line.setStrokeColor(NAVY);
        line.setMarginTop(4);
        line.setMarginBottom(6);
        document.add(line);
    }

    private Table createInfraDetailsTable(InfraRequest request, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 70}));
        table.setWidth(UnitValue.createPercentValue(100));

        table.addCell(fieldCell("Request ID", String.valueOf(request.getRequestId()), boldFont, regularFont));
        table.addCell(fieldCell("Resource Type", request.getResourceType(), boldFont, regularFont));
        table.addCell(fieldCell("Quantity", String.valueOf(request.getQuantity()), boldFont, regularFont));
        table.addCell(fieldCell("Status", request.getStatus().name(), boldFont, regularFont));
        table.addCell(fieldCell("Created At", formatDateTime(request.getCreatedAt()), boldFont, regularFont));
        table.addCell(fieldCell("Specification", val(request.getSpecification()), boldFont, regularFont));
        table.addCell(fieldCell("Justification", val(request.getJustification()), boldFont, regularFont));

        if (request.getPurchaseCost() != null) {
            table.addCell(fieldCell("Purchase Cost", "$" + request.getPurchaseCost().toString(), boldFont, regularFont));
        }

        return table;
    }

    private Table createInfraTimelineTable(InfraRequest request, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 40, 30}));
        table.setWidth(UnitValue.createPercentValue(100));

        table.addHeaderCell(compareHeaderCell("Stage", boldFont));
        table.addHeaderCell(compareHeaderCell("Approved By", boldFont));
        table.addHeaderCell(compareHeaderCell("Timestamp", boldFont));

        table.addCell(compareValueCell("Line Manager", regularFont));
        table.addCell(compareValueCell(request.getLmApprovedBy() != null ? String.valueOf(request.getLmApprovedBy()) : "Pending", regularFont));
        table.addCell(compareValueCell(request.getLmApprovedAt() != null ? formatDateTime(request.getLmApprovedAt()) : "-", regularFont));

        table.addCell(compareValueCell("Infrastructure", regularFont));
        table.addCell(compareValueCell(request.getInfraReviewedBy() != null ? String.valueOf(request.getInfraReviewedBy()) : "Pending", regularFont));
        table.addCell(compareValueCell(request.getInfraReviewedAt() != null ? formatDateTime(request.getInfraReviewedAt()) : "-", regularFont));

        table.addCell(compareValueCell("Finance", regularFont));
        table.addCell(compareValueCell(request.getFinanceApprovedBy() != null ? String.valueOf(request.getFinanceApprovedBy()) : "Pending", regularFont));
        table.addCell(compareValueCell(request.getFinanceApprovedAt() != null ? formatDateTime(request.getFinanceApprovedAt()) : "-", regularFont));

        return table;
    }

    private Table createInfraSignatureTable(InfraRequest request, PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.addHeaderCell(compareHeaderCell("Role", boldFont));
        table.addHeaderCell(compareHeaderCell("Signature", boldFont));

        table.addCell(compareValueCell("Requester", regularFont));
        table.addCell(compareValueCell("________________________", regularFont));
        table.addCell(compareValueCell("Line Manager", regularFont));
        table.addCell(compareValueCell("________________________", regularFont));
        table.addCell(compareValueCell("Infrastructure", regularFont));
        table.addCell(compareValueCell("________________________", regularFont));
        table.addCell(compareValueCell("Finance", regularFont));
        table.addCell(compareValueCell("________________________", regularFont));

        return table;
    }

    private void addInfraFooter(Document document, InfraRequest request, PdfFont regularFont) {
        LineSeparator line = new LineSeparator(new SolidLine(0.75f));
        line.setStrokeColor(LINE_SOFT);
        line.setMarginTop(10);
        line.setMarginBottom(4);
        document.add(line);

        Table footer = new Table(UnitValue.createPercentArray(new float[]{1, 1}));
        footer.setWidth(UnitValue.createPercentValue(100));

        Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(2);
        left.add(new Paragraph("Request #" + request.getRequestId() + "  ·  Generated " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")))
                .setFont(regularFont).setFontSize(7.5f).setFontColor(INK_SOFT));
        footer.addCell(left);

        Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(2).setTextAlignment(TextAlignment.RIGHT);
        right.add(new Paragraph("AssetIQ-Pro").setFont(regularFont).setFontSize(7.5f).setFontColor(INK_SOFT));
        footer.addCell(right);

        document.add(footer);
    }

    private Image generateInfraQRCode(InfraRequest request) {
        try {
            StringBuilder content = new StringBuilder();
            content.append("Request: ").append(request.getRequestId())
                    .append("\nType: ").append(request.getResourceType())
                    .append("\nStatus: ").append(request.getStatus().name());

            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix matrix = qrWriter.encode(content.toString(), BarcodeFormat.QR_CODE, 150, 150);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);

            ImageData imgData = ImageDataFactory.create(baos.toByteArray());
            return new Image(imgData);

        } catch (WriterException | java.io.IOException e) {
            log.warn("QR generation failed: {}", e.getMessage());
            return null;
        }
    }
}