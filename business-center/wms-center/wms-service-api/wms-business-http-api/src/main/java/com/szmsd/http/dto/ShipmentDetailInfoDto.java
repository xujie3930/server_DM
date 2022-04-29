package com.szmsd.http.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author zhangyuyuan
 * @date 2021-03-09 10:53
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "ShipmentDetailInfoDto", description = "ShipmentDetailInfoDto对象")
public class ShipmentDetailInfoDto implements Serializable {

    @ApiModelProperty(value = "SKU")
    private String sku;

    @ApiModelProperty(value = "数量")
    private Long qty;

    @ApiModelProperty(value = "指定编码")
    private String newLabelCode;

    /**
     * 数量运算
     *
     * @param qty qty
     */
    public void addQty(long qty) {
        this.qty = this.qty + qty;
    }
}
