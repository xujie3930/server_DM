package com.szmsd.delivery.controller;

import cn.hutool.core.io.IoUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.EasyExcelFactory;
import com.alibaba.excel.event.SyncReadListener;
import com.alibaba.excel.read.builder.ExcelReaderSheetBuilder;
import com.szmsd.bas.api.client.BasSubClientService;
import com.szmsd.bas.api.domain.dto.BasRegionSelectListQueryDto;
import com.szmsd.bas.api.domain.vo.BasRegionSelectListVO;
import com.szmsd.bas.api.feign.BasRegionFeignService;
import com.szmsd.bas.api.service.BaseProductClientService;
import com.szmsd.bas.domain.BaseProduct;
import com.szmsd.bas.dto.BaseProductConditionQueryDto;
import com.szmsd.bas.plugin.vo.BasSubWrapperVO;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.AssertUtil;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.utils.SpringUtils;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.delivery.dto.*;
import com.szmsd.delivery.imported.*;
import com.szmsd.delivery.service.IDelOutboundService;
import com.szmsd.delivery.vo.DelOutboundAddResponse;
import com.szmsd.inventory.api.service.InventoryFeignClientService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiSort;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ????????????
 *
 * @author asd
 * @since 2021-03-05
 */
@Api(tags = {"???????????? - ??????"})
@ApiSort(900)
@RestController
@RequestMapping("/api/outbound/import")
public class DelOutboundImportController extends BaseController {
    private Logger logger = LoggerFactory.getLogger(DelOutboundImportController.class);

    @Autowired
    private IDelOutboundService delOutboundService;
    @Autowired
    private BasSubClientService basSubClientService;
    @SuppressWarnings({"all"})
    @Autowired
    private BasRegionFeignService basRegionFeignService;
    @Autowired
    private InventoryFeignClientService inventoryFeignClientService;

    @Autowired
    private BaseProductClientService baseProductClientService;

    @PreAuthorize("@ss.hasPermi('DelOutbound:DelOutboundImport:collectionExportTemplate')")
    @GetMapping("/collectionExportTemplate")
    @ApiOperation(value = "???????????? - ?????? - ???????????? - SKU????????????", position = 100)
    public void collectionExportTemplate(HttpServletResponse response) {
        String len=getLen().toLowerCase(Locale.ROOT);
        String filePath=null;
        String fileName=null;
        if (len.equals("zh")){
             filePath = "/template/Del_collection_sku_import.xls";
             fileName = "??????SKU????????????";
        }else if (len.equals("en")){
            filePath = "/template/AddedSKUImportTemplate.xlsx";
            fileName = "AddedSKUImportTemplate";
        }

        this.downloadTemplate(response, filePath, fileName);
    }

    @PreAuthorize("@ss.hasPermi('DelOutbound:DelOutboundImport:collectionImportDetail')")
    @PostMapping("/collectionImportDetail")
    @ApiOperation(value = "???????????? - ?????? - ???????????? - SKU??????", position = 200)
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "form", dataType = "__file", name = "file", value = "????????????", required = true, allowMultiple = true)
    })
    public R<ImportResultData<?>> collectionImportDetail(HttpServletRequest request) {
        MultipartHttpServletRequest multipartHttpServletRequest = (MultipartHttpServletRequest) request;
        MultipartFile file = multipartHttpServletRequest.getFile("file");
        AssertUtil.notNull(file, "The uploaded file does not exist");
        String originalFilename = file.getOriginalFilename();
        AssertUtil.notNull(originalFilename, "The import file name does not exist");
        int lastIndexOf = originalFilename.lastIndexOf(".");
        String suffix = originalFilename.substring(lastIndexOf + 1);
        if (!"xls".equals(suffix) && !"xlsx".equals(suffix)) {
            throw new CommonException("999", "Only upload xls,xlsx file");
        }
        try {
            List<DelOutboundCollectionDetailImportDto> dtoList = EasyExcel.read(file.getInputStream(), DelOutboundCollectionDetailImportDto.class, new SyncReadListener()).sheet().doReadSync();

//            ExcelReaderSheetBuilder excelReaderSheetBuilder = EasyExcelFactory.read(file.getInputStream(), DelOutboundCollectionDetailImportDto.class, null).sheet(0);
//            List<DelOutboundCollectionDetailImportDto> dtoList = excelReaderSheetBuilder.doReadSync();
            if (CollectionUtils.isEmpty(dtoList)) {
                return R.ok(ImportResultData.buildFailData(ImportMessage.build("Import data cannot be empty")));
            }
            // ??????????????????????????????????????????
            Map<String, List<BasSubWrapperVO>> listMap = this.basSubClientService.getSub("059,060,061");
            List<BasSubWrapperVO> productAttributeList = listMap.get("059");
            List<BasSubWrapperVO> electrifiedModeList = listMap.get("060");
            List<BasSubWrapperVO> batteryPackagingList = listMap.get("061");
            // SKU???????????????
            DelOutboundCollectionSkuImportContext importContext = new DelOutboundCollectionSkuImportContext(dtoList, productAttributeList, electrifiedModeList, batteryPackagingList);
            // ???????????????????????????
            ImportResultData<DelOutboundCollectionDetailImportDto> importResult = new ImportValidationContainer<>(importContext, ImportValidation.build(new DelOutboundCollectionSkuImportValidation(importContext))).validData();
            // ??????SKU??????????????????
            if (!importResult.isStatus()) {
                return R.ok(importResult);
            }
            // ?????????????????????
            return R.ok(ImportResultData.buildSuccessData(dtoList));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return R.failed("File parsing exception");
        }
    }

    @PreAuthorize("@ss.hasPermi('DelOutbound:DelOutboundImport:collectionImportTemplate')")
    @GetMapping("/collectionImportTemplate")
    @ApiOperation(value = "???????????? - ?????? - ????????????????????????", position = 300)
    public void collectionImportTemplate(HttpServletResponse response) {

        String len=getLen().toLowerCase(Locale.ROOT);
        String filePath=null;
        String fileName=null;
        if (len.equals("zh")){
            filePath = "/template/DM-CentralizedTransportation.xlsx";
            fileName = "??????????????????";
        }else if (len.equals("en")){
            filePath = "/template/DM-CentralizedTransportation-en.xlsx";
            fileName = "CentralizedTransportationTemplate";
        }



        this.downloadTemplate(response, filePath, fileName);
    }

    /**
     * ????????????
     *
     * @param response response
     * @param filePath ?????????????????????${server.tomcat.basedir}??????????????????resources?????????
     * @param fileName ????????????
     */
    private void downloadTemplate(HttpServletResponse response, String filePath, String fileName) {
        // ?????????????????????????????????
        // ??????????????????????????????????????????????????????
        String basedir = SpringUtils.getProperty("server.tomcat.basedir", "/u01/www/ck1/delivery");
        File file = new File(basedir + "/" + filePath);
        InputStream inputStream = null;
        ServletOutputStream outputStream = null;
        try {
            if (file.exists()) {
                inputStream = new FileInputStream(file);
                response.setHeader("File-Source", "local");
            } else {
                Resource resource = new ClassPathResource(filePath);
                inputStream = resource.getInputStream();
                response.setHeader("File-Source", "resource");
            }
            outputStream = response.getOutputStream();
            //response???HttpServletResponse??????
            response.setContentType("application/vnd.ms-excel;charset=utf-8");
            String efn = URLEncoder.encode(fileName, "utf-8");
            //Loading plan.xls??????????????????????????????????????????????????????????????????????????????
            response.setHeader("Content-Disposition", "attachment;filename=" + efn + ".xls");
            IOUtils.copy(inputStream, outputStream);
        } catch (FileNotFoundException e) {
            logger.error(e.getMessage(), e);
            throw new CommonException("999", "??????????????????" + e.getMessage());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new CommonException("999", "????????????????????????" + e.getMessage());
        } finally {
            IoUtil.flush(outputStream);
            IoUtil.close(outputStream);
            IoUtil.close(inputStream);
        }
    }

    @PreAuthorize("@ss.hasPermi('DelOutbound:DelOutboundImport:collectionImport')")
    @PostMapping("/collectionImport")
    @ApiOperation(value = "???????????? - ?????? - ??????????????????", position = 400)
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "form", dataType = "String", name = "sellerCode", value = "????????????", required = true),
            @ApiImplicitParam(paramType = "form", dataType = "__file", name = "file", value = "????????????", required = true, allowMultiple = true)
    })
    public R<ImportResult> collectionImport(@RequestParam("sellerCode") String sellerCode, HttpServletRequest request) {
        MultipartHttpServletRequest multipartHttpServletRequest = (MultipartHttpServletRequest) request;
        MultipartFile file = multipartHttpServletRequest.getFile("file");
        AssertUtil.notNull(file, "?????????????????????");
        AssertUtil.isTrue(StringUtils.isNotEmpty(sellerCode), "????????????????????????");
        try {
            // copy?????????
            byte[] byteArray = IOUtils.toByteArray(file.getInputStream());
            // ????????????????????????sheet????????????
            DefaultAnalysisEventListener<DelOutboundCollectionImportDto> defaultAnalysisEventListener = EasyExcelFactoryUtil.read(new ByteArrayInputStream(byteArray), DelOutboundCollectionImportDto.class, 0, 1);
            if (defaultAnalysisEventListener.isError()) {
                return R.ok(ImportResult.buildFail(defaultAnalysisEventListener.getMessageList()));
            }
            List<DelOutboundCollectionImportDto> dataList = defaultAnalysisEventListener.getList();
            if (CollectionUtils.isEmpty(dataList)) {
                return R.ok(ImportResult.buildFail(ImportMessage.build("????????????????????????")));
            }
            // ????????????????????????sheet????????????
            DefaultAnalysisEventListener<DelOutboundCollectionDetailImportDto2> defaultAnalysisEventListener1 = EasyExcelFactoryUtil.read(new ByteArrayInputStream(byteArray), DelOutboundCollectionDetailImportDto2.class, 1, 1);
            if (defaultAnalysisEventListener1.isError()) {
                return R.ok(ImportResult.buildFail(defaultAnalysisEventListener1.getMessageList()));
            }
            List<DelOutboundCollectionDetailImportDto2> detailList = defaultAnalysisEventListener1.getList();
            if (CollectionUtils.isEmpty(detailList)) {
                return R.ok(ImportResult.buildFail(ImportMessage.build("??????????????????????????????")));
            }

            Map<String, List<BasSubWrapperVO>> listMap = this.basSubClientService.getSub("059,060,061,076");
            List<BasSubWrapperVO> productAttributeList = listMap.get("059");
            List<BasSubWrapperVO> electrifiedModeList = listMap.get("060");
            List<BasSubWrapperVO> batteryPackagingList = listMap.get("061");
            List<BasSubWrapperVO> packageConfirmList = listMap.get("076");


            // ??????????????????
            R<List<BasRegionSelectListVO>> countryListR = this.basRegionFeignService.countryList(new BasRegionSelectListQueryDto());


            List<String> detailSkuList = new ArrayList<>();

            for(DelOutboundCollectionDetailImportDto2 detailImportDto2: detailList){
                if(StringUtils.isNotEmpty(detailImportDto2.getCode())){
                    detailSkuList.add(detailImportDto2.getCode());
                }
            }
            Map<String, BaseProduct> productMap = new HashMap<>();
            if(!detailSkuList.isEmpty()){
                BaseProductConditionQueryDto conditionQueryDto = new BaseProductConditionQueryDto();
                conditionQueryDto.setSkus(detailSkuList);
                List<BaseProduct> productList = this.baseProductClientService.queryProductList(conditionQueryDto);
                if (CollectionUtils.isNotEmpty(productList)) {
                    productMap = productList.stream().collect(Collectors.toMap(BaseProduct::getCode, v -> v, (v, v2) -> v));
                }
            }


            List<BasRegionSelectListVO> countryList = R.getDataAndException(countryListR);
            // ????????????????????????
            DelOutboundCollectionImportContext importContext = new DelOutboundCollectionImportContext(dataList, countryList);
            // ??????????????????????????????
            DelOutboundOuterContext outerContext = new DelOutboundOuterContext();
            // ???????????????????????????
            ImportResult importResult = new ImportValidationContainer<>(importContext, ImportValidation.build(new DelOutboundCollectionImportValidation(outerContext, importContext))).valid();
            // ????????????????????????
            if (!importResult.isStatus()) {
                return R.ok(importResult);
            }
            // ?????????SKU???????????????
            DelOutboundDetailImportValidationData importValidationData = new DelOutboundDetailImportValidationData(sellerCode, inventoryFeignClientService);
            // ?????????SKU???????????????
            DelOutboundCollectionDetailImportContext importContext1 = new DelOutboundCollectionDetailImportContext(detailList, productAttributeList, electrifiedModeList, batteryPackagingList, productMap);
            // ?????????SKU??????????????????
            ImportResult importResult1 = new ImportValidationContainer<>(importContext1, ImportValidation.build(new DelOutboundCollectionDetailImportValidation(outerContext, importContext1))).valid();
            // ??????SKU??????????????????
            if (!importResult1.isStatus()) {
                return R.ok(importResult1);
            }



            // ?????????????????????
            List<DelOutboundDto> dtoList = new DelOutboundCollectionImportContainer(dataList, countryList, detailList, importValidationData, sellerCode, productMap).get();
            // ????????????
            // ????????????
            List<DelOutboundAddResponse> outboundAddResponseList = this.delOutboundService.insertDelOutbounds(dtoList);
            List<ImportMessage> messageList = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(outboundAddResponseList)) {
                int index = 1;
                for (DelOutboundAddResponse outboundAddResponse : outboundAddResponseList) {
                    if (!outboundAddResponse.getStatus()) {
                        messageList.add(new ImportMessage(index, 1, "", outboundAddResponse.getMessage()));
                    }
                    index++;
                }
            }
            // ?????????????????????
            ImportResult importResult2;
            if (CollectionUtils.isNotEmpty(messageList)) {
                importResult2 = ImportResult.buildFail(messageList);
            } else {
                importResult2 = ImportResult.buildSuccess();
            }
            importResult2.setResultList(outboundAddResponseList);
            // ?????????????????????
            return R.ok(importResult2);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            // ?????????????????????
            return R.ok(ImportResult.buildFail(ImportMessage.build(e.getMessage())));
        }
    }

    @PreAuthorize("@ss.hasPermi('DelOutbound:DelOutboundImport:packageTransferImportTemplate')")
    @GetMapping("/packageTransferImportTemplate")
    @ApiOperation(value = "???????????? - ?????? - ????????????????????????", position = 500)
    public void packageTransferImportTemplate(HttpServletResponse response) {

        String len=getLen().toLowerCase(Locale.ROOT);
        String filePath=null;
        String fileName=null;
        if (len.equals("zh")){
            filePath = "/template/DM_packageTransfer.xlsx";
            fileName = "??????????????????";
        }else if (len.equals("en")){
            filePath = "/template/DM_packageTransfer-en.xlsx";
            fileName = "PackageTransferTemplate";
        }



        this.downloadTemplate(response, filePath, fileName);

    }

    @PreAuthorize("@ss.hasPermi('DelOutbound:DelOutboundImport:packageTransferImport')")
    @PostMapping("/packageTransferImport")
    @ApiOperation(value = "???????????? - ?????? - ??????????????????", position = 501)
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "form", dataType = "String", name = "sellerCode", value = "????????????", required = true),
            @ApiImplicitParam(paramType = "form", dataType = "__file", name = "file", value = "????????????", required = true, allowMultiple = true)
    })
    public R<ImportResult> packageTransferImport(@RequestParam("sellerCode") String sellerCode, HttpServletRequest request) {
        MultipartHttpServletRequest multipartHttpServletRequest = (MultipartHttpServletRequest) request;
        MultipartFile file = multipartHttpServletRequest.getFile("file");
        AssertUtil.notNull(file, "?????????????????????");
        AssertUtil.isTrue(StringUtils.isNotEmpty(sellerCode), "????????????????????????");
        try {
            // copy?????????
            byte[] byteArray = IOUtils.toByteArray(file.getInputStream());
            // ????????????????????????sheet????????????
            PackageTransferDefaultAnalysisFormat packageTransferDefaultAnalysisFormat = new PackageTransferDefaultAnalysisFormat();
            DefaultAnalysisEventListener<DelOutboundPackageTransferImportDto> defaultAnalysisEventListener = EasyExcelFactoryUtil.read(new ByteArrayInputStream(byteArray), DelOutboundPackageTransferImportDto.class, 0, 1, packageTransferDefaultAnalysisFormat);
            if (defaultAnalysisEventListener.isError()) {
                return R.ok(ImportResult.buildFail(defaultAnalysisEventListener.getMessageList()));
            }
            List<DelOutboundPackageTransferImportDto> dataList = defaultAnalysisEventListener.getList();
            if (CollectionUtils.isEmpty(dataList)) {
                return R.ok(ImportResult.buildFail(ImportMessage.build("????????????????????????")));
            }
            // ????????????????????????sheet????????????
            DefaultAnalysisEventListener<DelOutboundPackageTransferDetailImportDto> defaultAnalysisEventListener1 = EasyExcelFactoryUtil.read(new ByteArrayInputStream(byteArray), DelOutboundPackageTransferDetailImportDto.class, 1, 1);
            if (defaultAnalysisEventListener1.isError()) {
                return R.ok(ImportResult.buildFail(defaultAnalysisEventListener1.getMessageList()));
            }
            List<DelOutboundPackageTransferDetailImportDto> detailList = defaultAnalysisEventListener1.getList();
            if (CollectionUtils.isEmpty(detailList)) {
                return R.ok(ImportResult.buildFail(ImportMessage.build("??????????????????????????????")));
            }
            // ??????????????????????????????????????????
            Map<String, List<BasSubWrapperVO>> listMap = this.basSubClientService.getSub("059,060,061,076");
            List<BasSubWrapperVO> productAttributeList = listMap.get("059");
            List<BasSubWrapperVO> electrifiedModeList = listMap.get("060");
            List<BasSubWrapperVO> batteryPackagingList = listMap.get("061");
            List<BasSubWrapperVO> packageConfirmList = listMap.get("076");
            // ??????????????????
            R<List<BasRegionSelectListVO>> countryListR = this.basRegionFeignService.countryList(new BasRegionSelectListQueryDto());
            List<BasRegionSelectListVO> countryList = R.getDataAndException(countryListR);
            // ????????????????????????
            DelOutboundPackageTransferImportContext importContext = new DelOutboundPackageTransferImportContext(dataList, countryList, packageConfirmList);
            // ??????????????????????????????
            DelOutboundOuterContext outerContext = new DelOutboundOuterContext();
            // ???????????????????????????
            ImportResult importResult = new ImportValidationContainer<>(importContext, ImportValidation.build(new DelOutboundPackageTransferImportValidation(outerContext, importContext))).valid();
            // ????????????????????????
            if (!importResult.isStatus()) {
                return R.ok(importResult);
            }
            // ?????????SKU???????????????
            DelOutboundPackageTransferDetailImportContext importContext1 = new DelOutboundPackageTransferDetailImportContext(detailList, productAttributeList, electrifiedModeList, batteryPackagingList);
            // ?????????SKU??????????????????
            ImportResult importResult1 = new ImportValidationContainer<>(importContext1, ImportValidation.build(new DelOutboundPackageTransferDetailImportValidation(outerContext, importContext1))).valid();
            // ??????SKU??????????????????
            if (!importResult1.isStatus()) {
                return R.ok(importResult1);
            }
            // ?????????????????????
            List<DelOutboundDto> dtoList = new DelOutboundPackageTransferImportContainer(dataList, countryList, packageConfirmList, detailList, sellerCode).get();
            // ????????????
            List<DelOutboundAddResponse> outboundAddResponseList = this.delOutboundService.insertDelOutbounds(dtoList);
            List<ImportMessage> messageList = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(outboundAddResponseList)) {
                int index = 1;
                for (DelOutboundAddResponse outboundAddResponse : outboundAddResponseList) {
                    if (!outboundAddResponse.getStatus()) {
                        messageList.add(new ImportMessage(index, 1, "", outboundAddResponse.getMessage()));
                    }
                    index++;
                }
            }
            // ?????????????????????
            ImportResult importResult2;
            if (CollectionUtils.isNotEmpty(messageList)) {
                importResult2 = ImportResult.buildFail(messageList);
            } else {
                importResult2 = ImportResult.buildSuccess();
            }
            importResult2.setResultList(outboundAddResponseList);
            return R.ok(importResult2);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            // ?????????????????????
            return R.ok(ImportResult.buildFail(ImportMessage.build(e.getMessage())));
        }
    }

    @PreAuthorize("@ss.hasPermi('DelOutbound:DelOutboundImport:batchImportTemplate')")
    @GetMapping("/batchImportTemplate")
    @ApiOperation(value = "???????????? - ?????? - ????????????????????????", position = 600)
    public void batchImportTemplate(HttpServletResponse response) {
/*
        String len=getLen().toLowerCase(Locale.ROOT);
        String filePath=null;
        String fileName=null;
        if (len.equals("zh")){
            filePath = "/template/DM_batch.xlsx";
            fileName = "??????????????????";
        }else if (len.equals("en")){
            filePath = "/template/DM_batch-en.xlsx";
            fileName = "BatchIssueTemplate";
        }



        this.downloadTemplate(response, filePath, fileName);*/


        String filePath = "/template/DM_batch.xlsx";
        String fileName = "??????????????????";
        this.downloadTemplate(response, filePath, fileName);
    }

    @PreAuthorize("@ss.hasPermi('DelOutbound:DelOutboundImport:batchImport')")
    @PostMapping("/batchImport")
    @ApiOperation(value = "???????????? - ?????? - ??????????????????", position = 601)
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "form", dataType = "String", name = "sellerCode", value = "????????????", required = true),
            @ApiImplicitParam(paramType = "form", dataType = "__file", name = "file", value = "????????????", required = true, allowMultiple = true)
    })
    public R<ImportResult> batchImport(@RequestParam("sellerCode") String sellerCode, HttpServletRequest request) {
        MultipartHttpServletRequest multipartHttpServletRequest = (MultipartHttpServletRequest) request;
        MultipartFile file = multipartHttpServletRequest.getFile("file");
        AssertUtil.notNull(file, "?????????????????????");
        AssertUtil.isTrue(StringUtils.isNotEmpty(sellerCode), "????????????????????????");
        try {
            // copy?????????
            byte[] byteArray = IOUtils.toByteArray(file.getInputStream());
            // ????????????????????????sheet????????????
            DefaultAnalysisEventListener<DelOutboundBatchImportDto> defaultAnalysisEventListener = EasyExcelFactoryUtil.read(new ByteArrayInputStream(byteArray), DelOutboundBatchImportDto.class, 0, 1);
            if (defaultAnalysisEventListener.isError()) {
                return R.ok(ImportResult.buildFail(defaultAnalysisEventListener.getMessageList()));
            }
            List<DelOutboundBatchImportDto> dataList = defaultAnalysisEventListener.getList();
            if (CollectionUtils.isEmpty(dataList)) {
                return R.ok(ImportResult.buildFail(ImportMessage.build("????????????????????????")));
            }
            // ????????????????????????sheet????????????
            DefaultAnalysisEventListener<DelOutboundBatchDetailImportDto> defaultAnalysisEventListener1 = EasyExcelFactoryUtil.read(new ByteArrayInputStream(byteArray), DelOutboundBatchDetailImportDto.class, 1, 1);
            if (defaultAnalysisEventListener1.isError()) {
                return R.ok(ImportResult.buildFail(defaultAnalysisEventListener1.getMessageList()));
            }
            List<DelOutboundBatchDetailImportDto> detailList = defaultAnalysisEventListener1.getList();
            if (CollectionUtils.isEmpty(detailList)) {
                return R.ok(ImportResult.buildFail(ImportMessage.build("??????????????????????????????")));
            }
            // ????????????
            Map<String, List<BasSubWrapperVO>> listMap = this.basSubClientService.getSub("079");
            List<BasSubWrapperVO> shipmentChannelList = listMap.get("079");
            // ??????????????????
            R<List<BasRegionSelectListVO>> countryListR = this.basRegionFeignService.countryList(new BasRegionSelectListQueryDto());
            List<BasRegionSelectListVO> countryList = R.getDataAndException(countryListR);
            // ????????????????????????
            DelOutboundBatchImportContext importContext = new DelOutboundBatchImportContext(dataList, countryList, shipmentChannelList);
            // ??????????????????????????????
            DelOutboundOuterContext outerContext = new DelOutboundOuterContext();
            // ???????????????????????????
            ImportResult importResult = new ImportValidationContainer<>(importContext, ImportValidation.build(new DelOutboundBatchImportValidation(outerContext, importContext))).valid();
            // ????????????????????????
            if (!importResult.isStatus()) {
                return R.ok(importResult);
            }
            // ?????????SKU???????????????
            DelOutboundBatchDetailImportContext importContext1 = new DelOutboundBatchDetailImportContext(detailList);
            // ?????????SKU??????????????????
            ImportResult importResult1 = new ImportValidationContainer<>(importContext1, ImportValidation.build(new DelOutboundBatchDetailImportValidation(outerContext, importContext1))).valid();
            // ??????SKU??????????????????
            if (!importResult1.isStatus()) {
                return R.ok(importResult1);
            }
            // ?????????????????????
            List<DelOutboundDto> dtoList = new DelOutboundBatchImportContainer(dataList, countryList, shipmentChannelList, detailList, sellerCode).get();
            // ????????????
            List<DelOutboundAddResponse> outboundAddResponseList = this.delOutboundService.insertDelOutbounds(dtoList);
            List<ImportMessage> messageList = new ArrayList<>();
            if (CollectionUtils.isNotEmpty(outboundAddResponseList)) {
                int index = 1;
                for (DelOutboundAddResponse outboundAddResponse : outboundAddResponseList) {
                    if (!outboundAddResponse.getStatus()) {
                        messageList.add(new ImportMessage(index, 1, "", outboundAddResponse.getMessage()));
                    }
                    index++;
                }
            }
            // ?????????????????????
            ImportResult importResult2;
            if (CollectionUtils.isNotEmpty(messageList)) {
                importResult2 = ImportResult.buildFail(messageList);
            } else {
                importResult2 = ImportResult.buildSuccess();
            }
            importResult2.setResultList(outboundAddResponseList);
            // ?????????????????????
            return R.ok(importResult2);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            // ?????????????????????
            return R.ok(ImportResult.buildFail(ImportMessage.build(e.getMessage())));
        }
    }

}
