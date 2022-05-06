package com.szmsd.http.service.impl;

import com.alibaba.fastjson.JSON;
import com.szmsd.http.config.HttpConfig;
import com.szmsd.http.dto.*;
import com.szmsd.http.service.IBasService;
import com.szmsd.http.service.http.WmsRequest;
import com.szmsd.http.vo.BaseOperationResponse;
import com.szmsd.http.vo.ResponseVO;
import org.springframework.stereotype.Service;

@Service(value = "iBasService")
public class BasServiceImpl extends WmsRequest implements IBasService {

    public BasServiceImpl(HttpConfig httpConfig) {
        super(httpConfig);
    }

    @Override
    public ResponseVO createPacking(PackingRequest packingRequest) {
        return JSON.parseObject(httpPost(packingRequest.getWarehouseCode(), "baseInfo.packings", packingRequest), ResponseVO.class);
    }

    @Override
    public ResponseVO createProduct(ProductRequest productRequest) {
        return JSON.parseObject(httpPost(productRequest.getWarehouseCode(), "baseInfo.products", productRequest), ResponseVO.class);
    }

    @Override
    public ResponseVO createMaterial(MaterialRequest materialRequest) {
        return JSON.parseObject(httpPost(materialRequest.getWarehouseCode(), "baseInfo.products", materialRequest), ResponseVO.class);
    }

    @Override
    public ResponseVO createSeller(SellerRequest sellerRequest) {
        return JSON.parseObject(httpPost(sellerRequest.getWarehouseCode(), "baseInfo.seller", sellerRequest), ResponseVO.class);
    }

    @Override
    public ResponseVO save(SpecialOperationRequest specialOperationRequest) {
        return JSON.parseObject(httpPost(specialOperationRequest.getWarehouseCode(), "baseInfo.operationType", specialOperationRequest), ResponseVO.class);
    }

    @Override
    public ResponseVO update(SpecialOperationResultRequest specialOperationResultRequest) {
        return JSON.parseObject(httpPut(specialOperationResultRequest.getWarehouseCode(), "baseInfo.operationResult", specialOperationResultRequest), ResponseVO.class);
    }

    @Override
    public BaseOperationResponse shipmentRule(AddShipmentRuleRequest addShipmentRuleRequest) {
        return JSON.parseObject(httpPost(addShipmentRuleRequest.getWarehouseCode(), "baseInfo.shipmentRule", addShipmentRuleRequest), BaseOperationResponse.class);
    }

    @Override
    public ResponseVO inspection(AddSkuInspectionRequest request) {
        return JSON.parseObject(httpPost(request.getWarehouseCode(), "baseInfo.inspection", request), ResponseVO.class);
    }
}
