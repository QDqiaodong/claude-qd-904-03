package com.tea.factory.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 清洗交接台操作请求：开始 / 完成 / 报障 / 确认 / 重新清洗 / 复验 共用，按操作取用字段。
 */
@Data
public class CleaningActionRequest {

    /** 操作人（清洗员 / 负责人 / 维修员） */
    @NotBlank(message = "操作人不能为空")
    private String operator;

    /** 残留风险：低 / 中 / 高 */
    private String residualRisk;

    /** 清洗方式：清水冲洗 / 食品级清洁剂 / 高温蒸汽 / 人工拆卸清扫 / 专项清洗 */
    private String cleanMethod;

    /** 清洗开始时间 */
    private LocalDateTime cleanStartTime;

    /** 清洗结束时间 */
    private LocalDateTime cleanEndTime;

    /** 故障描述 */
    private String faultNote;

    /** 维修 / 复验情况说明 */
    private String repairNote;
}
