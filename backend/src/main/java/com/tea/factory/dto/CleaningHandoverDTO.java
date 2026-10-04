package com.tea.factory.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.tea.factory.domain.CleaningHandoverLog;

import lombok.Data;

/**
 * 清洗交接单详情：单据本体字段 + 状态 / 风险 / 方式中文名 + 操作留痕 + 可执行操作 + 开工影响。
 * 工序台、设备列表、交接详情共用同一份口径，刷新后三处不允许互相矛盾。
 */
@Data
public class CleaningHandoverDTO {

    private String handoverNo;
    private String equipCode;
    private String equipName;
    private String equipType;
    private String batchNo;
    private String stepName;
    private String stepNameCn;
    private String status;
    private String statusName;

    private String residualRisk;
    private String cleanMethod;
    private LocalDateTime cleanStartTime;
    private LocalDateTime cleanEndTime;
    private String cleaner;
    private String faultNote;
    private Boolean faultAfterConfirm;
    private String decisionNote;
    private String repairNote;
    private String repairer;
    private LocalDateTime repairedAt;
    private String createdBy;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 是否仍锁定（未闭环）；true 时设备不能用于下一批次开工 */
    private boolean locked;

    /** 对开工的影响说明（如：等待负责人确认，暂不能开工） */
    private String lockReason;

    /** 当前页面可执行的操作：START / COMPLETE / REPORT_FAULT / CONFIRM / REOPEN / RECHECK */
    private List<String> allowedActions = new ArrayList<>();

    private List<CleaningHandoverLog> logs = new ArrayList<>();
}
