package com.tea.factory.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tea.factory.common.ApiResponse;
import com.tea.factory.domain.CleaningHandover;
import com.tea.factory.dto.CleaningActionRequest;
import com.tea.factory.dto.CleaningHandoverDTO;
import com.tea.factory.dto.EquipmentBoardDTO;
import com.tea.factory.service.CleaningHandoverService;

import jakarta.validation.Valid;

/**
 * 设备清洗交接台：交接单、设备开工可用性看板与清洗 / 报障 / 确认 / 维修放行操作。
 */
@RestController
@RequestMapping("/api/cleaning")
public class CleaningHandoverController {

    private final CleaningHandoverService handoverService;

    public CleaningHandoverController(CleaningHandoverService handoverService) {
        this.handoverService = handoverService;
    }

    /** 交接单列表（可按状态过滤） */
    @GetMapping("/handovers")
    public ApiResponse<List<CleaningHandoverDTO>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(handoverService.list(status));
    }

    /** 交接详情：含清洗登记、取舍说明与全部操作留痕 */
    @GetMapping("/handovers/{handoverNo}")
    public ApiResponse<CleaningHandoverDTO> detail(@PathVariable String handoverNo) {
        return ApiResponse.success(handoverService.detail(handoverNo));
    }

    /** 设备列表（开工可用性口径：未闭环交接单 / 故障锁死，旧设备沿用原状态） */
    @GetMapping("/equipment")
    public ApiResponse<List<EquipmentBoardDTO>> board() {
        return ApiResponse.success(handoverService.board());
    }

    /** 清洗人员：开始清洗（登记残留风险、清洗方式、开始时间） */
    @PostMapping("/handovers/{handoverNo}/start")
    public ApiResponse<CleaningHandover> start(@PathVariable String handoverNo,
            @Valid @RequestBody CleaningActionRequest request) {
        return ApiResponse.success("已开始清洗，设备在确认前仍不能开工",
                handoverService.start(handoverNo, request));
    }

    /** 清洗人员：标记清洗完成（填写结束时间），进入等待负责人确认 */
    @PostMapping("/handovers/{handoverNo}/complete")
    public ApiResponse<CleaningHandover> complete(@PathVariable String handoverNo,
            @Valid @RequestBody CleaningActionRequest request) {
        return ApiResponse.success("清洗完成，等待负责人确认；确认前设备不能开工",
                handoverService.complete(handoverNo, request));
    }

    /** 报故障：确认前报障立即冻结；确认后报障保留已确认结果并转维修 */
    @PostMapping("/handovers/{handoverNo}/fault")
    public ApiResponse<CleaningHandover> fault(@PathVariable String handoverNo,
            @Valid @RequestBody CleaningActionRequest request) {
        return ApiResponse.success("故障已登记，设备已冻结，复验合格前不能开工",
                handoverService.reportFault(handoverNo, request));
    }

    /** 负责人：确认设备恢复可用 */
    @PostMapping("/handovers/{handoverNo}/confirm")
    public ApiResponse<CleaningHandover> confirm(@PathVariable String handoverNo,
            @Valid @RequestBody CleaningActionRequest request) {
        return ApiResponse.success("已确认，设备恢复可用，可承接下一批次",
                handoverService.confirm(handoverNo, request));
    }

    /** 维修合格后重新清洗（故障冻结 -> 重新清洗） */
    @PostMapping("/handovers/{handoverNo}/reopen")
    public ApiResponse<CleaningHandover> reopen(@PathVariable String handoverNo,
            @Valid @RequestBody CleaningActionRequest request) {
        return ApiResponse.success("已登记维修并重新发起清洗", handoverService.reopenAfterRepair(handoverNo, request));
    }

    /** 保留结果转维修的设备复验合格后放行 */
    @PostMapping("/handovers/{handoverNo}/recheck")
    public ApiResponse<CleaningHandover> recheck(@PathVariable String handoverNo,
            @Valid @RequestBody CleaningActionRequest request) {
        return ApiResponse.success("复验合格，沿用已确认清洗结果，设备恢复可用",
                handoverService.recheckAndRelease(handoverNo, request));
    }
}
