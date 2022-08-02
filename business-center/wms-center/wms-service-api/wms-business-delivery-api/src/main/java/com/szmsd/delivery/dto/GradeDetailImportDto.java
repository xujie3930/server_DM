package com.szmsd.delivery.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.szmsd.common.core.annotation.Excel;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Accessors(chain = true)
@ApiModel(value = "GradeDetailImportDto", description = "等级方案-导入关联产品")
public class GradeDetailImportDto {

    @Excel(name = "产品代码")
    @ExcelProperty(index = 0)
    private String productCode;

    @Excel(name = "等级")
    @ExcelProperty(index = 1)
    private String grade;

    @Excel(name = "子产品代码")
    @ExcelProperty(index = 2)
    private String subProduct;

    @Excel(name = "生效时间")
    @ExcelProperty(index = 3)
    private Date beginTimeDate;

    @Excel(name = "截止时间")
    @ExcelProperty(index = 4)
    private Date endTimeDate;























}
