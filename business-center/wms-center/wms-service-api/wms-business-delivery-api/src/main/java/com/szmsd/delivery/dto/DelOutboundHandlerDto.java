package com.szmsd.delivery.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * @author zhangyuyuan
 * @date 2021-03-25 14:42
 */
@Data
@ApiModel(value = "DelOutboundHandlerDto", description = "DelOutboundHandlerDto对象")
public class DelOutboundHandlerDto implements Serializable {

    @NotNull(message = "IDS不能为空")
    @ApiModelProperty(value = "IDS")
    private List<Long> ids;
}
