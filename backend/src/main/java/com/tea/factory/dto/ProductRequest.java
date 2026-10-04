package com.tea.factory.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 成品入库录入。成品编号由后端自定义生成器生成，前端不用传。
 */
@Data
public class ProductRequest {

    private String batchNo;

    private String teaType;

    /** 特级 / 一级 / 二级 / 三级 */
    private String grade;

    /** 件数 */
    private Integer packages;

    /** 净重 kg */
    private BigDecimal netWeightKg;

    /** 入库仓位 */
    private String storageBin;

    /** 出厂水分 % */
    private BigDecimal moisturePct;
}
