package com.tea.factory.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 设备清洗交接单：工序收尾自动生成，带出刚完成的批次与设备；
 * 清洗人员登记残留风险、清洗方式与起止时间，可标记清洗完成或报故障；
 * 负责人确认后设备才恢复可用。
 *
 * 生命周期（active_key 非空即视为未闭环，仍锁定开工）：
 *   PENDING 清洗未开始
 *   IN_PROGRESS 清洗进行中
 *   AWAIT_CONFIRM 清洗完成、等待负责人确认
 *   FAULT_FREEZE 清洗期间报障（本次清洗结论作废，立即冻结，修复后重新清洗）
 *   KEEP_AND_REPAIR 已确认后再报障（保留已确认结果并转维修，复验合格后恢复）
 *   CONFIRMED 负责人已确认，设备恢复可用
 */
@Data
@Entity
@Table(name = "cleaning_handover")
public class CleaningHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 交接单号 QX+日期+序号 */
    @Column(name = "handover_no", nullable = false, length = 32, unique = true)
    private String handoverNo;

    /** 设备编号 */
    @Column(name = "equip_code", nullable = false, length = 32)
    private String equipCode;

    /** 设备名称（交接单形成时快照，避免设备改名后责任单失真） */
    @Column(name = "equip_name", length = 64)
    private String equipName;

    @Column(name = "equip_type", length = 16)
    private String equipType;

    /** 刚完成的批次号 */
    @Column(name = "batch_no", length = 32)
    private String batchNo;

    /** 刚结束的工序键 SPREAD / FIX / ROLL ... */
    @Column(name = "step_name", length = 16)
    private String stepName;

    /** PENDING / IN_PROGRESS / AWAIT_CONFIRM / FAULT_FREEZE / KEEP_AND_REPAIR / CONFIRMED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * 未闭环标记：与数据库 STORED 生成列对应（未确认时等于 equip_code，CONFIRMED 后为 NULL）。
     * insertable/updatable=false：真值由 MySQL 生成列维护并承担唯一索引，JPA 只读取、不写入
     * （写入生成列会被数据库拒绝）；事务内状态流转时由服务层同步设置同值，保证内存判断一致。
     */
    @Column(name = "active_key", insertable = false, updatable = false)
    private String activeKey;

    /** 残留风险：低 / 中 / 高（高风险需在方式中说明专项处置） */
    @Column(name = "residual_risk", length = 16)
    private String residualRisk;

    /** 清洗方式：清水冲洗 / 食品级清洁剂 / 高温蒸汽 / 人工拆卸清扫 / 专项清洗 */
    @Column(name = "clean_method", length = 64)
    private String cleanMethod;

    /** 清洗开始时间（清洗人员填写） */
    @Column(name = "clean_start_time")
    private LocalDateTime cleanStartTime;

    /** 清洗结束时间（清洗人员填写） */
    @Column(name = "clean_end_time")
    private LocalDateTime cleanEndTime;

    /** 清洗人员 */
    @Column(name = "cleaner", length = 32)
    private String cleaner;

    /** 故障描述（报障时填写） */
    @Column(name = "fault_note", length = 255)
    private String faultNote;

    /** 故障发生时设备是否已被负责人确认可用（决定取舍策略） */
    @Column(name = "fault_after_confirm")
    private Boolean faultAfterConfirm;

    /** 取舍与开工影响说明：冲突时写明立即冻结 / 保留结果转维修及对下一批次开工的影响 */
    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    /** 维修情况说明（转维修后由维修 / 负责人补录） */
    @Column(name = "repair_note", length = 255)
    private String repairNote;

    /** 维修 / 复验人员 */
    @Column(name = "repairer", length = 32)
    private String repairer;

    /** 复验时间 */
    @Column(name = "repaired_at")
    private LocalDateTime repairedAt;

    /** 形成交接单的工序收尾操作人 */
    @Column(name = "created_by", length = 32)
    private String createdBy;

    /** 负责人 */
    @Column(name = "confirmed_by", length = 32)
    private String confirmedBy;

    /** 确认时间 */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 工序收尾形成交接单的时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最近一次状态变更时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
