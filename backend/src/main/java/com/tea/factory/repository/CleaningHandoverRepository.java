package com.tea.factory.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tea.factory.domain.CleaningHandover;

public interface CleaningHandoverRepository extends JpaRepository<CleaningHandover, Long> {

    Optional<CleaningHandover> findByHandoverNo(String handoverNo);

    List<CleaningHandover> findAllByOrderByCreatedAtDesc();

    List<CleaningHandover> findByStatusOrderByCreatedAtDesc(String status);

    List<CleaningHandover> findByEquipCodeOrderByCreatedAtDesc(String equipCode);

    List<CleaningHandover> findByBatchNoOrderByCreatedAtDesc(String batchNo);

    /**
     * 设备当前未闭环交接单（设备级行锁由调用方先取 equipment 行保证串行化）。
     * active_key 非空的状态：PENDING / IN_PROGRESS / AWAIT_CONFIRM / FAULT_FREEZE / KEEP_AND_REPAIR。
     */
    List<CleaningHandover> findByActiveKeyOrderByCreatedAtDesc(String equipCode);

    Optional<CleaningHandover> findFirstByActiveKeyOrderByCreatedAtDesc(String equipCode);

    /** 最近一张交接单（含已确认的，用于设备列表展示“上次清洗”） */
    CleaningHandover findFirstByEquipCodeOrderByCreatedAtDesc(String equipCode);
}
