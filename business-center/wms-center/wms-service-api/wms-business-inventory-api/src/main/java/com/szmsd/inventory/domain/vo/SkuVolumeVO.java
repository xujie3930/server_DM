package com.szmsd.inventory.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
@ApiModel(value = "SkuVolumeVO", description = "SKU体积")
public class SkuVolumeVO {

    @ApiModelProperty(value = "SKU")
    private String sku;

    @ApiModelProperty(value = "数量")
    private Integer qty;

    @ApiModelProperty(value = "单个体积")
    private BigDecimal singleVolume;

    @ApiModelProperty(value = "体积")
    private BigDecimal volume;

    @ApiModelProperty(value = "操作时间")
    private String operateOn;

    @ApiModelProperty(value = "入库单")
    private String warehouseNo;

    @ApiModelProperty(value = "客户")
    private String cusCode;

}
