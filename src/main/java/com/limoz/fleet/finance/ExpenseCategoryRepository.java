package com.limoz.fleet.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findAllByOrderBySortOrderAscNameAsc();

    boolean existsByCodeIgnoreCase(String code);
}
