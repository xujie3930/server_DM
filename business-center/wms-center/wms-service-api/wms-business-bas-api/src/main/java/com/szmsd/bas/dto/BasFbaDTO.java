package com.szmsd.bas.dto;


import com.szmsd.common.core.annotation.Excel;
import com.szmsd.common.core.web.controller.QueryDto;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@ApiModel(value="BasFbaDTO", description="FBA仓库表")
public class BasFbaDTO  extends QueryDto {
    private static final long serialVersionUID = 1L;


    @ApiModelProperty(value = "ID")
    private Integer id;

    @ApiModelProperty(value = "fba仓库code")
    @Excel(name = "fba仓库code")
    private String fbaCode;

    @ApiModelProperty(value = "国家名称")
    @Excel(name = "国家名称")
    private String countryName;

    @ApiModelProperty(value = "国家code")
    private String countryCode;


    @ApiModelProperty(value = "邮编")
    @Excel(name = "邮编")
    private String postcode;

    @ApiModelProperty(value = "id集合")
    private List<Integer> ids;

}