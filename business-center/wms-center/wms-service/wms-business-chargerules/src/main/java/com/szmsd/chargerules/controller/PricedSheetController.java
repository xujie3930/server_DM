package com.szmsd.chargerules.controller;

import com.szmsd.chargerules.dto.PricedSheetDTO;
import com.szmsd.chargerules.dto.ProductSheetGradeDTO;
import com.szmsd.chargerules.service.IPricedSheetService;
import com.szmsd.chargerules.vo.PricedProductSheetVO;
import com.szmsd.chargerules.vo.PricedSheetExcelInfoVO;
import com.szmsd.chargerules.vo.PricedSheetInfoVO;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.utils.FileStream;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.http.dto.PricedSheetCodeCriteria;
import com.szmsd.http.vo.ResponseVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Api(tags = {"PricedSheet"})
@RestController
@RequestMapping("/sheets")
public class PricedSheetController extends BaseController {

    @Resource
    private IPricedSheetService iPricedSheetService;

    @PreAuthorize("@ss.hasPermi('sheets:list')")
    @GetMapping("/list/{productCode}")
    @ApiOperation(value = "报价表")
    public R<List<PricedProductSheetVO>> list(@PathVariable("productCode") String productCode) {
        List<PricedProductSheetVO> sheets = iPricedSheetService.sheets(productCode);
        return R.ok(sheets);
    }

    @PreAuthorize("@ss.hasPermi('sheets:info')")
    @GetMapping("/info/{sheetCode}")
    @ApiOperation(value = "根据报价表编号获取产品报价表信息")
    public R<PricedSheetInfoVO> info(@PathVariable("sheetCode") String sheetCode) {
        PricedSheetInfoVO info = iPricedSheetService.info(sheetCode);
        return R.ok(info);
    }

    @PreAuthorize("@ss.hasPermi('sheets:info')")
    @GetMapping("/excel/info/{sheetCode}")
    @ApiOperation(value = "报价表信息", notes = "excel内容")
    public R<PricedSheetExcelInfoVO> excelInfo(@PathVariable("sheetCode") String sheetCode) {
        PricedSheetExcelInfoVO info = iPricedSheetService.excelInfo(sheetCode);
        return R.ok(info);
    }

    @PostMapping("/saveOrUpdate")
    @ApiOperation(value = "创建/修改报价产品报价表详情信息")
    public R<ResponseVO> saveOrUpdate(@RequestBody PricedSheetDTO pricedSheetDTO) {
        if (StringUtils.isEmpty(pricedSheetDTO.getCode())) {
            iPricedSheetService.create(pricedSheetDTO);
        } else {
            iPricedSheetService.update(pricedSheetDTO);
        }
        return R.ok();
    }

    @PutMapping(value = "{sheetCode}/importFile", headers = "content-type=multipart/form-data")
    @ApiOperation(value = "使用file文件导入产品报价表信息")
    public R importFile(@PathVariable("sheetCode") String sheetCode, @RequestParam MultipartFile file) {
        iPricedSheetService.importFile(sheetCode, file);
        return R.ok();
    }

    @PostMapping("/grade")
    @ApiOperation(value = "修改一个计价产品信息的报价表对应的等级和生效时间段")
    public R grade(@RequestBody ProductSheetGradeDTO productSheetGradeDTO) {
        iPricedSheetService.grade(productSheetGradeDTO);
        return R.ok();
    }

    @PostMapping("/exportFile")
    @ApiOperation(value = "导出报价表信息")
    public void exportFile(HttpServletResponse response, @RequestBody PricedSheetCodeCriteria pricedSheetCodeCriteria) {
        FileStream fileStream = iPricedSheetService.exportFile(pricedSheetCodeCriteria);
        super.fileStreamWrite(response, fileStream);
    }

}
