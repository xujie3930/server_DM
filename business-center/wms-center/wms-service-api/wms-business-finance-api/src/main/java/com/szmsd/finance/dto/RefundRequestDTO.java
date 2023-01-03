package com.szmsd.finance.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.fastjson.JSONObject;
import com.szmsd.common.core.annotation.Excel;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.putinstorage.domain.dto.AttachmentFileDTO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.CollectionUtils;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * @ClassName: RefundRequestQueryDTO
 * @Description: 退款申请查询条件
 * @Author: 11
 * @Date: 2021-08-13 11:46
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "退款申请新增修改对象")
public class RefundRequestDTO {

    @ApiModelProperty(value = "id")
    private Integer id;

    @ApiModelProperty(value = "客户id")
    private Integer cusId;
    @ExcelProperty(  index = 0)
    @NotBlank(message = "客户代码不能为空")
    @ApiModelProperty(value = "客户代码")
    @Excel(name = "客户代码")
    private String cusCode;

    @ApiModelProperty(value = "客户名称")
    private String cusName;
    @ExcelProperty(  index = 1)
    @NotBlank(message = "处理性质不能为空")
    @ApiModelProperty(value = "处理性质")
    @Excel(name = "处理性质", combo = {"退费", "赔偿", "补收", "充值", "优惠", "增值服务"})
    private String treatmentProperties;
    @ApiModelProperty(value = "处理性质编码")
    private String treatmentPropertiesCode;
    @ExcelProperty( index = 2)
    @ApiModelProperty(value = "责任地区")
    @Excel(name = "责任地区")
    private String responsibilityArea;
    @ApiModelProperty(value = "责任地区编码")
    private String responsibilityAreaCode;

    @ApiModelProperty(value = "所属仓库")
    private String warehouseName;
    @ExcelProperty( index = 3)
    @NotBlank(message = "所属仓库不能为空")
    @Excel(name = "所属仓库")
    @ApiModelProperty(value = "所属仓库编码")
    private String warehouseCode;

    @ExcelProperty( index = 4)
    @Excel(name = "等级")
    @ApiModelProperty(value = "等级")
    private String gradeName;

    @ExcelProperty(  index =8)
    @NotBlank(message = "业务类型不能为空")
    @ApiModelProperty(value = "业务类型")
    @Excel(name = "业务类型")
    private String businessTypeName;
    @ApiModelProperty(value = "业务类型编码")
    private String businessTypeCode;


    @ExcelProperty(  index = 9)
    @ApiModelProperty(value = "业务明细")
    private String businessDetails;

    //@ExcelProperty(  index = 12)
    @ApiModelProperty(value = "业务明细编码")
    @Excel(name = "业务明细")
    private String businessDetailsCode;

    @ExcelProperty(  index = 10)
    @NotBlank(message = "费用类型不能为空")
    @ApiModelProperty(value = "费用类型")
    @Excel(name = "费用类型")
    private String feeTypeName;

    @ApiModelProperty(value = "费用类型编码")
    private String feeTypeCode;

    @ExcelProperty( index = 15)
    @Min(value = 0, message = "标准赔付异常")
    @NotNull(message = "标准赔付不能为空", groups = ICompensateCheck.class)
    @Excel(name = "标准赔付")
    @ApiModelProperty(value = "标准赔付")
    private BigDecimal standardPayout;

    @ExcelProperty(  index = 16)
    @Min(value = 0, message = "额外赔付异常")
    @NotNull(message = "额外赔付不能为空", groups = ICompensateCheck.class)
    @Excel(name = "额外赔付")
    @ApiModelProperty(value = "额外赔付")
    private BigDecimal additionalPayout;

    @ExcelProperty(  index = 17)
    @NotBlank(message = "供应商是否完成赔付不能为空", groups = ICompensateCheck.class)
    @ApiModelProperty(value = "供应商是否完成赔付（0：未完成，1：已完成）")
    @Excel(name = "供应商是否完成赔付", combo = {"未完成", "已完成"}, defaultValue = "未完成")
    private String compensationPaymentFlag;

    public void setCompensationPaymentFlag(String compensationPaymentFlag) {
        if (StringUtils.isNotBlank(compensationPaymentFlag)) {
            this.compensationPaymentFlag = compensationPaymentFlag;
        } else {
            this.compensationPaymentFlag = null;
        }
    }

    @ExcelProperty( index = 18)
    @NotNull(message = "赔付金额不能为空", groups = ICompensateCheck.class)
    @Min(value = 0, message = "赔付金额异常")
    @ApiModelProperty(value = "赔付金额")
    @Excel(name = "赔付金额")
    private BigDecimal payoutAmount;

    @ApiModelProperty(value = "赔付币别")
    private String compensationPaymentCurrency;

    @ExcelProperty(  index = 19)
    @NotBlank(message = "赔付币别不能为空", groups = ICompensateCheck.class)
    @Excel(name = "赔付币别")
    @ApiModelProperty(value = "赔付币别编码")
    private String compensationPaymentCurrencyCode;

    @ExcelProperty( index = 5)
    @ApiModelProperty(value = "处理号（工单id)")
    @Excel(name = "处理号")
    private String orderNo;

    @ApiModelProperty(value = "处理编号")
    private String processNo;

    @ApiModelProperty(value = "数量")
    private String num;

    @ExcelProperty(  index = 6)
    @Min(value = 0, message = "金额异常")
    @NotNull(message = "金额不能为空")
    @ApiModelProperty(value = "金额")
    @Excel(name = "金额")
    private BigDecimal amount;

    @ApiModelProperty(value = "币种名称")
    private String currencyName;

    @ExcelProperty(  index = 7)
    @NotBlank(message = "币种名称不能为空")
    @Excel(name = "币种")
    @ApiModelProperty(value = "币种编码 [subValue] == CNY")
    private String currencyCode;

    @ExcelProperty(  index = 12)
    @ApiModelProperty(value = "属性-数组")
    @Excel(name = "属性")
    private String attributes;
    @ApiModelProperty(value = "属性编码-数组")
    private String attributesCode;

    @ExcelProperty(  index = 20)
    @NotBlank(message = "供应商确认不赔付不能为空", groups = ICompensateCheck.class)
    @Excel(name = "供应商确认不赔付", combo = {"是", "否"}, defaultValue = "否")
    @ApiModelProperty(value = "供应商确认不赔付（0：否，1：是）")
    private String noCompensationFlag;

    public void setNoCompensationFlag(String noCompensationFlag) {
        if (StringUtils.isNotBlank(noCompensationFlag)) {
            this.noCompensationFlag = noCompensationFlag;
        } else {
            this.noCompensationFlag = null;
        }
    }

    @ExcelProperty(  index = 21)
    @NotBlank(message = "供应商确认赔付未到账不能为空", groups = ICompensateCheck.class)
    @Excel(name = "供应商确认赔付未到账", combo = {"是", "否"}, defaultValue = "否")
    @ApiModelProperty(value = "供应商确认赔付未到账（0：否，1：是）")
    private String compensationPaymentArrivedFlag;

    public void setCompensationPaymentArrivedFlag(String compensationPaymentArrivedFlag) {
        if (StringUtils.isNotBlank(compensationPaymentArrivedFlag)) {
            this.compensationPaymentArrivedFlag = compensationPaymentArrivedFlag;
        } else {
            this.compensationPaymentArrivedFlag = null;
        }
    }
    @ExcelProperty( index = 11)
    @Excel(name = "费用类别")
    @ApiModelProperty(value = "费用类别")
    private String feeCategoryName;

    @ApiModelProperty(value = "费用类别编码")
    private String feeCategoryCode;

    @ApiModelProperty(value = "处理号类型")
    private String orderType;

    @ApiModelProperty(value = "附件")
    private List<AttachmentFileDTO> attachment;

    public String getAttachment() {
        if (CollectionUtils.isEmpty(attachment)) return "";
        return JSONObject.toJSONString(attachment);
    }
    @ExcelProperty(  index = 13)
    @ApiModelProperty(value = "备注")
    @Excel(name = "备注")
    private String remark;

    @ExcelProperty(  index = 14)
    @ApiModelProperty(value = "附注")
    @Excel(name = "附注")
    private String noteAppended;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }
}