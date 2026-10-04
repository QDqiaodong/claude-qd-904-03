package com.tea.factory.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.tea.factory.generator.BatchNo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 制茶工单。
 * 主键 batchNo 由自定义 Hibernate 生成器自动生成（PC-茶类 + 日期 + 当日序号）。
 */
@Data
@Entity
@Table(name = "tea_process_order")
public class TeaProcessOrder {

    @Id
    @GeneratedValue
    @BatchNo
    @Column(name = "batch_no", nullable = false, length = 32)
    private String batchNo;

    /** 茶类：GREEN / RED / OOLONG / WHITE / PUER */
    @Column(name = "tea_type", nullable = false, length = 16)
    private String teaType;

    /** 工序序列，如 SPREAD>FIX>ROLL>DRY>PICK */
    @Column(name = "process_seq", length = 128)
    private String processSeq;

    /** 当前工序键 */
    @Column(name = "current_step", length = 16)
    private String currentStep;

    /** 当前工序在序列中的下标 */
    @Column(name = "current_step_index")
    private Integer currentStepIndex;

    /** 投料量 kg（批次原投料基准，全程不被工序产出改写） */
    @Column(name = "input_qty_kg", precision = 10, scale = 2)
    private BigDecimal inputQtyKg;

    /** 最终产量 kg（仅末道在制量交接确认后写入） */
    @Column(name = "output_qty_kg", precision = 10, scale = 2)
    private BigDecimal outputQtyKg;

    /**
     * 当前可用在制量 kg＝上道【已确认】交接产出（首道未交接时为空，开工时回退用投料量）。
     * 待复核 / 退回期间保持上道旧值，工序不前进。
     */
    @Column(name = "available_wip_kg", precision = 10, scale = 2)
    private BigDecimal availableWipKg;

    /** 数量口径：LEGACY 旧批次（历史记录派生）/ TRACKED 每道实称交接（旧批次首道实称起为混合口径） */
    @Column(name = "qty_mode", nullable = false, length = 16)
    private String qtyMode = "LEGACY";

    /** 来源摊青槽号 */
    @Column(name = "source_trough_no", length = 16)
    private String sourceTroughNo;

    /** 制茶师傅 */
    @Column(name = "operator", length = 32)
    private String operator;

    /** PROCESSING / DONE */
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
