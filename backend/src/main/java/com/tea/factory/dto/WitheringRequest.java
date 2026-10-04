package com.tea.factory.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 摊青槽登记：开槽登记与环境温湿度 / 失水率更新。
 */
@Data
public class WitheringRequest {

    /** 槽号（更新时必填） */
    private String troughNo;

    /** 关联的鲜叶进厂单号 */
    private String intakeNo;

    /** 关联的制茶工单批次号 */
    private String batchNo;

    private String teaType;

    /** 日光晒青 / 室内萎凋 */
    private String witherMethod;

    /** 摊叶厚度 cm */
    private BigDecimal leafThicknessCm;

    /** 摊青时长 h */
    private BigDecimal witherHours;

    /** 环境温度 ℃ */
    private BigDecimal tempC;

    /** 环境相对湿度 % */
    private BigDecimal humidityPct;

    /** 当前失水率 % */
    private BigDecimal currentLossPct;

    /** 在槽鲜叶重量 kg */
    private BigDecimal leafWeightKg;
}
