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
 * 工序在制量交接单：每道工序结束时登记实际产出重量、损耗原因与交接人。
 *
 * 口径取舍：采用「链式实际产出」——下道工序的可用在制量等于上道【已确认】交接产出，
 * 同时保留批次原投料基准（tea_process_order.input_qty_kg 全程不变），损耗按
 * “本步投入 − 本步产出”派生记账，使 总投入 = 最终产量 + 累计损耗 + 在制量 恒可核对。
 *
 * 同一批次同一工序唯一（uk_qty_batch_step）：退回后是更正同一张单，不会产生第二张有效单。
 * status：CONFIRMED 已确认（范围内自动确认或复核接受）/ PENDING_REVIEW 超限待复核 /
 *         RETURNED 已退回待班组更正 / LEGACY 旧工序记录派生（只解释历史，不参与复核、不锁批次）。
 */
@Data
@Entity
@Table(name = "step_qty_handover")
public class StepQtyHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "handover_no", nullable = false, length = 32)
    private String handoverNo;

    @Column(name = "batch_no", nullable = false, length = 32)
    private String batchNo;

    @Column(name = "tea_type", length = 16)
    private String teaType;

    /** 工序键：SPREAD / MAKING / FIX / ROLL / FERMENT / DRY / PICK */
    @Column(name = "step_name", nullable = false, length = 16)
    private String stepName;

    @Column(name = "step_index", nullable = false)
    private Integer stepIndex;

    /** 本步投入 kg＝上道已确认产出，首道＝批次投料（提交时锁定快照） */
    @Column(name = "input_qty_kg", precision = 10, scale = 2)
    private BigDecimal inputQtyKg;

    /** 实际产出重量 kg（旧记录为空表示当时未过磅） */
    @Column(name = "output_qty_kg", precision = 10, scale = 2)
    private BigDecimal outputQtyKg;

    /** 损耗量 kg＝投入−产出（派生） */
    @Column(name = "loss_qty_kg", precision = 10, scale = 2)
    private BigDecimal lossQtyKg;

    /** 损耗率 %＝损耗/投入*100（派生） */
    @Column(name = "loss_pct", precision = 6, scale = 2)
    private BigDecimal lossPct;

    /** 损耗原因 */
    @Column(name = "loss_reason", length = 64)
    private String lossReason;

    /** 交接人（接班人） */
    @Column(name = "handover_to", length = 32)
    private String handoverTo;

    /** 提交班组 / 结束工序操作人 */
    @Column(name = "submitted_by", length = 32)
    private String submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** 正常损耗范围下限 %（提交时快照，事后调表不改历史判定） */
    @Column(name = "limit_min_pct", precision = 6, scale = 2)
    private BigDecimal limitMinPct;

    /** 正常损耗范围上限 %（提交时快照） */
    @Column(name = "limit_max_pct", precision = 6, scale = 2)
    private BigDecimal limitMaxPct;

    /** 是否超出正常损耗范围 */
    @Column(name = "over_limit")
    private Boolean overLimit;

    /** CONFIRMED / PENDING_REVIEW / RETURNED / LEGACY */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "reviewed_by", length = 32)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 复核说明：接受异常的理由 / 复核结论 */
    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** 最近一次退回原因 */
    @Column(name = "returned_note", length = 500)
    private String returnedNote;

    /** 退回后更正重交次数 */
    @Column(name = "corrected_count", nullable = false)
    private Integer correctedCount = 0;

    /** 旧记录派生口径说明 */
    @Column(name = "legacy_note", length = 255)
    private String legacyNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
