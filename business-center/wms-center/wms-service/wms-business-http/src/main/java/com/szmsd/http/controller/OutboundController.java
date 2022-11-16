package com.szmsd.http.controller;

import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.http.dto.*;
import com.szmsd.http.service.ICarrierService;
import com.szmsd.http.service.IOutboundService;
import com.szmsd.http.vo.CreateShipmentResponseVO;
import com.szmsd.http.vo.ResponseVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiSort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author zhangyuyuan
 * @date 2021-03-09 10:45
 */
@Api(tags = {"出库管理"})
@ApiSort(100)
@RestController
@RequestMapping("/api/outbound/http")
public class OutboundController extends BaseController {

    @Autowired
    private IOutboundService outboundService;

    @Autowired
    private ICarrierService carrierService;

    @PostMapping("/shipment")
    @ApiOperation(value = "出库管理 - HTTP - #C1 创建出库单", position = 100)
    @ApiImplicitParam(name = "dto", value = "CreateShipmentRequestDto", dataType = "CreateShipmentRequestDto")
    public R<CreateShipmentResponseVO> shipmentCreate(@RequestBody CreateShipmentRequestDto dto) {
        return R.ok(outboundService.shipmentCreate(dto));
    }

    @DeleteMapping("/shipment")
    @ApiOperation(value = "出库管理 - HTTP - #C2 取消出库单", position = 200)
    @ApiImplicitParam(name = "dto", value = "ShipmentCancelRequestDto", dataType = "ShipmentCancelRequestDto")
    public R<ResponseVO> shipmentDelete(@RequestBody ShipmentCancelRequestDto dto) {
        return R.ok(outboundService.shipmentDelete(dto));
    }

    @PutMapping("/shipment/tracking")
    @ApiOperation(value = "出库管理 - HTTP - #C3 更新出库单挂号", position = 300)
    @ApiImplicitParam(name = "dto", value = "ShipmentTrackingChangeRequestDto", dataType = "ShipmentTrackingChangeRequestDto")
    public R<ResponseVO> shipmentTracking(@RequestBody ShipmentTrackingChangeRequestDto dto) {
        return R.ok(outboundService.shipmentTracking(dto));
    }


    @GetMapping("/shipment/shipmentOrderRealResult")
    @ApiOperation(value = "出库管理 - HTTP - # 根据参考号返回真实的挂号和标签数据", position = 300)
    @ApiImplicitParam(name = "dto", value = "referenceNumber", dataType = "referenceNumber")
    public R<ShipmentOrderResult> shipmentOrderRealResult(@RequestBody String referenceNumber) {
        return carrierService.shipmentOrderRealResult(referenceNumber);
    }


    @PutMapping("/shipment/label")
    @ApiOperation(value = "出库管理 - HTTP - #C4 更新出库单标签", position = 400)
    @ApiImplicitParam(name = "dto", value = "ShipmentLabelChangeRequestDto", dataType = "ShipmentLabelChangeRequestDto")
    public R<ResponseVO> shipmentLabel(@RequestBody ShipmentLabelChangeRequestDto dto) {
        return R.ok(outboundService.shipmentLabel(dto));
    }

    @PutMapping("/shipment/shipping")
    @ApiOperation(value = "出库管理 - HTTP - #D2 更新出库单发货指令", position = 500)
    @ApiImplicitParam(name = "dto", value = "ShipmentUpdateRequestDto", dataType = "ShipmentUpdateRequestDto")
    public R<ResponseVO> shipmentShipping(@RequestBody ShipmentUpdateRequestDto dto) {
        return R.ok(outboundService.shipmentShipping(dto));
    }


    @PutMapping("/shipment/multiboxrelation")
    @ApiOperation(value = "出库管理 - HTTP - #D3 更新出库单一件多票的单据匹配关系", position = 500)
    @ApiImplicitParam(name = "dto", value = "ShipmentMultiboxrelationRequestDto", dataType = "ShipmentMultiboxrelationRequestDto")
    public R<ResponseVO> shipmentMultiboxrelation(@RequestBody ShipmentMultiboxrelationRequestDto dto) {
        return R.ok(outboundService.shipmentMultiboxrelation(dto));
    }

    @PostMapping("/shipment/boxtransfer")
    @ApiOperation(value = "出库管理 - HTTP - #C6 大货订单推送", position = 500)
    @ApiImplicitParam(name = "dto", value = "BulkOrderRequestDto", dataType = "BulkOrderRequestDto")
    public R shipmentBoxtransfer(@RequestBody BulkOrderRequestDto dto) {
        return outboundService.shipmentBoxtransfer(dto);
    }

    @GetMapping("/getDirectExpressOrder")
    @ApiOperation(value = "获取中国直发订单状态")
    public R<DirectExpressOrderApiDTO> getDirectExpressOrder(@RequestParam("orderNo") String orderNo) {

        return outboundService.getDirectExpressOrder(orderNo);
    }

}
