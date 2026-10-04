package com.tea.factory.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 烘房：房号、层数、在烘批次与温控曲线。
 */
@Data
@Entity
@Table(name = "drying_room")
public class DryingRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 房号，如 HF-A */
    @Column(name = "room_no", nullable = false, length = 16, unique = true)
    private String roomNo;

    /** 层数 */
    @Column(name = "layers")
    private Integer layers;

    /** 在烘批次号 */
    @Column(name = "current_batch_no", length = 32)
    private String currentBatchNo;

    /** 温控曲线，如 毛火110℃/足火90℃ */
    @Column(name = "temp_curve", length = 128)
    private String tempCurve;

    @Column(name = "target_temp_c", precision = 6, scale = 1)
    private BigDecimal targetTempC;

    @Column(name = "current_temp_c", precision = 6, scale = 1)
    private BigDecimal currentTempC;

    /** 在烘时长 min */
    @Column(name = "drying_minutes")
    private Integer dryingMinutes;

    /** RUNNING / IDLE / CLEANING / FAULT */
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
