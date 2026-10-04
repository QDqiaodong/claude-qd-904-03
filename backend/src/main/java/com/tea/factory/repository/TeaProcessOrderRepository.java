package com.tea.factory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tea.factory.domain.TeaProcessOrder;

import jakarta.persistence.LockModeType;

public interface TeaProcessOrderRepository extends JpaRepository<TeaProcessOrder, String> {

    List<TeaProcessOrder> findByStatusOrderByCreatedAtDesc(String status);

    List<TeaProcessOrder> findTop20ByOrderByCreatedAtDesc();

    /**
     * 按批次号取行级写锁（PESSIMISTIC_WRITE）。
     * 结束工序、复核接受 / 退回、更正重交、下一道开工等改变在制量的事务都先抢这把锁，
     * 班组、复核人、下一班组几乎同时操作时整单串行：只可能产生一份有效交接结果，
     * 失败方的全部写入随事务回滚，不会出现“工序已前进却仍待复核”。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TeaProcessOrder o where o.batchNo = :batchNo")
    Optional<TeaProcessOrder> lockByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 手写 JPQL：各茶类当前停留工序分布。
     */
    @Query("select o.teaType, o.currentStep, count(o) "
            + "from TeaProcessOrder o where o.status = 'PROCESSING' "
            + "group by o.teaType, o.currentStep")
    List<Object[]> summarizeCurrentStep();
}
