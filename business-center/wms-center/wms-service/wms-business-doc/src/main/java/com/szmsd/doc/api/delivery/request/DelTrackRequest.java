package com.szmsd.doc.api.delivery.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * @author zhangyuyuan
 * @date 2021-07-28 16:07
 */
@Data
@ApiModel(value = "DelTrackRequest", description = "DelTrackRequest对象")
public class DelTrackRequest implements Serializable {

    @NotNull(message = "订单号不能为空")
    @ApiModelProperty(value = "订单号", required = true, dataType = "String", position = 1, example = "[\"XF0001\"]")
    private List<String> orderNos;
}
