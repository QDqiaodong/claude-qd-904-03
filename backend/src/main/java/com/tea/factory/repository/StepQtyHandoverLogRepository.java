package com.tea.factory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tea.factory.domain.StepQtyHandoverLog;

public interface StepQtyHandoverLogRepository extends JpaRepository<StepQtyHandoverLog, Long> {

    List<StepQtyHandoverLog> findByHandoverNoOrderByCreatedAtAsc(String handoverNo);

    List<StepQtyHandoverLog> findByBatchNoOrderByCreatedAtAsc(String batchNo);
}
