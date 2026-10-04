package com.tea.factory.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tea.factory.common.BizException;
import com.tea.factory.common.Dict;
import com.tea.factory.domain.FinishedProduct;
import com.tea.factory.domain.TeaEvaluation;
import com.tea.factory.domain.TeaProcessOrder;
import com.tea.factory.dto.EvalRankDTO;
import com.tea.factory.dto.EvaluationRequest;
import com.tea.factory.dto.ProductRequest;
import com.tea.factory.repository.FinishedProductRepository;
import com.tea.factory.repository.TeaEvaluationRepository;
import com.tea.factory.repository.TeaProcessOrderRepository;

/**
 * 审评与成品入库：五项评分定级，成品水分超标不允许入库。
 */
@Service
public class EvaluationService {

    private final TeaEvaluationRepository evaluationRepository;
    private final FinishedProductRepository productRepository;
    private final TeaProcessOrderRepository orderRepository;

    public EvaluationService(TeaEvaluationRepository evaluationRepository,
            FinishedProductRepository productRepository,
            TeaProcessOrderRepository orderRepository) {
        this.evaluationRepository = evaluationRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    /** 审评打分：五项必须齐全且各在 0~100，加权算出总分与等级 */
    @Transactional
    public TeaEvaluation evaluate(EvaluationRequest request) {
        if (request.getBatchNo() == null || request.getBatchNo().isBlank()) {
            throw new BizException("审评批次号不能为空");
        }
        TeaProcessOrder order = orderRepository.findById(request.getBatchNo())
                .orElseThrow(() -> new BizException("批次号 " + request.getBatchNo() + " 不存在"));

        BigDecimal shape = requireScore("外形", request.getShapeScore());
        BigDecimal soup = requireScore("汤色", request.getSoupScore());
        BigDecimal aroma = requireScore("香气", request.getAromaScore());
        BigDecimal taste = requireScore("滋味", request.getTasteScore());
        BigDecimal leafBase = requireScore("叶底", request.getLeafBaseScore());

        BigDecimal total = shape.multiply(Dict.evalWeight("外形"))
                .add(soup.multiply(Dict.evalWeight("汤色")))
                .add(aroma.multiply(Dict.evalWeight("香气")))
                .add(taste.multiply(Dict.evalWeight("滋味")))
                .add(leafBase.multiply(Dict.evalWeight("叶底")))
                .setScale(2, RoundingMode.HALF_UP);

        TeaEvaluation evaluation = new TeaEvaluation();
        evaluation.setBatchNo(request.getBatchNo());
        evaluation.setTeaType(order.getTeaType());
        evaluation.setShapeScore(shape);
        evaluation.setSoupScore(soup);
        evaluation.setAromaScore(aroma);
        evaluation.setTasteScore(taste);
        evaluation.setLeafBaseScore(leafBase);
        evaluation.setTotalScore(total);
        evaluation.setComment(request.getComment());
        evaluation.setDefect(request.getDefect());
        evaluation.setEvaluator(request.getEvaluator() == null ? "沈审评" : request.getEvaluator());
        evaluation.setEvaluatedAt(LocalDateTime.now());
        return evaluationRepository.save(evaluation);
    }

    public List<TeaEvaluation> topEvaluations() {
        return evaluationRepository.findTop20ByOrderByTotalScoreDesc();
    }

    public List<EvalRankDTO> ranking() {
        return evaluationRepository.ranking();
    }

    /** 成品入库：水分超过 7% 不允许入库 */
    @Transactional
    public FinishedProduct store(ProductRequest request) {
        if (request.getBatchNo() == null || request.getBatchNo().isBlank()) {
            throw new BizException("成品批次号不能为空");
        }
        TeaProcessOrder order = orderRepository.findById(request.getBatchNo())
                .orElseThrow(() -> new BizException("批次号 " + request.getBatchNo() + " 不存在"));
        if (!"DONE".equals(order.getStatus())) {
            throw new BizException("批次 " + request.getBatchNo() + " 尚有工序未完成（当前："
                    + Dict.stepName(order.getTeaType(), order.getCurrentStep()) + "），不允许入库");
        }
        if (!Dict.isValidFinishGrade(request.getGrade())) {
            throw new BizException("成品等级不合法，可选：" + String.join("、", Dict.FINISH_GRADES));
        }
        BigDecimal moisture = request.getMoisturePct();
        if (moisture == null) {
            throw new BizException("必须录入出厂水分");
        }
        if (moisture.compareTo(Dict.MAX_PRODUCT_MOISTURE) > 0) {
            throw new BizException("出厂水分 " + moisture + "% 超过上限 " + Dict.MAX_PRODUCT_MOISTURE
                    + "%，不允许入库，请回烘复火");
        }
        if (request.getPackages() == null || request.getPackages() <= 0) {
            throw new BizException("入库件数必须大于 0");
        }
        BigDecimal netWeight = request.getNetWeightKg();
        if (netWeight == null || netWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("净重必须大于 0");
        }
        if (request.getStorageBin() == null || request.getStorageBin().isBlank()) {
            throw new BizException("入库仓位不能为空");
        }

        FinishedProduct product = new FinishedProduct();
        product.setBatchNo(request.getBatchNo());
        product.setTeaType(order.getTeaType());
        product.setGrade(request.getGrade());
        product.setPackages(request.getPackages());
        product.setNetWeightKg(netWeight);
        product.setStorageBin(request.getStorageBin());
        product.setMoisturePct(moisture);
        product.setStatus("STORED");
        product.setStoredAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    public List<FinishedProduct> products() {
        return productRepository.findTop20ByOrderByStoredAtDesc();
    }

    private BigDecimal requireScore(String dim, BigDecimal score) {
        if (score == null) {
            throw new BizException("审评五项必须齐全，缺「" + dim + "」评分，不能定等级");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BizException("「" + dim + "」评分必须在 0~100 之间，当前为 " + score);
        }
        return score;
    }
}
