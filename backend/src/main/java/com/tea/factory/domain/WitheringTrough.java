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
 * 摊青槽批次履历：每轮摊青一条记录（开槽新增、下槽转 DONE）。
 * 物理槽号 troughNo 可反复承接新批次；同一槽号至多一条在槽（WITHERING / READY）记录，
 * 由数据库 active_no 唯一索引兜底；DONE 记录作为历史保留，可按槽号 / 批次追溯。
 */
@Data
@Entity
@Table(name = "withering_trough")
public class WitheringTrough {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 槽号，如 TQ-01（物理槽位，可被多个历史批次复用；唯一占用由 active_no 索引保证） */
    @Column(name = "trough_no", nullable = false, length = 16)
    private String troughNo;

    /** 关联鲜叶进厂单 */
    @Column(name = "intake_no", length = 32)
    private String intakeNo;

    /** 关联制茶工单批次号 */
    @Column(name = "batch_no", length = 32)
    private String batchNo;

    @Column(name = "tea_type", length = 16)
    private String teaType;

    /** 摊青方式：日光晒青 / 室内萎凋 */
    @Column(name = "wither_method", length = 16)
    private String witherMethod;

    /** 摊叶厚度 cm */
    @Column(name = "leaf_thickness_cm", precision = 5, scale = 2)
    private BigDecimal leafThicknessCm;

    /** 摊青时长 h */
    @Column(name = "wither_hours", precision = 5, scale = 1)
    private BigDecimal witherHours;

    /** 环境温度 ℃ */
    @Column(name = "temp_c", precision = 5, scale = 1)
    private BigDecimal tempC;

    /** 环境相对湿度 % */
    @Column(name = "humidity_pct", precision = 5, scale = 1)
    private BigDecimal humidityPct;

    /** 当前失水率 % */
    @Column(name = "current_loss_pct", precision = 5, scale = 2)
    private BigDecimal currentLossPct;

    /** 目标失水率 % */
    @Column(name = "target_loss_pct", precision = 5, scale = 2)
    private BigDecimal targetLossPct;

    /** WITHERING 摊青中 / READY 可杀青 / DONE 已下槽（历史） */
    @Column(name = "status", length = 16)
    private String status;

    /** 在槽鲜叶重量 kg */
    @Column(name = "leaf_weight_kg", precision = 10, scale = 2)
    private BigDecimal leafWeightKg;

    /** 开槽时间（本轮摊青开始） */
    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    /** 下槽时间（达标放行后写入，NULL 表示仍在槽） */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
