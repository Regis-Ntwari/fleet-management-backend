package com.limoz.fleet.finance.service;

import com.limoz.fleet.finance.domain.Currencies;
import com.limoz.fleet.finance.repository.BookingBillingReader;

import com.limoz.fleet.config.CacheConfig;
import com.limoz.fleet.finance.dto.FinanceSummaryResponse;
import com.limoz.fleet.settings.domain.SettingKeys;
import com.limoz.fleet.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Consolidated finance KPIs (Bills & Invoices / Payments / Expenses screens) in RWF. USD documents are
 * converted at {@code finance.usd_to_rwf_rate}. Invoiced, payment and expense figures cover the period;
 * outstanding and overdue balances are the position as of today.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinanceSummaryService {

    private final JdbcClient jdbcClient;
    private final BookingBillingReader bookingReader;
    private final SettingsService settingsService;
    private final Clock clock;
    private final ZoneId operationalZone;

    @Cacheable(cacheNames = CacheConfig.DASHBOARD, key = "'financeSummary:' + #from + ':' + #to")
    public FinanceSummaryResponse summary(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        BigDecimal rate = settingsService.getDecimal(SettingKeys.USD_TO_RWF_RATE);

        record Invoiced(long count, BigDecimal amount) {}
        Invoiced invoiced = jdbcClient.sql("select count(*) as cnt, coalesce(sum(case when currency = 'USD' then total_amount * :rate else total_amount end), 0) as total "
                        + "from invoices where status not in ('DRAFT','CANCELLED') and issue_date between :from and :to")
                .param("rate", rate).param("from", start).param("to", end)
                .query((rs, i) -> new Invoiced(rs.getLong("cnt"), rs.getBigDecimal("total"))).single();

        BigDecimal paidInPeriod = jdbcClient.sql("select coalesce(sum(case when currency = 'USD' then amount * :rate else amount end), 0) "
                        + "from payments where direction = 'IN' and reversed = false and invoice_id is not null and paid_at >= :from and paid_at < :to")
                .param("rate", rate)
                .param("from", start.atStartOfDay(operationalZone).toInstant().atOffset(ZoneOffset.UTC))
                .param("to", end.plusDays(1).atStartOfDay(operationalZone).toInstant().atOffset(ZoneOffset.UTC))
                .query(BigDecimal.class).single();

        record Position(long outstandingCount, BigDecimal outstanding, long overdueCount, BigDecimal overdue) {}
        Position position = jdbcClient.sql("select count(*) as outstanding_cnt, "
                        + "coalesce(sum(case when currency = 'USD' then (total_amount - amount_paid) * :rate else total_amount - amount_paid end), 0) as outstanding, "
                        + "count(*) filter (where status = 'OVERDUE' or due_date < :today) as overdue_cnt, "
                        + "coalesce(sum(case when currency = 'USD' then (total_amount - amount_paid) * :rate else total_amount - amount_paid end) "
                        + "  filter (where status = 'OVERDUE' or due_date < :today), 0) as overdue "
                        + "from invoices where status in ('ISSUED','PARTIALLY_PAID','OVERDUE')")
                .param("rate", rate).param("today", today)
                .query((rs, i) -> new Position(rs.getLong("outstanding_cnt"), rs.getBigDecimal("outstanding"), rs.getLong("overdue_cnt"),
                        rs.getBigDecimal("overdue"))).single();

        record Ledger(long count, BigDecimal in, BigDecimal out) {}
        Ledger ledger = jdbcClient.sql("select count(*) as cnt, "
                        + "coalesce(sum(case when currency = 'USD' then amount * :rate else amount end) filter (where direction = 'IN'), 0) as total_in, "
                        + "coalesce(sum(case when currency = 'USD' then amount * :rate else amount end) filter (where direction = 'OUT'), 0) as total_out "
                        + "from payments where reversed = false and paid_at >= :from and paid_at < :to")
                .param("rate", rate)
                .param("from", start.atStartOfDay(operationalZone).toInstant().atOffset(ZoneOffset.UTC))
                .param("to", end.plusDays(1).atStartOfDay(operationalZone).toInstant().atOffset(ZoneOffset.UTC))
                .query((rs, i) -> new Ledger(rs.getLong("cnt"), rs.getBigDecimal("total_in"), rs.getBigDecimal("total_out"))).single();

        List<FinanceSummaryResponse.CategoryAmount> expenses = jdbcClient.sql(
                        "select c.id, c.code, c.name, count(*) as cnt, "
                        + "coalesce(sum(case when e.currency = 'USD' then e.amount * :rate else e.amount end), 0) as total "
                        + "from expenses e join expense_categories c on c.id = e.category_id "
                        + "where e.status <> 'REJECTED' and e.incurred_on between :from and :to group by c.id, c.code, c.name order by total desc")
                .param("rate", rate).param("from", start).param("to", end)
                .query((rs, i) -> new FinanceSummaryResponse.CategoryAmount(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getLong("cnt"), rs.getBigDecimal("total")))
                .list();
        BigDecimal expensesTotal = expenses.stream().map(FinanceSummaryResponse.CategoryAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinanceSummaryResponse(start, end, Currencies.RWF, rate, invoiced.count(), invoiced.amount(), paidInPeriod,
                position.outstandingCount(), position.outstanding(), position.overdueCount(), position.overdue(), bookingReader.readyToBillCount(),
                ledger.count(), ledger.in(), ledger.out(), expensesTotal, expenses);
    }
}
