package com.tea.factory.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.Data;

/**
 * 按茶类统计的数量时间线汇总（与工单详情同一条数量时间线聚合，不使用任何估算产出）。
 * 口径：
 *  · 总投入：批次原投料基准之和；
 *  · 最终产量：末道【已确认】交接产出之和（未完工批次不计）；
 *  · 累计损耗：已确认交接（含旧记录派生）登记过的损耗之和；
 *  · 当前在制量：在制批次上道已确认产出之和。
 * 恒等式：总投入 = 最终产量 + 累计损耗 + 当前在制量 + 未登记差。
 */
@Data
public class TeaTypeYieldDTO {

    private String teaType;
    private String teaTypeName;

    private Long batchCount;
    private Long doneBatchCount;
    private Long processingBatchCount;

    private BigDecimal inputQtyKg;
    private BigDecimal finalOutputQtyKg;
    private BigDecimal accumulatedLossQtyKg;
    private BigDecimal workInProcessQtyKg;
    private BigDecimal unrecordedGapQtyKg;
    private BigDecimal reconcileGapQtyKg;

    /** 产出率 %＝最终产量/总投入*100（在制批次只投未产，故产出率随完工进度上升） */
    private BigDecimal yieldRatePct;

    /** 综合损耗率 %＝累计损耗/（已产出+累计损耗）*100，反映已经过磅环节的损耗强度 */
    private BigDecimal lossRatePct;

    /** 对平状态：BALANCED / PARTIAL（旧批次部分重量未登记） */
    private String reconcileStatus;

    public BigDecimal getYieldRatePct() {
        if (inputQtyKg == null || inputQtyKg.signum() == 0 || finalOutputQtyKg == null) {
            return BigDecimal.ZERO;
        }
        return finalOutputQtyKg.multiply(BigDecimal.valueOf(100))
                .divide(inputQtyKg, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getLossRatePct() {
        BigDecimal accounted = nz(finalOutputQtyKg).add(nz(accumulatedLossQtyKg)).add(nz(workInProcessQtyKg));
        if (accounted.signum() == 0 || accumulatedLossQtyKg == null) {
            return BigDecimal.ZERO;
        }
        return accumulatedLossQtyKg.multiply(BigDecimal.valueOf(100))
                .divide(accounted, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
