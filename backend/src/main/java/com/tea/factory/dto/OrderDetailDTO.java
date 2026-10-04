package com.tea.factory.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.tea.factory.domain.ProcessStepRecord;

/**
 * 工单详情：工单本体 + 工序序列进度 + 工艺参数记录 + 数量交接时间线 + 数量对账。
 */
public class OrderDetailDTO {

    private String batchNo;
    private String teaType;
    private String teaTypeName;
    private List<String> flow = new ArrayList<>();
    private List<String> flowNames = new ArrayList<>();
    private String currentStep;
    private String currentStepName;
    private Integer currentStepIndex;
    private BigDecimal inputQtyKg;
    private BigDecimal outputQtyKg;
    /** 当前可用在制量＝上道已确认产出 */
    private BigDecimal availableWipKg;
    /** 数量口径 TRACKED / LEGACY（混合批次由 qtyBalance.qtyMode=MIXED 表达） */
    private String qtyMode;
    private String qtyModeName;
    private String status;
    private String sourceTroughNo;
    private String operator;
    private List<ProcessStepRecord> steps = new ArrayList<>();

    /** 最近一道工序收尾形成的清洗交接单号（无设备工序时为空） */
    private String lastHandoverNo;

    /** 本次结束工序形成的在制量交接单号 / 状态（供前端提示待复核） */
    private String qtyHandoverNo;
    private String qtyHandoverStatus;
    private String qtyHandoverStatusName;
    /** 本次结束是否推进了工序（false＝超范围待复核，停在原工序） */
    private Boolean stepAdvanced;

    /** 数量时间线：每道工序的投入 / 产出 / 损耗 / 交接人 / 复核结论，与产出率统计同源 */
    private List<StepQtyNodeDTO> qtyTimeline = new ArrayList<>();

    /** 批次数量对账：总投入 = 最终产量 + 累计损耗 + 在制量（+ 未登记差） */
    private QtyBalanceDTO qtyBalance;

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getTeaType() {
        return teaType;
    }

    public void setTeaType(String teaType) {
        this.teaType = teaType;
    }

    public String getTeaTypeName() {
        return teaTypeName;
    }

    public void setTeaTypeName(String teaTypeName) {
        this.teaTypeName = teaTypeName;
    }

    public List<String> getFlow() {
        return flow;
    }

    public void setFlow(List<String> flow) {
        this.flow = flow;
    }

    public List<String> getFlowNames() {
        return flowNames;
    }

    public void setFlowNames(List<String> flowNames) {
        this.flowNames = flowNames;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
    }

    public String getCurrentStepName() {
        return currentStepName;
    }

    public void setCurrentStepName(String currentStepName) {
        this.currentStepName = currentStepName;
    }

    public Integer getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(Integer currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
    }

    public BigDecimal getInputQtyKg() {
        return inputQtyKg;
    }

    public void setInputQtyKg(BigDecimal inputQtyKg) {
        this.inputQtyKg = inputQtyKg;
    }

    public BigDecimal getOutputQtyKg() {
        return outputQtyKg;
    }

    public void setOutputQtyKg(BigDecimal outputQtyKg) {
        this.outputQtyKg = outputQtyKg;
    }

    public BigDecimal getAvailableWipKg() {
        return availableWipKg;
    }

    public void setAvailableWipKg(BigDecimal availableWipKg) {
        this.availableWipKg = availableWipKg;
    }

    public String getQtyMode() {
        return qtyMode;
    }

    public void setQtyMode(String qtyMode) {
        this.qtyMode = qtyMode;
    }

    public String getQtyModeName() {
        return qtyModeName;
    }

    public void setQtyModeName(String qtyModeName) {
        this.qtyModeName = qtyModeName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSourceTroughNo() {
        return sourceTroughNo;
    }

    public void setSourceTroughNo(String sourceTroughNo) {
        this.sourceTroughNo = sourceTroughNo;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public List<ProcessStepRecord> getSteps() {
        return steps;
    }

    public void setSteps(List<ProcessStepRecord> steps) {
        this.steps = steps;
    }

    public String getLastHandoverNo() {
        return lastHandoverNo;
    }

    public void setLastHandoverNo(String lastHandoverNo) {
        this.lastHandoverNo = lastHandoverNo;
    }

    public String getQtyHandoverNo() {
        return qtyHandoverNo;
    }

    public void setQtyHandoverNo(String qtyHandoverNo) {
        this.qtyHandoverNo = qtyHandoverNo;
    }

    public String getQtyHandoverStatus() {
        return qtyHandoverStatus;
    }

    public void setQtyHandoverStatus(String qtyHandoverStatus) {
        this.qtyHandoverStatus = qtyHandoverStatus;
    }

    public String getQtyHandoverStatusName() {
        return qtyHandoverStatusName;
    }

    public void setQtyHandoverStatusName(String qtyHandoverStatusName) {
        this.qtyHandoverStatusName = qtyHandoverStatusName;
    }

    public Boolean getStepAdvanced() {
        return stepAdvanced;
    }

    public void setStepAdvanced(Boolean stepAdvanced) {
        this.stepAdvanced = stepAdvanced;
    }

    public List<StepQtyNodeDTO> getQtyTimeline() {
        return qtyTimeline;
    }

    public void setQtyTimeline(List<StepQtyNodeDTO> qtyTimeline) {
        this.qtyTimeline = qtyTimeline;
    }

    public QtyBalanceDTO getQtyBalance() {
        return qtyBalance;
    }

    public void setQtyBalance(QtyBalanceDTO qtyBalance) {
        this.qtyBalance = qtyBalance;
    }
}
