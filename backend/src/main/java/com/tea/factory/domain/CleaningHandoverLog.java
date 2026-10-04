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
 * 清洗交接操作留痕：开始清洗、完成、报障、确认、重新清洗、复验放行，
 * 以及并发冲突中被驳回的操作全部记录，保证责任可追溯。
 */
@Data
@Entity
@Table(name = "cleaning_handover_log")
public class CleaningHandoverLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "handover_no", nullable = false, length = 32)
    private String handoverNo;

    @Column(name = "equip_code", nullable = false, length = 32)
    private String equipCode;

    /** 操作类型：CREATE / START / COMPLETE / REPORT_FAULT / CONFIRM / REOPEN / RECHECK / REJECTED */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    /** 操作发生时的单据状态 */
    @Column(name = "from_status", length = 20)
    private String fromStatus;

    /** 操作生效后的单据状态（被驳回的操作记录驳回前状态） */
    @Column(name = "to_status", length = 20)
    private String toStatus;

    /** 操作人 */
    @Column(name = "operator", length = 32)
    private String operator;

    /** 操作结果：EFFECTIVE 已生效 / REJECTED 被驳回（他人结论先生效） */
    @Column(name = "result", length = 16)
    private String result;

    /** 说明：登记内容摘要、驳回原因、冲突取舍与对开工的影响 */
    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
