package com.szmsd.http.controller;

import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.common.core.web.page.PageVO;
import com.szmsd.http.dto.OperationRecordDto;
import com.szmsd.http.dto.discount.DiscountPageRequest;
import com.szmsd.http.dto.discount.MergeDiscountDto;
import com.szmsd.http.dto.discount.UpdateDiscountCustomDto;
import com.szmsd.http.dto.discount.UpdateDiscountDetailDto;
import com.szmsd.http.service.IHttpDiscountService;
import com.szmsd.http.dto.discount.DiscountPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@Api(tags = {"折扣方案"})
@RestController
@RequestMapping("/api/discount/http")
public class HttpDiscountController extends BaseController {

    @Resource
    private IHttpDiscountService httpDiscountService;


    @PostMapping("/operationRecord")
    @ApiOperation(value = "获取操作记录")
    public R<OperationRecordDto> operationRecord(@RequestBody String id) {
        return httpDiscountService.operationRecord(id);
    }
    @PostMapping("/page")
    @ApiOperation(value = "分页查询折扣方案")
    public R<PageVO> page(@RequestBody DiscountPageRequest pageDTO) {
        return httpDiscountService.page(pageDTO);
    }


    @PostMapping("/detailResult")
    @ApiOperation(value = "获取折扣方案明细信息")
    public R detailResult(@RequestBody String id) {
        return httpDiscountService.detailResult(id);
    }

    @PostMapping("/detailResultPage")
    @ApiOperation(value = "获取折扣方案明细信息分页")
    public R detailResultPage(@RequestBody DiscountPage discountPage) {
        return httpDiscountService.detailResultPage(discountPage);
    }


    @PostMapping("/create")
    @ApiOperation(value = "创建折扣方案")
    public R create(@RequestBody MergeDiscountDto dto) {
        return httpDiscountService.create(dto);
    }


    @PostMapping("/update")
    @ApiOperation(value = "修改折扣方案")
    public R update(@RequestBody MergeDiscountDto dto) {
        return httpDiscountService.update(dto);
    }

    @PostMapping("/detailImport")
    @ApiOperation(value = "修改折扣方案关联产品")
    public R detailImport(@RequestBody UpdateDiscountDetailDto dto) {
        return httpDiscountService.detailImport(dto);
    }

    @PostMapping("/customUpdate")
    @ApiOperation(value = "修改折扣方案关联客户")
    public R customUpdate(@RequestBody UpdateDiscountCustomDto dto) {
        return httpDiscountService.customUpdate(dto);
    }

    /**
     * //cod接口拉取数据
     * @return
     */
    @PostMapping("insertBasCodExternal")
    public R basCodlist() {
        R r=  httpDiscountService.insertBasCodExternal();
        return r;
    }

}
