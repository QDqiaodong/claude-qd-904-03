package com.tea.factory.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 工序开始 / 结束的参数录入。不同工序用到的字段不同。
 */
@Data
public class StepRequest {

    /** 批次号 */
    private String batchNo;

    /** 工序键：SPREAD / MAKING / FIX / ROLL / FERMENT / DRY / PICK */
    private String stepName;

    /** 使用设备编号 */
    private String equipmentCode;

    /** 温度 ℃（杀青 / 发酵 / 干燥毛火） */
    private BigDecimal paramTempC;

    /** 第二段温度 ℃（干燥足火） */
    private BigDecimal paramTemp2C;

    /** 时长 min */
    private Integer paramMinutes;

    /** 第二段时长 min */
    private Integer paramMinutes2;

    /** 转速 r/min（揉捻） */
    private Integer paramRpm;

    /** 压力 kg（揉捻） */
    private BigDecimal paramPressure;

    /** 投叶量 kg（杀青） */
    private BigDecimal paramLeafQty;

    /** 相对湿度 %（发酵） */
    private BigDecimal humidityPct;

    /** 参数速记 */
    private String paramProfile;

    private String operator;

    /** 异常记录 */
    private String abnormalNote;

    /** 工序结束登记：实际产出重量 kg（过磅值；下道以此为可用在制量） */
    private BigDecimal actualOutputQtyKg;

    /** 工序结束登记：损耗原因 */
    private String lossReason;

    /** 工序结束登记：交接人（交给谁） */
    private String handoverTo;
}
