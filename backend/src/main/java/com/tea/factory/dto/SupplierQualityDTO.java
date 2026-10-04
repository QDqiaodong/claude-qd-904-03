package com.tea.factory.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 按供应商汇总鲜叶验收质量（JPQL 投影，构造器参数个数与 JPQL 中 select new 一一对应）。
 */
public class SupplierQualityDTO {

    private final String supplierName;
    private final Long intakeCount;
    private final BigDecimal grossWeightKg;
    private final BigDecimal netWeightKg;
    private final BigDecimal avgMoisturePct;
    private final BigDecimal avgImpurityPct;
    private final BigDecimal deductWeightKg;
    private final BigDecimal settleAmount;

    public SupplierQualityDTO(String supplierName, Long intakeCount, BigDecimal grossWeightKg,
            BigDecimal netWeightKg, BigDecimal avgMoisturePct, BigDecimal avgImpurityPct,
            BigDecimal deductWeightKg, BigDecimal settleAmount) {
        this.supplierName = supplierName;
        this.intakeCount = intakeCount;
        this.grossWeightKg = grossWeightKg;
        this.netWeightKg = netWeightKg;
        this.avgMoisturePct = avgMoisturePct;
        this.avgImpurityPct = avgImpurityPct;
        this.deductWeightKg = deductWeightKg;
        this.settleAmount = settleAmount;
    }

    /**
     * avg() 被推导成 Double 时会投影到这个重载，字段含义与上面的构造器完全一致。
     */
    public SupplierQualityDTO(String supplierName, Long intakeCount, BigDecimal grossWeightKg,
            BigDecimal netWeightKg, Double avgMoisturePct, Double avgImpurityPct,
            BigDecimal deductWeightKg, BigDecimal settleAmount) {
        this(supplierName, intakeCount, grossWeightKg, netWeightKg,
                avgMoisturePct == null ? null : BigDecimal.valueOf(avgMoisturePct),
                avgImpurityPct == null ? null : BigDecimal.valueOf(avgImpurityPct),
                deductWeightKg, settleAmount);
    }

    /** 扣重比例（%），在 Java 侧算，避免 JPQL 里做除法 */
    public BigDecimal getDeductRatePct() {
        if (grossWeightKg == null || grossWeightKg.signum() == 0 || deductWeightKg == null) {
            return BigDecimal.ZERO;
        }
        return deductWeightKg.multiply(BigDecimal.valueOf(100)).divide(grossWeightKg, 2, RoundingMode.HALF_UP);
    }

    public String getSupplierName() {
        return supplierName;
    }

    public Long getIntakeCount() {
        return intakeCount;
    }

    public BigDecimal getGrossWeightKg() {
        return grossWeightKg;
    }

    public BigDecimal getNetWeightKg() {
        return netWeightKg;
    }

    public BigDecimal getAvgMoisturePct() {
        return avgMoisturePct;
    }

    public BigDecimal getAvgImpurityPct() {
        return avgImpurityPct;
    }

    public BigDecimal getDeductWeightKg() {
        return deductWeightKg;
    }

    public BigDecimal getSettleAmount() {
        return settleAmount;
    }
}
