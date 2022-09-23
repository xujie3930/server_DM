package com.szmsd.delivery.dto;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.szmsd.common.core.annotation.Excel;
import com.szmsd.common.core.annotation.Excel.ColumnType;
import com.szmsd.common.core.annotation.Excel.Type;
import com.szmsd.common.core.annotation.Excels;
import com.szmsd.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Date;
import java.util.List;

/**
 * 用户对象 sys_user
 *
 * @author szmsd
 */

@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Data
@ApiModel(value = "导入模板")
public class DelQueryServiceImport {
    private static final long serialVersionUID = 1L;

    @Excel(name = "订单号" ,width = 30, type = Type.ALL)
    private String orderNo;

    @Excel(name = "查件原因",width = 30, type = Type.ALL)
    private String reason;

    @Excel(name = "备注",width = 30, type = Type.ALL)
    private String remark;

    @ApiModelProperty(value = "操作类型(0表示管理端,1表示客户端)")
    @TableField(exist = false)
    private int operationType=0;



}
