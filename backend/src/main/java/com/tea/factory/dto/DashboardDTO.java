package com.tea.factory.dto;

import java.util.List;

/**
 * 工序盘首页概览：进厂、摊青、在制工单、设备、成品与排行。
 */
public class DashboardDTO {

    private long intakeCount;
    private java.math.BigDecimal intakeNetWeightKg;
    private long witheringCount;
    private long readyTroughCount;
    private long processingOrderCount;
    private long doneOrderCount;
    private long runningEquipmentCount;
    private long faultEquipmentCount;
    private long productCount;
    private java.math.BigDecimal productWeightKg;
    private List<SupplierQualityDTO> supplierQuality;
    private List<TeaTypeYieldDTO> teaTypeYield;
    private List<EvalRankDTO> evalRanking;

    public long getIntakeCount() {
        return intakeCount;
    }

    public void setIntakeCount(long intakeCount) {
        this.intakeCount = intakeCount;
    }

    public java.math.BigDecimal getIntakeNetWeightKg() {
        return intakeNetWeightKg;
    }

    public void setIntakeNetWeightKg(java.math.BigDecimal intakeNetWeightKg) {
        this.intakeNetWeightKg = intakeNetWeightKg;
    }

    public long getWitheringCount() {
        return witheringCount;
    }

    public void setWitheringCount(long witheringCount) {
        this.witheringCount = witheringCount;
    }

    public long getReadyTroughCount() {
        return readyTroughCount;
    }

    public void setReadyTroughCount(long readyTroughCount) {
        this.readyTroughCount = readyTroughCount;
    }

    public long getProcessingOrderCount() {
        return processingOrderCount;
    }

    public void setProcessingOrderCount(long processingOrderCount) {
        this.processingOrderCount = processingOrderCount;
    }

    public long getDoneOrderCount() {
        return doneOrderCount;
    }

    public void setDoneOrderCount(long doneOrderCount) {
        this.doneOrderCount = doneOrderCount;
    }

    public long getRunningEquipmentCount() {
        return runningEquipmentCount;
    }

    public void setRunningEquipmentCount(long runningEquipmentCount) {
        this.runningEquipmentCount = runningEquipmentCount;
    }

    public long getFaultEquipmentCount() {
        return faultEquipmentCount;
    }

    public void setFaultEquipmentCount(long faultEquipmentCount) {
        this.faultEquipmentCount = faultEquipmentCount;
    }

    public long getProductCount() {
        return productCount;
    }

    public void setProductCount(long productCount) {
        this.productCount = productCount;
    }

    public java.math.BigDecimal getProductWeightKg() {
        return productWeightKg;
    }

    public void setProductWeightKg(java.math.BigDecimal productWeightKg) {
        this.productWeightKg = productWeightKg;
    }

    public List<SupplierQualityDTO> getSupplierQuality() {
        return supplierQuality;
    }

    public void setSupplierQuality(List<SupplierQualityDTO> supplierQuality) {
        this.supplierQuality = supplierQuality;
    }

    public List<TeaTypeYieldDTO> getTeaTypeYield() {
        return teaTypeYield;
    }

    public void setTeaTypeYield(List<TeaTypeYieldDTO> teaTypeYield) {
        this.teaTypeYield = teaTypeYield;
    }

    public List<EvalRankDTO> getEvalRanking() {
        return evalRanking;
    }

    public void setEvalRanking(List<EvalRankDTO> evalRanking) {
        this.evalRanking = evalRanking;
    }
}
