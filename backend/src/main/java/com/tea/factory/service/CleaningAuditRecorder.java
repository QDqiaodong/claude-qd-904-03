package com.tea.factory.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.domain.CleaningHandoverLog;
import com.tea.factory.repository.CleaningHandoverLogRepository;

/**
 * 清洗交接留痕的独立事务写入器。
 * 并发冲突中后到者的操作所在主事务会回滚，驳回留痕用 REQUIRES_NEW 单独提交，不被连带回滚。
 */
@Component
public class CleaningAuditRecorder {

    private final CleaningHandoverLogRepository logRepository;

    public CleaningAuditRecorder(CleaningHandoverLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(String handoverNo, String equipCode, String action, String currentStatus,
            String operator, String detail) {
        CleaningHandoverLog log = new CleaningHandoverLog();
        log.setHandoverNo(handoverNo);
        log.setEquipCode(equipCode);
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
