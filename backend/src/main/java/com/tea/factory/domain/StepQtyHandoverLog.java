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
 * 在制量交接操作留痕：提交 / 范围内自动确认 / 复核接受 / 复核退回 / 更正重交，
 * 以及并发冲突中被驳回的操作（REJECTED 用独立事务补写，不随主事务回滚）。
 */
@Data
@Entity
@Table(name = "step_qty_handover_log")
public class StepQtyHandoverLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "handover_no", nullable = false, length = 32)
    private String handoverNo;

    @Column(name = "batch_no", nullable = false, length = 32)
    private String batchNo;

    @Column(name = "step_name", length = 16)
    private String stepName;

    /** SUBMIT / AUTO_CONFIRM / REVIEW_ACCEPT / REVIEW_RETURN / CORRECT */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(name = "operator", length = 32)
    private String operator;

    /** EFFECTIVE / REJECTED */
    @Column(name = "result", length = 16)
    private String result;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
