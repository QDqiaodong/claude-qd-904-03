package com.tea.factory.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 批次数量对账：无论新旧口径都必须能相互核对——
 * 总投入 = 最终产量 + 累计损耗 + 当前在制量（+ 未登记在制量差）。
 */
@Data
public class QtyBalanceDTO {

    /** 批次总投入 kg（原投料基准，全程不变） */
    private BigDecimal totalInputKg;

    /** 最终产量 kg（末道确认后有值） */
    private BigDecimal finalOutputKg;

    /** 累计已确认损耗 kg（产出已过磅的工序损耗之和；旧记录未过磅部分不计入） */
    private BigDecimal accumulatedLossKg;

    /** 当前可用在制量 kg＝上道已确认产出 */
    private BigDecimal workInProcessKg;

    /** 未登记重量的在制量差 kg（旧批次中间工序没称，单列而不是吞进损耗或按 0 处理） */
    private BigDecimal unrecordedGapKg;

    /** 核对差额 kg＝总投入 − 最终产量 − 累计损耗 − 当前在制量 − 未登记差（在容差内即 0） */
    private BigDecimal reconcileGapKg;

    /** BALANCED 对平 / UNBALANCED 对不平 / PARTIAL 部分登记（旧口径，差异已归入未登记差） */
    private String reconcileStatus;

    private String reconcileStatusName;

    /** 累计产出率 %＝最终产量/总投入*100 */
    private BigDecimal finalYieldRatePct;

    /** 数量口径：TRACKED 逐道实称 / LEGACY 旧批派生 / MIXED 旧批次中途起逐道实称 */
    private String qtyMode;
    private String qtyModeName;
}
