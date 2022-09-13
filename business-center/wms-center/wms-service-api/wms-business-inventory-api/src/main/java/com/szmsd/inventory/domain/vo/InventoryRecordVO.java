package com.szmsd.inventory.domain.vo;

import com.szmsd.common.core.annotation.Excel;
import com.szmsd.common.core.language.annotation.FieldJsonI18n;
import com.szmsd.common.core.language.enums.LocalLanguageTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ApiModel(value = "InventoryRecordVO", description = "InventoryRecordVO库存日志")
public class InventoryRecordVO {

    @ApiModelProperty(value = "单据号")
    @Excel(name = "关联单号")
    private String receiptNo;

    @ApiModelProperty(value = "SKU")
    @Excel(name = "sku")
    private String sku;

    @ApiModelProperty(value = "上架sku")
    @Excel(name = "原上架sku")
    private String putawaySku;


    @ApiModelProperty(value = "目的仓库编码")
    @Excel(name = "仓库")
    private String warehouseCode;

    @ApiModelProperty(value = "类型：系统语言")
    @FieldJsonI18n(localLanguageType = LocalLanguageTypeEnum.INVENTORY_RECORD_TYPE)
    @Excel(name = "调整类型")
    private String typeName;

    @ApiModelProperty(value = "操作数量")
    @Excel(name = "调整库存")
    private Integer quantity;

    @ApiModelProperty(value = "之前总库存")
    @Excel(name = "原来总库存")
    private Integer beforeTotalInventory;

    @ApiModelProperty(value = "之前可用库存")
    private Integer beforeAvailableInventory;

    @ApiModelProperty(value = "之前冻结库存")
    @Excel(name = "原来冻结库存")
    private Integer beforeFreezeInventory;

    @ApiModelProperty(value = "之前总入库")
    private Integer beforeTotalInbound;

    @ApiModelProperty(value = "之前总出库")
    private Integer beforeTotalOutbound;

    @ApiModelProperty(value = "之后总库存")
    @Excel(name = "调整后总库存")
    private Integer afterTotalInventory;

    @ApiModelProperty(value = "之后可用库存")
    private Integer afterAvailableInventory;

    @ApiModelProperty(value = "之后原冻结库存")
    @Excel(name = "调整后冻结库存")
    private Integer afterFreezeInventory;

    @ApiModelProperty(value = "之后原总入库")
    private Integer afterTotalInbound;

    @ApiModelProperty(value = "之后原总出库")
    private Integer afterTotalOutbound;

    @ApiModelProperty(value = "操作人姓名")
    @Excel(name = "操作人")
    private String operator;

    @ApiModelProperty(value = "操作时间")
    @Excel(name = "变动时间")
    private String operateOn;

    @ApiModelProperty(value = "日志")
    private String logs;

    @ApiModelProperty(value = "批次号")
    @Excel(name = "批次号")
    private String batchNumber;


}
