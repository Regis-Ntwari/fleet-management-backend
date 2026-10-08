package com.limoz.fleet.reporting.export;

import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.Map;

/** Landscape A4 PDF with a header band, metadata block, zebra rows and totals. */
@Component
public class PdfReportExporter implements ReportExporter {

    private static final Color BRAND = new Color(10, 94, 62);
    private static final Color ZEBRA = new Color(243, 246, 244);

    @Override
    public ReportFormat format() {
        return ReportFormat.PDF;
    }

    @Override
    public byte[] export(ReportTable table) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean wide = table.columns().size() > 6;
        Document document = new Document(wide ? PageSize.A4.rotate() : PageSize.A4, 28, 28, 36, 36);
        PdfWriter.getInstance(document, out);
        document.open();
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, BRAND);
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.DARK_GRAY);
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, Color.BLACK);
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, Color.BLACK);

        document.add(new Paragraph(table.title(), titleFont));
        for (Map.Entry<String, String> meta : table.metadata().entrySet()) {
            document.add(new Paragraph(meta.getKey() + ": " + meta.getValue(), metaFont));
        }
        document.add(new Paragraph(" "));

        PdfPTable pdf = new PdfPTable(table.columns().size());
        pdf.setWidthPercentage(100);
        pdf.setHeaderRows(1);
        for (ReportTable.Column column : table.columns()) {
            PdfPCell cell = new PdfPCell(new Phrase(column.label(), headFont));
            cell.setBackgroundColor(BRAND);
            cell.setPadding(5);
            cell.setHorizontalAlignment(align(column.align()));
            pdf.addCell(cell);
        }
        int index = 0;
        for (var row : table.rows()) {
            for (int c = 0; c < table.columns().size(); c++) {
                Object value = c < row.size() ? row.get(c) : "";
                PdfPCell cell = new PdfPCell(new Phrase(ReportValues.display(value), cellFont));
                cell.setPadding(4);
                cell.setBorderColor(Color.LIGHT_GRAY);
                cell.setHorizontalAlignment(ReportValues.isNumeric(value) ? Element.ALIGN_RIGHT : align(table.columns().get(c).align()));
                if (index % 2 == 1) cell.setBackgroundColor(ZEBRA);
                pdf.addCell(cell);
            }
            index++;
        }
        if (table.totals() != null) {
            for (int c = 0; c < table.columns().size(); c++) {
                Object value = c < table.totals().size() ? table.totals().get(c) : "";
                PdfPCell cell = new PdfPCell(new Phrase(ReportValues.display(value), totalFont));
                cell.setPadding(4);
                cell.setBorder(Rectangle.TOP);
                cell.setHorizontalAlignment(ReportValues.isNumeric(value) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                pdf.addCell(cell);
            }
        }
        if (table.rows().isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No data for the selected criteria", cellFont));
            empty.setColspan(table.columns().size());
            empty.setPadding(8);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            pdf.addCell(empty);
        }
        document.add(pdf);
        document.close();
        return out.toByteArray();
    }

    private static int align(ReportTable.Align align) {
        return switch (align) {
            case RIGHT -> Element.ALIGN_RIGHT;
            case CENTER -> Element.ALIGN_CENTER;
            default -> Element.ALIGN_LEFT;
        };
    }
}
