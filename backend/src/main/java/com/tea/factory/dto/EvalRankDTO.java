package com.tea.factory.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审评得分排行榜（JPQL 投影，审评记录关联工单取茶类）。
 */
public class EvalRankDTO {

    private final String batchNo;
    private final String teaType;
    private final BigDecimal totalScore;
    private final String grade;
    private final String comment;
    private final String evaluator;
    private final LocalDateTime evaluatedAt;

    public EvalRankDTO(String batchNo, String teaType, BigDecimal totalScore, String comment,
            String evaluator, LocalDateTime evaluatedAt) {
        this.batchNo = batchNo;
        this.teaType = teaType;
        this.totalScore = totalScore;
        this.comment = comment;
        this.evaluator = evaluator;
        this.evaluatedAt = evaluatedAt;
        this.grade = com.tea.factory.common.Dict.gradeOf(totalScore);
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getTeaType() {
        return teaType;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public String getGrade() {
        return grade;
    }

    public String getComment() {
        return comment;
    }

    public String getEvaluator() {
        return evaluator;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }
}
