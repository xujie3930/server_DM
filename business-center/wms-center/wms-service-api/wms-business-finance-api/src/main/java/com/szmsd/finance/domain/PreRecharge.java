package com.szmsd.finance.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.szmsd.bas.plugin.BasSubCommonPlugin;
import com.szmsd.bas.plugin.BasSubValueCommonParameter;
import com.szmsd.common.plugin.annotation.AutoFieldI18n;
import com.szmsd.common.plugin.annotation.AutoFieldValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author liulei
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ApiModel(value = "预充值", description = "预充值")
@TableName("fss_pre_recharge")
public class PreRecharge extends FssBaseEntity {
    @ApiModelProperty(value = "ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "客户id")
    private Long cusId;

    @ApiModelProperty(value = "客户编码")
    private String cusCode;

    @ApiModelProperty(value = "客户名称")
    private String cusName;

    @ApiModelProperty(value = "请求参数唯一标识/流水号")
    private String serialNo;

    @ApiModelProperty(value = "汇款方式 0电汇 1转账 2支票 3 微信 4 支付宝")
    private String remittanceMethod;


    @ApiModelProperty(value = "币种编码")
    private String currencyCode;

    @AutoFieldI18n
    @ApiModelProperty(value = "币种名")
    private String currencyName;

    @ApiModelProperty(value = "金额")
    private BigDecimal amount;

    @ApiModelProperty(value = "手续费")
    private BigDecimal procedureAmount;

    @ApiModelProperty(value = "实际支付金额")
    private BigDecimal acturlAmount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "汇款日期")
    private Date remittanceTime;

    @ApiModelProperty(value = "收款账号")
    private String gatheringAccount;

    @ApiModelProperty(value = "汇款凭证")
    private String paymentVoucher;

    @AutoFieldI18n
    @ApiModelProperty(value = "审核状态(默认0=未审核，1=审核，2=审核未通过 3=审核驳回)")
    private String verifyStatus;

    @AutoFieldI18n
    @ApiModelProperty(value = "审核备注")
    private String verifyRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "审核日期")
    private Date verifyDate;

    @ApiModelProperty(value = "收款银行名称")
    private String dueBankName;

    @ApiModelProperty(value = "银行账号ID")
    private String bankId;

    @ApiModelProperty(value = "驳回意见")
    private String rejectRemark;

    @ApiModelProperty(value = "驳回时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date rejectDate;
}
