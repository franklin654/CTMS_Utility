package com.ctms.ctms_backend.budget.service;

import com.ctms.ctms_backend.budget.dto.BudgetLineItemResponse;
import com.ctms.ctms_backend.budget.dto.BudgetVersionResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

/** BL Epic 8 Story 02 ("export Excel/PDF"). Mirrors DashboardExportService's exact structure --
 * same previously-unused poi-ooxml/openpdf dependencies, no new libraries added. */
@Service
public class BudgetExportService {

    private final BudgetService budgetService;

    public BudgetExportService(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    public byte[] export(Long studyId, String format) {
        BudgetVersionResponse current = budgetService.getCurrentVersion(studyId);
        List<BudgetVersionResponse> history = budgetService.listVersions(studyId);
        return "excel".equalsIgnoreCase(format) ? toExcel(current, history) : toPdf(current, history);
    }

    private byte[] toExcel(BudgetVersionResponse current, List<BudgetVersionResponse> history) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet lineItemSheet = workbook.createSheet("Planned vs Actual");
            Row header = lineItemSheet.createRow(0);
            header.createCell(0).setCellValue("Cost Category");
            header.createCell(1).setCellValue("Planned");
            header.createCell(2).setCellValue("Actual");
            header.createCell(3).setCellValue("Variance");
            header.createCell(4).setCellValue("Currency");
            int rowIdx = 1;
            for (BudgetLineItemResponse item : current.lineItems()) {
                Row row = lineItemSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.costCategory());
                row.createCell(1).setCellValue(item.plannedAmount().doubleValue());
                row.createCell(2).setCellValue(item.actualAmount() == null ? 0 : item.actualAmount().doubleValue());
                row.createCell(3).setCellValue(item.variance() == null ? 0 : item.variance().doubleValue());
                row.createCell(4).setCellValue(item.currency());
            }

            Sheet historySheet = workbook.createSheet("Version History");
            Row historyHeader = historySheet.createRow(0);
            historyHeader.createCell(0).setCellValue("Version");
            historyHeader.createCell(1).setCellValue("Status");
            historyHeader.createCell(2).setCellValue("Reason");
            historyHeader.createCell(3).setCellValue("Created By");
            historyHeader.createCell(4).setCellValue("Created At");
            int historyRowIdx = 1;
            for (BudgetVersionResponse version : history) {
                Row row = historySheet.createRow(historyRowIdx++);
                row.createCell(0).setCellValue(version.versionNumber());
                row.createCell(1).setCellValue(version.status());
                row.createCell(2).setCellValue(version.reason() == null ? "" : version.reason());
                row.createCell(3).setCellValue(version.createdByUsername());
                row.createCell(4).setCellValue(version.createdAt().toString());
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate Excel export", e);
        }
    }

    private byte[] toPdf(BudgetVersionResponse current, List<BudgetVersionResponse> history) {
        Document document = new Document();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Font sectionFont = new Font(Font.HELVETICA, 12, Font.BOLD);
            document.add(new Paragraph("Budget Report -- " + current.studyCode(), titleFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Planned vs Actual (version " + current.versionNumber() + ")", sectionFont));
            PdfPTable lineItemTable = new PdfPTable(5);
            addHeaderRow(lineItemTable, "Cost Category", "Planned", "Actual", "Variance", "Currency");
            for (BudgetLineItemResponse item : current.lineItems()) {
                lineItemTable.addCell(item.costCategory());
                lineItemTable.addCell(item.plannedAmount().toString());
                lineItemTable.addCell(item.actualAmount() == null ? "-" : item.actualAmount().toString());
                lineItemTable.addCell(item.variance() == null ? "-" : item.variance().toString());
                lineItemTable.addCell(item.currency());
            }
            document.add(lineItemTable);
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Version History", sectionFont));
            PdfPTable historyTable = new PdfPTable(4);
            addHeaderRow(historyTable, "Version", "Status", "Reason", "Created By");
            for (BudgetVersionResponse version : history) {
                historyTable.addCell(String.valueOf(version.versionNumber()));
                historyTable.addCell(version.status());
                historyTable.addCell(version.reason() == null ? "-" : version.reason());
                historyTable.addCell(version.createdByUsername());
            }
            document.add(historyTable);

            document.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate PDF export", e);
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate PDF export", e);
        }
    }

    private void addHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }
}
