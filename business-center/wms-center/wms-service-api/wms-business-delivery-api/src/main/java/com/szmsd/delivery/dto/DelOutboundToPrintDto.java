package com.szmsd.delivery.dto;

import com.szmsd.common.core.validator.ValidationUpdateGroup;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * @author zhangyuyuan
 * @date 2021-03-05 14:21
 */
@Data
@ApiModel(value = "DelOutboundToPrintDto", description = "DelOutboundToPrintDto对象")
public class DelOutboundToPrintDto implements Serializable {

    @NotNull(message = "ID不能为空", groups = ValidationUpdateGroup.class)
    @ApiModelProperty(value = "ID")
    private Long id;

    @ApiModelProperty(hidden = true)
    private boolean batch;

    @ApiModelProperty(hidden = true)
    private List<Long> ids;
}
