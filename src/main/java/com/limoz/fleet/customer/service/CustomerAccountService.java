package com.limoz.fleet.customer.service;

import com.limoz.fleet.customer.domain.CommitmentStatus;
import com.limoz.fleet.customer.domain.Customer;
import com.limoz.fleet.customer.domain.PurchaseOrderStatus;
import com.limoz.fleet.customer.repository.CommitmentRepository;
import com.limoz.fleet.customer.repository.PurchaseOrderRepository;

import com.limoz.fleet.customer.dto.CustomerAccountSummary;
import com.limoz.fleet.finance.domain.Currencies;
import com.limoz.fleet.settings.domain.SettingKeys;
import com.limoz.fleet.settings.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;

/**
 * Read-only account position of a client (bookings, contracts, billing and payments). Bookings and
 * invoices belong to other modules, so the figures are aggregated directly on the tables.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerAccountService {

    private static final String ACTIVE_BOOKINGS =
            "select count(*) from bookings where customer_id = :customerId and status not in ('DRAFT','COMPLETED','CANCELLED')";

    private static final String BILLING =
            "select count(*) as invoices, "
            + "coalesce(sum(case when currency = 'USD' then total_amount * :rate else total_amount end), 0) as billed, "
            + "coalesce(sum(case when currency = 'USD' then amount_paid * :rate else amount_paid end), 0) as paid, "
            + "count(*) filter (where status = 'OVERDUE' or (status in ('ISSUED','PARTIALLY_PAID') and due_date < :today)) as overdue "
            + "from invoices where customer_id = :customerId and status not in ('DRAFT','CANCELLED')";

    private final JdbcClient jdbcClient;
    private final CustomerService customerService;
    private final CommitmentRepository commitmentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SettingsService settingsService;
    private final Clock clock;

    public CustomerAccountSummary summary(Long customerId) {
        Customer customer = customerService.load(customerId);
        BigDecimal rate = settingsService.getDecimal(SettingKeys.USD_TO_RWF_RATE);
        long activeBookings = jdbcClient.sql(ACTIVE_BOOKINGS).param("customerId", customerId).query(Long.class).single();
        long commitments = commitmentRepository.countByCustomerIdAndStatusNot(customerId, CommitmentStatus.CANCELLED);
        long openOrders = purchaseOrderRepository.countByCustomerIdAndStatusIn(customerId,
                EnumSet.of(PurchaseOrderStatus.OPEN, PurchaseOrderStatus.PART_INVOICED));
        return jdbcClient.sql(BILLING)
                .param("customerId", customerId)
                .param("rate", rate)
                .param("today", LocalDate.now(clock))
                .query((rs, i) -> {
                    BigDecimal billed = rs.getBigDecimal("billed");
                    BigDecimal paid = rs.getBigDecimal("paid");
                    return new CustomerAccountSummary(customer.getId(), customer.getName(), activeBookings, commitments, openOrders,
                            rs.getLong("invoices"), billed, paid, billed.subtract(paid), rs.getLong("overdue"), Currencies.RWF);
                })
                .single();
    }
}
