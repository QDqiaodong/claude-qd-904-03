package com.tea.factory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 损耗待复核看板行：超限待复核的交接单 + 复核退回待班组更正的交接单。
 */
@Data
public class PendingReviewDTO {

    private String handoverNo;
    private String batchNo;
    private String teaType;
    private String teaTypeName;
    private String stepName;
    private String stepNameCn;
    private Integer stepIndex;

    private BigDecimal inputQtyKg;
    private BigDecimal outputQtyKg;
    private BigDecimal lossQtyKg;
    private BigDecimal lossPct;
    private BigDecimal limitMinPct;
    private BigDecimal limitMaxPct;
    private Boolean overLimit;

    private String lossReason;
    private String handoverTo;
    private String submittedBy;
    private LocalDateTime submittedAt;

    /** PENDING_REVIEW / RETURNED */
    private String status;
    private String statusName;
    private String returnedNote;
    private Integer correctedCount;
}
