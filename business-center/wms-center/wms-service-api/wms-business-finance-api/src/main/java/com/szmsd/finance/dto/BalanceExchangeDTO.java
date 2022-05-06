package com.szmsd.finance.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * @author liulei
 */
@Data
public class BalanceExchangeDTO {
    @ApiModelProperty(value = "原币别code")
    private Long exchangeFromCode;

    @ApiModelProperty(value = "原币别")
    private String exchangeFrom;

    @ApiModelProperty(value = "现币别code")
    private Long exchangeToCode;

    @ApiModelProperty(value = "现币别")
    private Long exchangeTo;

    @ApiModelProperty(value = "比率")
    private BigDecimal rate;

    @ApiModelProperty(value = "交易金额")
    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal amount;

    private String remark;

}
