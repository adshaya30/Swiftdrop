package com.swiftdrop.parcel.order.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.swiftdrop.common.enums.ParcelStatus;
import com.swiftdrop.common.security.CurrentUsers;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.lifecycle.security.ParcelAccessPolicy;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-PAR-010: the "Delivery Receipt" PDF, generated on demand with OpenPDF. All user text is added as plain text,
 * never interpreted as markup (NFR-SEC-007). Times are shown in Asia/Colombo (BR-18).
 */
@Service
public class ReceiptService {

    static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font TEXT = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);

    private final ParcelAccessPolicy access;
    private final Clock clock;

    @Autowired
    public ReceiptService(ParcelAccessPolicy access) {
        this(access, Clock.systemUTC());
    }

    ReceiptService(ParcelAccessPolicy access, Clock clock) {
        this.access = access;
        this.clock = clock;
    }

    /** The caller's role was checked by the controller; a non-owner customer or merchant gets 404 (FR-PAR-017). */
    @Transactional(readOnly = true)
    public Receipt generate(UUID parcelId) {
        Parcel parcel = access.requireAccessible(parcelId, CurrentUsers.get());
        return new Receipt(parcel.getTrackingNumber(), render(parcel, clock.instant()));
    }

    static byte[] render(Parcel p, Instant issuedAt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.addTitle("SwiftDrop Delivery Receipt " + p.getTrackingNumber());
            document.open();
            document.add(new Paragraph("SwiftDrop", TITLE));
            document.add(new Paragraph("Delivery Receipt", HEADING));
            document.add(spacer());

            PdfPTable summary = table(2);
            row(summary, "Receipt number", p.getTrackingNumber());
            row(summary, "Issue date", DATE.format(issuedAt.atZone(COLOMBO)));
            row(summary, "Status", p.getStatus().label());
            if (p.getStatus() == ParcelStatus.DELIVERED && p.getTerminalAt() != null) {
                row(summary, "Delivered at", DATE_TIME.format(p.getTerminalAt().atZone(COLOMBO)));
            }
            document.add(summary);

            document.add(section("Pickup"));
            document.add(lines(p.getPickup().getContactName(), p.getPickup().getAddressLine1(),
                    p.getPickup().getAddressLine2(), place(p.getPickup().getCity(), p.getPickup().getDistrictName(),
                            p.getPickup().getPostalCode())));

            document.add(section("Drop-off"));
            document.add(lines(p.getDropoff().getContactName(), p.getDropoff().getAddressLine1(),
                    p.getDropoff().getAddressLine2(), place(p.getDropoff().getCity(),
                            p.getDropoff().getDistrictName(), p.getDropoff().getPostalCode())));

            document.add(section("Parcel"));
            PdfPTable parcel = table(2);
            row(parcel, "Weight", p.getWeightKg().toPlainString() + " kg");
            row(parcel, "Size", p.getSize().name());
            if (p.getDescription() != null && !p.getDescription().isBlank()) {
                row(parcel, "Description", p.getDescription());
            }
            document.add(parcel);

            document.add(section("Fee"));
            PdfPTable fee = table(2);
            row(fee, "Base fee", money(p.getCurrency(), p.getFeeBase()));
            row(fee, "Weight fee", money(p.getCurrency(), p.getFeeWeight()));
            row(fee, "Size surcharge", money(p.getCurrency(), p.getFeeSize()));
            row(fee, "Discount", money(p.getCurrency(), p.getFeeDiscount()));
            PdfPCell totalLabel = new PdfPCell(new Phrase("Total", BOLD));
            PdfPCell totalValue = new PdfPCell(new Phrase(money(p.getCurrency(), p.getFeeTotal()), BOLD));
            totalLabel.setBorder(PdfPCell.TOP);
            totalValue.setBorder(PdfPCell.TOP);
            totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            fee.addCell(totalLabel);
            fee.addCell(totalValue);
            document.add(fee);
        } catch (DocumentException e) {
            throw new IllegalStateException("Receipt could not be generated", e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return out.toByteArray();
    }

    /** Two decimals and the currency (SRS 1.1 formats). */
    static String money(String currency, BigDecimal amount) {
        return currency + " " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String place(String city, String district, String postalCode) {
        String text = city + ", " + district;
        return postalCode == null ? text : text + " " + postalCode;
    }

    private static Paragraph section(String title) {
        Paragraph paragraph = new Paragraph(title, HEADING);
        paragraph.setSpacingBefore(14);
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    private static Paragraph lines(String... values) {
        List<String> present = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                present.add(value);
            }
        }
        return new Paragraph(String.join("\n", present), TEXT);
    }

    private static Paragraph spacer() {
        Paragraph paragraph = new Paragraph(" ", TEXT);
        paragraph.setSpacingAfter(4);
        return paragraph;
    }

    private static PdfPTable table(int columns) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        return table;
    }

    private static void row(PdfPTable table, String label, String value) {
        PdfPCell left = new PdfPCell(new Phrase(label, BOLD));
        PdfPCell right = new PdfPCell(new Phrase(value, TEXT));
        left.setBorder(PdfPCell.NO_BORDER);
        right.setBorder(PdfPCell.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(left);
        table.addCell(right);
    }

    /** The PDF and the tracking number for its file name. */
    public record Receipt(String trackingNumber, byte[] pdf) {
    }
}
