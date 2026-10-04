package com.tea.factory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tea.factory.domain.StepQtyHandover;

import jakarta.persistence.LockModeType;

public interface StepQtyHandoverRepository extends JpaRepository<StepQtyHandover, Long> {

    /** 同一批次同一工序至多一张交接单（DB 层 uk_qty_batch_step 兜底） */
    Optional<StepQtyHandover> findByBatchNoAndStepIndex(String batchNo, Integer stepIndex);

    /** 结束工序提交前对（批次，工序）间隙加锁：并发双提交在此串行，第二个读到已存在的交接单被友好驳回 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT 1 FROM step_qty_handover WHERE batch_no = :batchNo AND step_index = :stepIndex FOR UPDATE",
            nativeQuery = true)
    Optional<Object> lockGapByBatchAndStep(@Param("batchNo") String batchNo, @Param("stepIndex") Integer stepIndex);

    Optional<StepQtyHandover> findByHandoverNo(String handoverNo);

    List<StepQtyHandover> findByBatchNoOrderByStepIndexAsc(String batchNo);

    /** 上一道已确认交接产出：开工下一道时取本步可用在制量 */
    Optional<StepQtyHandover> findByBatchNoAndStepIndexAndStatus(
            String batchNo, Integer stepIndex, String status);

    /**
     * 按交接单号取行级写锁：提交复核结论 / 更正重交先锁单再读状态，
     * 多个复核人几乎同时操作时在数据库层串行，只可能有一个结论生效。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from StepQtyHandover h where h.handoverNo = :handoverNo")
    Optional<StepQtyHandover> lockByHandoverNo(@Param("handoverNo") String handoverNo);

    List<StepQtyHandover> findByStatusOrderBySubmittedAtDesc(String status);

    /** 待复核看板：待复核 + 已退回（都挂起工序、需要结论或更正） */
    List<StepQtyHandover> findByStatusInOrderBySubmittedAtDesc(List<String> statuses);

    long countByStatus(String status);
}
