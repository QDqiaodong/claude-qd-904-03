package com.tea.factory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.tea.factory.domain.TeaEvaluation;
import com.tea.factory.dto.EvalRankDTO;

public interface TeaEvaluationRepository extends JpaRepository<TeaEvaluation, Long> {

    List<TeaEvaluation> findTop20ByOrderByTotalScoreDesc();

    List<TeaEvaluation> findByBatchNoOrderByEvaluatedAtDesc(String batchNo);

    /**
     * 手写 JPQL：审评得分排行榜（审评记录 theta join 工单取茶类，按总分降序）。
     */
    @Query("select new com.tea.factory.dto.EvalRankDTO("
            + "e.batchNo, o.teaType, e.totalScore, e.comment, e.evaluator, e.evaluatedAt) "
            + "from TeaEvaluation e, TeaProcessOrder o "
            + "where e.batchNo = o.batchNo "
            + "order by e.totalScore desc")
    List<EvalRankDTO> ranking();

    /**
     * 手写 JPQL：按审评员统计审评份数与平均分。
     */
    @Query("select e.evaluator, count(e), avg(e.totalScore) "
            + "from TeaEvaluation e "
            + "group by e.evaluator "
            + "order by avg(e.totalScore) desc")
    List<Object[]> summarizeByEvaluator();
}
