package com.tea.factory.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.domain.StepQtyHandoverLog;
import com.tea.factory.repository.StepQtyHandoverLogRepository;

/**
 * 在制量交接留痕的独立事务写入器。
 * 并发冲突中后到者的主事务会回滚，驳回留痕用 REQUIRES_NEW 单独提交，不被连带回滚。
 */
@Component
public class QtyHandoverAuditRecorder {

    private final StepQtyHandoverLogRepository logRepository;

    public QtyHandoverAuditRecorder(StepQtyHandoverLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(String handoverNo, String batchNo, String stepName, String action,
            String currentStatus, String operator, String detail) {
        StepQtyHandoverLog log = new StepQtyHandoverLog();
        log.setHandoverNo(handoverNo);
        log.setBatchNo(batchNo);
        log.setStepName(stepName);
        log.setAction(action);
        log.setFromStatus(currentStatus);
        log.setToStatus(currentStatus);
        log.setOperator(operator);
        log.setResult("REJECTED");
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
    }
}
