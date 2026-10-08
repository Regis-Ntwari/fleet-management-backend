package com.limoz.fleet.booking;

import com.limoz.fleet.booking.dto.BookingLineRequest;
import com.limoz.fleet.booking.dto.BookingRequest;
import com.limoz.fleet.booking.dto.BookingResponse;
import com.limoz.fleet.customer.CustomerService;
import com.limoz.fleet.customer.CustomerType;
import com.limoz.fleet.customer.dto.CustomerRequest;
import com.limoz.fleet.customer.dto.CustomerResponse;
import com.limoz.fleet.document.DocumentService;
import com.limoz.fleet.document.DocumentTypeRepository;
import com.limoz.fleet.document.dto.DocumentRequest;
import com.limoz.fleet.support.TestData;
import com.limoz.fleet.vehicle.VehicleCategoryRepository;
import com.limoz.fleet.vehicle.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixtures for booking / dispatch tests: clients, dispatch-compliant vehicles and bookings. */
@Component
@RequiredArgsConstructor
public class BookingTestData {

    public static final BigDecimal DAY_RATE = new BigDecimal("160000");
    private static final List<String> REQUIRED_DOCUMENT_CODES = List.of("INSURANCE", "INSPECTION", "ROAD_LICENCE", "RURA_PERMIT");
    private static final AtomicInteger SEQ = new AtomicInteger(500);

    private final TestData data;
    private final CustomerService customerService;
    private final DocumentService documentService;
    private final DocumentTypeRepository documentTypeRepository;
    private final VehicleCategoryRepository categoryRepository;
    private final BookingService bookingService;

    /** The shared vehicle / driver fixtures. */
    public TestData data() {
        return data;
    }

    public CustomerResponse customer() {
        int n = SEQ.incrementAndGet();
        return customerService.create(new CustomerRequest("CL" + n, "Client " + n + " Ltd", CustomerType.CORPORATE, "10" + n + "00",
                "Contact " + n, "client" + n + "@test.limoz.rw", "+2507880" + n, "KN 4 Ave", "Kigali", "Rwanda", null, null, true, null));
    }

    public Long minibusCategoryId() {
        return data.anyCategoryId();
    }

    public Long coasterCategoryId() {
        return categoryRepository.findByCodeIgnoreCase("COASTER").orElseThrow().getId();
    }

    /** A vehicle holding every document required for dispatch, valid for a year. */
    public VehicleResponse compliantVehicle() {
        VehicleResponse vehicle = data.vehicle();
        for (String code : REQUIRED_DOCUMENT_CODES) {
            Long typeId = documentTypeRepository.findByCodeIgnoreCase(code).orElseThrow().getId();
            documentService.addVehicleDocument(vehicle.id(), new DocumentRequest(typeId, code + "-" + vehicle.id(), "Issuer",
                    LocalDate.now().minusMonths(1), LocalDate.now().plusYears(1), null, null, null));
        }
        return vehicle;
    }

    public BookingLineRequest line(Long categoryId, int quantity, PricingType pricingType, LocalDate start, LocalDate end, BigDecimal unitPrice) {
        return new BookingLineRequest(categoryId, null, quantity, pricingType, start, end, unitPrice, null);
    }

    public BookingRequest request(Long customerId, Boolean confirm, List<BookingLineRequest> lines) {
        return new BookingRequest(customerId, null, "Ops contact", "+250788111222", null, ServiceType.CHARTER, "Kigali HQ", "Musanze",
                null, null, 12, "RWF", BookingSource.INTERNAL, "Test booking", lines, confirm);
    }

    /** A confirmed single-line minibus booking of {@code quantity} vehicles for [start, end] at the daily rate. */
    public BookingResponse confirmedBooking(Long customerId, int quantity, LocalDate start, LocalDate end) {
        return bookingService.create(request(customerId, true,
                List.of(line(minibusCategoryId(), quantity, PricingType.FULL_DAY, start, end, DAY_RATE))));
    }
}
