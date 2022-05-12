package com.szmsd.http.dto;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author zhangyuyuan
 * @date 2021-03-24 11:45
 */
@Data
@Accessors(chain = true)
public class ShipmentChargeInfo implements Serializable {

    // 产品代码
    private String productCode;

    // 产品名称
    private String productName;

    // 终端运输商
    private String terminalCarrier;

    // 挂号服务
    private String logisticsRouteId;

    // 发货规则
    private String shipmentRule;

    // 装箱规则
    private String packingRule;

    // 物流商code
    private String logisticsProviderCode;

    // 报价表代码
    private String sheetCode;

    // 客户代码
    private String clientCode;

    // 重量段（Min）
    private Double minWeight;

    // 重量段（Max）
    private Double maxWeight;

    // 计费方式
    private String chargeRuleType;

    // 分区名称
    private String zoneName;

    // 计价的等级
    private String grade;

    // 价格计算方式
    private String calculationMethod;

    // 供应商计价类型
    private String supplierCalcType;

    // 供应商计价id（线路图id或者物流服务id）
    private String supplierCalcId;

    //是否提货服务
    private Integer isPickupPackageService;

    //提货服务名称
    private String pickupPackageServiceName;

    @JSONField(name = "package")
    private PricingPackageInfo packageInfo;
}
