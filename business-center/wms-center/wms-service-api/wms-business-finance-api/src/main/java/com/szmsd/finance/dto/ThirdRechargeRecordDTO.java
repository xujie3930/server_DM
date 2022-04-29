package com.szmsd.finance.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.szmsd.finance.domain.FssBaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * @author liulei
 */
@Data
public class ThirdRechargeRecordDTO {
    @ApiModelProperty(value = "ID")
    private Long id;

    @ApiModelProperty(value = "客户id")
    private Long cusId;

    @ApiModelProperty(value = "客户编码")
    private String cusCode;

    @ApiModelProperty(value = "客户名称")
    private String cusName;

    @ApiModelProperty(value = "充值编号")
    private String rechargeNo;

    @ApiModelProperty(value = "充值金额")
    private BigDecimal rechargeAmount;

    @ApiModelProperty(value = "充值币种")
    private String rechargeCurrency;

    @ApiModelProperty(value = "手续费")
    private BigDecimal transactionAmount;

    @ApiModelProperty(value = "手续费币种")
    private String transactionCurrency;

    @ApiModelProperty(value = "实际到账金额")
    private BigDecimal actualAmount;

    @ApiModelProperty(value = "实际到账金额币种")
    private String actualCurrency;

    @ApiModelProperty(value = "充值状态")
    private String rechargeStatus;

    @ApiModelProperty(value = "请求参数唯一标识/流水号")
    private String serialNo;

    @ApiModelProperty(value = "错误编码")
    private String errorCode;

    @ApiModelProperty(value = "错误信息")
    private String errorMessage;

}
