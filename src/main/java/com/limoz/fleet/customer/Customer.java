package com.limoz.fleet.customer;

import com.limoz.fleet.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Client / corporate account LIMOZ provides transport for. */
@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends BaseEntity {

    @Column(name = "customer_code", length = 20)
    private String customerCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType = CustomerType.CORPORATE;

    @Column(length = 30)
    private String tin;

    @Column(name = "contact_person", length = 120)
    private String contactPerson;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String address;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String country = "Rwanda";

    @Column(name = "account_manager_user_id")
    private Long accountManagerUserId;

    @Column(name = "credit_limit", precision = 16, scale = 2)
    private BigDecimal creditLimit;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String notes;
}
