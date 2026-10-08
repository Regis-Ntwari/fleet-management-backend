package com.limoz.fleet.finance.repository;

import com.limoz.fleet.finance.dto.ReadyToBillResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Read-only projection of the billing-relevant parts of a booking (header, requested lines, extra charges
 * and deployment vouchers). The Booking aggregate is owned by the dispatch module, so invoicing reads the
 * tables directly instead of depending on that module's entities.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingBillingReader {

    public record BookingHeader(Long id, String bookingNumber, Long customerId, String status, String currency, BigDecimal totalAmount,
                                LocalDate startDate, LocalDate endDate, Instant completedAt) {}

    public record BookingLineRow(Long id, String category, int quantity, String pricingType, LocalDate startDate, LocalDate endDate,
                                 BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record ExtraChargeRow(Long id, String description, BigDecimal amount) {}

    public record VoucherRow(Long id, String voucherNumber, String plateNumber, BigDecimal institutionAmount) {}

    private static final String READY_TO_BILL =
            "select b.id, b.booking_number, b.customer_id, c.name as customer_name, b.start_date, b.end_date, b.completed_at, b.currency, b.total_amount, "
            + "(select coalesce(sum(bl.quantity), 0) from booking_lines bl where bl.booking_id = b.id) as vehicle_count "
            + "from bookings b join customers c on c.id = b.customer_id "
            + "where b.status in ('READY_FOR_BILLING','COMPLETED') "
            + "and not exists (select 1 from invoices i where i.booking_id = b.id and i.status <> 'CANCELLED') ";

    private final JdbcClient jdbcClient;

    public Optional<BookingHeader> header(Long bookingId) {
        return jdbcClient.sql("select id, booking_number, customer_id, status, currency, total_amount, start_date, end_date, completed_at "
                        + "from bookings where id = :id")
                .param("id", bookingId)
                .query((rs, i) -> new BookingHeader(rs.getLong("id"), rs.getString("booking_number"), rs.getLong("customer_id"),
                        rs.getString("status"), rs.getString("currency"), rs.getBigDecimal("total_amount"),
                        date(rs, "start_date"), date(rs, "end_date"), instant(rs, "completed_at")))
                .optional();
    }

    public List<BookingLineRow> lines(Long bookingId) {
        return jdbcClient.sql("select bl.id, vc.name as category, bl.quantity, bl.pricing_type, bl.start_date, bl.end_date, bl.unit_price, bl.line_total "
                        + "from booking_lines bl join vehicle_categories vc on vc.id = bl.category_id where bl.booking_id = :id order by bl.id")
                .param("id", bookingId)
                .query((rs, i) -> new BookingLineRow(rs.getLong("id"), rs.getString("category"), rs.getInt("quantity"), rs.getString("pricing_type"),
                        date(rs, "start_date"), date(rs, "end_date"), rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total")))
                .list();
    }

    public List<ExtraChargeRow> extraCharges(Long bookingId) {
        return jdbcClient.sql("select id, description, amount from booking_extra_charges where booking_id = :id order by id")
                .param("id", bookingId)
                .query((rs, i) -> new ExtraChargeRow(rs.getLong("id"), rs.getString("description"), rs.getBigDecimal("amount")))
                .list();
    }

    /** Non-cancelled deployment vouchers of the booking, each carrying the amount billed to the institution. */
    public List<VoucherRow> vouchers(Long bookingId) {
        return jdbcClient.sql("select dv.id, dv.voucher_number, v.plate_number, dv.institution_amount from deployment_vouchers dv "
                        + "join vehicles v on v.id = dv.vehicle_id where dv.booking_id = :id and dv.status <> 'CANCELLED' order by dv.id")
                .param("id", bookingId)
                .query((rs, i) -> new VoucherRow(rs.getLong("id"), rs.getString("voucher_number"), rs.getString("plate_number"),
                        rs.getBigDecimal("institution_amount")))
                .list();
    }

    /** Completed bookings (READY_FOR_BILLING / COMPLETED) that have no active invoice yet. */
    public List<ReadyToBillResponse> readyToBill() {
        return jdbcClient.sql(READY_TO_BILL + "order by b.end_date asc, b.id asc")
                .query((rs, i) -> new ReadyToBillResponse(rs.getLong("id"), rs.getString("booking_number"), rs.getLong("customer_id"),
                        rs.getString("customer_name"), date(rs, "start_date"), date(rs, "end_date"), instant(rs, "completed_at"),
                        rs.getLong("vehicle_count"), rs.getString("currency"), rs.getBigDecimal("total_amount")))
                .list();
    }

    public long readyToBillCount() {
        return jdbcClient.sql("select count(*) from (" + READY_TO_BILL + ") r").query(Long.class).single();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
