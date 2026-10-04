package com.tea.factory.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 退回后班组更正重交：只改重量与损耗说明，不允许改工艺参数（工艺记录在结束工序时已定稿）。
 */
@Data
public class QtyCorrectRequest {

    /** 更正后的实际产出重量 kg */
    @NotNull(message = "请填写更正后的实际产出重量")
    private BigDecimal actualOutputQtyKg;

    /** 更正后的损耗原因 / 说明 */
    @NotBlank(message = "请填写更正后的损耗原因与说明")
    private String lossReason;

    /** 交接人（可保持原交接人或改填） */
    @NotBlank(message = "请填写交接人")
    private String handoverTo;

    /** 更正操作人（班组） */
    @NotBlank(message = "操作人不能为空")
    private String operator;
}
