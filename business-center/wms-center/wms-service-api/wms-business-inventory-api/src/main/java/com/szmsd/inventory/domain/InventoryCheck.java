package com.szmsd.inventory.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.szmsd.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;


@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "Inventory", description = "Inventory盘点表")
public class InventoryCheck extends BaseEntity {

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty(value = "申请单号")
    private String orderNo;

    @ApiModelProperty(value = "客户代码")
    private String customCode;

    @ApiModelProperty(value = "仓库代码")
    private String warehouseCode;

    @ApiModelProperty(value = "库存盘点审批结果")
    private Integer status;

    @ApiModelProperty(value = "审批不通过原因")
    private String reason;

    @ApiModelProperty(value = "创建ID", hidden = true)
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @ApiModelProperty(value = "修改者ID", hidden = true)
    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;


}
