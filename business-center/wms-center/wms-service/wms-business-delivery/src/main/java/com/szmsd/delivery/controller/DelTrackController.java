package com.szmsd.delivery.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.szmsd.bas.api.client.BasSubClientService;
import com.szmsd.bas.api.feign.BasCarrierKeywordFeignService;
import com.szmsd.bas.plugin.vo.BasSubWrapperVO;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.AssertUtil;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.utils.SpringUtils;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import com.szmsd.common.core.utils.bean.BeanUtils;
import com.szmsd.common.core.utils.poi.ExcelUtil;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.log.annotation.Log;
import com.szmsd.common.log.enums.BusinessType;
import com.szmsd.common.plugin.annotation.AutoValue;
import com.szmsd.delivery.domain.DelOutbound;
import com.szmsd.delivery.domain.DelOutboundAddress;
import com.szmsd.delivery.domain.DelTrack;
import com.szmsd.delivery.dto.*;
import com.szmsd.delivery.event.ChangeDelOutboundLatestTrackEvent;
import com.szmsd.delivery.imported.DefaultAnalysisEventListener;
import com.szmsd.delivery.imported.EasyExcelFactoryUtil;
import com.szmsd.delivery.imported.ImportMessage;
import com.szmsd.delivery.imported.ImportResult;
import com.szmsd.delivery.service.IDelOutboundAddressService;
import com.szmsd.delivery.service.IDelOutboundService;
import com.szmsd.delivery.service.IDelTrackService;
import com.szmsd.delivery.util.SHA256Util;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * <p>
 * ???????????????
 * </p>
 *
 * @author YM
 * @since 2022-02-10
 */

@Slf4j
@Api(tags = {""})
@RestController
@RequestMapping("/del-track")
public class DelTrackController extends BaseController {

    @Resource
    private IDelTrackService delTrackService;

    @Value("${webhook.secret}")
    private String webhookSecret;

    @Resource
    private ApplicationContext applicationContext;

    @Autowired
    private BasSubClientService basSubClientService;

    @Autowired
    private IDelOutboundAddressService delOutboundAddressService;

    @Autowired
    private BasCarrierKeywordFeignService basCarrierKeywordFeignService;

    @Autowired
    private IDelOutboundService delOutboundService;
    /**
     * ??????????????????
     */
    @PreAuthorize("@ss.hasPermi('DelTrack:DelTrack:list')")
    @GetMapping("/list")
    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    @AutoValue
    public TableDataInfo list(DelTrack delTrack) {
        startPage();
        List<DelTrack> list = delTrackService.selectDelTrackList(delTrack);
        return getDataTable(list);
    }

    @PostMapping("/commonTrackList")
    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    @AutoValue
    public R<DelTrackMainCommonDto> commonTrackList(@RequestBody List<String> orderNos) {
        Map<String, List<BasSubWrapperVO>> listMap = this.basSubClientService.getSub("099");
        List<BasSubWrapperVO> delTrackStateTypeList = listMap.get("099");
        Map<String, BasSubWrapperVO> delTrackStateTypeMap
                = delTrackStateTypeList.stream().collect(Collectors.toMap(BasSubWrapperVO::getSubValue, Function.identity()));
        List<DelTrack> list = delTrackService.commonTrackList(orderNos);
        Map<String, Boolean> cacheMap = new HashMap<String, Boolean>();
        for (int i = 0; i < list.size(); i++) {
            DelTrack track = list.get(i);
            if(StringUtils.isEmpty(track.getCarrierCode())){
                continue;
            }
            boolean ignore = true;
            String key = track.getCarrierCode()+":"+track.getDescription();
            if(!cacheMap.containsKey(key)){
                R<Boolean> booleanR = this.basCarrierKeywordFeignService.checkExistKeyword(track.getCarrierCode(), track.getDisplay());
                if(null != booleanR && booleanR.getData() != null){
                    ignore = booleanR.getData();
                }
                cacheMap.put(key, ignore);
            }else{
                ignore = cacheMap.get(key);
            }
            if (ignore) {
                list.remove(i);
                i--;
            }
        }
        List<DelTrackCommonDto> newList = BeanMapperUtil.mapList(list, DelTrackCommonDto.class);
        for (DelTrackCommonDto dto: newList){
            BasSubWrapperVO vo  = delTrackStateTypeMap.get(dto.getTrackingStatus());
            if (vo != null) {
                dto.setTrackingStatusName(vo.getSubName());
            }
        }



        Set<String> threeSet = new TreeSet<String>();
        for(DelTrack delTrack: list){
            threeSet.add(delTrack.getOrderNo());
        }
        orderNos.clear();
        orderNos.addAll(threeSet);


        //????????????????????????
        java.util.Map<String, List<DelTrackCommonDto>> groupBy = newList.stream().collect(Collectors.groupingBy(DelTrackCommonDto::getOrderNo));
        Map<String, Integer> delTrackStateDto = new HashMap();
        for (String ordersNo: orderNos){
            List<DelTrackCommonDto> detailList = groupBy.get(ordersNo);
            if(detailList != null){
                String trackingStatus = detailList.get(0).getTrackingStatus();
                if(delTrackStateDto.containsKey(trackingStatus)){
                    delTrackStateDto.put(trackingStatus, delTrackStateDto.get(trackingStatus) + 1);
                }else{
                    delTrackStateDto.put(trackingStatus, 1);
                }
            }
        }

        //????????????
        List<DelTrackDetailDto> mainDetailDataList = new ArrayList();
        for (String ordersNo: orderNos){
            List<DelTrackCommonDto> detailList = groupBy.get(ordersNo);
            if(detailList != null){
                DelTrackDetailDto detailDto = new DelTrackDetailDto();
                mainDetailDataList.add(detailDto);
                BeanUtils.copyProperties(detailList.get(0), detailDto);
                detailDto.setTrackingList(detailList);

                //?????????????????????????????????
                long day = 0;
                if(detailDto.getTrackingTime() != null && detailList.get(detailList.size() - 1).getTrackingTime() != null){
                    day = DateUtil.betweenDay(detailDto.getTrackingTime(), detailList.get(detailList.size() - 1).getTrackingTime(),  true);
                    if(day < 0){
                        day = 0;
                    }
                }

                detailDto.setTrackDays(day);

            }
        }

        //??????????????????
        List<String> orders = mainDetailDataList.stream().map(e -> e.getOrderNo()).collect(Collectors.toList());
        if(orders.size() > 0){
            LambdaQueryWrapper<DelOutboundAddress> queryWrapper = Wrappers.lambdaQuery();
            queryWrapper.in(DelOutboundAddress::getOrderNo, orders);
            List<DelOutboundAddress> addressList = delOutboundAddressService.list(queryWrapper);
            java.util.Map<String, DelOutboundAddress> addressMap =
                    addressList.stream().collect(Collectors.toMap(DelOutboundAddress::getOrderNo, account -> account));
            for (DelTrackDetailDto dto: mainDetailDataList){
                DelOutboundAddress address = addressMap.get(dto.getOrderNo());
                if(address != null){
                    BeanUtils.copyProperties(address, dto);
                }
            }

        }


        DelTrackMainCommonDto mainDto = new DelTrackMainCommonDto();
        mainDto.setDelTrackStateDto(delTrackStateDto);
        mainDto.setTrackingList(mainDetailDataList);
        mainDto.setDelTrackStateTypeList(delTrackStateTypeList);
        return R.ok(mainDto);
    }

    /**
     * ??????????????????
     */
    @PreAuthorize("@ss.hasPermi('DelTrack:DelTrack:export')")
    @Log(title = "??????", businessType = BusinessType.EXPORT)
    @GetMapping("/export")
    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    public void export(HttpServletResponse response, DelTrack delTrack) throws IOException {
        List<DelTrack> list = delTrackService.selectDelTrackList(delTrack);
        ExcelUtil<DelTrack> util = new ExcelUtil<DelTrack>(DelTrack.class);
        util.exportExcel(response, list, "DelTrack");

    }

    @PreAuthorize("@ss.hasPermi('DelTrack:DelTrack:importTrackExcelTemplate')")
    @GetMapping("/importTrackExcelTemplate")
    @ApiOperation(value = "??????????????????", position = 100)
    public void collectionExportTemplate(HttpServletResponse response) {
        String filePath = "/template/DM_track.xlsx";
        String fileName = "????????????";
        this.downloadTemplate(response, filePath, fileName, "xlsx");
    }

    @Log(title = "??????", businessType = BusinessType.IMPORT)
    @ApiOperation(value = "??????", notes = "??????")
    @PostMapping("/importTrack")
    public R<ImportResult> importTrack(MultipartFile file){
        AssertUtil.notNull(file, "?????????????????????");
        try {
            byte[] byteArray = IOUtils.toByteArray(file.getInputStream());
            DefaultAnalysisEventListener<ImportTrackDto> defaultAnalysisEventListener = EasyExcelFactoryUtil.read(new ByteArrayInputStream(byteArray), ImportTrackDto.class, 0, 1);
            if (defaultAnalysisEventListener.isError()) {
                return R.ok(ImportResult.buildFail(defaultAnalysisEventListener.getMessageList()));
            }
            List<ImportTrackDto> dataList = defaultAnalysisEventListener.getList();
            if (CollectionUtils.isEmpty(dataList)) {
                return R.ok(ImportResult.buildFail(ImportMessage.build("????????????????????????")));
            }
            List<ImportMessage> messageList = new ArrayList<>();
            List<DelTrack> tracks = BeanMapperUtil.mapList(dataList, DelTrack.class);
            int i = 1;
            for(ImportTrackDto track : dataList){
                if (StringUtils.isBlank(track.getOrderNo())){
                    messageList.add(new ImportMessage(i, 1, null ,"?????????????????????" ));
                }
                if (StringUtils.isBlank(track.getTrackingNo())){
                    messageList.add(new ImportMessage(i, 2, null ,"???????????????????????????" ));
                }
                if (StringUtils.isBlank(track.getTrackingStatus())){
                    messageList.add(new ImportMessage(i, 1, null ,"????????????????????????" ));
                }
                i++;

            }
            if (CollectionUtils.isNotEmpty(messageList)) {
                return R.ok(ImportResult.buildFail(messageList));
            }
            tracks.forEach(track -> {
                track.setSource("2");
                track.setTrackingTime(new Date());
                applicationContext.publishEvent(new ChangeDelOutboundLatestTrackEvent(track));
            });
            delTrackService.saveBatch(tracks);
            return R.ok();
        } catch (IOException e) {
            e.printStackTrace();
            return R.ok(ImportResult.buildFail(ImportMessage.build(e.getMessage())));
        }
    }

    @PreAuthorize("@ss.hasPermi('DelTrack:DelTrack:addOrUpdate')")
    @Log(title = "??????", businessType = BusinessType.INSERT)
    @PostMapping("addOrUpdate")
    public R addOrUpdate(@RequestBody DelTrack delTrack){

        if(StringUtils.isEmpty(delTrack.getOrderNo()) || StringUtils.isEmpty(delTrack.getTrackingNo())){
            throw new CommonException("400", "?????????????????????????????????");
        }

        DelOutbound dataDelOutbound = delOutboundService.getByOrderNo(delTrack.getOrderNo());
        if(dataDelOutbound == null){
            throw new CommonException("400", "????????????????????????");
        }else if(!StringUtils.equals(dataDelOutbound.getTrackingNo(), delTrack.getTrackingNo())){
            throw new CommonException("400", "??????????????????????????????????????????");

        }

        delTrack.setSource("2"); // ????????????
        delTrack.setTrackingTime(new Date());
        delTrackService.saveOrUpdate(delTrack);
        applicationContext.publishEvent(new ChangeDelOutboundLatestTrackEvent(delTrack));
        return R.ok();
    }

    /**
     * ????????????
     */
    @PreAuthorize("@ss.hasPermi('DelTrack:DelTrack:remove')")
    @Log(title = "??????", businessType = BusinessType.DELETE)
    @DeleteMapping("remove")
    @ApiOperation(value = "????????????", notes = "????????????")
    public R remove(@RequestBody List<String> ids) {
        return toOk(delTrackService.deleteDelTrackByIds(ids));
    }


    @ApiOperation(value = "????????????TrackingYee?????????????????????", notes = "")
    @PostMapping("/traceCallback")
    public R traceCallback(@RequestHeader(value = "trackingyee-webhook-signature") String trackingyeeSign,@RequestBody Object params){
        if(params == null) {
            return R.failed("??????????????????????????????");
        }
        // ????????????
//        String trackingyeeSign = request.getHeader("trackingyee-webhook-signature");
        String requestStr = JSONObject.toJSONString(params, SerializerFeature.WriteMapNullValue);
        String verifySign = SHA256Util.getSHA256Str(webhookSecret + requestStr);
        log.info("trackingyeeSign: {}", trackingyeeSign);
        log.info("????????????????????????: {}", webhookSecret + requestStr);
        log.info("verifySign: {}", verifySign);
        TrackingYeeTraceDto trackingYeeTraceDto = JSONObject.parseObject(requestStr, TrackingYeeTraceDto.class);
        if (StringUtils.isBlank(trackingyeeSign) || !trackingyeeSign.equalsIgnoreCase(verifySign)) {
            return R.failed("??????????????????????????????");
        }
        if (trackingYeeTraceDto == null) {
            return R.failed("??????????????????????????????");
        }
        // ????????????
        delTrackService.traceCallback(trackingYeeTraceDto);
        return R.ok();
    }

    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    @GetMapping("getTrackAnalysis")
    public R getTrackAnalysis(TrackAnalysisRequestDto requestDto, @RequestHeader("langr") String langr){
        requestDto.setLang(langr);
        return R.ok(delTrackService.getTrackAnalysis(requestDto));
    }

    @ApiOperation(value = "?????????????????????????????????????????????????????????", notes = "?????????????????????????????????????????????????????????")
    @GetMapping("getProductAnalysis")
    public R getProductAnalysis(TrackAnalysisRequestDto requestDto){
        return R.ok(delTrackService.getProductServiceAnalysis(requestDto));
    }

    /**
     * ??????????????????
     */
    @PreAuthorize("@ss.hasPermi('DelTrack:DelTrack:exportTrackAnalysis')")
    @Log(title = "??????", businessType = BusinessType.EXPORT)
    @GetMapping("/exportTrackAnalysis")
    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    public void exportTrackAnalysis(HttpServletResponse response, TrackAnalysisRequestDto requestDto, @RequestHeader("langr") String langr) throws IOException {
        requestDto.setLang(langr);
        List<TrackAnalysisExportDto> list = delTrackService.getAnalysisExportData(requestDto);
        ExcelUtil<TrackAnalysisExportDto> util = new ExcelUtil<TrackAnalysisExportDto>(TrackAnalysisExportDto.class);
        util.exportExcel(response, list, "TrackAnalysis");
    }

    /**
     * ????????????
     *
     * @param response response
     * @param filePath ?????????????????????${server.tomcat.basedir}??????????????????resources?????????
     * @param fileName ????????????
     * @param ext      ?????????
     */
    private void downloadTemplate(HttpServletResponse response, String filePath, String fileName, String ext) {
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
                org.springframework.core.io.Resource resource = new ClassPathResource(filePath);
                inputStream = resource.getInputStream();
                response.setHeader("File-Source", "resource");
            }
            outputStream = response.getOutputStream();
            //response???HttpServletResponse??????
            response.setContentType("application/vnd.ms-excel;charset=utf-8");
            //Loading plan.xls??????????????????????????????????????????????????????????????????????????????
            String efn = URLEncoder.encode(fileName, "utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + efn + "." + ext);
            IOUtils.copy(inputStream, outputStream);
        } catch (FileNotFoundException e) {
            log.error(e.getMessage(), e);
            throw new CommonException("400", "??????????????????" + e.getMessage());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new CommonException("500", "????????????????????????" + e.getMessage());
        } finally {
            IoUtil.flush(outputStream);
            IoUtil.close(outputStream);
            IoUtil.close(inputStream);
        }
    }
}
