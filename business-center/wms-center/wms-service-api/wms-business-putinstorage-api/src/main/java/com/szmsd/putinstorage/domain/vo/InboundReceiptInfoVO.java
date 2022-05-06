package com.szmsd.putinstorage.domain.vo;

import com.szmsd.common.core.language.annotation.FieldJsonI18n;
import com.szmsd.common.core.language.constant.RedisLanguageTable;
import com.szmsd.common.core.language.enums.LocalLanguageTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@ApiModel(value = "InboundReceiptInfoVO", description = "InboundReceiptInfoVO入库详情")
public class InboundReceiptInfoVO {

    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "入库单号")
    private String warehouseNo;

    @ApiModelProperty(value = "采购单")
    private String orderNo;

    @ApiModelProperty(value = "客户编码")
    private String cusCode;

    @ApiModelProperty(value = "普通入库（OMS用）：Normal" +
            "集运入库（OMS用）：Collection" +
            "包裹转运入库（OMS用）：PackageTransfer" +
            "新SKU入库（OMS用）：NewSku" +
            "拆分SKU入库（OMS用）：SplitSku" +
            "上架入库（Yewu用）：Putaway" +
            "点数入库（Yewu用）：Counting" +
            "包裹转运入库（OMS用）：PackageTransfer")
    private String orderType;

    @ApiModelProperty(value = "目的仓库编码")
    private String warehouseCode;

    @ApiModelProperty(value = "入库方式编码")
    private String warehouseMethodCode;

    @ApiModelProperty(value = "类别编码")
    private String warehouseCategoryCode;

    @ApiModelProperty(value = "VAT")
    private String vat;

    @ApiModelProperty(value = "送货方式编码")
    private String deliveryWayCode;

    @ApiModelProperty(value = "送货单号")
    private String deliveryNo;

    @ApiModelProperty(value = "合计申报数量")
    private Integer totalDeclareQty;

    @ApiModelProperty(value = "合计上架数量")
    private Integer totalPutQty;

    @ApiModelProperty(value = "产品货源地编码")
    private String goodsSourceCode;
    @Deprecated
    @ApiModelProperty(value = "挂号",notes = "没用")
    private String trackingNumber;

    @ApiModelProperty(value = "状态")
    private String status;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "入库明细")
    private List<InboundReceiptDetailVO> inboundReceiptDetails;

    @ApiModelProperty(value = "物流到货明细")
    private List<InboundTrackingVO> inboundTrackingList;

    // --------------------多语言字段--------------------

    @ApiModelProperty(value = "状态0已取消，1初始，2已提审，3审核通过，-3审核失败，4处理中，5已完成")
    @FieldJsonI18n(localLanguageType = LocalLanguageTypeEnum.INBOUND_RECEIPT_STATUS)
    private String statusName;

    @ApiModelProperty(value = "客户名称 - 当前系统语言")
    @FieldJsonI18n(type = RedisLanguageTable.BAS_CUSTOMER)
    private String cusName;

    @ApiModelProperty(value = "目的仓库名称 - 当前系统语言")
    @FieldJsonI18n(type = RedisLanguageTable.BAS_WAREHOUSE)
    private String warehouseName;

    @ApiModelProperty(value = "入库方式名称 - 当前系统语言")
    @FieldJsonI18n(type = RedisLanguageTable.BAS_SUB)
    private String warehouseMethodName;

    @ApiModelProperty(value = "类别名称 - 当前系统语言")
    @FieldJsonI18n(type = RedisLanguageTable.BAS_SUB)
    private String warehouseCategoryName;

    @ApiModelProperty(value = "送货方式名称 - 当前系统语言")
    @FieldJsonI18n(type = RedisLanguageTable.BAS_SUB)
    private String deliveryWayName;
    
    @ApiModelProperty(value = "产品货源地名称 - 当前系统语言")
    @FieldJsonI18n(type = RedisLanguageTable.BAS_SUB)
    private String goodsSourceName;
    @ApiModelProperty(value = "数据来源")
    private String sourceType;

    @ApiModelProperty(value = "揽收单号")
    private String collectionNo;

}
