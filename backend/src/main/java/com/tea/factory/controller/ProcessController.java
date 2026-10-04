package com.tea.factory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tea.factory.common.ApiResponse;
import com.tea.factory.domain.ProcessStepRecord;
import com.tea.factory.domain.TeaProcessOrder;
import com.tea.factory.dto.OrderCreateRequest;
import com.tea.factory.dto.OrderDetailDTO;
import com.tea.factory.dto.StepRequest;
import com.tea.factory.dto.TeaTypeYieldDTO;
import com.tea.factory.service.ProcessService;

import jakarta.validation.Valid;

/**
 * 制茶工序流转。
 */
@RestController
@RequestMapping("/api/process")
public class ProcessController {

    private final ProcessService processService;

    public ProcessController(ProcessService processService) {
        this.processService = processService;
    }

    /** 新建工单，批次号由后端自动生成 */
    @PostMapping
    public ApiResponse<TeaProcessOrder> create(@Valid @RequestBody OrderCreateRequest request) {
        TeaProcessOrder order = processService.createOrder(request);
        return ApiResponse.success("已开单，批次号 " + order.getBatchNo(), order);
    }

    @GetMapping
    public ApiResponse<List<OrderDetailDTO>> list() {
        return ApiResponse.success(processService.orderList());
    }

    @GetMapping("/{batchNo}")
    public ApiResponse<OrderDetailDTO> detail(@PathVariable String batchNo) {
        return ApiResponse.success(processService.orderDetail(batchNo));
    }

    /** 开始某道工序 */
    @PostMapping("/{batchNo}/start")
    public ApiResponse<ProcessStepRecord> start(@PathVariable String batchNo, @RequestBody StepRequest request) {
        request.setBatchNo(batchNo);
        return ApiResponse.success("工序已开始", processService.startStep(batchNo, request));
    }

    /** 结束本工序：登记实际产出 / 损耗 / 交接人，范围内自动推进，超范围进入待复核 */
    @PostMapping("/{batchNo}/finish")
    public ApiResponse<OrderDetailDTO> finish(@PathVariable String batchNo, @RequestBody StepRequest request) {
        request.setBatchNo(batchNo);
        OrderDetailDTO detail = processService.finishStep(batchNo, request);
        String message;
        if (Boolean.FALSE.equals(detail.getStepAdvanced())) {
            message = "本工序已结束，损耗超出正常范围，交接单 " + detail.getQtyHandoverNo()
                    + " 进入待复核；复核结论确认前工序停在「" + detail.getCurrentStepName() + "」，下道不得开工";
        } else {
            message = "本工序完成，已推进到「" + detail.getCurrentStepName() + "」";
        }
        return ApiResponse.success(message, detail);
    }

    @GetMapping("/{batchNo}/steps")
    public ApiResponse<List<ProcessStepRecord>> steps(@PathVariable String batchNo) {
        return ApiResponse.success(processService.stepsOf(batchNo));
    }

    /** 按茶类统计投料量与产出率 */
    @GetMapping("/yield")
    public ApiResponse<List<TeaTypeYieldDTO>> yieldByTeaType() {
        return ApiResponse.success(processService.yieldByTeaType());
    }
}
