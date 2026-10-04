package com.tea.factory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 鲜叶进厂验收录入。单号由后端自定义生成器生成，前端不用传。
 */
@Data
public class IntakeRequest {

    @NotBlank(message = "供应商 / 茶农不能为空")
    private String supplierName;

    private String plotCode;

    private LocalDate pickDate;

    private String leafGrade;

    @NotNull(message = "毛重不能为空")
    @DecimalMin(value = "0.01", message = "毛重必须大于 0")
    private BigDecimal grossWeightKg;

    /** 含水量 % */
    private BigDecimal moisturePct;

    /** 杂质率 % */
    private BigDecimal impurityPct;

    private String inspector;
}
