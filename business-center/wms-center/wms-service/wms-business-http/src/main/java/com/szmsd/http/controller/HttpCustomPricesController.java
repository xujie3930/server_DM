package com.szmsd.http.controller;

import com.alibaba.fastjson.JSON;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.http.dto.OperationRecordDto;
import com.szmsd.http.dto.custom.*;
import com.szmsd.http.service.IHttpCustomPricesService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Api(tags = {"客户等级/折扣报价维护"})
@RestController
@RequestMapping("/api/customPrices/http")
public class HttpCustomPricesController extends BaseController {

    @Resource
    private IHttpCustomPricesService httpCustomPricesService;


    @PostMapping("/operationRecord")
    @ApiOperation(value = "获取操作记录")
    public R<OperationRecordDto> operationRecord(@RequestBody String id) {
        return httpCustomPricesService.operationRecord(id);
    }
    @PostMapping("/result/{clientCode}")
    @ApiOperation(value = "获取折扣/等级方案数据")
    public R<CustomPricesPageDto> result(@PathVariable("clientCode") String clientCode) {
//        Map map=new HashMap();
//        map.put("clientCode",clientCode);
//        String clientCodes= JSON.toJSONString(map);
        return httpCustomPricesService.page(clientCode);
    }

    @PostMapping("/updateDiscount")
    @ApiOperation(value = "修改客户折扣主信息")
    public R updateDiscount(@RequestBody UpdateCustomMainDto dto) {

        return httpCustomPricesService.updateDiscount(dto);
    }

    @PostMapping("/updateGrade")
    @ApiOperation(value = "修改客户等级主信息")
    public R updateGrade(@RequestBody UpdateCustomMainDto dto) {
        return httpCustomPricesService.updateGrade(dto);
    }


    @PostMapping("/updateGradeDetail")
    @ApiOperation(value = "修改客户等级明细信息")
    public R updateGradeDetail(@RequestBody CustomGradeMainDto dto) {
        return httpCustomPricesService.updateGradeDetail(dto);
    }


    @PostMapping("/updateDiscountDetail")
    @ApiOperation(value = "修改客户折扣明细信息")
    public R updateDiscountDetail(@RequestBody CustomDiscountMainDto dto) {
        return httpCustomPricesService.updateDiscountDetail(dto);
    }

}
