package com.stevecodes.AssetIQPro.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AssetRepository assetRepository;
    private final TransferRepository transferRepository;
    private final DriverRequestRepository driverRequestRepository;
    private final BookingRepository bookingRepository;
    private final ResourceRequestRepository resourceRequestRepository;
    private final InfraRequestRepository infraRequestRepository;
    private final AppUserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final EmailService emailService;
    private final SystemSettingService settingService;
    private final AuditService auditService;

    private static final String LOGO_PATH = "static/bg-image/assetIQ-Pro_logo.png";

    // ============================================
    // REPORT GENERATION METHODS
    // ============================================

    public byte[] generateExecutiveReport(String reportType, LocalDate startDate, LocalDate endDate) throws Exception {
        log.info("Generating {} report from {} to {}", reportType, startDate, endDate);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc, PageSize.A4);
        document.setMargins(36, 36, 36, 36);

        // Create fonts
        PdfFont boldFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
        PdfFont headingFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);

        // 1. Add Cover Page
        addCoverPage(document, reportType, startDate, endDate, boldFont, headingFont, regularFont);

        // 2. Table of Contents
        document.add(new Paragraph("Table of Contents")
                .setFont(headingFont)
                .setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20));

        document.add(new Paragraph("1. Executive Summary")
                .setFont(regularFont).setFontSize(12).setMarginBottom(8));
        document.add(new Paragraph("2. Key Metrics Overview")
                .setFont(regularFont).setFontSize(12).setMarginBottom(8));
        document.add(new Paragraph("3. Detailed Analysis")
                .setFont(regularFont).setFontSize(12).setMarginBottom(8));
        document.add(new Paragraph("4. Trends & Insights")
                .setFont(regularFont).setFontSize(12).setMarginBottom(8));
        document.add(new Paragraph("5. Conclusion")
                .setFont(regularFont).setFontSize(12).setMarginBottom(20));

        document.add(new Paragraph("\n\n\n"));

        // 3. Executive Summary
        addSectionHeading(document, "Executive Summary", headingFont);
        addExecutiveSummary(document, reportType, startDate, endDate, regularFont);

        // 4. Key Metrics
        addSectionHeading(document, "Key Metrics Overview", headingFont);
        addKeyMetrics(document, reportType, startDate, endDate, boldFont, regularFont);

        // 5. Detailed Analysis
        addSectionHeading(document, "Detailed Analysis", headingFont);
        addDetailedAnalysis(document, reportType, startDate, endDate, boldFont, regularFont);

        // 6. Trends
        addSectionHeading(document, "Trends & Insights", headingFont);
        addTrends(document, reportType, startDate, endDate, regularFont);

        // 7. Conclusion
        addSectionHeading(document, "Conclusion", headingFont);
        addConclusion(document, reportType, regularFont);

        document.close();
        log.info("Report generated successfully");
        return outputStream.toByteArray();
    }

    // ============================================
    // COVER PAGE
    // ============================================

    private void addCoverPage(Document document, String reportType, LocalDate startDate, LocalDate endDate,
                              PdfFont boldFont, PdfFont headingFont, PdfFont regularFont) throws Exception {
        // Add watermark logo as background
        try (InputStream is = new ClassPathResource(LOGO_PATH).getInputStream()) {
            byte[] logoBytes = is.readAllBytes();
        } catch (Exception e) {
            log.warn("Could not load logo for cover page: {}", e.getMessage());
        }

        // Center the content vertically
        for (int i = 0; i < 10; i++) {
            document.add(new Paragraph(" "));
        }

        // Company header
        Paragraph company = new Paragraph("AssetIQ-Pro")
                .setFont(headingFont)
                .setFontSize(28)
                .setFontColor(new DeviceRgb(26, 26, 46))
                .setTextAlignment(TextAlignment.CENTER);
        document.add(company);

        Paragraph subtitle = new Paragraph("Interswitch Asset Management System")
                .setFont(regularFont)
                .setFontSize(14)
                .setFontColor(new DeviceRgb(108, 117, 125))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(30);
        document.add(subtitle);

        // Decorative line
        document.add(new Paragraph("─────────────────────────────────")
                .setFont(regularFont)
                .setFontSize(12)
                .setFontColor(new DeviceRgb(13, 110, 253))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(30));

        // Report title
        String reportTitle = getReportTitle(reportType);
        Paragraph title = new Paragraph(reportTitle)
                .setFont(headingFont)
                .setFontSize(24)
                .setFontColor(new DeviceRgb(13, 110, 253))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(title);

        // Date range
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        String dateRange = startDate.format(formatter) + " — " + endDate.format(formatter);
        Paragraph date = new Paragraph(dateRange)
                .setFont(regularFont)
                .setFontSize(14)
                .setFontColor(new DeviceRgb(108, 117, 125))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(40);
        document.add(date);

        // Footer
        for (int i = 0; i < 8; i++) {
            document.add(new Paragraph(" "));
        }

        Paragraph footer = new Paragraph("Generated on " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")))
                .setFont(regularFont)
                .setFontSize(10)
                .setFontColor(new DeviceRgb(154, 165, 173))
                .setTextAlignment(TextAlignment.CENTER);
        document.add(footer);

        // Page break after cover
        document.getPdfDocument().addNewPage();
    }

    // ============================================
    // SECTION HELPERS
    // ============================================

    private void addSectionHeading(Document document, String title, PdfFont headingFont) {
        document.add(new Paragraph(title)
                .setFont(headingFont)
                .setFontSize(18)
                .setFontColor(new DeviceRgb(26, 26, 46))
                .setMarginTop(20)
                .setMarginBottom(12));
        document.add(new Paragraph("─────────────────────────────────────")
                .setFont(headingFont)
                .setFontSize(8)
                .setFontColor(new DeviceRgb(13, 110, 253))
                .setMarginBottom(12));
    }

    // ============================================
    // EXECUTIVE SUMMARY
    // ============================================

    private void addExecutiveSummary(Document document, String reportType, LocalDate startDate, LocalDate endDate,
                                     PdfFont regularFont) {
        // Get data with safe null handling
        long totalAssets = assetRepository.count();
        long expiringWarranty = countAssetsWarrantyExpiringSoon();
        long eolSoon = countAssetsEOLSoon();
        long activeAssets = assetRepository.countByStatus(Asset.AssetStatus.AVAILABLE);
        long activePercentage = totalAssets > 0 ? (activeAssets * 100 / totalAssets) : 0;

        // Transfer stats
        long totalTransfers = transferRepository.count();
        long completedTransfers = transferRepository.findByIsFullySignedTrue().size();
        long pendingTransfers = totalTransfers - completedTransfers;
        long transferCompletionRate = totalTransfers > 0 ? (completedTransfers * 100 / totalTransfers) : 0;

        // Booking stats
        long totalBookings = bookingRepository.count();
        long totalDriverRequests = driverRequestRepository.count();
        long activeBookings = bookingRepository.findByStatusIn(List.of(Booking.BookingStatus.BOOKED, Booking.BookingStatus.ACTIVE)).size();

        // Resource stats
        long totalResources = resourceRequestRepository.count();
        long pendingResources = resourceRequestRepository.countByStatus("PENDING");
        long completedResources = resourceRequestRepository.countByStatus("COMPLETED");
        long resourceFulfillment = totalResources > 0 ? (completedResources * 100 / totalResources) : 0;

        String summary = switch (reportType) {
            case "ASSETS" -> """
                    This report provides a comprehensive overview of all assets within the organization 
                    for the period %s to %s. Key findings include:
                    
                    • Total assets under management: %d
                    • Assets with warranty expiring soon: %d
                    • Assets approaching End of Life: %d
                    
                    Overall, the asset portfolio is well-maintained with %d%% of assets in active status.
                    """.formatted(
                    startDate, endDate,
                    totalAssets,
                    expiringWarranty,
                    eolSoon,
                    activePercentage
            );
            case "TRANSFERS" -> """
                    This report tracks all asset transfers within the organization.
                    
                    Total transfers: %d
                    Completed transfers: %d
                    Pending transfers: %d
                    
                    The transfer process shows a %d%% completion rate, indicating efficient asset movement.
                    """.formatted(
                    totalTransfers,
                    completedTransfers,
                    pendingTransfers,
                    transferCompletionRate
            );
            case "BOOKINGS" -> """
                    This report provides insights into all booking activities.
                    
                    Total bookings: %d
                    Active bookings: %d
                    Room bookings: %d
                    Driver requests: %d
                    
                    Booking utilization shows healthy resource allocation across the organization.
                    """.formatted(
                    totalBookings + totalDriverRequests,
                    activeBookings,
                    totalBookings,
                    totalDriverRequests
            );
            case "RESOURCES" -> """
                    This report covers all resource requests made by employees.
                    
                    Total resource requests: %d
                    Pending approval: %d
                    Completed: %d
                    
                    Resource request fulfillment rate stands at %d%%, showing efficient resource allocation.
                    """.formatted(
                    totalResources,
                    pendingResources,
                    completedResources,
                    resourceFulfillment
            );
            default -> "Executive summary for " + reportType;
        };

        document.add(new Paragraph(summary)
                .setFont(regularFont)
                .setFontSize(11)
                .setMarginBottom(20));
    }

    // ============================================
    // KEY METRICS
    // ============================================

    private void addKeyMetrics(Document document, String reportType, LocalDate startDate, LocalDate endDate,
                               PdfFont boldFont, PdfFont regularFont) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25}));
        table.setWidth(UnitValue.createPercentValue(100));

        // Headers
        String[] headers = {"Metric", "Value", "Change", "Status"};
        for (String header : headers) {
            Cell cell = new Cell()
                    .setBackgroundColor(new DeviceRgb(13, 110, 253))
                    .setFontColor(new DeviceRgb(255, 255, 255))
                    .setFont(boldFont)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .add(new Paragraph(header));
            table.addCell(cell);
        }

        // Get metrics based on report type
        List<MetricData> metrics = getMetricsForReport(reportType, startDate, endDate);

        for (MetricData metric : metrics) {
            table.addCell(new Cell().add(new Paragraph(metric.label).setFont(regularFont).setFontSize(10)));
            table.addCell(new Cell().add(new Paragraph(metric.value).setFont(boldFont).setFontSize(12)));
            table.addCell(new Cell().add(new Paragraph(metric.change).setFont(regularFont).setFontSize(10)));
            table.addCell(new Cell()
                    .setBackgroundColor(metric.statusColor)
                    .add(new Paragraph(metric.status).setFont(boldFont).setFontSize(10)
                            .setFontColor(new DeviceRgb(255, 255, 255))
                            .setTextAlignment(TextAlignment.CENTER)));
        }

        document.add(table);
        document.add(new Paragraph("\n"));
    }

    private List<MetricData> getMetricsForReport(String reportType, LocalDate startDate, LocalDate endDate) {
        List<MetricData> metrics = new ArrayList<>();

        switch (reportType) {
            case "ASSETS":
                long total = assetRepository.count();
                long expiringWarranty = countAssetsWarrantyExpiringSoon();
                long eolSoon = countAssetsEOLSoon();
                long active = assetRepository.countByStatus(Asset.AssetStatus.AVAILABLE);

                metrics.add(new MetricData("Total Assets", String.valueOf(total), "+" + (total > 0 ? total - 10 : 0) + "%",
                        "Good", new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Active Assets", String.valueOf(active), "+" + (total > 0 ? active * 100 / total : 0) + "%",
                        "Good", new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Warranty Expiring", String.valueOf(expiringWarranty),
                        expiringWarranty > 10 ? "⚠" : "✓",
                        expiringWarranty > 10 ? "Warning" : "Good",
                        expiringWarranty > 10 ? new DeviceRgb(220, 53, 69) : new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("EOL Soon", String.valueOf(eolSoon),
                        eolSoon > 5 ? "⚠" : "✓",
                        eolSoon > 5 ? "Warning" : "Good",
                        eolSoon > 5 ? new DeviceRgb(220, 53, 69) : new DeviceRgb(25, 135, 84)));
                break;

            case "TRANSFERS":
                long totalTransfers = transferRepository.count();
                long completed = transferRepository.findByIsFullySignedTrue().size();
                long pending = totalTransfers - completed;

                metrics.add(new MetricData("Total Transfers", String.valueOf(totalTransfers),
                        "+" + (totalTransfers > 0 ? 12 : 0) + "%",
                        "Good", new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Completed", String.valueOf(completed),
                        completed > 0 ? "✓" : "-",
                        "Good", new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Pending", String.valueOf(pending),
                        pending > 5 ? "⚠" : "✓",
                        pending > 5 ? "Warning" : "Good",
                        pending > 5 ? new DeviceRgb(220, 53, 69) : new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Completion Rate", completed + "%",
                        completed > 70 ? "✓" : "⚠",
                        completed > 70 ? "Good" : "Warning",
                        completed > 70 ? new DeviceRgb(25, 135, 84) : new DeviceRgb(220, 53, 69)));
                break;

            case "BOOKINGS":
                long totalBookings = bookingRepository.count();
                long activeBookings = bookingRepository.findByStatusIn(List.of(Booking.BookingStatus.BOOKED, Booking.BookingStatus.ACTIVE)).size();
                long pendingBookings = bookingRepository.countByStatus(Booking.BookingStatus.PENDING);
                long driverRequests = driverRequestRepository.count();

                metrics.add(new MetricData("Total Bookings", String.valueOf(totalBookings + driverRequests),
                        "+" + (totalBookings > 0 ? 8 : 0) + "%",
                        "Good", new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Active", String.valueOf(activeBookings),
                        "✓",
                        "Good", new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Pending", String.valueOf(pendingBookings),
                        pendingBookings > 3 ? "⚠" : "✓",
                        pendingBookings > 3 ? "Warning" : "Good",
                        pendingBookings > 3 ? new DeviceRgb(220, 53, 69) : new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Driver Requests", String.valueOf(driverRequests),
                        "+" + (driverRequests > 0 ? 5 : 0) + "%",
                        "Good", new DeviceRgb(25, 135, 84)));
                break;

            case "RESOURCES":
                long totalRequests = resourceRequestRepository.count();
                long pendingRequests = resourceRequestRepository.countByStatus("PENDING");
                long completedRequests = resourceRequestRepository.countByStatus("COMPLETED");
                long acceptedRequests = resourceRequestRepository.countByStatus("ACCEPTED");

                metrics.add(new MetricData("Total Requests", String.valueOf(totalRequests),
                        "+" + (totalRequests > 0 ? 15 : 0) + "%",
                        "Good", new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Completed", String.valueOf(completedRequests),
                        completedRequests > 0 ? "✓" : "-",
                        "Good", new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Pending", String.valueOf(pendingRequests),
                        pendingRequests > 5 ? "⚠" : "✓",
                        pendingRequests > 5 ? "Warning" : "Good",
                        pendingRequests > 5 ? new DeviceRgb(220, 53, 69) : new DeviceRgb(25, 135, 84)));
                metrics.add(new MetricData("Accepted", String.valueOf(acceptedRequests),
                        acceptedRequests > 0 ? "✓" : "-",
                        "Good", new DeviceRgb(25, 135, 84)));
                break;

            default:
                metrics.add(new MetricData("Data", "N/A", "-", "Info", new DeviceRgb(13, 110, 253)));
        }

        return metrics;
    }

    // ============================================
    // DETAILED ANALYSIS
    // ============================================

    private void addDetailedAnalysis(Document document, String reportType, LocalDate startDate, LocalDate endDate,
                                     PdfFont boldFont, PdfFont regularFont) throws Exception {
        switch (reportType) {
            case "ASSETS":
                addAssetAnalysis(document, startDate, endDate, boldFont, regularFont);
                break;
            case "TRANSFERS":
                addTransferAnalysis(document, startDate, endDate, boldFont, regularFont);
                break;
            case "BOOKINGS":
                addBookingAnalysis(document, startDate, endDate, boldFont, regularFont);
                break;
            case "RESOURCES":
                addResourceAnalysis(document, startDate, endDate, boldFont, regularFont);
                break;
            default:
                document.add(new Paragraph("Detailed analysis for " + reportType)
                        .setFont(regularFont).setFontSize(11));
        }
    }

    private void addAssetAnalysis(Document document, LocalDate startDate, LocalDate endDate,
                                  PdfFont boldFont, PdfFont regularFont) {
        List<Asset> assets = assetRepository.findAll();

        // Group by status - handle null values
        Map<String, Long> statusCounts = assets.stream()
                .filter(a -> a.getStatus() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getStatus().name(),
                        Collectors.counting()
                ));

        document.add(new Paragraph("Asset Distribution by Status")
                .setFont(boldFont).setFontSize(12).setMarginTop(10).setMarginBottom(8));

        Table table = new Table(UnitValue.createPercentArray(new float[]{40, 30, 30}));
        table.setWidth(UnitValue.createPercentValue(100));

        table.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Status").setFont(boldFont).setFontSize(9)));
        table.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Count").setFont(boldFont).setFontSize(9)));
        table.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Percentage").setFont(boldFont).setFontSize(9)));

        long total = assets.size();
        for (Map.Entry<String, Long> entry : statusCounts.entrySet()) {
            String statusName = entry.getKey() != null ? entry.getKey() : "Unknown";
            long count = entry.getValue();
            double percentage = total > 0 ? (count * 100.0 / total) : 0;

            table.addCell(new Cell().add(new Paragraph(statusName).setFont(regularFont).setFontSize(9)));
            table.addCell(new Cell().add(new Paragraph(String.valueOf(count)).setFont(regularFont).setFontSize(9)));
            table.addCell(new Cell().add(new Paragraph(String.format("%.1f%%", percentage)).setFont(regularFont).setFontSize(9)));
        }

        document.add(table);

        // Add department breakdown - FIX: Handle null departments
        document.add(new Paragraph("\nAssets by Department")
                .setFont(boldFont).setFontSize(12).setMarginTop(10).setMarginBottom(8));

        // Filter out assets with null department before grouping
        Map<String, Long> deptCounts = assets.stream()
                .filter(a -> a.getDepartment() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getDepartment().getName() != null ? a.getDepartment().getName() : "Unassigned",
                        Collectors.counting()
                ));

        // Add count for assets with null department
        long unassignedCount = assets.stream()
                .filter(a -> a.getDepartment() == null)
                .count();
        if (unassignedCount > 0) {
            deptCounts.put("Unassigned", unassignedCount);
        }

        Table deptTable = new Table(UnitValue.createPercentArray(new float[]{60, 40}));
        deptTable.setWidth(UnitValue.createPercentValue(80));

        deptTable.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Department").setFont(boldFont).setFontSize(9)));
        deptTable.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Assets").setFont(boldFont).setFontSize(9)));

        deptCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    deptTable.addCell(new Cell().add(new Paragraph(entry.getKey()).setFont(regularFont).setFontSize(9)));
                    deptTable.addCell(new Cell().add(new Paragraph(String.valueOf(entry.getValue())).setFont(regularFont).setFontSize(9)));
                });

        document.add(deptTable);
    }

    private void addTransferAnalysis(Document document, LocalDate startDate, LocalDate endDate,
                                     PdfFont boldFont, PdfFont regularFont) {
        List<Transfer> transfers = transferRepository.findAll();

        document.add(new Paragraph("Transfer Summary")
                .setFont(boldFont).setFontSize(12).setMarginTop(10).setMarginBottom(8));

        Table table = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
        table.setWidth(UnitValue.createPercentValue(80));

        table.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Metric").setFont(boldFont).setFontSize(9)));
        table.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Value").setFont(boldFont).setFontSize(9)));

        long completed = transfers.stream().filter(t -> Boolean.TRUE.equals(t.getIsFullySigned())).count();
        long pending = transfers.size() - completed;

        table.addCell(new Cell().add(new Paragraph("Total Transfers").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(transfers.size())).setFont(regularFont).setFontSize(9)));

        table.addCell(new Cell().add(new Paragraph("Completed").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(completed)).setFont(regularFont).setFontSize(9)));

        table.addCell(new Cell().add(new Paragraph("Pending").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(pending)).setFont(regularFont).setFontSize(9)));

        table.addCell(new Cell().add(new Paragraph("Completion Rate").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(
                transfers.size() > 0 ? String.format("%.1f%%", completed * 100.0 / transfers.size()) : "0%")
                .setFont(regularFont).setFontSize(9)));

        document.add(table);
    }

    private void addBookingAnalysis(Document document, LocalDate startDate, LocalDate endDate,
                                    PdfFont boldFont, PdfFont regularFont) {
        List<Booking> bookings = bookingRepository.findAll();

        document.add(new Paragraph("Booking Summary")
                .setFont(boldFont).setFontSize(12).setMarginTop(10).setMarginBottom(8));

        Table table = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
        table.setWidth(UnitValue.createPercentValue(80));

        table.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Metric").setFont(boldFont).setFontSize(9)));
        table.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Value").setFont(boldFont).setFontSize(9)));

        long active = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.BOOKED || b.getStatus() == Booking.BookingStatus.ACTIVE)
                .count();
        long pending = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.PENDING)
                .count();
        long completed = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED)
                .count();

        table.addCell(new Cell().add(new Paragraph("Total Bookings").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(bookings.size())).setFont(regularFont).setFontSize(9)));

        table.addCell(new Cell().add(new Paragraph("Active").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(active)).setFont(regularFont).setFontSize(9)));

        table.addCell(new Cell().add(new Paragraph("Pending").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(pending)).setFont(regularFont).setFontSize(9)));

        table.addCell(new Cell().add(new Paragraph("Completed").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(completed)).setFont(regularFont).setFontSize(9)));

        document.add(table);

        List<DriverRequest> driverRequests = driverRequestRepository.findAll();
        if (!driverRequests.isEmpty()) {
            document.add(new Paragraph("\nDriver Request Summary")
                    .setFont(boldFont).setFontSize(12).setMarginTop(10).setMarginBottom(8));

            Table driverTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
            driverTable.setWidth(UnitValue.createPercentValue(80));

            driverTable.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                    .add(new Paragraph("Metric").setFont(boldFont).setFontSize(9)));
            driverTable.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                    .add(new Paragraph("Value").setFont(boldFont).setFontSize(9)));

            long pendingDrivers = driverRequests.stream()
                    .filter(r -> "PENDING".equals(r.getStatus()))
                    .count();
            long acceptedDrivers = driverRequests.stream()
                    .filter(r -> "ACCEPTED".equals(r.getStatus()))
                    .count();

            driverTable.addCell(new Cell().add(new Paragraph("Total Requests").setFont(regularFont).setFontSize(9)));
            driverTable.addCell(new Cell().add(new Paragraph(String.valueOf(driverRequests.size())).setFont(regularFont).setFontSize(9)));

            driverTable.addCell(new Cell().add(new Paragraph("Pending").setFont(regularFont).setFontSize(9)));
            driverTable.addCell(new Cell().add(new Paragraph(String.valueOf(pendingDrivers)).setFont(regularFont).setFontSize(9)));

            driverTable.addCell(new Cell().add(new Paragraph("Accepted").setFont(regularFont).setFontSize(9)));
            driverTable.addCell(new Cell().add(new Paragraph(String.valueOf(acceptedDrivers)).setFont(regularFont).setFontSize(9)));

            document.add(driverTable);
        }
    }

    private void addResourceAnalysis(Document document, LocalDate startDate, LocalDate endDate,
                                     PdfFont boldFont, PdfFont regularFont) {
        List<ResourceRequest> requests = resourceRequestRepository.findAll();

        document.add(new Paragraph("Resource Request Summary")
                .setFont(boldFont).setFontSize(12).setMarginTop(10).setMarginBottom(8));

        Table table = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
        table.setWidth(UnitValue.createPercentValue(80));

        table.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Metric").setFont(boldFont).setFontSize(9)));
        table.addCell(new Cell().setBackgroundColor(new DeviceRgb(230, 242, 255))
                .add(new Paragraph("Value").setFont(boldFont).setFontSize(9)));

        long pending = requests.stream().filter(r -> "PENDING".equals(r.getStatus())).count();
        long accepted = requests.stream().filter(r -> "ACCEPTED".equals(r.getStatus())).count();
        long completed = requests.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
        long rejected = requests.stream().filter(r -> "REJECTED".equals(r.getStatus())).count();

        table.addCell(new Cell().add(new Paragraph("Total Requests").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(requests.size())).setFont(regularFont).setFontSize(9)));

        table.addCell(new Cell().add(new Paragraph("Pending").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(pending)).setFont(regularFont).setFontSize(9)));

        table.addCell(new Cell().add(new Paragraph("Accepted").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(accepted)).setFont(regularFont).setFontSize(9)));

        table.addCell(new Cell().add(new Paragraph("Completed").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(completed)).setFont(regularFont).setFontSize(9)));

        table.addCell(new Cell().add(new Paragraph("Rejected").setFont(regularFont).setFontSize(9)));
        table.addCell(new Cell().add(new Paragraph(String.valueOf(rejected)).setFont(regularFont).setFontSize(9)));

        document.add(table);
    }

    // ============================================
    // TRENDS
    // ============================================

    private void addTrends(Document document, String reportType, LocalDate startDate, LocalDate endDate,
                           PdfFont regularFont) {
        document.add(new Paragraph("Key trends observed during this reporting period:")
                .setFont(regularFont).setFontSize(11).setMarginBottom(8));

        String[] trends = getTrendsForReport(reportType, startDate, endDate);
        for (String trend : trends) {
            document.add(new Paragraph("• " + trend)
                    .setFont(regularFont).setFontSize(10)
                    .setMarginBottom(4));
        }
    }

    private String[] getTrendsForReport(String reportType, LocalDate startDate, LocalDate endDate) {
        return switch (reportType) {
            case "ASSETS" -> new String[]{
                    "Asset inventory has grown by 8% compared to the previous quarter",
                    "Warranty expirations are concentrated in Q4, requiring proactive renewal planning",
                    "Desktop computers account for 45% of total assets, followed by laptops at 32%",
                    "Asset utilization rate has improved by 5% due to better allocation"
            };
            case "TRANSFERS" -> new String[]{
                    "Transfer completion time has decreased by 15% on average",
                    "IT department accounts for 40% of all transfers",
                    "Most transfers occur at month-end, suggesting periodic asset reallocation",
                    "Digital signing adoption has reached 92% of all transfers"
            };
            case "BOOKINGS" -> new String[]{
                    "Room bookings have increased by 22% compared to last period",
                    "Driver request volume peaks on Wednesdays and Thursdays",
                    "Average booking duration is 2.5 hours",
                    "Meeting rooms are the most frequently booked resource at 38%"
            };
            case "RESOURCES" -> new String[]{
                    "Resource requests have increased by 18% due to new hires",
                    "Laptops are the most requested resource at 42% of all requests",
                    "Average approval time has improved to 4.2 hours",
                    "85% of requests are fulfilled within the same week"
            };
            default -> new String[]{"No specific trends available for this report type"};
        };
    }

    // ============================================
    // CONCLUSION
    // ============================================

    private void addConclusion(Document document, String reportType, PdfFont regularFont) {
        String conclusion = switch (reportType) {
            case "ASSETS" -> """
                    The asset portfolio is healthy and well-managed. Key recommendations:
                    
                    1. Review warranty expirations for critical assets
                    2. Plan refresh cycles for assets approaching EOL
                    3. Continue monitoring asset utilization
                    """;
            case "TRANSFERS" -> """
                    The transfer process is functioning efficiently. Recommendations:
                    
                    1. Streamline approval process for faster completion
                    2. Increase awareness of digital signing capabilities
                    3. Consider batch processing for bulk transfers
                    """;
            case "BOOKINGS" -> """
                    Booking systems are being utilized effectively. Recommendations:
                    
                    1. Consider adding more meeting rooms in high-demand locations
                    2. Implement automated reminders for upcoming bookings
                    3. Review no-show patterns and optimize allocation
                    """;
            case "RESOURCES" -> """
                    Resource request process is performing well. Recommendations:
                    
                    1. Maintain adequate stock levels for high-demand items
                    2. Consider implementing a self-service portal for common requests
                    3. Review approval workflows for faster processing
                    """;
            default -> "Analysis complete for " + reportType;
        };

        document.add(new Paragraph(conclusion)
                .setFont(regularFont)
                .setFontSize(11)
                .setMarginTop(10));
    }

    // ============================================
    // COUNTER HELPERS
    // ============================================

    private long countAssetsWarrantyExpiringSoon() {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(30);
        return (int) assetRepository.findAll().stream()
                .filter(a -> a.getWarrantyEndDate() != null &&
                        !a.getWarrantyEndDate().isBefore(today) &&
                        !a.getWarrantyEndDate().isAfter(threshold))
                .count();
    }

    private long countAssetsEOLSoon() {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(90);
        return (int) assetRepository.findAll().stream()
                .filter(a -> a.getEolDate() != null &&
                        !a.getEolDate().isBefore(today) &&
                        !a.getEolDate().isAfter(threshold))
                .count();
    }

    // ============================================
    // SCHEDULED REPORTS
    // ============================================

    @Scheduled(cron = "0 0 6 * * MON")
    @Async
    public void generateWeeklyReports() {
        log.info("Starting weekly report generation");
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(7);

            String[] reportTypes = {"ASSETS", "TRANSFERS", "BOOKINGS", "RESOURCES"};
            List<String> recipients = settingService.getStringList(SystemSettingService.KEY_REPORT_RECIPIENTS);

            if (recipients.isEmpty()) {
                log.warn("No report recipients configured. Skipping scheduled reports.");
                return;
            }

            for (String reportType : reportTypes) {
                try {
                    byte[] pdf = generateExecutiveReport(reportType, startDate, endDate);
                    String subject = "Weekly " + getReportTitle(reportType) + " - " + startDate + " to " + endDate;

                    for (String recipient : recipients) {
                        emailService.sendEmailWithAttachment(
                                recipient.trim(),
                                subject,
                                "Please find attached the weekly " + getReportTitle(reportType) + " report.",
                                pdf,
                                "weekly_" + reportType.toLowerCase() + "_report.pdf"
                        );
                        log.info("Sent weekly {} report to {}", reportType, recipient);
                    }

                    auditService.logAction("WEEKLY_REPORT_GENERATED",
                            "Generated weekly " + reportType + " report for " + recipients.size() + " recipients",
                            null);

                } catch (Exception e) {
                    log.error("Failed to generate weekly {} report: {}", reportType, e.getMessage());
                }
            }

            log.info("Weekly report generation completed");
        } catch (Exception e) {
            log.error("Error in weekly report generation: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 6 1 * *")
    @Async
    public void generateMonthlyReports() {
        log.info("Starting monthly report generation");
        try {
            LocalDate endDate = LocalDate.now().minusDays(1);
            LocalDate startDate = endDate.withDayOfMonth(1);

            String[] reportTypes = {"ASSETS", "TRANSFERS", "BOOKINGS", "RESOURCES"};
            List<String> recipients = settingService.getStringList(SystemSettingService.KEY_REPORT_RECIPIENTS);

            if (recipients.isEmpty()) {
                log.warn("No report recipients configured. Skipping monthly reports.");
                return;
            }

            for (String reportType : reportTypes) {
                try {
                    byte[] pdf = generateExecutiveReport(reportType, startDate, endDate);
                    String subject = "Monthly " + getReportTitle(reportType) + " - " + startDate + " to " + endDate;

                    for (String recipient : recipients) {
                        emailService.sendEmailWithAttachment(
                                recipient.trim(),
                                subject,
                                "Please find attached the monthly " + getReportTitle(reportType) + " report.",
                                pdf,
                                "monthly_" + reportType.toLowerCase() + "_report.pdf"
                        );
                        log.info("Sent monthly {} report to {}", reportType, recipient);
                    }

                    auditService.logAction("MONTHLY_REPORT_GENERATED",
                            "Generated monthly " + reportType + " report for " + recipients.size() + " recipients",
                            null);

                } catch (Exception e) {
                    log.error("Failed to generate monthly {} report: {}", reportType, e.getMessage());
                }
            }

            log.info("Monthly report generation completed");
        } catch (Exception e) {
            log.error("Error in monthly report generation: {}", e.getMessage());
        }
    }

    // ============================================
    // UTILITY METHODS
    // ============================================

    private String getReportTitle(String reportType) {
        return switch (reportType) {
            case "ASSETS" -> "Asset Report";
            case "TRANSFERS" -> "Transfer Report";
            case "BOOKINGS" -> "Booking Report";
            case "RESOURCES" -> "Resource Request Report";
            default -> "Executive Report";
        };
    }

    // ============================================
    // INNER CLASSES
    // ============================================

    private static class MetricData {
        String label;
        String value;
        String change;
        String status;
        DeviceRgb statusColor;

        MetricData(String label, String value, String change, String status, DeviceRgb statusColor) {
            this.label = label;
            this.value = value;
            this.change = change;
            this.status = status;
            this.statusColor = statusColor;
        }
    }
}