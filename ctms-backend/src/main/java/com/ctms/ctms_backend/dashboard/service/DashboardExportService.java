package com.ctms.ctms_backend.dashboard.service;

import com.ctms.ctms_backend.dashboard.dto.DashboardSummaryResponse;
import com.ctms.ctms_backend.dashboard.dto.HighRiskSiteResponse;
import com.ctms.ctms_backend.milestone.dto.MilestoneResponse;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** BL Epic 6 Story 03 ("export available PDF/Excel"). Reuses the already-in-pom, previously
 * unused poi-ooxml / openpdf dependencies -- no new libraries added. */
@Service
public class DashboardExportService {

    private final DashboardService dashboardService;

    public DashboardExportService(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    public byte[] export(Long studyId, String country, Long siteId, String phase, String actorUsername, String format) {
        DashboardSummaryResponse summary = dashboardService.summary(studyId, country, siteId, phase, actorUsername);
        return "excel".equalsIgnoreCase(format) ? toExcel(summary) : toPdf(summary);
    }

    private byte[] toExcel(DashboardSummaryResponse summary) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeMapSheet(workbook, "Enrollment by Status", summary.enrollmentByStatus());
            writeMapSheet(workbook, "Site Activation by Status", summary.siteActivationByStatus());
            writeMapSheet(workbook, "Sites by Country", summary.sitesByCountry());

            Sheet adherenceSheet = workbook.createSheet("Visit Adherence");
            Row adherenceRow = adherenceSheet.createRow(0);
            adherenceRow.createCell(0).setCellValue("Visit Adherence Rate (%)");
            adherenceRow.createCell(1).setCellValue(summary.visitAdherenceRatePercent() == null ? 0 : summary.visitAdherenceRatePercent());

            Sheet riskSheet = workbook.createSheet("High-Risk Sites");
            Row riskHeader = riskSheet.createRow(0);
            riskHeader.createCell(0).setCellValue("Site Code");
            riskHeader.createCell(1).setCellValue("Name");
            riskHeader.createCell(2).setCellValue("Missed Visit Rate (%)");
            riskHeader.createCell(3).setCellValue("Open High-Severity AEs");
            int riskRowIdx = 1;
            for (HighRiskSiteResponse site : summary.highRiskSites()) {
                Row row = riskSheet.createRow(riskRowIdx++);
                row.createCell(0).setCellValue(site.siteCode());
                row.createCell(1).setCellValue(site.name());
                row.createCell(2).setCellValue(site.missedVisitRatePercent());
                row.createCell(3).setCellValue(site.openHighSeverityAeCount());
            }

            Sheet milestoneSheet = workbook.createSheet("Milestones");
            Row milestoneHeader = milestoneSheet.createRow(0);
            milestoneHeader.createCell(0).setCellValue("Study");
            milestoneHeader.createCell(1).setCellValue("Type");
            milestoneHeader.createCell(2).setCellValue("Planned Date");
            milestoneHeader.createCell(3).setCellValue("Actual Date");
            milestoneHeader.createCell(4).setCellValue("Delayed");
            int milestoneRowIdx = 1;
            for (MilestoneResponse m : summary.milestones()) {
                Row row = milestoneSheet.createRow(milestoneRowIdx++);
                row.createCell(0).setCellValue(m.studyCode());
                row.createCell(1).setCellValue(m.milestoneType());
                row.createCell(2).setCellValue(m.plannedDate().toString());
                row.createCell(3).setCellValue(m.actualDate() == null ? "" : m.actualDate().toString());
                row.createCell(4).setCellValue(m.delayed());
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate Excel export", e);
        }
    }

    private void writeMapSheet(XSSFWorkbook workbook, String sheetName, Map<String, Long> data) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Status");
        header.createCell(1).setCellValue("Count");
        int rowIdx = 1;
        for (Map.Entry<String, Long> entry : data.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            Cell cell = row.createCell(1);
            cell.setCellValue(entry.getValue());
        }
    }

    private byte[] toPdf(DashboardSummaryResponse summary) {
        Document document = new Document();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            document.add(new Paragraph("CTMS Dashboard Report", titleFont));
            document.add(Chunk.NEWLINE);

            addMapSection(document, "Enrollment by Status", summary.enrollmentByStatus(), sectionFont);
            addMapSection(document, "Site Activation by Status", summary.siteActivationByStatus(), sectionFont);
            addMapSection(document, "Sites by Country", summary.sitesByCountry(), sectionFont);

            document.add(new Paragraph("Visit Adherence Rate: "
                    + (summary.visitAdherenceRatePercent() == null ? "N/A" : String.format("%.1f%%", summary.visitAdherenceRatePercent())),
                    sectionFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("High-Risk Sites", sectionFont));
            PdfPTable riskTable = new PdfPTable(4);
            addHeaderRow(riskTable, "Site Code", "Name", "Missed Visit Rate (%)", "Open High-Severity AEs");
            for (HighRiskSiteResponse site : summary.highRiskSites()) {
                riskTable.addCell(site.siteCode());
                riskTable.addCell(site.name());
                riskTable.addCell(String.format("%.1f", site.missedVisitRatePercent()));
                riskTable.addCell(String.valueOf(site.openHighSeverityAeCount()));
            }
            document.add(riskTable);
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Milestones", sectionFont));
            PdfPTable milestoneTable = new PdfPTable(5);
            addHeaderRow(milestoneTable, "Study", "Type", "Planned", "Actual", "Delayed");
            for (MilestoneResponse m : summary.milestones()) {
                milestoneTable.addCell(m.studyCode());
                milestoneTable.addCell(m.milestoneType());
                milestoneTable.addCell(m.plannedDate().toString());
                milestoneTable.addCell(m.actualDate() == null ? "-" : m.actualDate().toString());
                milestoneTable.addCell(m.delayed() ? "Yes" : "No");
            }
            document.add(milestoneTable);

            document.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate PDF export", e);
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate PDF export", e);
        }
    }

    private void addMapSection(Document document, String title, Map<String, Long> data, Font sectionFont) throws DocumentException {
        document.add(new Paragraph(title, sectionFont));
        for (Map.Entry<String, Long> entry : data.entrySet()) {
            document.add(new Paragraph(entry.getKey() + ": " + entry.getValue(), new Font(Font.HELVETICA, 10)));
        }
        document.add(Chunk.NEWLINE);
    }

    private void addHeaderRow(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new org.openpdf.text.Phrase(header));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }
}
