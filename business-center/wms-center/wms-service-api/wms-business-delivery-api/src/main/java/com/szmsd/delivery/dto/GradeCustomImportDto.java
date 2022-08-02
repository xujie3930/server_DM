package com.szmsd.delivery.dto;

import com.szmsd.common.core.annotation.Excel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Accessors(chain = true)
@ApiModel(value = "GradeCustomImportDto", description = "等级方案-导入关联客户")
public class GradeCustomImportDto {


    @Excel(name = "客户代码")
    private String clientCode;

    @Excel(name = "生效时间")
    private Date beginTimeDate;

    @Excel(name = "截止时间")
    private Date endTimeDate;

    @Excel(name = "是否有效")
    private String isValidStr;























}
