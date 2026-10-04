package com.tea.factory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tea.factory.domain.Equipment;

import jakarta.persistence.LockModeType;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Equipment findByEquipCode(String equipCode);

    List<Equipment> findByEquipTypeOrderByEquipCodeAsc(String type);

    List<Equipment> findByStatusOrderByEquipCodeAsc(String status);

    /**
     * 按设备编号取行级写锁（PESSIMISTIC_WRITE）。
     * 清洗交接、报障、开工等涉及同一台设备状态的事务都先抢这把锁，
     * 保证多人几乎同时确认 / 报障时串行执行，最终只保留一个有效结论。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.equipCode = :code")
    Optional<Equipment> lockByEquipCode(@Param("code") String equipCode);
}
