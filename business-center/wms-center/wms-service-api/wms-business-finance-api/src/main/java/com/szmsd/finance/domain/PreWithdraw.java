package com.szmsd.finance.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
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
@ApiModel(value = "预提现", description = "预充值")
@TableName("fss_pre_withdraw")
public class PreWithdraw extends FssBaseEntity {

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

    @ApiModelProperty(value = "币种编码")
    private String currencyCode;

    @ApiModelProperty(value = "币种名")
    private String currencyName;

    @ApiModelProperty(value = "金额")
    private BigDecimal amount;

    @ApiModelProperty(value = "审核状态(默认0=未审核，1=审核，2=审核未通过 3=提现驳回)")
    private String verifyStatus;

    @ApiModelProperty(value = "审核备注")
    private String verifyRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "审核日期")
    private Date verifyDate;

    @ApiModelProperty(value = "收款银行名称")
    private String dueBankName;

    @ApiModelProperty(value = "收款银行账号")
    private String dueBankCode;

    @ApiModelProperty(value = "收款凭证")
    private String paymentVoucher;

    @ApiModelProperty(value = "收款人姓名")
    private String dueUserName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "提现申请日期")
    private Date withdrawTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "提现驳回时间")
    private Date rejectTime;

    @ApiModelProperty(value = "提现驳回原因")
    private String rejectRemark;

    @ApiModelProperty(value = "提现驳回操作人")
    private String rejectUserBy;

}
