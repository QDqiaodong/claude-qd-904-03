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
 * 审评记录：外形 / 汤色 / 香气 / 滋味 / 叶底 五项评分与总分、评语。
 */
@Data
@Entity
@Table(name = "tea_evaluation")
public class TeaEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "batch_no", nullable = false, length = 32)
    private String batchNo;

    @Column(name = "tea_type", length = 16)
    private String teaType;

    /** 外形 */
    @Column(name = "shape_score", precision = 5, scale = 2)
    private BigDecimal shapeScore;

    /** 汤色 */
    @Column(name = "soup_score", precision = 5, scale = 2)
    private BigDecimal soupScore;

    /** 香气 */
    @Column(name = "aroma_score", precision = 5, scale = 2)
    private BigDecimal aromaScore;

    /** 滋味 */
    @Column(name = "taste_score", precision = 5, scale = 2)
    private BigDecimal tasteScore;

    /** 叶底 */
    @Column(name = "leaf_base_score", precision = 5, scale = 2)
    private BigDecimal leafBaseScore;

    /** 加权总分 */
    @Column(name = "total_score", precision = 5, scale = 2)
    private BigDecimal totalScore;

    /** 评语 */
    @Column(name = "comment", length = 255)
    private String comment;

    /** 缺陷记录 */
    @Column(name = "defect", length = 64)
    private String defect;

    /** 审评员 */
    @Column(name = "evaluator", length = 32)
    private String evaluator;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;
}
