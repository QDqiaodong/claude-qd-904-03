package com.tea.factory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 数量时间线节点：一道工序一行，工单详情与产出率统计共用同一条数量时间线。
 * 既有“工艺参数视角”（process_step_record），也有“数量交接视角”（本节点），
 * 下道工序的可用在制量、超限复核结论都在这一行上可查。
 */
@Data
public class StepQtyNodeDTO {

    /** 工序下标 */
    private Integer stepIndex;

    /** 工序键 */
    private String stepName;

    /** 工序显示名（红茶发酵 / 普洱渥堆等） */
    private String stepNameCn;

    /** 交接单号（未交接为空） */
    private String handoverNo;

    /** 本步投入 kg＝上道已确认产出，首道＝批次投料 */
    private BigDecimal inputQtyKg;

    /** 实际产出重量 kg（旧记录未过磅为空） */
    private BigDecimal outputQtyKg;

    /** 本步损耗 kg（产出未登记为空，绝不按 0 计） */
    private BigDecimal lossQtyKg;

    /** 损耗率 % */
    private BigDecimal lossPct;

    private String lossReason;

    private String handoverTo;

    private String submittedBy;

    private LocalDateTime submittedAt;

    /** 正常损耗范围 % */
    private BigDecimal limitMinPct;
    private BigDecimal limitMaxPct;

    /** 是否超正常范围 */
    private Boolean overLimit;

    /**
     * 节点状态：NOT_STARTED 未开始 / RUNNING 执行中 /
     * CONFIRMED 已确认 / PENDING_REVIEW 待复核 / RETURNED 已退回更正 /
     * NOT_HANDED 已结束但未登记数量（旧口径推进）/ LEGACY 旧记录派生
     */
    private String status;

    private String statusName;

    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private String returnedNote;
    private Integer correctedCount;

    /** 旧记录派生口径说明 */
    private String legacyNote;
}
