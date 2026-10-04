package com.tea.factory.dto;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 审评录入：五项评分（每项 0~100，必须齐全）+ 评语 + 审评员。
 */
@Data
public class EvaluationRequest {

    private String batchNo;

    /** 外形 */
    private BigDecimal shapeScore;

    /** 汤色 */
    private BigDecimal soupScore;

    /** 香气 */
    private BigDecimal aromaScore;

    /** 滋味 */
    private BigDecimal tasteScore;

    /** 叶底 */
    private BigDecimal leafBaseScore;

    private String comment;

    private String defect;

    private String evaluator;
}
