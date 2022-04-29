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
@ApiModel(value = "InventoryCheckDetails", description = "Inventory盘点详情表")
public class InventoryCheckDetails extends BaseEntity {

    @ApiModelProperty(value = "主键ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @ApiModelProperty(value = "盘点申请单号")
    private String orderNo;

    @ApiModelProperty(value = "sku")
    private String sku;

    @ApiModelProperty(value = "系统数量")
    private Integer systemQty;

    @ApiModelProperty(value = "盘点数量")
    private Integer countingQty;

    @ApiModelProperty(value = "差异数量，等于盘点数量减去系统数量")
    private Integer diffQty;

    @ApiModelProperty(value = "盘点时间")
    private String checkTime;

    @ApiModelProperty(value = "创建ID", hidden = true)
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @ApiModelProperty(value = "修改者ID", hidden = true)
    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;

}
