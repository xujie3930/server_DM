package com.szmsd.finance.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author liulei
 */
@Data
@Accessors(chain = true)
public class AccountBalanceDTO implements Serializable {
    @ApiModelProperty(value = "ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "客户id")
    private Long cusId;

    @ApiModelProperty(value = "客户编码")
    private String cusCode;

    @ApiModelProperty(value = "客户名称")
    private String custName;

    @ApiModelProperty(value = "币种id")
    private String currencyCode;

    @ApiModelProperty(value = "币种姓名")
    private String currencyName;

    @ApiModelProperty(value = "可用余额")
    private BigDecimal currentBalance;

    @ApiModelProperty(value = "冻结余额")
    private BigDecimal freezeBalance;

    @ApiModelProperty(value = "总余额")
    private BigDecimal totalBalance;

    private String remark;

}
