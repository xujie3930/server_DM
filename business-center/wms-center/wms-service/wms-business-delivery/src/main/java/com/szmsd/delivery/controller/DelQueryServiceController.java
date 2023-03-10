package com.szmsd.delivery.controller;
import cn.afterturn.easypoi.excel.ExcelExportUtil;
import cn.afterturn.easypoi.excel.ExcelImportUtil;
import cn.afterturn.easypoi.excel.entity.ExportParams;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.event.SyncReadListener;
import com.github.pagehelper.PageInfo;
import com.szmsd.bas.api.feign.BasTranslateFeignService;
import com.szmsd.bas.dto.BasSkuRuleMatchingImportDto;
import com.szmsd.bas.dto.BaseProductImportDto;
import com.szmsd.common.core.constant.HttpStatus;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import com.szmsd.common.core.web.controller.QueryDto;
import com.szmsd.common.security.utils.SecurityUtils;
import com.szmsd.delivery.dto.*;
import com.szmsd.delivery.service.IDelOutboundService;
import com.szmsd.exception.dto.ExceptionInfoExportDto;
import com.szmsd.finance.domain.AccountBalance;
import com.szmsd.system.api.domain.SysUser;
import org.apache.commons.io.IOUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import com.szmsd.common.core.domain.R;
import org.springframework.web.bind.annotation.*;
import com.szmsd.delivery.service.IDelQueryServiceService;
import com.szmsd.delivery.domain.DelQueryService;
import com.szmsd.common.log.annotation.Log;
import com.szmsd.common.core.web.page.TableDataInfo;
import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.szmsd.common.core.utils.poi.ExcelUtil;
import com.szmsd.common.log.enums.BusinessType;
import io.swagger.annotations.Api;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;
import java.io.IOException;
import java.util.Locale;

import org.springframework.web.bind.annotation.RestController;
import io.swagger.annotations.ApiOperation;
import com.szmsd.common.core.web.controller.BaseController;
import org.springframework.web.multipart.MultipartFile;


/**
* <p>
    * ???????????? ???????????????
    * </p>
*
* @author Administrator
* @since 2022-06-08
*/


@Api(tags = {"????????????"})
@RestController
@RequestMapping("/del-query-service")
public class DelQueryServiceController extends BaseController{

     @Resource
     private IDelQueryServiceService delQueryServiceService;




     /**
       * ??????????????????????????????
     */
      @PreAuthorize("@ss.hasPermi('DelQueryService:DelQueryService:list')")
      @GetMapping("/list")
      @ApiOperation(value = "??????????????????????????????",notes = "??????????????????????????????")
      public R<PageInfo<DelQueryService>> list(DelQueryServiceDto delQueryService)
     {
          return delQueryServiceService.selectDelQueryServiceListrs(delQueryService);
      }


    @PostMapping("/importTemplate")
    @ApiOperation(httpMethod = "POST", value = "????????????")
    public void importTemplate(HttpServletResponse response) throws IOException {
      String len=getLen().toLowerCase(Locale.ROOT);
      if (len.equals("zh")){
          commonExport(response, "DelQueryService");
      }else if (len.equals("en")){
          commonExport(response, "CombinedParcelOutboundTemplate");
      }

//        ExcelUtil<DelQueryServiceImport> util = new ExcelUtil<DelQueryServiceImport>(DelQueryServiceImport.class);
//        util.importTemplateExcel(response, "DelQueryService");
    }

    @PostMapping("/importTemplatefk")
    @ApiOperation(httpMethod = "POST", value = "????????????")
    public void importTemplatefk(HttpServletResponse response) throws IOException {
        String len=getLen().toLowerCase(Locale.ROOT);
        if (len.equals("zh")){
            commonExport(response, "?????????????????????");
        }else if (len.equals("en")){
            commonExport(response, "CombinedParcelOutboundTemplate");
        }

//        ExcelUtil<DelQueryServiceImport> util = new ExcelUtil<DelQueryServiceImport>(DelQueryServiceImport.class);
//        util.importTemplateExcel(response, "DelQueryService");
    }

    private String getFileName(String fileName) {
        return String.format("/template/%s_%s.xlsx", fileName, getLen().toLowerCase(Locale.ROOT));
    }

    private void commonExport(HttpServletResponse response, String fileName) {
        //"/template/??????????????????.xls"
        ClassPathResource classPathResource = new ClassPathResource(getFileName(fileName));
        try (InputStream inputStream = classPathResource.getInputStream();
             ServletOutputStream outputStream = response.getOutputStream()) {

            response.setContentType("application/vnd.ms-excel;charset=utf-8");
            response.setCharacterEncoding("UTF-8");
            String excelName = URLEncoder.encode(fileName, "UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + excelName + ".xls");
            IOUtils.copy(inputStream, outputStream);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @PostMapping("/importData")
    @ApiOperation(httpMethod = "POST", value = "????????????????????????")
    public R importData(MultipartFile file, HttpServletRequest httpServletRequest) throws Exception {

        List<DelQueryServiceImportExcle> list1 = EasyExcel.read(file.getInputStream(), DelQueryServiceImportExcle.class, new SyncReadListener()).sheet().doReadSync();

//        ExcelUtil<DelQueryServiceImport> util = new ExcelUtil<DelQueryServiceImport>(DelQueryServiceImport.class);
//        List<DelQueryServiceImport> list = util.importExcel(file.getInputStream());

        //?????????????????????
        List<DelQueryServiceImport> list = BeanMapperUtil.mapList(list1, DelQueryServiceImport.class);


        list.forEach(x->{
            x.setOperationType(Integer.parseInt(httpServletRequest.getParameter("operationType")));

        });
        return delQueryServiceService.importData(list);
    }

    @PostMapping("/importDatafk")
    @ApiOperation(httpMethod = "POST", value = "??????????????????????????????")
    public R importDatafk(MultipartFile file, HttpServletRequest httpServletRequest) throws Exception {

        List<DelQueryServiceImportFkExcle> list1 = EasyExcel.read(file.getInputStream(), DelQueryServiceImportFkExcle.class, new SyncReadListener()).sheet().doReadSync();



        return delQueryServiceService.importDatafk(list1);
    }

    /**
    * ??????????????????????????????
    */
     @PreAuthorize("@ss.hasPermi('DelQueryService:DelQueryService:export')")
     @Log(title = "??????????????????", businessType = BusinessType.EXPORT)
     @GetMapping("/export")
     @ApiOperation(value = "??????????????????????????????",notes = "??????????????????????????????")
     public void export(HttpServletResponse response, DelQueryServiceDto delQueryService) throws IOException {
     List<DelQueryServiceExc> list = delQueryServiceService.selectDelQueryServiceListex(delQueryService);
         ExportParams params = new ExportParams();
        //params.setTitle("????????????");




         Workbook workbook = ExcelExportUtil.exportExcel(params, DelQueryServiceExc.class, list);


         Sheet sheet= workbook.getSheet("sheet0");

         //?????????????????????
         Row row2 =sheet.getRow(0);

         for (int i=0;i<14;i++){
             Cell deliveryTimeCell = row2.getCell(i);

             CellStyle styleMain = workbook.createCellStyle();
//             if (i==18){
//                 styleMain.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
//             }else {
//                 styleMain.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
//
//             }
             styleMain.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
             Font font = workbook.createFont();
             //true??????????????????????????????
             font.setBold(true);
             //??????????????????????????????????????????????????????????????????
             font.setColor(IndexedColors.WHITE.getIndex());
             //??????????????????????????????????????????
             styleMain.setFont(font);

             styleMain.setFillPattern(FillPatternType.SOLID_FOREGROUND);
             styleMain.setAlignment(HorizontalAlignment.CENTER);
             styleMain.setVerticalAlignment(VerticalAlignment.CENTER);
//        CellStyle style =  workbook.createCellStyle();
//        style.setFillPattern(HSSFColor.HSSFColorPredefined.valueOf(""));
//        style.setFillForegroundColor(IndexedColors.RED.getIndex());
             deliveryTimeCell.setCellStyle(styleMain);
         }

         //?????????????????????
         Row row3 =sheet.getRow(1);
         for (int x=13;x<16;x++) {
             Cell deliveryTimeCell1 = row3.getCell(x);
             CellStyle styleMain1 = workbook.createCellStyle();
             styleMain1.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
             Font font1 = workbook.createFont();
             //true??????????????????????????????
             font1.setBold(true);
             //??????????????????????????????????????????????????????????????????
             font1.setColor(IndexedColors.WHITE.getIndex());
             //??????????????????????????????????????????
             styleMain1.setFont(font1);

             styleMain1.setFillPattern(FillPatternType.SOLID_FOREGROUND);
             styleMain1.setAlignment(HorizontalAlignment.CENTER);
             styleMain1.setVerticalAlignment(VerticalAlignment.CENTER);
             deliveryTimeCell1.setCellStyle(styleMain1);
         }

         try {
             String fileName="??????????????????"+System.currentTimeMillis();
             URLEncoder.encode(fileName, "UTF-8");
             //response.setHeader("Content-Disposition", "attachment;filename=" + new String(fileName.getBytes(), "ISO8859-1"));
             response.setContentType("application/vnd.ms-excel");
             response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8") + ".xls");

             response.addHeader("Pargam", "no-cache");
             response.addHeader("Cache-Control", "no-cache");

             ServletOutputStream outStream = null;
             try {
                 outStream = response.getOutputStream();
                 workbook.write(outStream);
                 outStream.flush();
             } finally {
                 outStream.close();
             }
         } catch (Exception e) {
             e.printStackTrace();
         }

     }


    /**
     * ??????????????????????????????
     */
    @PreAuthorize("@ss.hasPermi('DelQueryService:DelQueryService:exporterror')")
    @Log(title = "????????????????????????????????????", businessType = BusinessType.EXPORT)
    @GetMapping("/exporterror")
    @ApiOperation(value = "????????????????????????????????????",notes = "????????????????????????????????????")
    public void exporterror(HttpServletResponse response) throws IOException {
        List<DelQueryServiceExcErroe> list = delQueryServiceService.selectDelQueryServiceExcErroe();
        ExportParams params = new ExportParams();





        Workbook workbook = ExcelExportUtil.exportExcel(params, DelQueryServiceExcErroe.class, list);


        Sheet sheet= workbook.getSheet("sheet0");

        //?????????????????????
        Row row2 =sheet.getRow(0);

        for (int i=0;i<3;i++){
            Cell deliveryTimeCell = row2.getCell(i);

            CellStyle styleMain = workbook.createCellStyle();
//             if (i==18){
//                 styleMain.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
//             }else {
//                 styleMain.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
//
//             }
            styleMain.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            Font font = workbook.createFont();
            //true??????????????????????????????
            font.setBold(true);
            //??????????????????????????????????????????????????????????????????
            font.setColor(IndexedColors.WHITE.getIndex());
            //??????????????????????????????????????????
            styleMain.setFont(font);

            styleMain.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            styleMain.setAlignment(HorizontalAlignment.CENTER);
            styleMain.setVerticalAlignment(VerticalAlignment.CENTER);
//        CellStyle style =  workbook.createCellStyle();
//        style.setFillPattern(HSSFColor.HSSFColorPredefined.valueOf(""));
//        style.setFillForegroundColor(IndexedColors.RED.getIndex());
            deliveryTimeCell.setCellStyle(styleMain);
        }



        try {
            String fileName="????????????????????????"+System.currentTimeMillis();
            URLEncoder.encode(fileName, "UTF-8");
            //response.setHeader("Content-Disposition", "attachment;filename=" + new String(fileName.getBytes(), "ISO8859-1"));
            response.setContentType("application/vnd.ms-excel");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8") + ".xls");

            response.addHeader("Pargam", "no-cache");
            response.addHeader("Cache-Control", "no-cache");

            ServletOutputStream outStream = null;
            try {
                outStream = response.getOutputStream();
                workbook.write(outStream);
                outStream.flush();
            } finally {
                outStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
    * ????????????????????????????????????
    */
    @PreAuthorize("@ss.hasPermi('DelQueryService:DelQueryService:query')")
    @GetMapping(value = "getInfo/{id}")
    @ApiOperation(value = "????????????????????????????????????",notes = "????????????????????????????????????")
    public R getInfo(@PathVariable("id") String id)
    {
    return R.ok(delQueryServiceService.selectDelQueryServiceById(id));
    }

    /**
     * ?????????????????????????????????
     */
    @PreAuthorize("@ss.hasPermi('DelQueryService:DelQueryService:getOrderInfo')")
    @GetMapping(value = "getOrderInfo/{orderNo}/{operationType}")
    @ApiOperation(value = "?????????????????????????????????",notes = "?????????????????????????????????")
    public R getOrderInfo(@PathVariable("orderNo") String orderNo,@PathVariable("operationType") Integer operationType)
    {
        return R.ok(delQueryServiceService.getOrderInfo(orderNo,operationType));
    }

    /**
    * ????????????????????????
    */
    @PreAuthorize("@ss.hasPermi('DelQueryService:DelQueryService:add')")
    @Log(title = "??????????????????", businessType = BusinessType.INSERT)
    @PostMapping("add")
    @ApiOperation(value = "????????????????????????",notes = "????????????????????????")
    public R add(@RequestBody DelQueryService delQueryService)
    {
    return toOk(delQueryServiceService.insertDelQueryService(delQueryService));
    }

    /**
    * ????????????????????????
    */
    @PreAuthorize("@ss.hasPermi('DelQueryService:DelQueryService:edit')")
    @Log(title = "??????????????????", businessType = BusinessType.UPDATE)
    @PutMapping("edit")
    @ApiOperation(value = " ????????????????????????",notes = "????????????????????????")
    public R edit(@RequestBody DelQueryService delQueryService)
    {
    return toOk(delQueryServiceService.updateDelQueryService(delQueryService));
    }

    /**
    * ????????????????????????
    */
    @PreAuthorize("@ss.hasPermi('DelQueryService:DelQueryService:remove')")
    @Log(title = "??????????????????", businessType = BusinessType.DELETE)
    @DeleteMapping("remove")
    @ApiOperation(value = "????????????????????????",notes = "????????????????????????")
    public R remove(@RequestBody List<String> ids)
    {
    return toOk(delQueryServiceService.deleteDelQueryServiceByIds(ids));
    }

}
