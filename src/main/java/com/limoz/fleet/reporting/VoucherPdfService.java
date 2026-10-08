package com.limoz.fleet.reporting;

import com.limoz.fleet.booking.DeploymentVoucher;
import com.limoz.fleet.booking.DeploymentVoucherRepository;
import com.limoz.fleet.common.exception.ResourceNotFoundException;
import com.limoz.fleet.reporting.export.ReportValues;
import com.limoz.fleet.settings.SettingKeys;
import com.limoz.fleet.settings.SettingsService;
import com.limoz.fleet.user.UserRepository;
import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

/** Printable deployment voucher (LIMOZ/000628/2026) mirroring the reference application's voucher layout. */
@Service
@RequiredArgsConstructor
public class VoucherPdfService {

    private static final Color BRAND = new Color(10, 94, 62);

    private final DeploymentVoucherRepository voucherRepository;
    private final SettingsService settings;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public byte[] render(Long voucherId) {
        DeploymentVoucher v = voucherRepository.findDetailedById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment voucher", voucherId));
        String manager = v.getAccountManagerUserId() == null ? "-"
                : userRepository.findById(v.getAccountManagerUserId()).map(u -> u.getFullName()).orElse("-");
        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BRAND);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND);
        Font label = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        Font value = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.BLACK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.BLACK);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph(settings.get(SettingKeys.COMPANY_LEGAL_NAME), h1));
        doc.add(new Paragraph(settings.get(SettingKeys.COMPANY_ADDRESS) + " · " + settings.get(SettingKeys.COMPANY_CITY) + " · "
                + settings.get(SettingKeys.COMPANY_PHONE) + " · TIN " + settings.get(SettingKeys.COMPANY_TIN), small));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("DEPLOYMENT VOUCHER  " + v.getVoucherNumber(), h1));
        doc.add(new Paragraph("Booking " + v.getBooking().getBookingNumber() + " · Status " + v.getStatus().name().replace('_', ' '), small));
        doc.add(new Paragraph(" "));

        long distance = v.getStartKm() != null && v.getEndKm() != null ? v.getEndKm() - v.getStartKm() : 0;
        section(doc, h2, "Order & billing");
        grid(doc, label, value,
                "Reference No", v.getVoucherNumber(), "Date", ReportValues.display(v.getVoucherDate()),
                "Purchase Order", v.getPurchaseOrderId() == null ? "-" : "#" + v.getPurchaseOrderId(), "P.O amount", money(v.getPoAmount()),
                "Institution / Client", v.getCustomer().getName(), "Tel", v.getClientTel() == null ? "-" : v.getClientTel(),
                "Account manager", manager, "Planned days", String.valueOf(v.getPlannedDays()));
        section(doc, h2, "Vehicle & route");
        grid(doc, label, value,
                "Plate No", v.getVehicle().getPlateNumber(), "Category", v.getVehicle().getCategory().getName(),
                "Destination", v.getDestination() == null ? "-" : v.getDestination(), "Driver", v.getDriver().getFullName(),
                "Owner", v.getOwnerName() == null ? settings.get(SettingKeys.COMPANY_NAME) : v.getOwnerName(),
                "Owner driver", v.getOwnerDriverName() == null ? "-" : v.getOwnerDriverName());
        section(doc, h2, "Trip readings");
        grid(doc, label, value,
                "Start KM", v.getStartKm() == null ? "-" : v.getStartKm() + " km", "End KM", v.getEndKm() == null ? "Pending" : v.getEndKm() + " km",
                "Distance", v.getEndKm() == null ? "Pending" : distance + " km", "Effective days", v.getEffectiveDays().stripTrailingZeros().toPlainString() + " / " + v.getPlannedDays());
        section(doc, h2, "Billing");
        grid(doc, label, value,
                "Rate", v.getEffectiveDays().setScale(3, java.math.RoundingMode.HALF_UP) + " DAY × " + money(v.getDayRate()), "Institution amount (RWF)", money(v.getInstitutionAmount()),
                "Owner amount", money(v.getOwnerAmount()), "Fuel amount", money(v.getFuelAmount()),
                "Net amount (owner − fuel)", money(v.getNetAmount()), "Mission due amount", money(v.getMissionDueAmount()));
        section(doc, h2, "Comment & observation");
        grid(doc, label, value,
                "Comment", v.getComment() == null ? "-" : v.getComment(), "Observation", v.getObservation() == null ? "-" : v.getObservation());
        doc.add(new Paragraph(" "));
        PdfPTable sign = new PdfPTable(3);
        sign.setWidthPercentage(100);
        for (String s : new String[]{"Dispatcher", "Driver", "Client representative"}) {
            PdfPCell c = new PdfPCell(new Phrase("\n\n\n______________________\n" + s, small));
            c.setBorder(Rectangle.NO_BORDER);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            sign.addCell(c);
        }
        doc.add(sign);
        doc.close();
        return out.toByteArray();
    }

    private static void section(Document doc, Font font, String title) {
        Paragraph p = new Paragraph(title.toUpperCase(), font);
        p.setSpacingBefore(8);
        p.setSpacingAfter(3);
        doc.add(p);
    }

    private static void grid(Document doc, Font label, Font value, String... pairs) {
        PdfPTable t = new PdfPTable(4);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{1.2f, 2f, 1.2f, 2f});
        for (int i = 0; i < pairs.length; i += 2) {
            PdfPCell l = new PdfPCell(new Phrase(pairs[i], label));
            PdfPCell v = new PdfPCell(new Phrase(pairs[i + 1], value));
            for (PdfPCell c : new PdfPCell[]{l, v}) {
                c.setBorderColor(Color.LIGHT_GRAY);
                c.setPadding(4);
            }
            t.addCell(l);
            t.addCell(v);
        }
        if ((pairs.length / 2) % 2 == 1) {
            PdfPCell e = new PdfPCell(new Phrase(""));
            e.setColspan(2);
            e.setBorder(Rectangle.NO_BORDER);
            t.addCell(e);
        }
        doc.add(t);
    }

    private static String money(BigDecimal v) {
        return v == null ? "-" : ReportValues.display(v) + " RWF";
    }
}
