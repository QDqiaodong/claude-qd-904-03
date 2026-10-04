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
 * 制茶设备：杀青机 / 揉捻机 / 发酵室 / 烘干机 / 提香机。
 */
@Data
@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 设备编号，如 SQ-01 */
    @Column(name = "equip_code", nullable = false, length = 32, unique = true)
    private String equipCode;

    @Column(name = "equip_name", length = 64)
    private String equipName;

    /** 杀青机 / 揉捻机 / 发酵室 / 烘干机 / 提香机 */
    @Column(name = "equip_type", length = 16)
    private String equipType;

    @Column(name = "model", length = 64)
    private String model;

    /** 容量 kg */
    @Column(name = "capacity_kg", precision = 8, scale = 1)
    private BigDecimal capacityKg;

    /** 当前温度 ℃ */
    @Column(name = "current_temp_c", precision = 6, scale = 1)
    private BigDecimal currentTempC;

    /** RUNNING / IDLE / CLEANING / FAULT */
    @Column(name = "status", length = 16)
    private String status;

    /** 所在车间 */
    @Column(name = "workshop", length = 32)
    private String workshop;

    /** 当前批次 */
    @Column(name = "current_batch_no", length = 32)
    private String currentBatchNo;

    @Column(name = "last_maintain_at")
    private LocalDateTime lastMaintainAt;
}
