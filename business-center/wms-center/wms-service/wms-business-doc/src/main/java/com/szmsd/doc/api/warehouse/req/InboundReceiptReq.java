package com.szmsd.doc.api.warehouse.req;

import com.alibaba.fastjson.JSONObject;
import com.szmsd.common.core.utils.StringToolkit;
import com.szmsd.doc.api.SwaggerDictionary;
import com.szmsd.putinstorage.domain.dto.AttachmentFileDTO;
import com.szmsd.putinstorage.enums.SourceTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@Validated
@Data
@Accessors(chain = true)
@ApiModel(value = "InboundReceiptDTO", description = "入库参数")
public class InboundReceiptReq {

//    @ApiModelProperty(value = "主键ID")
//    private Long id;
    @Size(max = 30, message = "入库单号仅支持0-30字符")
    @ApiModelProperty(value = "入库单号 (0-30]", hidden = true)
    private String warehouseNo;
    @Size(max = 30, message = "采购单仅支持0-30字符")
    @ApiModelProperty(value = "采购单 (0-30]", hidden = true)
    private String orderNo;
    //    @NotBlank(message = "客户编码不能为空")
    @Size(max = 30, message = "客户编码仅支持 0-30字符")
    @ApiModelProperty(value = "客户编码 (0-30]", hidden = true, example = "CNYWO7", required = true)
    private String cusCode;
    @NotBlank(message = "入库方式不能为空")
    @Size(max = 30, message = "入库方式仅支持0-30字符")
    @ApiModelProperty(value = "普通入库：Normalr", allowableValues = "Normal,PackageTransfer,NewSku", hidden = true)
    private String orderType = "Normal";
    @NotBlank(message = "目的仓库编码不能为空")
    @Size(max = 30, message = "目的仓库编码仅支持0-30字符")
    @ApiModelProperty(value = "目的仓库编码 /warehouse/page (0-30]", example = "NJ", required = true)
    private String warehouseCode;

    //    @SwaggerDictionary(dicCode = "055")
    @NotBlank(message = "入库方式编码不能为空")
    @Size(max = 30, message = "入库方式编码仅支持0-30字符")
    @ApiModelProperty(value = "入库方式编码 (0-30]", example = "055001", allowableValues = "055001:SKU点数上架,055002:重新贴标上架,055003:裸货上架,055004:包材入库", required = true)
    private String warehouseMethodCode;
    @SwaggerDictionary(dicCode = "056")
    @NotBlank(message = "类别编码不能为空")
    @Size(max = 30, message = "类别编码仅支持0-30字符")
    @ApiModelProperty(value = "类别编码 (0-30]", example = "056001", required = true)
    private String warehouseCategoryCode;
    @Size(max = 30, message = "VAT 仅支持 0-30 字符")
    @ApiModelProperty(value = "VAT")
    private String vat;
    @SwaggerDictionary(dicCode = "053")
    @NotBlank(message = "送货方式编码不能为空")
    @Size(max = 30, message = "送货方式编码仅支持0-30字符")
    @ApiModelProperty(value = "送货方式编码 (0-30]", example = "053001", required = true)
    private String deliveryWayCode;

    @Size(max = 200, message = "送货单号仅支持0-200字符")
    @ApiModelProperty(value = "送货单号（快递单号） 可支持多个", example = "1121,22")
    private String deliveryNo;

    public InboundReceiptReq setDeliveryNo(String deliveryNo) {
        this.deliveryNo = deliveryNo;
        this.deliveryNoList = StringToolkit.getCodeByArray(deliveryNo);
        return this;
    }

    @ApiModelProperty(value = "送货单号-多个", hidden = true)
    private List<String> deliveryNoList;
    //明细计算
    //    @NotNull(message = "合计申报数量不能为空")
    @Min(value = 0, message = "合计申报数量不能小于0")
    @ApiModelProperty(value = "合计申报数量", required = true, hidden = true)
    private Integer totalDeclareQty;
    @Min(value = 0, message = "合计上架数量不能小于0")
    @ApiModelProperty(value = "合计上架数量", hidden = true)
    private Integer totalPutQty = 0;
    @Size(max = 30, message = "产品货源地编码仅支持0-30字符")
    @ApiModelProperty(value = "产品货源地编码  (0-30]", allowableValues = "0:本土,1:进口", example = "0")
    private String goodsSourceCode;
    @Size(max = 200, message = "挂号长度仅支持0-200字符")
    @ApiModelProperty(value = "挂号 (0-200]", hidden = true)
    private String trackingNumber;
    @Size(max = 500, message = "备注长度仅支持0-500字符")
    @ApiModelProperty(value = "备注 (0-500]", example = "备注")
    private String remark;

    @Size(max = 2, message = "单证信息文件-最多上传2张")
    @ApiModelProperty(value = "单证信息文件-列表(base64) (0-2]")
    private List<FileInfoBase64> documentsFileBase64List;

    @ApiModelProperty(value = "单证信息文件", hidden = true)
    private List<AttachmentFileDTO> documentsFile;

    @ApiModelProperty(value = "数据来源",hidden = true)
    private String sourceType= SourceTypeEnum.DOC.name();
//    @ApiModelProperty(value = "状态0已取消，1初始，2已提审，3审核通过，-3审核失败，4处理中，5已完成")
//    private String status;

    @Override
    public String toString() {
        return JSONObject.toJSONString(this);
    }
}
