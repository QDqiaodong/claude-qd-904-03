package com.tea.factory.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 新建制茶工单。批次号由后端自定义生成器生成，前端不用传。
 */
@Data
public class OrderCreateRequest {

    /** GREEN / RED / OOLONG / WHITE / PUER */
    private String teaType;

    @NotNull(message = "投料量不能为空")
    @DecimalMin(value = "0.01", message = "投料量必须大于 0")
    private BigDecimal inputQtyKg;

    /** 来源摊青槽号 */
    private String sourceTroughNo;

    /** 制茶师傅 */
    private String operator;
}
