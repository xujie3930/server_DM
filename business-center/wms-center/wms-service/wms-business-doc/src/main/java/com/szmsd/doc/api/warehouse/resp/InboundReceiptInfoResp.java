package com.szmsd.doc.api.warehouse.resp;

import com.szmsd.putinstorage.domain.vo.InboundTrackingVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@ApiModel(value = "InboundReceiptInfoVO", description = "InboundReceiptInfoVO入库详情")
public class InboundReceiptInfoResp {

    @ApiModelProperty(value = "主键ID")
    private Long id;

    @ApiModelProperty(value = "入库单号")
    private String warehouseNo;

//    @ApiModelProperty(value = "采购单")
//    private String orderNo;
//
//    @ApiModelProperty(value = "客户编码")
//    private String cusCode;

    @ApiModelProperty(value = "入库单类型")
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

    @ApiModelProperty(value = "挂号")
    private String trackingNumber;

//    @ApiModelProperty(value = "状态")
//    private String status;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "入库明细")
    private List<InboundReceiptDetailResp> inboundReceiptDetails;

    @ApiModelProperty(value = "物流到货明细")
    private List<InboundTrackingVO> inboundTrackingList;

    // --------------------多语言字段--------------------
//
//    @ApiModelProperty(value = "状态0已取消，1初始，2已提审，3审核通过，-3审核失败，4处理中，5已完成")
//    private String statusName;

//    @ApiModelProperty(value = "客户名称 - 当前系统语言")
//    private String cusName;

    @ApiModelProperty(value = "目的仓库名称 - 当前系统语言")
    private String warehouseName;

    @ApiModelProperty(value = "入库方式名称 - 当前系统语言")
    private String warehouseMethodName;

    @ApiModelProperty(value = "类别名称 - 当前系统语言")
    private String warehouseCategoryName;

    @ApiModelProperty(value = "送货方式名称 - 当前系统语言")
    private String deliveryWayName;

    @ApiModelProperty(value = "产品货源地名称 - 当前系统语言")
    private String goodsSourceName;

    @ApiModelProperty(value = "单证信息",notes = "对外接口")
    private List<AttachmentFileResp> documentInformation;

}
