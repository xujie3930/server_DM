package com.szmsd.http.controller;

import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.utils.FileStream;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.http.dto.*;
import com.szmsd.http.service.ICarrierService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @author zhangyuyuan
 * @date 2021-03-30 11:47
 */
@Api(tags = {"Carrier"})
@RestController
@RequestMapping("/api/carrier/http")
public class CarrierController extends BaseController {

    @Autowired
    private ICarrierService carrierService;

    @PostMapping("/shipmentOrder")
    @ApiOperation(value = "创建承运商物流订单（客户端）")
    public R<ResponseObject.ResponseObjectWrapper<ShipmentOrderResult, ProblemDetails>> shipmentOrder(@RequestBody CreateShipmentOrderCommand command) {
        return R.ok(carrierService.shipmentOrder(command));
    }

    @PostMapping("/submission")
    @ApiOperation(value = "出库管理 - HTTP - # 提交承运商物流订单（客户端）状态", position = 300)
    @ApiImplicitParam(name = "dto", value = "referenceNumber", dataType = "referenceNumber")
    public R submission(@RequestBody ShipmentOrderSubmissionParam shipmentOrderSubmissionParam) {
        return carrierService.submission(shipmentOrderSubmissionParam);
    }

    @PostMapping("/cancellation")
    @ApiOperation(value = "取消承运商物流订单（客户端）")
    public R<ResponseObject.ResponseObjectWrapper<CancelShipmentOrderBatchResult, ErrorDataDto>> cancellation(@RequestBody CancelShipmentOrderCommand command) {
        return R.ok(carrierService.cancellation(command));
    }

    @GetMapping("/label")
    @ApiOperation(value = "根据订单号返回标签文件流")
    public R<ResponseObject.ResponseObjectWrapper<FileStream, ProblemDetails>> label(@RequestBody CreateShipmentOrderCommand command) {
        return R.ok(carrierService.label(command));
    }
}
