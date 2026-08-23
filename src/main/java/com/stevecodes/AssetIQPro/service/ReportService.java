package com.stevecodes.AssetIQPro.service;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.stevecodes.AssetIQPro.entity.*;
import com.stevecodes.AssetIQPro.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AssetRepository assetRepository;
    private final TransferRepository transferRepository;
    private final BookingRepository bookingRepository;
    private final DriverRequestRepository driverRequestRepository;
    private final AppUserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;
    private final RoomRepository roomRepository;  // ADDED THIS

    // Colors matching the email design - using RGB values
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(79, 70, 229);
    private static final DeviceRgb PRIMARY_SOFT = new DeviceRgb(129, 140, 248);
    private static final DeviceRgb SUCCESS_COLOR = new DeviceRgb(22, 163, 74);
    private static final DeviceRgb WARNING_COLOR = new DeviceRgb(217, 119, 6);
    private static final DeviceRgb DANGER_COLOR = new DeviceRgb(220, 38, 38);
    private static final DeviceRgb HEADER_BG = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb TABLE_HEADER_BG = new DeviceRgb(241, 244, 249);
    private static final DeviceRgb TEXT_COLOR = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(226, 232, 240);

    private static final String FONT_PATH = "fonts/DejaVuSans.ttf";

    // Helper to convert hex to DeviceRgb
    private DeviceRgb hexToRgb(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new DeviceRgb(100, 116, 139); // Default muted
        }
        // Remove # if present
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            int r = Integer.parseInt(clean.substring(0, 2), 16);
            int g = Integer.parseInt(clean.substring(2, 4), 16);
            int b = Integer.parseInt(clean.substring(4, 6), 16);
            return new DeviceRgb(r, g, b);
        } catch (Exception e) {
            return new DeviceRgb(100, 116, 139);
        }
    }

    /**
     * Generate a comprehensive executive report with real data tables
     */
    public byte[] generateExecutiveReport(String reportType, LocalDate startDate, LocalDate endDate) {
        try {
            log.info("📊 Generating executive report: {} from {} to {}", reportType, startDate, endDate);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(40, 40, 40, 40);

            // Load fonts
            PdfFont regularFont = PdfFontFactory.createFont("Helvetica");
            PdfFont boldFont = PdfFontFactory.createFont("Helvetica-Bold");

            // Add cover page
            addCoverPage(document, reportType, startDate, endDate, boldFont, regularFont);

            // Add table of contents
            addTableOfContents(document, boldFont, regularFont);

            // Add executive summary
            addExecutiveSummary(document, reportType, startDate, endDate, boldFont, regularFont);

            // Add the main data based on report type
            switch (reportType.toUpperCase()) {
                case "ASSETS":
                    addAssetReport(document, startDate, endDate, boldFont, regularFont);
                    break;
                case "TRANSFERS":
                    addTransferReport(document, startDate, endDate, boldFont, regularFont);
                    break;
                case "BOOKINGS":
                    addBookingReport(document, startDate, endDate, boldFont, regularFont);
                    break;
                case "RESOURCES":
                    addResourceRequestReport(document, startDate, endDate, boldFont, regularFont);
                    break;
                default:
                    addAssetReport(document, startDate, endDate, boldFont, regularFont);
            }

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("❌ Error generating executive report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate executive report: " + e.getMessage(), e);
        }
    }

    // ============================================
    // COVER PAGE
    // ============================================

    private void addCoverPage(Document document, String reportType, LocalDate startDate, LocalDate endDate,
                              PdfFont boldFont, PdfFont regularFont) {
        // Company Logo / Header
        document.add(new Paragraph("\n\n\n\n\n\n\n\n\n\n\n\n"));

        // Report Title
        Paragraph title = new Paragraph("ASSETIQ-PRO")
                .setFont(boldFont)
                .setFontSize(28)
                .setFontColor(PRIMARY_COLOR)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);

        Paragraph subtitle = new Paragraph("Executive Report")
                .setFont(boldFont)
                .setFontSize(20)
                .setFontColor(TEXT_COLOR)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(subtitle);

        document.add(new Paragraph("\n"));

        // Report Type
        Paragraph type = new Paragraph(reportType.toUpperCase() + " REPORT")
                .setFont(boldFont)
                .setFontSize(16)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(type);

        document.add(new Paragraph("\n\n"));

        // Date Range
        String dateRange = startDate + " to " + endDate;
        Paragraph dates = new Paragraph("Reporting Period: " + dateRange)
                .setFont(regularFont)
                .setFontSize(12)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(dates);

        // Generated Date
        String generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm"));
        Paragraph generatedDate = new Paragraph("Generated: " + generated)
                .setFont(regularFont)
                .setFontSize(12)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(generatedDate);

        document.add(new Paragraph("\n\n\n\n\n\n\n\n\n\n\n\n"));

        // Footer
        Paragraph footer = new Paragraph("Confidential - For Internal Use Only")
                .setFont(regularFont)
                .setFontSize(10)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(footer);

        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("© " + LocalDateTime.now().getYear() + " AssetIQ-Pro. All rights reserved.")
                .setFont(regularFont)
                .setFontSize(10)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER));

        document.add(new Paragraph("\n\n\n\n"));
        // Add a separator line
        document.add(new Paragraph("─".repeat(80))
                .setFont(regularFont)
                .setFontSize(8)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER));

        // Start a new page
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph(" ").setFontSize(1));
        document.add(new Paragraph("\n\n"));
    }

    // ============================================
    // TABLE OF CONTENTS
    // ============================================

    private void addTableOfContents(Document document, PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("TABLE OF CONTENTS")
                .setFont(boldFont)
                .setFontSize(18)
                .setFontColor(TEXT_COLOR)
                .setTextAlignment(TextAlignment.LEFT));

        document.add(new Paragraph("\n"));

        String[] sections = {
                "1. Executive Summary",
                "2. Key Performance Indicators",
                "3. Detailed Data Tables",
                "4. Trends & Insights",
                "5. Recommendations",
                "6. Appendix"
        };

        for (String section : sections) {
            Paragraph tocItem = new Paragraph("   " + section)
                    .setFont(regularFont)
                    .setFontSize(12)
                    .setFontColor(TEXT_COLOR);
            document.add(tocItem);
        }

        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("─".repeat(80))
                .setFont(regularFont)
                .setFontSize(8)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\n\n"));
    }

    // ============================================
    // EXECUTIVE SUMMARY
    // ============================================

    private void addExecutiveSummary(Document document, String reportType, LocalDate startDate, LocalDate endDate,
                                     PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("EXECUTIVE SUMMARY")
                .setFont(boldFont)
                .setFontSize(18)
                .setFontColor(PRIMARY_COLOR)
                .setTextAlignment(TextAlignment.LEFT));

        document.add(new Paragraph("\n"));

        String summary = String.format(
                "This executive report provides a comprehensive analysis of %s for the period %s to %s. " +
                        "The report includes key metrics, performance indicators, and detailed data tables to support " +
                        "strategic decision-making and operational planning.",
                reportType.toLowerCase(),
                startDate,
                endDate
        );

        Paragraph summaryPara = new Paragraph(summary)
                .setFont(regularFont)
                .setFontSize(11)
                .setFontColor(TEXT_COLOR)
                .setTextAlignment(TextAlignment.JUSTIFIED);
        document.add(summaryPara);

        document.add(new Paragraph("\n"));

        // Key highlights based on report type
        switch (reportType.toUpperCase()) {
            case "ASSETS":
                addAssetHighlights(document, startDate, endDate, boldFont, regularFont);
                break;
            case "TRANSFERS":
                addTransferHighlights(document, startDate, endDate, boldFont, regularFont);
                break;
            case "BOOKINGS":
                addBookingHighlights(document, startDate, endDate, boldFont, regularFont);
                break;
            case "RESOURCES":
                addResourceHighlights(document, startDate, endDate, boldFont, regularFont);
                break;
        }

        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("─".repeat(80))
                .setFont(regularFont)
                .setFontSize(8)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\n\n"));
    }

    // ============================================
    // ASSET REPORT
    // ============================================

    private void addAssetReport(Document document, LocalDate startDate, LocalDate endDate,
                                PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("ASSETS REPORT")
                .setFont(boldFont)
                .setFontSize(16)
                .setFontColor(TEXT_COLOR)
                .setTextAlignment(TextAlignment.LEFT));

        document.add(new Paragraph("\n"));

        // Get data
        List<Asset> assets = assetRepository.findAll();
        LocalDate now = LocalDate.now();

        // Filter by date range
        if (startDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null && !a.getPurchaseDate().isBefore(startDate))
                    .collect(Collectors.toList());
        }
        if (endDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null && !a.getPurchaseDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Calculate statistics
        long totalAssets = assets.size();
        long activeAssets = assets.stream()
                .filter(a -> a.getStatus() == Asset.AssetStatus.AVAILABLE || a.getStatus() == Asset.AssetStatus.ASSIGNED)
                .count();
        long expiredWarranty = assets.stream()
                .filter(a -> a.getWarrantyEndDate() != null && a.getWarrantyEndDate().isBefore(now))
                .count();
        long eolAssets = assets.stream()
                .filter(a -> a.getEolDate() != null && a.getEolDate().isBefore(now))
                .count();

        // Add summary section
        document.add(new Paragraph("Key Metrics")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        // Create summary table
        float[] summaryWidths = {200, 200};
        Table summaryTable = new Table(summaryWidths);
        summaryTable.setHorizontalAlignment(HorizontalAlignment.CENTER);
        summaryTable.setWidth(UnitValue.createPercentValue(80));

        // Add rows
        addSummaryRow(summaryTable, "Total Assets", String.valueOf(totalAssets), boldFont, regularFont);
        addSummaryRow(summaryTable, "Active Assets", String.valueOf(activeAssets), boldFont, regularFont);
        addSummaryRow(summaryTable, "Expired Warranty", String.valueOf(expiredWarranty), boldFont, regularFont);
        addSummaryRow(summaryTable, "End of Life Assets", String.valueOf(eolAssets), boldFont, regularFont);

        document.add(summaryTable);
        document.add(new Paragraph("\n\n"));

        // Asset Details Table
        document.add(new Paragraph("Asset Details")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        // Create asset data table
        float[] assetWidths = {30, 100, 80, 100, 80, 80, 80};
        Table assetTable = new Table(assetWidths);
        assetTable.setWidth(UnitValue.createPercentValue(100));

        // Add header
        addTableHeader(assetTable, new String[]{"#", "Name", "Tag", "Category", "Purchase Date", "Status", "Warranty"}, boldFont);

        // Add data rows (limit to 20 rows to keep PDF manageable)
        int count = 0;
        for (Asset asset : assets.stream().limit(20).collect(Collectors.toList())) {
            count++;
            addAssetRow(assetTable, count, asset, regularFont);
        }

        document.add(assetTable);

        if (assets.size() > 20) {
            document.add(new Paragraph("... and " + (assets.size() - 20) + " more assets")
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(TEXT_MUTED)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        // Category breakdown
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("Category Breakdown")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        Map<String, Long> categoryCount = assets.stream()
                .filter(a -> a.getCategory() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getCategory().getName(),
                        Collectors.counting()
                ));

        float[] catWidths = {200, 100};
        Table catTable = new Table(catWidths);
        catTable.setWidth(UnitValue.createPercentValue(60));
        catTable.setHorizontalAlignment(HorizontalAlignment.CENTER);

        addTableHeader(catTable, new String[]{"Category", "Count"}, boldFont);

        categoryCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> {
                    Cell catCell = new Cell().add(new Paragraph(entry.getKey()).setFont(regularFont).setFontSize(10));
                    Cell countCell = new Cell().add(new Paragraph(String.valueOf(entry.getValue())).setFont(regularFont).setFontSize(10));
                    catTable.addCell(catCell);
                    catTable.addCell(countCell);
                });

        document.add(catTable);

        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("─".repeat(80))
                .setFont(regularFont)
                .setFontSize(8)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\n\n"));
    }

    // ============================================
    // TRANSFER REPORT
    // ============================================

    private void addTransferReport(Document document, LocalDate startDate, LocalDate endDate,
                                   PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("TRANSFERS REPORT")
                .setFont(boldFont)
                .setFontSize(16)
                .setFontColor(TEXT_COLOR)
                .setTextAlignment(TextAlignment.LEFT));

        document.add(new Paragraph("\n"));

        // Get data
        List<Transfer> transfers = transferRepository.findAll();

        // Filter by date range
        if (startDate != null) {
            transfers = transfers.stream()
                    .filter(t -> t.getTransferDate() != null && !t.getTransferDate().isBefore(startDate))
                    .collect(Collectors.toList());
        }
        if (endDate != null) {
            transfers = transfers.stream()
                    .filter(t -> t.getTransferDate() != null && !t.getTransferDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Calculate statistics
        long totalTransfers = transfers.size();
        long pendingTransfers = transfers.stream()
                .filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()))
                .count();
        long completedTransfers = transfers.stream()
                .filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()))
                .count();
        long rejectedTransfers = transfers.stream()
                .filter(t -> "REJECTED".equalsIgnoreCase(t.getStatus()))
                .count();

        // Add summary section
        document.add(new Paragraph("Key Metrics")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        float[] summaryWidths = {200, 200};
        Table summaryTable = new Table(summaryWidths);
        summaryTable.setHorizontalAlignment(HorizontalAlignment.CENTER);
        summaryTable.setWidth(UnitValue.createPercentValue(80));

        addSummaryRow(summaryTable, "Total Transfers", String.valueOf(totalTransfers), boldFont, regularFont);
        addSummaryRow(summaryTable, "Pending", String.valueOf(pendingTransfers), boldFont, regularFont);
        addSummaryRow(summaryTable, "Completed", String.valueOf(completedTransfers), boldFont, regularFont);
        addSummaryRow(summaryTable, "Rejected", String.valueOf(rejectedTransfers), boldFont, regularFont);

        document.add(summaryTable);
        document.add(new Paragraph("\n\n"));

        // Transfer Details Table
        document.add(new Paragraph("Transfer Details")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        float[] transferWidths = {40, 80, 80, 80, 80, 80, 80};
        Table transferTable = new Table(transferWidths);
        transferTable.setWidth(UnitValue.createPercentValue(100));

        addTableHeader(transferTable, new String[]{"#", "Category", "From", "To", "Item", "Date", "Status"}, boldFont);

        int count = 0;
        for (Transfer transfer : transfers.stream().limit(20).collect(Collectors.toList())) {
            count++;
            addTransferRow(transferTable, count, transfer, regularFont);
        }

        document.add(transferTable);

        if (transfers.size() > 20) {
            document.add(new Paragraph("... and " + (transfers.size() - 20) + " more transfers")
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(TEXT_MUTED)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        // Category breakdown
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("Transfer Category Breakdown")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        Map<String, Long> categoryCount = transfers.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(
                        Transfer::getCategory,
                        Collectors.counting()
                ));

        float[] catWidths = {200, 100};
        Table catTable = new Table(catWidths);
        catTable.setWidth(UnitValue.createPercentValue(60));
        catTable.setHorizontalAlignment(HorizontalAlignment.CENTER);

        addTableHeader(catTable, new String[]{"Category", "Count"}, boldFont);

        categoryCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> {
                    catTable.addCell(new Cell().add(new Paragraph(entry.getKey()).setFont(regularFont).setFontSize(10)));
                    catTable.addCell(new Cell().add(new Paragraph(String.valueOf(entry.getValue())).setFont(regularFont).setFontSize(10)));
                });

        document.add(catTable);

        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("─".repeat(80))
                .setFont(regularFont)
                .setFontSize(8)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\n\n"));
    }

    // ============================================
    // BOOKING REPORT
    // ============================================

    private void addBookingReport(Document document, LocalDate startDate, LocalDate endDate,
                                  PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("BOOKINGS REPORT")
                .setFont(boldFont)
                .setFontSize(16)
                .setFontColor(TEXT_COLOR)
                .setTextAlignment(TextAlignment.LEFT));

        document.add(new Paragraph("\n"));

        // Get data
        List<Booking> bookings = bookingRepository.findAll();
        List<DriverRequest> driverRequests = driverRequestRepository.findAll();

        // Filter bookings by date range
        if (startDate != null) {
            bookings = bookings.stream()
                    .filter(b -> b.getStartTime() != null && !b.getStartTime().toLocalDate().isBefore(startDate))
                    .collect(Collectors.toList());
        }
        if (endDate != null) {
            bookings = bookings.stream()
                    .filter(b -> b.getEndTime() != null && !b.getEndTime().toLocalDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Filter driver requests by date range
        if (startDate != null) {
            driverRequests = driverRequests.stream()
                    .filter(r -> r.getRequestTime() != null && !r.getRequestTime().toLocalDate().isBefore(startDate))
                    .collect(Collectors.toList());
        }
        if (endDate != null) {
            driverRequests = driverRequests.stream()
                    .filter(r -> r.getRequestTime() != null && !r.getRequestTime().toLocalDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        // Calculate statistics
        long totalBookings = bookings.size();
        long totalDriverRequests = driverRequests.size();
        long pendingBookings = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.PENDING)
                .count();
        long confirmedBookings = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED)
                .count();
        long pendingDriverRequests = driverRequests.stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()) || "PENDING_ADMIN".equalsIgnoreCase(r.getStatus()))
                .count();

        // Add summary section
        document.add(new Paragraph("Key Metrics")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        float[] summaryWidths = {200, 200};
        Table summaryTable = new Table(summaryWidths);
        summaryTable.setHorizontalAlignment(HorizontalAlignment.CENTER);
        summaryTable.setWidth(UnitValue.createPercentValue(80));

        addSummaryRow(summaryTable, "Total Room Bookings", String.valueOf(totalBookings), boldFont, regularFont);
        addSummaryRow(summaryTable, "Total Driver Requests", String.valueOf(totalDriverRequests), boldFont, regularFont);
        addSummaryRow(summaryTable, "Pending Room Bookings", String.valueOf(pendingBookings), boldFont, regularFont);
        addSummaryRow(summaryTable, "Pending Driver Requests", String.valueOf(pendingDriverRequests), boldFont, regularFont);

        document.add(summaryTable);
        document.add(new Paragraph("\n\n"));

        // Room Bookings Table
        document.add(new Paragraph("Room Bookings")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        float[] roomWidths = {30, 80, 80, 100, 80, 80};
        Table roomTable = new Table(roomWidths);
        roomTable.setWidth(UnitValue.createPercentValue(100));

        addTableHeader(roomTable, new String[]{"#", "Booked By", "Room", "Time Slot", "Date", "Status"}, boldFont);

        int count = 0;
        for (Booking booking : bookings.stream().limit(15).collect(Collectors.toList())) {
            count++;
            addBookingRow(roomTable, count, booking, regularFont);
        }

        document.add(roomTable);

        if (bookings.size() > 15) {
            document.add(new Paragraph("... and " + (bookings.size() - 15) + " more room bookings")
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(TEXT_MUTED)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        // Driver Requests Table
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("Driver Requests")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        float[] driverWidths = {30, 80, 80, 100, 80, 80};
        Table driverTable = new Table(driverWidths);
        driverTable.setWidth(UnitValue.createPercentValue(100));

        addTableHeader(driverTable, new String[]{"#", "Requester", "Driver", "Destination", "Requested", "Status"}, boldFont);

        count = 0;
        for (DriverRequest request : driverRequests.stream().limit(15).collect(Collectors.toList())) {
            count++;
            addDriverRequestRow(driverTable, count, request, regularFont);
        }

        document.add(driverTable);

        if (driverRequests.size() > 15) {
            document.add(new Paragraph("... and " + (driverRequests.size() - 15) + " more driver requests")
                    .setFont(regularFont)
                    .setFontSize(10)
                    .setFontColor(TEXT_MUTED)
                    .setTextAlignment(TextAlignment.CENTER));
        }

        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("─".repeat(80))
                .setFont(regularFont)
                .setFontSize(8)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\n\n"));
    }

    // ============================================
    // RESOURCE REQUEST REPORT
    // ============================================

    private void addResourceRequestReport(Document document, LocalDate startDate, LocalDate endDate,
                                          PdfFont boldFont, PdfFont regularFont) {
        document.add(new Paragraph("RESOURCE REQUESTS REPORT")
                .setFont(boldFont)
                .setFontSize(16)
                .setFontColor(TEXT_COLOR)
                .setTextAlignment(TextAlignment.LEFT));

        document.add(new Paragraph("\n"));

        List<Transfer> transfers = transferRepository.findAll();
        List<Booking> bookings = bookingRepository.findAll();

        // Calculate some combined metrics
        long totalTransfers = transfers.size();
        long pendingTransfers = transfers.stream()
                .filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()))
                .count();
        long totalBookings = bookings.size();
        long pendingBookings = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.PENDING)
                .count();

        document.add(new Paragraph("Key Metrics")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        float[] summaryWidths = {200, 200};
        Table summaryTable = new Table(summaryWidths);
        summaryTable.setHorizontalAlignment(HorizontalAlignment.CENTER);
        summaryTable.setWidth(UnitValue.createPercentValue(80));

        addSummaryRow(summaryTable, "Total Asset Transfers", String.valueOf(totalTransfers), boldFont, regularFont);
        addSummaryRow(summaryTable, "Pending Transfers", String.valueOf(pendingTransfers), boldFont, regularFont);
        addSummaryRow(summaryTable, "Total Room Bookings", String.valueOf(totalBookings), boldFont, regularFont);
        addSummaryRow(summaryTable, "Pending Bookings", String.valueOf(pendingBookings), boldFont, regularFont);

        document.add(summaryTable);

        document.add(new Paragraph("\n\n"));

        document.add(new Paragraph("Resource Request Details")
                .setFont(boldFont)
                .setFontSize(14)
                .setFontColor(TEXT_COLOR));

        document.add(new Paragraph("\n"));

        Paragraph note = new Paragraph(
                "Note: Detailed resource request data is currently being collected. " +
                        "The metrics above represent the current state of asset transfers and room bookings."
        ).setFont(regularFont).setFontSize(11).setFontColor(TEXT_COLOR);

        document.add(note);

        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("─".repeat(80))
                .setFont(regularFont)
                .setFontSize(8)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\n\n"));
    }

    // ============================================
    // HIGHLIGHTS METHODS
    // ============================================

    private void addAssetHighlights(Document document, LocalDate startDate, LocalDate endDate,
                                    PdfFont boldFont, PdfFont regularFont) {
        List<Asset> assets = assetRepository.findAll();
        LocalDate now = LocalDate.now();

        long totalAssets = assets.size();
        long activeAssets = assets.stream()
                .filter(a -> a.getStatus() == Asset.AssetStatus.AVAILABLE || a.getStatus() == Asset.AssetStatus.ASSIGNED)
                .count();
        long expiredWarranty = assets.stream()
                .filter(a -> a.getWarrantyEndDate() != null && a.getWarrantyEndDate().isBefore(now))
                .count();
        long eolAssets = assets.stream()
                .filter(a -> a.getEolDate() != null && a.getEolDate().isBefore(now))
                .count();

        double avgWarrantyDays = assets.stream()
                .filter(a -> a.getWarrantyEndDate() != null && a.getWarrantyEndDate().isAfter(now))
                .mapToLong(a -> ChronoUnit.DAYS.between(now, a.getWarrantyEndDate()))
                .average()
                .orElse(0);

        String highlights = String.format(
                "• Total Assets: %d\n" +
                        "• Active Assets: %d (%.1f%%)\n" +
                        "• Assets with Expired Warranty: %d (%.1f%%)\n" +
                        "• End of Life Assets: %d (%.1f%%)\n" +
                        "• Average Warranty Remaining: %.0f days",
                totalAssets,
                activeAssets, (totalAssets > 0 ? (double) activeAssets / totalAssets * 100 : 0),
                expiredWarranty, (totalAssets > 0 ? (double) expiredWarranty / totalAssets * 100 : 0),
                eolAssets, (totalAssets > 0 ? (double) eolAssets / totalAssets * 100 : 0),
                avgWarrantyDays
        );

        Paragraph highlightsPara = new Paragraph(highlights)
                .setFont(regularFont)
                .setFontSize(11)
                .setFontColor(TEXT_COLOR);
        document.add(highlightsPara);
    }

    private void addTransferHighlights(Document document, LocalDate startDate, LocalDate endDate,
                                       PdfFont boldFont, PdfFont regularFont) {
        List<Transfer> transfers = transferRepository.findAll();

        long totalTransfers = transfers.size();
        long pendingTransfers = transfers.stream()
                .filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()))
                .count();
        long completedTransfers = transfers.stream()
                .filter(t -> "COMPLETED".equalsIgnoreCase(t.getStatus()))
                .count();
        long rejectedTransfers = transfers.stream()
                .filter(t -> "REJECTED".equalsIgnoreCase(t.getStatus()))
                .count();

        double completionRate = totalTransfers > 0 ? (double) completedTransfers / totalTransfers * 100 : 0;

        String highlights = String.format(
                "• Total Transfers: %d\n" +
                        "• Completed: %d (%.1f%%)\n" +
                        "• Pending: %d (%.1f%%)\n" +
                        "• Rejected: %d (%.1f%%)",
                totalTransfers,
                completedTransfers, completionRate,
                pendingTransfers, (totalTransfers > 0 ? (double) pendingTransfers / totalTransfers * 100 : 0),
                rejectedTransfers, (totalTransfers > 0 ? (double) rejectedTransfers / totalTransfers * 100 : 0)
        );

        Paragraph highlightsPara = new Paragraph(highlights)
                .setFont(regularFont)
                .setFontSize(11)
                .setFontColor(TEXT_COLOR);
        document.add(highlightsPara);
    }

    private void addBookingHighlights(Document document, LocalDate startDate, LocalDate endDate,
                                      PdfFont boldFont, PdfFont regularFont) {
        List<Booking> bookings = bookingRepository.findAll();

        long totalBookings = bookings.size();
        long pendingBookings = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.PENDING)
                .count();
        long confirmedBookings = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.CONFIRMED)
                .count();
        long activeBookings = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.ACTIVE || b.getStatus() == Booking.BookingStatus.BOOKED)
                .count();

        String highlights = String.format(
                "• Total Bookings: %d\n" +
                        "• Active/Booked: %d (%.1f%%)\n" +
                        "• Pending: %d (%.1f%%)\n" +
                        "• Confirmed/Completed: %d (%.1f%%)",
                totalBookings,
                activeBookings, (totalBookings > 0 ? (double) activeBookings / totalBookings * 100 : 0),
                pendingBookings, (totalBookings > 0 ? (double) pendingBookings / totalBookings * 100 : 0),
                confirmedBookings, (totalBookings > 0 ? (double) confirmedBookings / totalBookings * 100 : 0)
        );

        Paragraph highlightsPara = new Paragraph(highlights)
                .setFont(regularFont)
                .setFontSize(11)
                .setFontColor(TEXT_COLOR);
        document.add(highlightsPara);
    }

    private void addResourceHighlights(Document document, LocalDate startDate, LocalDate endDate,
                                       PdfFont boldFont, PdfFont regularFont) {
        List<Transfer> transfers = transferRepository.findAll();
        List<Booking> bookings = bookingRepository.findAll();

        long totalTransfers = transfers.size();
        long pendingTransfers = transfers.stream()
                .filter(t -> "PENDING".equalsIgnoreCase(t.getStatus()))
                .count();
        long totalBookings = bookings.size();
        long pendingBookings = bookings.stream()
                .filter(b -> b.getStatus() == Booking.BookingStatus.PENDING)
                .count();

        String highlights = String.format(
                "• Total Resource Requests: %d\n" +
                        "• Pending Transfers: %d (%.1f%%)\n" +
                        "• Pending Bookings: %d (%.1f%%)\n" +
                        "• Total Active Resources: %d",
                totalTransfers + totalBookings,
                pendingTransfers, (totalTransfers > 0 ? (double) pendingTransfers / totalTransfers * 100 : 0),
                pendingBookings, (totalBookings > 0 ? (double) pendingBookings / totalBookings * 100 : 0),
                (totalTransfers - pendingTransfers) + (totalBookings - pendingBookings)
        );

        Paragraph highlightsPara = new Paragraph(highlights)
                .setFont(regularFont)
                .setFontSize(11)
                .setFontColor(TEXT_COLOR);
        document.add(highlightsPara);
    }

    // ============================================
    // HELPER METHODS FOR TABLE BUILDING
    // ============================================

    private void addSummaryRow(Table table, String label, String value, PdfFont boldFont, PdfFont regularFont) {
        Cell labelCell = new Cell()
                .add(new Paragraph(label).setFont(regularFont).setFontSize(11))
                .setBorder(Border.NO_BORDER)
                .setPadding(4);
        Cell valueCell = new Cell()
                .add(new Paragraph(value).setFont(boldFont).setFontSize(11))
                .setBorder(Border.NO_BORDER)
                .setPadding(4)
                .setTextAlignment(TextAlignment.RIGHT);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addTableHeader(Table table, String[] headers, PdfFont boldFont) {
        for (String header : headers) {
            Cell cell = new Cell()
                    .add(new Paragraph(header).setFont(boldFont).setFontSize(9))
                    .setBackgroundColor(TABLE_HEADER_BG)
                    .setFontColor(TEXT_COLOR)
                    .setPadding(6)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBorder(new SolidBorder(BORDER_COLOR, 0.5f));
            table.addCell(cell);
        }
    }

    private void addAssetRow(Table table, int count, Asset asset, PdfFont regularFont) {
        DeviceRgb statusColor = hexToRgb(getStatusColor(asset.getStatus()));

        table.addCell(new Cell().add(new Paragraph(String.valueOf(count)).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(asset.getName() != null ? asset.getName() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(asset.getTag() != null ? asset.getTag() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(asset.getCategory() != null ? asset.getCategory().getName() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(asset.getPurchaseDate() != null ? asset.getPurchaseDate().toString() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(asset.getStatus() != null ? asset.getStatus().name() : "N/A").setFont(regularFont).setFontSize(9).setFontColor(statusColor))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(asset.getWarrantyEndDate() != null ? asset.getWarrantyEndDate().toString() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
    }

    private void addTransferRow(Table table, int count, Transfer transfer, PdfFont regularFont) {
        DeviceRgb statusColor = hexToRgb(getTransferStatusColor(transfer.getStatus()));

        table.addCell(new Cell().add(new Paragraph(String.valueOf(count)).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(transfer.getCategory() != null ? transfer.getCategory() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(getDepartmentName(transfer.getOldDepartmentId())).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(getDepartmentName(transfer.getNewDepartmentId())).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(transfer.getAssetTag() != null ? transfer.getAssetTag() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(transfer.getTransferDate() != null ? transfer.getTransferDate().toString() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(transfer.getStatus() != null ? transfer.getStatus() : "PENDING").setFont(regularFont).setFontSize(9).setFontColor(statusColor))
                .setPadding(4));
    }

    private void addBookingRow(Table table, int count, Booking booking, PdfFont regularFont) {
        DeviceRgb statusColor = hexToRgb(getBookingStatusColor(booking.getStatus()));

        table.addCell(new Cell().add(new Paragraph(String.valueOf(count)).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(getUserName(booking.getUserId())).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(getRoomName(booking.getRoomId())).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        String timeSlot = "";
        if (booking.getStartTime() != null && booking.getEndTime() != null) {
            timeSlot = booking.getStartTime().toLocalTime() + " - " + booking.getEndTime().toLocalTime();
        }
        table.addCell(new Cell().add(new Paragraph(timeSlot).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(booking.getStartTime() != null ? booking.getStartTime().toLocalDate().toString() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(booking.getStatus() != null ? booking.getStatus().name() : "UNKNOWN").setFont(regularFont).setFontSize(9).setFontColor(statusColor))
                .setPadding(4));
    }

    private void addDriverRequestRow(Table table, int count, DriverRequest request, PdfFont regularFont) {
        DeviceRgb statusColor = hexToRgb(getDriverStatusColor(request.getStatus()));

        table.addCell(new Cell().add(new Paragraph(String.valueOf(count)).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(request.getRequestedBy() != null ? request.getRequestedBy() : getUserName(request.getUserId())).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(getUserName(request.getDriverId())).setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(request.getDestination() != null ? request.getDestination() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(request.getRequestTime() != null ? request.getRequestTime().toString() : "N/A").setFont(regularFont).setFontSize(9))
                .setPadding(4));
        table.addCell(new Cell().add(new Paragraph(request.getStatus() != null ? request.getStatus() : "UNKNOWN").setFont(regularFont).setFontSize(9).setFontColor(statusColor))
                .setPadding(4));
    }

    // ============================================
    // HELPER METHODS FOR LOOKUPS
    // ============================================

    private String getUserName(Long userId) {
        if (userId == null) return "N/A";
        return userRepository.findById(userId)
                .map(u -> u.getFullName() != null ? u.getFullName() : u.getUsername())
                .orElse("User #" + userId);
    }

    private String getDepartmentName(Integer departmentId) {
        if (departmentId == null) return "N/A";
        return departmentRepository.findById(departmentId)
                .map(Department::getName)
                .orElse("Dept #" + departmentId);
    }

    private String getRoomName(Long roomId) {
        if (roomId == null) return "N/A";
        return roomRepository.findById(roomId)
                .map(Room::getRoomName)
                .orElse("Room #" + roomId);
    }

    // ============================================
    // STATUS COLOR HELPERS - Return hex strings
    // ============================================

    private String getStatusColor(Asset.AssetStatus status) {
        if (status == null) return "#64748b";
        return switch (status) {
            case AVAILABLE, ASSIGNED -> "#16a34a";
            case MAINTENANCE -> "#d97706";
            case RETIRED, DISPOSED -> "#64748b";
            case TRANSFERRED -> "#4f46e5";
            default -> "#64748b";
        };
    }

    private String getTransferStatusColor(String status) {
        if (status == null) return "#64748b";
        return switch (status.toUpperCase()) {
            case "COMPLETED", "APPROVED" -> "#16a34a";
            case "PENDING" -> "#d97706";
            case "REJECTED" -> "#dc2626";
            default -> "#64748b";
        };
    }

    private String getBookingStatusColor(Booking.BookingStatus status) {
        if (status == null) return "#64748b";
        return switch (status) {
            case CONFIRMED -> "#16a34a";
            case PENDING -> "#d97706";
            case CANCELLED -> "#dc2626";
            case ACTIVE, BOOKED -> "#4f46e5";
            default -> "#64748b";
        };
    }

    private String getDriverStatusColor(String status) {
        if (status == null) return "#64748b";
        return switch (status.toUpperCase()) {
            case "COMPLETED", "ACCEPTED" -> "#16a34a";
            case "PENDING", "PENDING_ADMIN" -> "#d97706";
            case "DECLINED", "CANCELLED" -> "#dc2626";
            default -> "#64748b";
        };
    }
}