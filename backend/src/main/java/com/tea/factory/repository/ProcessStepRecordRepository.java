package com.tea.factory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.tea.factory.domain.ProcessStepRecord;

public interface ProcessStepRecordRepository extends JpaRepository<ProcessStepRecord, Long> {

    List<ProcessStepRecord> findByBatchNoOrderByStepIndexAsc(String batchNo);

    Optional<ProcessStepRecord> findByBatchNoAndStepNameAndStatus(String batchNo, String stepName, String status);

    /**
     * 手写 JPQL：统计各工序的执行次数、平均时长与异常次数。
     */
    @Query("select r.stepName, count(r), avg(r.paramMinutes), "
            + "sum(case when r.abnormalNote is not null and r.abnormalNote <> '' then 1 else 0 end) "
            + "from ProcessStepRecord r "
            + "group by r.stepName")
    List<Object[]> summarizeStepExecution();
}
