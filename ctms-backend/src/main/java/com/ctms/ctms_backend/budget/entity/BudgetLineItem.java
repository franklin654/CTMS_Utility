package com.ctms.ctms_backend.budget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "budget_line_item")
@Getter
@Setter
@NoArgsConstructor
public class BudgetLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_version_id", nullable = false)
    private BudgetVersion budgetVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_category", nullable = false, length = 30)
    private CostCategory costCategory;

    @Column(name = "planned_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal plannedAmount;

    @Column(nullable = false, length = 3)
    private String currency;
}
