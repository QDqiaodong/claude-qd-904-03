package com.tea.factory.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.tea.factory.generator.ProductNo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 成品入库。主键 productNo 由自定义 Hibernate 生成器自动生成（CP + 日期 + 当日序号）。
 */
@Data
@Entity
@Table(name = "finished_product")
public class FinishedProduct {

    @Id
    @GeneratedValue
    @ProductNo
    @Column(name = "product_no", nullable = false, length = 32)
    private String productNo;

    @Column(name = "batch_no", nullable = false, length = 32)
    private String batchNo;

    @Column(name = "tea_type", length = 16)
    private String teaType;

    /** 特级 / 一级 / 二级 / 三级 */
    @Column(name = "grade", length = 16)
    private String grade;

    /** 件数 */
    @Column(name = "packages")
    private Integer packages;

    /** 净重 kg */
    @Column(name = "net_weight_kg", precision = 10, scale = 2)
    private BigDecimal netWeightKg;

    /** 入库仓位 */
    @Column(name = "storage_bin", length = 32)
    private String storageBin;

    /** 出厂水分 % */
    @Column(name = "moisture_pct", precision = 5, scale = 2)
    private BigDecimal moisturePct;

    /** STORED / SHIPPED */
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "stored_at")
    private LocalDateTime storedAt;
}
