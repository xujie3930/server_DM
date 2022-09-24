package com.szmsd.open.controller;

import com.alibaba.fastjson.JSONObject;
import com.szmsd.common.core.domain.R;
import com.szmsd.open.vo.ResponseVO;
import com.szmsd.putinstorage.api.feign.InboundReceiptFeignService;
import com.szmsd.putinstorage.domain.dto.ReceiptRequest;
import com.szmsd.putinstorage.domain.dto.ReceivingCompletedRequest;
import com.szmsd.putinstorage.domain.dto.ReceivingRequest;
import com.szmsd.putinstorage.domain.dto.ReceivingTrackingRequest;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


@Api(tags = {"Inbound"})
@RestController
@RequestMapping("/api/inbound")
public class InboundReceiptController extends BaseController {

    @Resource
    private InboundReceiptFeignService inboundReceiptFeignService;

    @PostMapping("/receiving")
    @ApiOperation(value = "#B1 接收入库上架", notes = "#B1 接收入库上架")
    public ResponseVO receiving(@RequestBody ReceivingRequest receivingRequest) {
        log.info("#B1 接收入库上架：{}", JSONObject.toJSONString(receivingRequest));
        R.getDataAndException(inboundReceiptFeignService.receiving(receivingRequest));
        return ResponseVO.ok();
    }

    @PostMapping("/receipt")
    @ApiOperation(value = "#B6 转运订单收货调用", notes = "#B6 转运订单收货调用")
    public ResponseVO receipt(@RequestBody ReceiptRequest receiptRequest) {
        log.info("#B6 转运订单收货调用：{}", JSONObject.toJSONString(receiptRequest));
        R.getDataAndException(inboundReceiptFeignService.receipt(receiptRequest));
        return ResponseVO.ok();
    }


    @PostMapping("/receiving/completed")
    @ApiOperation(value = "#B3 接收完成入库", notes = "#B3 接收完成入库")
    public ResponseVO completed(@RequestBody ReceivingCompletedRequest receivingCompletedRequest) {
        log.info("#B3 接收完成入库：{}", JSONObject.toJSONString(receivingCompletedRequest));
        R.getDataAndException(inboundReceiptFeignService.completed(receivingCompletedRequest));
        return ResponseVO.ok();
    }

    @PostMapping("/receiving/tracking")
    @ApiOperation(value = "#B5 物流到货接收确认", notes = "#B5 物流到货接收确认")
    public ResponseVO tracking(@Validated @RequestBody ReceivingTrackingRequest receivingCompletedRequest) {
        log.info("#B5 物流到货接收确认：{}", JSONObject.toJSONString(receivingCompletedRequest));
        R.getDataAndException(inboundReceiptFeignService.tracking(receivingCompletedRequest));
        return ResponseVO.ok();
    }
}
