package com.tea.factory.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.tea.factory.generator.IntakeNo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 鲜叶进厂验收单。
 * 主键 intakeNo 由自定义 Hibernate 生成器自动生成（XY + 日期 + 当日序号）。
 */
@Data
@Entity
@Table(name = "leaf_intake")
public class LeafIntake {

    @Id
    @GeneratedValue
    @IntakeNo
    @Column(name = "intake_no", nullable = false, length = 32)
    private String intakeNo;

    /** 供应商 / 茶农 */
    @Column(name = "supplier_name", nullable = false, length = 64)
    private String supplierName;

    /** 采摘地块编号 */
    @Column(name = "plot_code", length = 32)
    private String plotCode;

    /** 采摘日期 */
    @Column(name = "pick_date")
    private LocalDate pickDate;

    /** 鲜叶等级：单芽 / 一芽一叶 / 一芽二叶 / 对夹叶 */
    @Column(name = "leaf_grade", length = 16)
    private String leafGrade;

    /** 毛重 kg */
    @Column(name = "gross_weight_kg", precision = 10, scale = 2)
    private BigDecimal grossWeightKg;

    /** 含水量 % */
    @Column(name = "moisture_pct", precision = 5, scale = 2)
    private BigDecimal moisturePct;

    /** 杂质率 % */
    @Column(name = "impurity_pct", precision = 5, scale = 2)
    private BigDecimal impurityPct;

    /** 验收员 */
    @Column(name = "inspector", length = 32)
    private String inspector;

    /** 扣重 kg */
    @Column(name = "deduct_weight_kg", precision = 10, scale = 2)
    private BigDecimal deductWeightKg;

    /** 扣重比例 % */
    @Column(name = "deduct_rate_pct", precision = 5, scale = 2)
    private BigDecimal deductRatePct;

    /** 扣重原因 */
    @Column(name = "deduct_reason", length = 128)
    private String deductReason;

    /** 扣重后重量 kg */
    @Column(name = "net_weight_kg", precision = 10, scale = 2)
    private BigDecimal netWeightKg;

    /** 等级单价 元/kg */
    @Column(name = "unit_price", precision = 8, scale = 2)
    private BigDecimal unitPrice;

    /** 结算价 元 */
    @Column(name = "settle_price", precision = 12, scale = 2)
    private BigDecimal settlePrice;

    /** ACCEPTED / DEDUCTED / REJECTED */
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
