package com.szmsd.http.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * @author zhangyuyuan
 * @date 2021-03-30 11:31
 */
@Data
@Accessors(chain = true)
@ApiModel(value = "CreateShipmentOrderCommand", description = "CreateShipmentOrderCommand对象")
public class CreateShipmentOrderCommand implements Serializable {

    @ApiModelProperty(value = "仓库代码")
    private String warehouseCode;

    @ApiModelProperty(value = "业务流水号")
    private String referenceNumber;

    @ApiModelProperty(value = "订单号")
    private String orderNumber;

    @ApiModelProperty(value = "客户代码")
    private String clientNumber;

    @ApiModelProperty(value = "收货地址")
    private AddressCommand receiverAddress;

    @ApiModelProperty(value = "退货地址")
    private AddressCommand returnAddress;

    @ApiModelProperty(value = "包裹信息")
    private List<Package> packages;

    @ApiModelProperty(value = "物流承运商")
    private Carrier carrier;

    @ApiModelProperty(value = "税号")
    private List<Taxation> taxations;

    @ApiModelProperty(value = "COD")
    private BigDecimal codAmount;

    @ApiModelProperty(value = "承运商标签路径")
    private String shipmentOrderLabelUrl;
/*
    @ApiModelProperty(value = "亚马逊挂号服务")
    private String amazonLogisticsRouteId;*/
}
