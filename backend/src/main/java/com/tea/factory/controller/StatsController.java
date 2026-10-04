package com.tea.factory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tea.factory.common.ApiResponse;
import com.tea.factory.dto.DashboardDTO;
import com.tea.factory.dto.EvalRankDTO;
import com.tea.factory.dto.SupplierQualityDTO;
import com.tea.factory.dto.TeaTypeYieldDTO;
import com.tea.factory.service.StatsService;

/**
 * 工序盘概览与三张汇总表。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardDTO> dashboard() {
        return ApiResponse.success(statsService.dashboard());
    }

    /** 按供应商汇总鲜叶验收质量 */
    @GetMapping("/supplier-quality")
    public ApiResponse<List<SupplierQualityDTO>> supplierQuality() {
        return ApiResponse.success(statsService.supplierQuality());
    }

    /** 按茶类统计投料量与产出率 */
    @GetMapping("/tea-yield")
    public ApiResponse<List<TeaTypeYieldDTO>> teaYield() {
        return ApiResponse.success(statsService.teaTypeYield());
    }

    /** 审评得分排行 */
    @GetMapping("/eval-ranking")
    public ApiResponse<List<EvalRankDTO>> evalRanking() {
        return ApiResponse.success(statsService.evalRanking());
    }
}
