package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.InfraRequest;
import com.stevecodes.AssetIQPro.entity.Transfer;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfGenerationService {

    private static final Color PRIMARY_COLOR = new DeviceRgb(0, 102, 204);
    private static final Color SECONDARY_COLOR = new DeviceRgb(51, 51, 51);
    private static final Color LIGHT_GRAY = new DeviceRgb(240, 240, 240);

    // ============================================
    // Infra Request Report
    // ============================================

    public byte[] generateInfraRequestReport(InfraRequest request) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.A4);

        // Header
        addHeader(document, "Infrastructure Request Report");
        addSubHeader(document, "Request #" + request.getRequestId());

        // Request Details
        addSectionTitle(document, "Request Details");
        Table detailsTable = createDetailsTable(request);
        document.add(detailsTable);

        // Approval Timeline
        addSectionTitle(document, "Approval Timeline");
        Table timelineTable = createTimelineTable(request);
        document.add(timelineTable);

        // Signatures
        addSectionTitle(document, "Signatures");
        Table signatureTable = createSignatureTable(request);
        document.add(signatureTable);

        // Footer
        addFooter(document);

        document.close();
        return baos.toByteArray();
    }

    // ============================================
    // Transfer Certificate
    // ============================================

    public byte[] generateTransferCertificatePdf(Transfer transfer, List<Transfer> relatedTransfers) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf, PageSize.A4);

        // Header
        addHeader(document, "Asset Transfer Certificate");
        addSubHeader(document, "Transfer #" + transfer.getTransferId());

        // Transfer Details
        addSectionTitle(document, "Transfer Details");
        Table detailsTable = createTransferDetailsTable(transfer);
        document.add(detailsTable);

        // Asset History
        if (relatedTransfers != null && !relatedTransfers.isEmpty()) {
            addSectionTitle(document, "Asset History");
            Table historyTable = createHistoryTable(relatedTransfers);
            document.add(historyTable);
        }

        // Signatures
        addSectionTitle(document, "Signatures");
        Table signatureTable = createTransferSignatureTable(transfer);
        document.add(signatureTable);

        // Footer
        addFooter(document);

        document.close();
        return baos.toByteArray();
    }

    // ============================================
    // Table Creation Methods
    // ============================================

    private Table createDetailsTable(InfraRequest request) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 70}));
        table.setWidth(UnitValue.createPercentValue(100));

        addRow(table, "Request ID", request.getRequestId().toString());
        addRow(table, "Resource Type", request.getResourceType());
        addRow(table, "Quantity", request.getQuantity().toString());
        addRow(table, "Status", request.getStatus().name());
        addRow(table, "Created At", formatDateTime(request.getCreatedAt()));
        addRow(table, "Specification", request.getSpecification());
        addRow(table, "Justification", request.getJustification());

        if (request.getPurchaseCost() != null) {
            addRow(table, "Purchase Cost", "$" + request.getPurchaseCost().toString());
        }

        return table;
    }

    private Table createTimelineTable(InfraRequest request) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 40, 30}));
        table.setWidth(UnitValue.createPercentValue(100));
        addHeaderRow(table, "Stage", "Approved By", "Timestamp");

        addRow(table, "Line Manager",
                request.getLmApprovedBy() != null ? request.getLmApprovedBy().toString() : "Pending",
                request.getLmApprovedAt() != null ? formatDateTime(request.getLmApprovedAt()) : "-");

        addRow(table, "Infrastructure",
                request.getInfraReviewedBy() != null ? request.getInfraReviewedBy().toString() : "Pending",
                request.getInfraReviewedAt() != null ? formatDateTime(request.getInfraReviewedAt()) : "-");

        addRow(table, "Finance",
                request.getFinanceApprovedBy() != null ? request.getFinanceApprovedBy().toString() : "Pending",
                request.getFinanceApprovedAt() != null ? formatDateTime(request.getFinanceApprovedAt()) : "-");

        return table;
    }

    private Table createSignatureTable(InfraRequest request) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
        table.setWidth(UnitValue.createPercentValue(100));
        addHeaderRow(table, "Role", "Signature");

        addRow(table, "Requester", "________________________");
        addRow(table, "Line Manager", "________________________");
        addRow(table, "Infrastructure", "________________________");
        addRow(table, "Finance", "________________________");

        return table;
    }

    private Table createTransferDetailsTable(Transfer transfer) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 70}));
        table.setWidth(UnitValue.createPercentValue(100));

        addRow(table, "Transfer ID", transfer.getTransferId().toString());
        addRow(table, "Asset Tag", transfer.getAssetTag());
        addRow(table, "Serial Number", transfer.getSerialNumber());
        addRow(table, "Transfer Date", transfer.getTransferDate() != null ?
                transfer.getTransferDate().toString() : "-");

        // Use ID fields directly instead of entity relationships
        addRow(table, "From Department ID", transfer.getOldDepartmentId() != null ?
                transfer.getOldDepartmentId().toString() : "-");
        addRow(table, "To Department ID", transfer.getNewDepartmentId() != null ?
                transfer.getNewDepartmentId().toString() : "-");

        return table;
    }

    private Table createHistoryTable(List<Transfer> transfers) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{20, 20, 20, 40}));
        table.setWidth(UnitValue.createPercentValue(100));
        addHeaderRow(table, "Transfer ID", "Date", "From Dept ID", "To Dept ID");

        for (Transfer t : transfers) {
            String from = t.getOldDepartmentId() != null ? t.getOldDepartmentId().toString() : "-";
            String to = t.getNewDepartmentId() != null ? t.getNewDepartmentId().toString() : "-";
            addRow(table, t.getTransferId().toString(),
                    t.getTransferDate() != null ? t.getTransferDate().toString() : "-",
                    from, to);
        }

        return table;
    }

    private Table createTransferSignatureTable(Transfer transfer) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
        table.setWidth(UnitValue.createPercentValue(100));
        addHeaderRow(table, "Role", "Signature");

        addRow(table, "Old Department Handover",
                transfer.getOldHandoverBySignedAt() != null ? "✓ Signed" : "________________________");
        addRow(table, "Old Department Receiver",
                transfer.getOldReceivedBySignedAt() != null ? "✓ Signed" : "________________________");
        addRow(table, "New Department Handover",
                transfer.getNewHandoverBySignedAt() != null ? "✓ Signed" : "________________________");
        addRow(table, "New Department Receiver",
                transfer.getNewReceivedBySignedAt() != null ? "✓ Signed" : "________________________");
        addRow(table, "Configured By",
                transfer.getConfiguredBySignedAt() != null ? "✓ Signed" : "________________________");
        addRow(table, "Infrastructure Representative",
                transfer.getInfraRepSignedAt() != null ? "✓ Signed" : "________________________");
        addRow(table, "Finance Representative",
                transfer.getFinanceRepSignedAt() != null ? "✓ Signed" : "________________________");

        return table;
    }

    // ============================================
    // Helper Methods
    // ============================================

    private void addHeader(Document document, String title) {
        Paragraph header = new Paragraph(title)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(18)
                .setBold()
                .setFontColor(PRIMARY_COLOR);
        document.add(header);
        document.add(new Paragraph(" "));
    }

    private void addSubHeader(Document document, String subtitle) {
        Paragraph subHeader = new Paragraph(subtitle)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(14)
                .setFontColor(SECONDARY_COLOR);
        document.add(subHeader);
        document.add(new Paragraph(" "));
    }

    private void addSectionTitle(Document document, String title) {
        Paragraph section = new Paragraph(title)
                .setFontSize(14)
                .setBold()
                .setFontColor(PRIMARY_COLOR);
        document.add(section);
        document.add(new Paragraph(" "));
    }

    private void addFooter(Document document) {
        document.add(new Paragraph(" "));
        Paragraph footer = new Paragraph("Generated by AssetIQ-Pro on " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(10)
                .setFontColor(SECONDARY_COLOR);
        document.add(footer);
    }

    private void addRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label)).setBold());
        table.addCell(new Cell().add(new Paragraph(value)));
    }

    private void addHeaderRow(Table table, String... headers) {
        for (String header : headers) {
            table.addHeaderCell(new Cell().add(new Paragraph(header))
                    .setBold()
                    .setBackgroundColor(LIGHT_GRAY));
        }
    }

    private void addRow(Table table, String... values) {
        for (String value : values) {
            table.addCell(new Cell().add(new Paragraph(value)));
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return "-";
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}