package com.tea.factory.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tea.factory.domain.LeafIntake;
import com.tea.factory.dto.SupplierQualityDTO;

public interface LeafIntakeRepository extends JpaRepository<LeafIntake, String> {

    List<LeafIntake> findTop20ByOrderByCreatedAtDesc();

    List<LeafIntake> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime from);

    /**
     * 手写 JPQL：按供应商汇总鲜叶验收质量（车次、毛重、扣重后重量、平均含水量 / 杂质率、结算金额）。
     */
    @Query("select new com.tea.factory.dto.SupplierQualityDTO("
            + "l.supplierName, count(l), sum(l.grossWeightKg), sum(l.netWeightKg), "
            + "avg(l.moisturePct), avg(l.impurityPct), sum(l.deductWeightKg), sum(l.settlePrice)) "
            + "from LeafIntake l "
            + "group by l.supplierName "
            + "order by sum(l.grossWeightKg) desc")
    List<SupplierQualityDTO> summarizeBySupplier();

    /**
     * 手写 JPQL：按鲜叶等级汇总当日进厂量。
     */
    @Query("select l.leafGrade, count(l), sum(l.netWeightKg) "
            + "from LeafIntake l where l.createdAt >= :from "
            + "group by l.leafGrade order by sum(l.netWeightKg) desc")
    List<Object[]> summarizeByGrade(@Param("from") LocalDateTime from);
}
