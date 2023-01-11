package com.szmsd.finance.dto;

import com.alibaba.fastjson.JSONObject;
import com.szmsd.common.core.annotation.Excel;
import com.szmsd.finance.enums.CreditConstant;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * @ClassName: CreditInfoBO
 * @Description: 授信额度信息
 * @Author: 11
 * @Date: 2021-09-07 14:56
 */
@Slf4j
@Setter
@Getter
@Accessors(chain = true)
@ApiModel(description = "授信额度信息")
public class CreditInfoBO {
    /**
     * 09-07 授信额度新增
     */
    @ApiModelProperty(value = "授信类型(0：额度，1：期限)")
    @Excel(name = "授信类型(0：额度，1：期限)")
    private String creditType;

    @ApiModelProperty(value = "授信状态（0：未启用，1：启用中，2：欠费停用，3：已禁用）")
    @Excel(name = "授信状态（0：未启用，1：启用中，2：欠费停用，3：已禁用）")
    private Integer creditStatus;

    @ApiModelProperty(value = "授信额度")
    @Excel(name = "授信额度")
    private BigDecimal creditLine;

    @ApiModelProperty(value = "使用额度金额")
    @Excel(name = "使用额度金额")
    private BigDecimal creditUseAmount;

    @ApiModelProperty(value = "授信开始时间")
    @Excel(name = "授信开始时间")
    private LocalDateTime creditBeginTime;

    @ApiModelProperty(value = "授信结束时间")
    @Excel(name = "授信结束时间")
    private LocalDateTime creditEndTime;

    @ApiModelProperty(value = "授信时间间隔")
    @Excel(name = "授信时间间隔")
    private Integer creditTimeInterval;

    @ApiModelProperty(value = "授信时间单位")
    @Excel(name = "授信时间单位")
    private String creditTimeUnit;

    @ApiModelProperty(value = "授信缓冲截止时间")
    @Excel(name = "授信缓冲截止时间")
    private LocalDateTime creditBufferTime;

    @ApiModelProperty(value = "授信缓冲时间间隔")
    @Excel(name = "授信缓冲时间间隔")
    private Integer creditBufferTimeInterval;

    @ApiModelProperty(value = "授信缓冲时间单位")
    @Excel(name = "授信缓冲时间单位")
    private String creditBufferTimeUnit;

    @ApiModelProperty(value = "缓冲时间使用额度")
    @Excel(name = "缓冲时间使用额度")
    private BigDecimal creditBufferUseAmount;

    @ApiModelProperty(value = "需要还款额")
    @Excel(name = "需要还款额")
    private BigDecimal requiredRepayment;

    @ApiModelProperty(value = "还款额")
    @Excel(name = "还款额")
    private BigDecimal repaymentAmount;

    public CreditInfoBO() {
        this.creditType = "0";
        this.creditStatus = 3;
        this.creditLine = BigDecimal.ZERO;
        this.creditUseAmount = BigDecimal.ZERO;
        this.creditBeginTime = LocalDateTime.of(2000, 1, 1, 1, 1, 1);
        this.creditEndTime = LocalDateTime.of(2000, 1, 1, 1, 1, 1);
        this.creditTimeInterval = 0;
        this.creditTimeUnit = ChronoUnit.DAYS.name();
        this.creditBufferTime = LocalDateTime.of(2000, 1, 1, 1, 1, 1);
        this.creditBufferTimeInterval = 0;
        this.creditBufferTimeUnit = ChronoUnit.DAYS.name();
        this.creditBufferUseAmount = BigDecimal.ZERO;
        this.requiredRepayment = BigDecimal.ZERO;
        this.repaymentAmount = BigDecimal.ZERO;
    }

    /**
     * 使用信用额度扣费
     * 逾期/余额为0时 触发欠费 停用/禁用
     *
     * @param amount 扣完余额 要使用授信额度的金额
     * @return 是否可以扣费 且 修改授信额
     */
    protected boolean changeCreditAmount(BigDecimal amount, boolean updateCredit) {
        CreditConstant.CreditTypeEnum creditTypeEnum = CreditConstant.CreditTypeEnum.getThisByTypeCode(this.creditType);
        BigDecimal canUseAmount = this.creditLine.subtract(this.creditUseAmount);
        log.info("creditTypeEnum:{}",creditTypeEnum);
        switch (creditTypeEnum) {
            case QUOTA:
                log.info("creditTypeEnum:canUseAmount:{},amount:{},updateCredit:{}",canUseAmount,amount,updateCredit);
                // 授信额度扣减 足额可扣减 反之不行
                if (amount.compareTo(canUseAmount) > 0) {
                    return false;
                } else {
                    // 刚好够扣则设置停用
                    if (amount.compareTo(canUseAmount) == 0) {
                        this.creditStatus = CreditConstant.CreditStatusEnum.ARREARAGE_DEACTIVATION.getValue();
                    }
                    if (updateCredit) {
                        this.creditUseAmount = this.creditUseAmount.add(amount);
                    }
                    return true;
                }
            case TIME_LIMIT:

                log.info("creditTypeEnum:TIME_LIMIT");
                // 余额 不足 但是在授信期间（A+B）则都可以支付
                LocalDateTime now = LocalDateTime.now();
                boolean after = now.isAfter(this.creditBeginTime);
                boolean before = now.isBefore(this.creditEndTime);
                log.info("creditTypeEnum:canUseAmount:{},{}",after,before);
                if (after && before) {

                    if (updateCredit) {
                        this.creditUseAmount = this.creditUseAmount.add(amount);
                    }
                    log.info("creditUseAmount:{}",creditUseAmount);
                    return true;
                } else {
                    //判断是否是 缓存期 缓冲期仍可下单 记录在缓冲期中作为下一期账单
                    boolean bufferBefore = now.isBefore(this.creditBufferTime);
                    if (after && bufferBefore) {
                        if (updateCredit) {
                            this.creditBufferUseAmount = this.creditBufferUseAmount.add(amount);
                        }
                        log.info("creditUseAmount111:{}",creditBufferUseAmount);
                        return true;
                    } else {
                        // 逾期不还 禁用账号 充值所有金额才能继续使用
//                        if (updateCredit)
//                        this.creditStatus = CreditConstant.CreditStatusEnum.ARREARAGE_DEACTIVATION.getValue();
                        return false;
                    }
                }
            default:
                return false;
        }
    }

    /**
     * 充值（还款）授信额度
     *
     * @param amount
     * @return
     */
    public BigDecimal rechargeCreditAmount(BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();
        // 逾期禁用需要还清所有欠款，开启下次账期
        CreditConstant.CreditTypeEnum creditTypeEnum = CreditConstant.CreditTypeEnum.getThisByTypeCode(this.creditType);
        switch (creditTypeEnum) {
            case QUOTA:
                log.info("rechargeCreditAmount QUOTA : {}",amount);
                if (amount.compareTo(this.creditUseAmount) >= 0) {
                    this.repaymentAmount = this.creditUseAmount;
                    this.creditStatus = CreditConstant.CreditStatusEnum.ACTIVE.getValue();
                    BigDecimal resultAmount = amount.subtract(this.creditUseAmount);
                    this.creditUseAmount = BigDecimal.ZERO;
                    return resultAmount;
                } else {
                    BigDecimal cU = this.creditUseAmount.subtract(amount);
                    this.creditUseAmount = cU;
                    this.repaymentAmount = amount;
                    return BigDecimal.ZERO;
                }
            case TIME_LIMIT:
                // 优先还清欠款 还清后充值余额
                log.info("rechargeCreditAmount TIME_LIMIT : {}",amount);
                if (amount.compareTo(this.creditUseAmount) >= 0) {
                    this.repaymentAmount = this.creditUseAmount;
                    BigDecimal resultAmount = amount.subtract(this.creditUseAmount);
                    this.creditUseAmount = BigDecimal.ZERO;
                    log.info("rechargeCreditAmount 充值{}",resultAmount);
                    return resultAmount;
                } else {
                    BigDecimal cU = this.creditUseAmount.subtract(amount);
                    this.creditUseAmount = cU;
                    this.repaymentAmount = amount;
                    return BigDecimal.ZERO;
                }
            default:
                return amount;
        }
    }

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }
}
