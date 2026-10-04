package com.tea.factory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tea.factory.common.ApiResponse;
import com.tea.factory.domain.StepQtyHandover;
import com.tea.factory.dto.LossReviewRequest;
import com.tea.factory.dto.PendingReviewDTO;
import com.tea.factory.dto.QtyCorrectRequest;
import com.tea.factory.service.StepQtyHandoverService;

import jakarta.validation.Valid;

/**
 * 工序在制量交接与损耗复核：待复核看板、复核结论、退回后的更正重交。
 */
@RestController
@RequestMapping("/api/process/qty")
public class StepQtyHandoverController {

    private final StepQtyHandoverService handoverService;

    public StepQtyHandoverController(StepQtyHandoverService handoverService) {
        this.handoverService = handoverService;
    }

    /** 待复核 / 已退回待更正的交接单 */
    @GetMapping("/pending")
    public ApiResponse<List<PendingReviewDTO>> pending() {
        return ApiResponse.success(handoverService.pendingList());
    }

    /** 复核人给出结论：accept=true 接受异常并说明理由；false 退回班组更正 */
    @PostMapping("/handovers/{handoverNo}/review")
    public ApiResponse<StepQtyHandover> review(@PathVariable String handoverNo,
            @Valid @RequestBody LossReviewRequest request) {
        StepQtyHandover h = handoverService.review(handoverNo, request);
        boolean accepted = Boolean.TRUE.equals(request.getAccept());
        String message = accepted
                ? "已接受异常损耗，交接产出生效，下道工序可以开工"
                : "已退回班组更正，更正前下道工序不能开工";
        return ApiResponse.success(message, h);
    }

    /** 班组按退回意见更正实际产出重量与损耗说明后重交 */
    @PostMapping("/handovers/{handoverNo}/correct")
    public ApiResponse<StepQtyHandover> correct(@PathVariable String handoverNo,
            @Valid @RequestBody QtyCorrectRequest request) {
        StepQtyHandover h = handoverService.correct(handoverNo, request);
        String message = "CONFIRMED".equals(h.getStatus())
                ? "更正后损耗在正常范围内，已确认，工序继续推进"
                : "更正后损耗仍超范围，重新进入待复核";
        return ApiResponse.success(message, h);
    }
}
