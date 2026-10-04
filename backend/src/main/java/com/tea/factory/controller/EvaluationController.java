package com.tea.factory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tea.factory.common.ApiResponse;
import com.tea.factory.domain.FinishedProduct;
import com.tea.factory.domain.TeaEvaluation;
import com.tea.factory.dto.EvalRankDTO;
import com.tea.factory.dto.EvaluationRequest;
import com.tea.factory.dto.ProductRequest;
import com.tea.factory.service.EvaluationService;

/**
 * 审评与成品入库。
 */
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /** 五项评分审评，自动算总分与等级 */
    @PostMapping
    public ApiResponse<TeaEvaluation> evaluate(@RequestBody EvaluationRequest request) {
        TeaEvaluation evaluation = evaluationService.evaluate(request);
        return ApiResponse.success("审评完成，总分 " + evaluation.getTotalScore(), evaluation);
    }

    @GetMapping
    public ApiResponse<List<TeaEvaluation>> list() {
        return ApiResponse.success(evaluationService.topEvaluations());
    }

    /** 审评得分排行 */
    @GetMapping("/ranking")
    public ApiResponse<List<EvalRankDTO>> ranking() {
        return ApiResponse.success(evaluationService.ranking());
    }

    /** 成品入库，编号由后端自动生成 */
    @PostMapping("/product")
    public ApiResponse<FinishedProduct> store(@RequestBody ProductRequest request) {
        FinishedProduct product = evaluationService.store(request);
        return ApiResponse.success("入库成功，成品编号 " + product.getProductNo(), product);
    }

    @GetMapping("/product")
    public ApiResponse<List<FinishedProduct>> products() {
        return ApiResponse.success(evaluationService.products());
    }
}
