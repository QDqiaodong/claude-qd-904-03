package com.tea.factory.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 设备列表行：设备原始状态 + 当前交接单口径的“能否开工”结论。
 * 旧设备（没有任何交接单）沿用原状态，不参与清洗锁定，legacy=true 时前端标注“无交接单，沿用原状态”。
 */
@Data
public class EquipmentBoardDTO {

    private Long id;
    private String equipCode;
    private String equipName;
    private String equipType;
    private String model;
    private BigDecimal capacityKg;
    private BigDecimal currentTempC;
    /** 设备原始状态 RUNNING / IDLE / CLEANING / PENDING_CONFIRM / FAULT */
    private String status;
    private String statusName;
    private String workshop;
    private String currentBatchNo;

    /** 该设备是否存在交接单（没有的是旧设备，沿用原状态、不锁死） */
    private boolean hasHandover;
    /** 是否无任何交接单的旧设备 */
    private boolean legacy;

    /** 当前未闭环交接单号（无则空） */
    private String activeHandoverNo;
    /** 当前交接单状态 */
    private String handoverStatus;
    private String handoverStatusName;
    /** 最近批次（交接单带出） */
    private String handoverBatchNo;

    /** 综合结论：能否用于下一批次开工 */
    private boolean availableForNextBatch;
    /** 不可开工原因：清洗未开始 / 清洗进行中 / 等待负责人确认 / 故障冻结 / 保留结果转维修 / 正在加工批次 / 设备故障 */
    private String unavailableReason;
}
