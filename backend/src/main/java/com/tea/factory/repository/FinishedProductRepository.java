package com.tea.factory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.tea.factory.domain.FinishedProduct;

public interface FinishedProductRepository extends JpaRepository<FinishedProduct, String> {

    List<FinishedProduct> findTop20ByOrderByStoredAtDesc();

    List<FinishedProduct> findByBatchNoOrderByStoredAtDesc(String batchNo);

    /**
     * 手写 JPQL：按等级统计成品件数与净重。
     */
    @Query("select p.grade, count(p), sum(p.packages), sum(p.netWeightKg) "
            + "from FinishedProduct p "
            + "group by p.grade")
    List<Object[]> summarizeByGrade();
}
