package com.limoz.fleet.customer;

import com.limoz.fleet.customer.dto.CustomerResponse;
import com.limoz.fleet.customer.dto.CustomerSummary;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getCustomerCode(), c.getName(), c.getCustomerType(), c.getTin(), c.getContactPerson(),
                c.getEmail(), c.getPhone(), c.getAddress(), c.getCity(), c.getCountry(), c.getAccountManagerUserId(), c.getCreditLimit(),
                c.isActive(), c.getNotes(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public CustomerSummary toSummary(Customer c) {
        return c == null ? null : new CustomerSummary(c.getId(), c.getCustomerCode(), c.getName(), c.getTin(), c.getPhone());
    }
}
