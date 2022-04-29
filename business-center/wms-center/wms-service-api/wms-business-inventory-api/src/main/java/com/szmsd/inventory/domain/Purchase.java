package com.szmsd.inventory.domain;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.szmsd.common.core.web.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import com.szmsd.common.core.annotation.Excel;


/**
 * <p>
 * 采购单
 * </p>
 *
 * @author 11
 * @since 2021-04-26
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ApiModel(value = "采购单", description = "Purchase对象")
public class Purchase extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "ID")
    @TableId(value = "id", type = IdType.AUTO)
    @Excel(name = "ID")
    private Integer id;

    @ApiModelProperty(value = "创建人编号")
    @Excel(name = "创建人编号")
    private String createBy;

    @ApiModelProperty(value = "修改人编号")
    @Excel(name = "修改人编号")
    private String updateBy;

    @ApiModelProperty(value = "版本号")
    @Excel(name = "版本号")
    private Integer version;

    @ApiModelProperty(value = "逻辑删除标识；2-已删除，0-未删除")
    @Excel(name = "逻辑删除标识；2-已删除，0-未删除")
    private String delFlag;

    @ApiModelProperty(value = "客户代码")
    @Excel(name = "客户代码")
    private String customCode;

    @ApiModelProperty(value = "采购单号")
    @Excel(name = "采购单号")
    private String purchaseNo;

    @ApiModelProperty(value = "已入库数-出库单里的可用库存")
    @Excel(name = "已入库数-出库单里的可用库存")
    private Integer availableInventory;

    @ApiModelProperty(value = "采购数量")
    @Excel(name = "采购数量")
    private Integer purchaseQuantity;

    @ApiModelProperty(value = "剩余需要采购的数量")
    @Excel(name = "剩余需要采购的数量")
    private Integer remainingPurchaseQuantity;

    @ApiModelProperty(value = "已创建入库单的数量")
    @Excel(name = "已创建入库单的数量")
    private Integer quantityInStorageCreated;

    @ApiModelProperty(value = "已到仓数量")
    @Excel(name = "已到仓数量")
    private Integer arrivalQuantity;

    @ApiModelProperty(value = "目标仓库code")
    @Excel(name = "目标仓库code")
    private String warehouseCode;

    @ApiModelProperty(value = "目标仓库")
    @Excel(name = "目标仓库")
    private String warehouseName;

    @ApiModelProperty(value = "出库方式编码")
    @Excel(name = "出库方式编码")
    private String orderType;

    @ApiModelProperty(value = "出库方式名")
    @Excel(name = "出库方式名")
    private String orderTypeName;

    @ApiModelProperty(value = "送货方式")
    @Excel(name = "送货方式")
    private String deliveryWayName;

    @ApiModelProperty(value = "送货方式编码")
    @Excel(name = "送货方式编码")
    private String deliveryWay;

    @ApiModelProperty(value = "出库单号")
    @Excel(name = "出库单号")
    private String orderNo;

    @ApiModelProperty(value = "VAT")
    @Excel(name = "VAT")
    private String vat;

    @ApiModelProperty(value = "类别")
    @Excel(name = "类别")
    private String warehouseCategoryName;

    @ApiModelProperty(value = "类别编码")
    @Excel(name = "类别编码")
    private String warehouseCategoryCode;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }
}
