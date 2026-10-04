package com.tea.factory.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 工序执行记录：某批次某一道工序的开始 / 结束与操作参数、异常。
 */
@Data
@Entity
@Table(name = "process_step_record")
public class ProcessStepRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "batch_no", nullable = false, length = 32)
    private String batchNo;

    @Column(name = "tea_type", length = 16)
    private String teaType;

    /** 工序键：SPREAD / FIX / ROLL / FERMENT / DRY / PICK / MAKING */
    @Column(name = "step_name", nullable = false, length = 16)
    private String stepName;

    /** 工序在序列中的下标 */
    @Column(name = "step_index")
    private Integer stepIndex;

    /** 使用设备编号 */
    @Column(name = "equipment_code", length = 32)
    private String equipmentCode;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 操作参数：温度 ℃（杀青 / 发酵 / 干燥毛火） */
    @Column(name = "param_temp_c", precision = 6, scale = 1)
    private BigDecimal paramTempC;

    /** 操作参数：第二段温度 ℃（干燥足火） */
    @Column(name = "param_temp2_c", precision = 6, scale = 1)
    private BigDecimal paramTemp2C;

    /** 操作参数：时长 min */
    @Column(name = "param_minutes")
    private Integer paramMinutes;

    /** 操作参数：第二段时长 min */
    @Column(name = "param_minutes2")
    private Integer paramMinutes2;

    /** 操作参数：转速 r/min */
    @Column(name = "param_rpm")
    private Integer paramRpm;

    /** 操作参数：压力 kg */
    @Column(name = "param_pressure", precision = 5, scale = 1)
    private BigDecimal paramPressure;

    /** 操作参数：投叶量 kg */
    @Column(name = "param_leaf_qty", precision = 10, scale = 2)
    private BigDecimal paramLeafQty;

    /** 操作参数：相对湿度 % */
    @Column(name = "humidity_pct", precision = 5, scale = 1)
    private BigDecimal humidityPct;

    /** 参数速记（分段温度曲线等） */
    @Column(name = "param_profile", length = 128)
    private String paramProfile;

    @Column(name = "operator", length = 32)
    private String operator;

    /** 异常记录 */
    @Column(name = "abnormal_note", length = 255)
    private String abnormalNote;

    /** RUNNING / DONE */
    @Column(name = "status", length = 16)
    private String status;
}
