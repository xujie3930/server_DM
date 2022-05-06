package com.szmsd.doc.api.delivery.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@ApiModel(description = "DelOutboundBatchPackingRequest对象")
public class DelOutboundBatchPackingDetailRequest implements Serializable {
    @NotBlank(message = "sku不能为空")
    @ApiModelProperty(value = "SKU")
    private String sku;

    @NotNull(message = "数量不能为空")
    @Min(value = 0,message = "数量异常")
    @ApiModelProperty(value = "数量")
    private Long qty;
    @ApiModelProperty(value = "是否需要贴新SKU标签(对应生成明细中的sku是否贴新标签，true时需要填写新标签) 默认：false",allowableValues = "true,false")
    private Boolean needNewLabel = false;
    @ApiModelProperty(value = "指定编码")
    private String newLabelCode;
}
