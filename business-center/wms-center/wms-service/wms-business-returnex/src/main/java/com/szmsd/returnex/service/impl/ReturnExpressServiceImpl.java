package com.szmsd.returnex.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.event.SyncReadListener;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.szmsd.bas.api.domain.BasCodeDto;
import com.szmsd.bas.api.domain.vo.BasRegionSelectListVO;
import com.szmsd.bas.api.feign.BasFeignService;
import com.szmsd.bas.api.feign.BaseProductFeignService;
import com.szmsd.bas.domain.BaseProduct;
import com.szmsd.bas.dto.BaseProductConditionQueryDto;
import com.szmsd.common.core.constant.HttpStatus;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.AssertUtil;
import com.szmsd.common.core.exception.web.BaseException;
import com.szmsd.common.core.web.domain.BaseEntity;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.security.domain.LoginUser;
import com.szmsd.common.security.utils.SecurityUtils;
import com.szmsd.delivery.api.feign.DelOutboundFeignService;
import com.szmsd.delivery.dto.DelOutboundAddressDto;
import com.szmsd.delivery.dto.DelOutboundDto;
import com.szmsd.delivery.dto.DelOutboundListQueryDto;
import com.szmsd.delivery.enums.DelOutboundStateEnum;
import com.szmsd.delivery.vo.DelOutboundAddResponse;
import com.szmsd.delivery.vo.DelOutboundAddressVO;
import com.szmsd.delivery.vo.DelOutboundListVO;
import com.szmsd.delivery.vo.DelOutboundVO;
import com.szmsd.http.dto.returnex.CreateExpectedReqDTO;
import com.szmsd.http.dto.returnex.ProcessingUpdateReqDTO;
import com.szmsd.http.dto.returnex.ReturnDetail;
import com.szmsd.http.dto.returnex.ReturnDetailWMS;
import com.szmsd.inventory.api.feign.InventoryFeignService;
import com.szmsd.inventory.domain.dto.InventoryAdjustmentDTO;
import com.szmsd.returnex.api.feign.client.IHttpFeignClientService;
import com.szmsd.returnex.command.ReturnExpressAutoGeneratorDestoryFeeCmd;
import com.szmsd.returnex.command.ReturnExpressAutoGeneratorFeeCmd;
import com.szmsd.returnex.config.BeanCopyUtil;
import com.szmsd.returnex.config.ConfigStatus;
import com.szmsd.returnex.config.IRemoteApi;
import com.szmsd.returnex.constant.ReturnExpressConstant;
import com.szmsd.returnex.domain.ReturnExpressDetail;
import com.szmsd.returnex.dto.*;
import com.szmsd.returnex.dto.wms.ReturnArrivalReqDTO;
import com.szmsd.returnex.dto.wms.ReturnProcessingFinishReqDTO;
import com.szmsd.returnex.dto.wms.ReturnProcessingReqDTO;
import com.szmsd.returnex.mapper.ReturnExpressMapper;
import com.szmsd.returnex.service.IReturnExpressGoodService;
import com.szmsd.returnex.service.IReturnExpressService;
import com.szmsd.returnex.vo.ReturnExpressGoodVO;
import com.szmsd.returnex.vo.ReturnExpressListVO;
import com.szmsd.returnex.vo.ReturnExpressVO;
import jodd.util.StringUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @ClassName: ReturnExpressServiceImpl
 * @Description: ??????
 * @Author: 11
 * @Date: 2021/3/26 11:47
 */
@Slf4j
@Service
public class ReturnExpressServiceImpl extends ServiceImpl<ReturnExpressMapper, ReturnExpressDetail> implements IReturnExpressService {

    @Resource
    private ReturnExpressMapper returnExpressMapper;

    @Resource
    private IHttpFeignClientService httpFeignClient;

    @Resource
    private IReturnExpressGoodService returnExpressGoodService;

    @Resource
    private BasFeignService basFeignService;

    @Resource
    private BaseProductFeignService baseProductFeignService;

    @Resource
    private ConfigStatus configStatus;

    @Resource
    private InventoryFeignService inventoryFeignService;

    @Resource
    private DelOutboundFeignService delOutboundFeignService;

    @Resource
    private IRemoteApi iRemoteApi;

    @Resource
    private ThreadPoolTaskExecutor returnThreadTaskPool;

    /**
     * ????????????sellerCode
     *
     * @return
     */
    private String getSellCode() {
        return Optional.ofNullable(SecurityUtils.getLoginUser()).map(LoginUser::getSellerCode).orElse("");
    }

    /**
     * ????????????
     *
     * @return
     */
    public String genNo() {
        String code = ReturnExpressConstant.GENERATE_CODE;
        String appId = ReturnExpressConstant.GENERATE_APP_ID;
//        log.info("???????????????????????????code={}", code);
        R<List<String>> r = basFeignService.create(new BasCodeDto().setAppId(appId).setCode(code));
        AssertUtil.notNull(r, "??????????????????");
        AssertUtil.isTrue(r.getCode() == HttpStatus.SUCCESS, code + "?????????????????????" + r.getMsg());
        String s = r.getData().get(0);
//        log.info("???????????????????????????????????????, {}-{}", code, s);
        return s;
    }

    /**
     * ???????????????-??????????????????
     *
     * @return ????????????
     */
    @Override
    public String createExpectedNo() {
        return genNo();
    }

    @Override
    public List<ReturnExpressListVO> selectClientReturnOrderList(ReturnExpressListQueryDTO queryDto) {
//        queryDto.setSellerCode(getSellCode());
        return selectReturnOrderList(queryDto);
    }

    /**
     * ????????????????????????
     * /api/return/details #G2-????????????????????????
     *
     * @param returnProcessingReqDTO ????????????
     * @return ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveProcessingInfoFromVms(ReturnProcessingReqDTO returnProcessingReqDTO) {
        log.info("??????WMS?????????????????? {}", returnProcessingReqDTO);
        ReturnExpressDetail detail = returnExpressMapper.selectOne(Wrappers.<ReturnExpressDetail>lambdaUpdate()
                .eq(ReturnExpressDetail::getDealStatus, configStatus.getDealStatus().getWmsWaitReceive())
                .eq(ReturnExpressDetail::getProcessType, configStatus.getUnpackingInspection())
                .eq(ReturnExpressDetail::getReturnNo, returnProcessingReqDTO.getReturnNo()));
        AssertUtil.notNull(detail, "???????????????!");

        //wms???????????????????????????????????? ??????????????????????????????
        ReturnExpressDetail returnExpressDetail = new ReturnExpressDetail();
        int update = returnExpressMapper.update(returnExpressDetail, Wrappers.<ReturnExpressDetail>lambdaUpdate()
                .eq(ReturnExpressDetail::getDealStatus, configStatus.getDealStatus().getWmsWaitReceive())
                .eq(ReturnExpressDetail::getProcessType, configStatus.getUnpackingInspection())
                .eq(ReturnExpressDetail::getReturnNo, returnProcessingReqDTO.getReturnNo())

                .set(ReturnExpressDetail::getDealStatus, configStatus.getDealStatus().getWaitCustomerDeal())
                .set(ReturnExpressDetail::getDealStatusStr, configStatus.getDealStatus().getWaitCustomerDealStr())
                .last("LIMIT 1")
        );
        AssertUtil.isTrue(1 == update, "???????????????????????????????????????");
        log.info("?????????????????? {}", returnExpressDetail);

        List<ReturnDetail> details = returnProcessingReqDTO.getDetails();
        if (CollectionUtils.isEmpty(details)) {
            log.info("?????????????????? ?????????????????????");
            return update;
        }
        List<ReturnExpressGoodAddDTO> returnExpressGoodAddDTOS = BeanCopyUtil.copyListProperties(details, ReturnExpressGoodAddDTO::new);
        returnExpressGoodService.addOrUpdateGoodInfoBatch(returnExpressGoodAddDTOS, detail.getId());
        return update;
    }

    /**
     * ??????????????? - ??????
     *
     * @param queryDto ????????????
     * @return ????????????
     */
    @Override
    public List<ReturnExpressListVO> selectReturnOrderList(ReturnExpressListQueryDTO queryDto) {
        String queryNo = queryDto.getQueryNoOne();
        if (com.szmsd.common.core.utils.StringUtils.isNotEmpty(queryNo)) {
            List<String> queryNoList = splitToArray(queryNo, "[\n,]");
            queryDto.setQueryNoOneList(queryNoList);
        }

        String queryNoTwo = queryDto.getQueryNoTwo();
        if (com.szmsd.common.core.utils.StringUtils.isNotEmpty(queryNoTwo)) {
            List<String> queryNoList = splitToArray(queryNoTwo, "[\n,]");
            queryDto.setQueryNoTwoList(queryNoList);
        }
        return returnExpressMapper.selectPageList(queryDto);
    }

    public static List<String> splitToArray(String text, String split) {
        String[] arr = text.split(split);
        if (arr.length == 0) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (String s : arr) {
            if (com.szmsd.common.core.utils.StringUtils.isEmpty(s)) {
                continue;
            }
            list.add(s);
        }
        return list;
    }

    /**
     * ????????????????????? - ??????
     *
     * @param queryDto ????????????
     * @return ????????????
     */
    @Override
    public List<ReturnExpressListVO> pageForNoUserBind(ReturnExpressListQueryDTO queryDto) {
        queryDto.setNoUserQuery(true);
        return selectReturnOrderList(queryDto);
    }

    /**
     * ???????????????????????????
     *
     * @param expressAssignDTO ????????????
     * @return ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assignUsersForNoUserBindBatch(ReturnExpressAssignDTO expressAssignDTO) {
        int update = returnExpressMapper.update(new ReturnExpressDetail(), Wrappers.<ReturnExpressDetail>lambdaUpdate()
                .isNull(ReturnExpressDetail::getSellerCode)
                .in(ReturnExpressDetail::getId, expressAssignDTO.getIds())
                .set(ReturnExpressDetail::getSellerCode, expressAssignDTO.getSellerCode())
                .set(ReturnExpressDetail::getDealStatus, configStatus.getDealStatus().getWaitCustomerDeal())
                .set(ReturnExpressDetail::getDealStatusStr, configStatus.getDealStatus().getWaitCustomerDealStr())
        );
        return update;
    }

    /**
     * ???????????????refNo
     *
     * @param returnExpressAddDTO
     */
    public void setRefNoByOrderNo(ReturnExpressAddDTO returnExpressAddDTO) {
        if (StringUtils.isNotBlank(returnExpressAddDTO.getRefNo())) return;
        DelOutboundListQueryDto delOutboundListQueryDto = new DelOutboundListQueryDto();
        delOutboundListQueryDto.setOrderNo(returnExpressAddDTO.getFromOrderNo());
        delOutboundListQueryDto.setQueryAll(true);
        TableDataInfo<DelOutboundListVO> page = delOutboundFeignService.page(delOutboundListQueryDto);
        List<DelOutboundListVO> rows = page.getRows();
        if (CollectionUtils.isNotEmpty(rows)) {
            DelOutboundListVO delOutboundListVO = rows.get(0);
            returnExpressAddDTO.setRefNo(delOutboundListVO.getRefNo());
        }
    }

    /**
     * ???????????????
     *
     * @param returnExpressAddDTO ??????
     * @return ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public <T extends ReturnExpressAddDTO> int insertReturnExpressDetail(T returnExpressAddDTO) {
        if (StringUtils.isBlank(returnExpressAddDTO.getSellerCode()))
            returnExpressAddDTO.setSellerCode(getSellCode());
        checkSubmit(returnExpressAddDTO);
        if (StringUtils.isBlank(returnExpressAddDTO.getExpectedNo())) {
            String expectedNo = createExpectedNo();
            returnExpressAddDTO.setExpectedNo(expectedNo);
            // ????????????????????????????????????????????????????????????????????????????????????????????????
            if (StringUtils.isBlank(returnExpressAddDTO.getReturnNo())) {
                returnExpressAddDTO.setReturnNo(expectedNo);
            }
        }
        handleExpectedCreate(returnExpressAddDTO);
        this.setRefNoByOrderNo(returnExpressAddDTO);
        return saveReturnExpressDetail(returnExpressAddDTO.convertThis(ReturnExpressDetail.class));
    }

    /**
     * ?????????????????????????????? ???????????????????????????PRC????????????????????????????????????????????????????????????????????????????????????????????????WMS
     *
     * @param returnExpressAddDTO
     */
    public void makeNewOutboundOrder(ReturnExpressServiceAddDTO returnExpressAddDTO) {
        //TODO
        boolean reassign = returnExpressAddDTO.getReturnType().equals(configStatus.getReassign());
        if (reassign) {
            log.info("?????????????????????{}", returnExpressAddDTO);
            String returnNo = returnExpressAddDTO.getReturnNo();
        }
    }

    /**
     * ??????wms ???????????????
     *
     * @param returnExpressAddDTO
     */
    private <T extends ReturnExpressAddDTO> void handleExpectedCreate(T returnExpressAddDTO) {
        //?????????????????????????????????????????????????????? ?????????????????????????????????
        if (!"070003".equals(returnExpressAddDTO.getReturnType())) {
            DelOutboundListQueryDto delOutboundListQueryDto = new DelOutboundListQueryDto();
            delOutboundListQueryDto.setOrderNo(returnExpressAddDTO.getFromOrderNo());
            delOutboundListQueryDto.setQueryAll(true);
            TableDataInfo<DelOutboundListVO> page = delOutboundFeignService.page(delOutboundListQueryDto);
            if (page != null && page.getCode() == 200) {
                List<DelOutboundListVO> rows = page.getRows();
                if (CollectionUtils.isNotEmpty(rows)) {
                    DelOutboundListVO delOutboundListVO = rows.get(0);
                    boolean equals = delOutboundListVO.getState().equals(DelOutboundStateEnum.COMPLETED.getCode());
                    AssertUtil.isTrue(equals, "??????????????????" + returnExpressAddDTO.getFromOrderNo() + "?????????/?????????!");
                    if (StringUtils.isBlank(returnExpressAddDTO.getSellerCode()))
                        returnExpressAddDTO.setSellerCode(delOutboundListVO.getCustomCode());
                    if (StringUtils.isBlank(returnExpressAddDTO.getScanCode()))
                        returnExpressAddDTO.setScanCode(delOutboundListVO.getTrackingNo());
                } else {
                    throw new RuntimeException("???????????????????????????!");
                }
            } else {
                throw new RuntimeException("??????????????????????????????,?????????!");
            }
        }
        String returnSource = returnExpressAddDTO.getReturnSource();
        if (!configStatus.getReturnSource().getOmsReturn().equals(returnSource)) {
            // ??????????????? ??????WMS??????
            CreateExpectedReqDTO createExpectedReqDTO = returnExpressAddDTO.convertThis(CreateExpectedReqDTO.class);
            createExpectedReqDTO.setRefOrderNo(returnExpressAddDTO.getFromOrderNo());
            //???????????? ????????????
            createExpectedReqDTO.setProcessType(configStatus.getPrCode(returnExpressAddDTO.getProcessType()));
            httpFeignClient.expectedCreate(createExpectedReqDTO);
        }
    }

    private void checkSubmit(ReturnExpressAddDTO returnExpressAddDTO) {
        //?????????????????????sku
        Optional.of(returnExpressAddDTO).map(ReturnExpressAddDTO::getProcessType).ifPresent(x -> {
            boolean equals = configStatus.getWholePackageOnShelves().equals(x);
            if (equals) {
                AssertUtil.isTrue(StringUtils.isNotBlank(returnExpressAddDTO.getSku()), "???????????????sku??????");
            }
        });

        // OMS ?????????????????? 069001????????? 069005
        String returnSource = returnExpressAddDTO.getReturnSource();
        String processType = returnExpressAddDTO.getProcessType();
        String omsReturn = configStatus.getReturnSource().getOmsReturn();
        if (StringUtils.isNotBlank(processType)) {
            if (returnSource.equals(omsReturn)) {
                List<String> allProcessType = Arrays.asList(configStatus.getReassign(), configStatus.getDestroy());
                AssertUtil.isTrue(allProcessType.contains(processType), "OMS?????????????????????,??????/??????");
            } else {
                List<String> allProcessType = Collections.singletonList(configStatus.getReassign());
                AssertUtil.isTrue(!allProcessType.contains(processType), "??????????????????????????????");
            }
        }
        // ??????????????????
        String fromOrderNo = returnExpressAddDTO.getFromOrderNo();
        Integer integer = returnExpressMapper.selectCount(Wrappers.<ReturnExpressDetail>lambdaQuery()
                //?????? ??????
                .eq(StringUtils.isNotBlank(returnExpressAddDTO.getScanCode()),ReturnExpressDetail::getScanCode, returnExpressAddDTO.getScanCode())
                .or().eq(StringUtils.isNotBlank(fromOrderNo), ReturnExpressDetail::getFromOrderNo, fromOrderNo)
                .select(ReturnExpressDetail::getId));
        AssertUtil.isTrue(integer == 0, "?????????????????????/???????????????????????????");
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param returnExpressDetail
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public int saveReturnExpressDetail(ReturnExpressDetail returnExpressDetail) {
        //OMS ?????????????????????
        if (configStatus.getReturnSource().getOmsReturn().equals(returnExpressDetail.getReturnSource())) {
            returnExpressDetail.setDealStatus(configStatus.getDealStatus().getWaitCustomerDeal());
            returnExpressDetail.setDealStatusStr(configStatus.getDealStatus().getWaitCustomerDealStr());
        } else {
            returnExpressDetail.setDealStatus(configStatus.getDealStatus().getWmsWaitReceive());
            returnExpressDetail.setDealStatusStr(configStatus.getDealStatus().getWmsWaitReceiveStr());
        }
        if (returnExpressDetail.getScanCode()==null||returnExpressDetail.getScanCode().equals("")){
            returnExpressDetail.setScanCode(returnExpressDetail.getFromOrderNo());
        }
        returnExpressDetail.setReturnFeeStatus(0);
        returnExpressDetail.setDestoryFeeStatus(0);
        return returnExpressMapper.insert(returnExpressDetail);
    }

    /**
     * ??????WMS??????????????????
     * /api/return/arrival #G1-????????????????????????
     *
     * @param returnArrivalReqDTO ??????VMS??????????????????
     * @return ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveArrivalInfoFormWms(ReturnArrivalReqDTO returnArrivalReqDTO) {
        log.info("??????wms ??????????????????{}", returnArrivalReqDTO);
         if(StringUtils.isNotBlank(returnArrivalReqDTO.getFromOrderNo())){
             returnExpressMapper.updateDelOutbound(returnArrivalReqDTO.getFromOrderNo());
        }
        if (StringUtils.isNotBlank(returnArrivalReqDTO.getExpectedNo())) {
            //?????? ??????????????? ????????????????????????
            ReturnExpressDetail returnExpressDetailCheck = returnExpressMapper.selectOne(Wrappers.<ReturnExpressDetail>lambdaUpdate()
                    .eq(ReturnExpressDetail::getExpectedNo, returnArrivalReqDTO.getExpectedNo())
                    .eq(ReturnExpressDetail::getDealStatus, configStatus.getDealStatus().getWmsWaitReceive()).last("LIMIT 1"));
            AssertUtil.notNull(returnExpressDetailCheck, "???????????????!");
            String dealStatus = configStatus.getDealStatus().getWaitCustomerDeal();
            String dealStatusStr = configStatus.getDealStatus().getWaitCustomerDealStr();
            // ??????/?????? ?????? ?????????????????????????????? ?????? G2 ??????????????????????????? ?????? G3??????????????????
            String processType = returnExpressDetailCheck.getProcessType();
            boolean isOpenAndCheck = processType.equals(configStatus.getUnpackingInspection());
            boolean isDestroy = processType.equals(configStatus.getDestroy()) || processType.equals(configStatus.getWholePackageOnShelves());
            if (isOpenAndCheck) {
                dealStatus = configStatus.getDealStatus().getWmsWaitReceive();
                dealStatusStr = configStatus.getDealStatus().getWmsWaitReceiveStr();
            } else if (isDestroy) {
                dealStatus = configStatus.getDealStatus().getWmsReceivedDealWay();
                dealStatusStr = configStatus.getDealStatus().getWmsReceivedDealWayStr();
            }

            int update = returnExpressMapper.update(new ReturnExpressDetail(), Wrappers.<ReturnExpressDetail>lambdaUpdate()
                    .eq(ReturnExpressDetail::getExpectedNo, returnArrivalReqDTO.getExpectedNo())
                    .eq(ReturnExpressDetail::getDealStatus, configStatus.getDealStatus().getWmsWaitReceive())
                    .set(ReturnExpressDetail::getReturnNo, returnArrivalReqDTO.getReturnNo())
                    .set(ReturnExpressDetail::getFromOrderNo, returnArrivalReqDTO.getFromOrderNo())
                    .set(StringUtil.isNotBlank(returnArrivalReqDTO.getExpectedNo()), ReturnExpressDetail::getExpectedNo, returnArrivalReqDTO.getExpectedNo())
                    .set(ReturnExpressDetail::getScanCode, returnArrivalReqDTO.getScanCode())
                    .set(ReturnExpressDetail::getSellerCode, returnArrivalReqDTO.getSellerCode())
                    .set(StringUtil.isNotBlank(returnArrivalReqDTO.getRemark()), BaseEntity::getRemark, returnArrivalReqDTO.getRemark())
                    .set(ReturnExpressDetail::getArrivalTime, LocalDateTime.now())
                    .set(isDestroy, ReturnExpressDetail::getFinishTime, LocalDateTime.now())
                    .set(ReturnExpressDetail::getArrivalTime, LocalDateTime.now())
                    .set(ReturnExpressDetail::getDealStatus, dealStatus)
                    .set(ReturnExpressDetail::getDealStatusStr, dealStatusStr)
            );
            return update;
        }else {
            // ??????????????? ???????????????
            ReturnExpressDetail returnExpressDetail = returnArrivalReqDTO.convertThis(ReturnExpressDetail.class);
            returnExpressDetail.setReturnSource(configStatus.getReturnSource().getWmsReturn());
            returnExpressDetail.setReturnSourceStr(configStatus.getReturnSource().getWmsReturnStr());
            returnExpressDetail.setArrivalTime(LocalDateTime.now());
            if (StringUtils.isNotBlank(returnExpressDetail.getSellerCode())) {
                returnExpressDetail.setDealStatus(configStatus.getDealStatus().getWaitCustomerDeal());
                returnExpressDetail.setDealStatusStr(configStatus.getDealStatus().getWaitCustomerDealStr());
            } else {
                returnExpressDetail.setDealStatus(configStatus.getDealStatus().getWaitAssigned());
                returnExpressDetail.setDealStatusStr(configStatus.getDealStatus().getWaitAssignedStr());
            }
            returnExpressDetail.setDestoryFeeStatus(0);
            returnExpressDetail.setReturnFeeStatus(0);
            int insert = returnExpressMapper.insert(returnExpressDetail);
            // ????????????
            return insert;
        }
    }




    /**
     * ??????VMS????????????????????????
     * /api/return/processing #G2-????????????????????????
     *
     * @param returnProcessingReqDTO ??????WMS????????????????????????
     * @return ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int finishProcessingInfoFromWms(ReturnProcessingFinishReqDTO returnProcessingReqDTO) {
        log.info("??????WMS?????????????????????????????? {}", returnProcessingReqDTO);
        String dealStatus = configStatus.getDealStatus().getWmsFinish();
        String dealStatusStr = configStatus.getDealStatus().getWmsFinishStr();

        int update = returnExpressMapper.update(new ReturnExpressDetail(), Wrappers.<ReturnExpressDetail>lambdaUpdate()
                .eq(ReturnExpressDetail::getReturnNo, returnProcessingReqDTO.getReturnNo())
                .eq(ReturnExpressDetail::getDealStatus, configStatus.getDealStatus().getWmsReceivedDealWay())

                .set(ReturnExpressDetail::getDealStatus, dealStatus)
                .set(ReturnExpressDetail::getDealStatusStr, dealStatusStr)
                .set(ReturnExpressDetail::getFinishTime, LocalDateTime.now())
                .last("LIMIT 1")
        );
        log.info("??????WMS???????????????????????? {} - ???????????? {}", returnProcessingReqDTO, update);
        AssertUtil.isTrue(update == 1, "????????????");

        //????????? ????????????????????????????????????
        addSkuInventory(returnProcessingReqDTO, dealStatus);
        return update;
    }

    /**
     * ??????sku????????????
     *
     * @param returnProcessingReqDTO
     * @param dealStatus
     */
    private void addSkuInventory(ReturnProcessingFinishReqDTO returnProcessingReqDTO, String dealStatus) {
        ReturnExpressDetail returnExpressDetail = returnExpressMapper.selectOne(Wrappers.<ReturnExpressDetail>lambdaQuery()
                .eq(ReturnExpressDetail::getDealStatus, dealStatus)
                .eq(ReturnExpressDetail::getReturnNo, returnProcessingReqDTO.getReturnNo())
                .last("LIMIT 1")
        );

        String warehouseCode = returnExpressDetail.getWarehouseCode();
        String sellerCode = returnExpressDetail.getSellerCode();
        //????????????
        if (returnExpressDetail.getProcessType().equals(configStatus.getPutawayByDetail())) {
            log.info("????????????????????????--??????????????????????????????????????????sku????????????");
            Integer id = returnExpressDetail.getId();
            List<ReturnExpressGoodVO> goodVOList = returnExpressGoodService.queryGoodListByExId(id);
            goodVOList = merage(goodVOList);
            // ???details?????????sku ?????????????????????????????????????????????
            List<String> collect = goodVOList.stream().map(ReturnExpressGoodVO::getPutawaySku).collect(Collectors.toList());
            String sku = String.join(",", collect);
            //2021-07-06?????? ??????????????????sku????????????????????????sku????????????????????? ????????????
//            TableDataInfo<InventorySkuVO> page = inventoryFeignService.page(warehouseCode, sku, sellerCode, collect.size());
//            log.info("warehouseCode:{},sku:{},sellerCode:{},??????????????????sku??????: {}", warehouseCode, sku, sellerCode, JSONObject.toJSONString(page.getRows()));
//            AssertUtil.isTrue(page.getCode() == 200, "????????????????????????!");
//            List<InventorySkuVO> rows = page.getRows();
            Map<String, Integer> needAddSkuNum = goodVOList.stream().collect(Collectors.toMap(ReturnExpressGoodVO::getPutawaySku, ReturnExpressGoodVO::getPutawayQty));
            //??????sku ????????????
            collect.forEach(x -> {
                InventoryAdjustmentDTO inventoryAdjustmentDTO = new InventoryAdjustmentDTO();
                //???????????????
                inventoryAdjustmentDTO.setSku(x);
                inventoryAdjustmentDTO.setAdjustment("5");
                Integer skuAddNum = needAddSkuNum.get(x);
                inventoryAdjustmentDTO.setWarehouseCode(warehouseCode);
                inventoryAdjustmentDTO.setSellerCode(sellerCode);
                inventoryAdjustmentDTO.setQuantity(skuAddNum);
                inventoryAdjustmentDTO.setFormReturn(true);
                inventoryAdjustmentDTO.setReceiptNo(returnProcessingReqDTO.getReturnNo());
                log.info("??????????????????????????????{}", JSONObject.toJSONString(inventoryAdjustmentDTO));
                inventoryFeignService.adjustment(inventoryAdjustmentDTO);
            });
        }
        if (returnExpressDetail.getProcessType().equals(configStatus.getWholePackageOnShelves())) {
            log.info("????????????--??????????????????????????????????????????sku???????????? + 1 ");
            String sku = returnExpressDetail.getSku();
//            TableDataInfo<InventorySkuVO> page = inventoryFeignService.page(warehouseCode, sku, sellerCode, 1);
//            AssertUtil.isTrue(page.getCode() == 200, "????????????????????????!");
//            List<InventorySkuVO> rows = page.getRows();
//            //??????????????????+1
//            rows.forEach(x -> {
            InventoryAdjustmentDTO inventoryAdjustmentDTO = new InventoryAdjustmentDTO();
//                BeanUtils.copyProperties(x, inventoryAdjustmentDTO);
            //???????????????
            inventoryAdjustmentDTO.setAdjustment("5");
            inventoryAdjustmentDTO.setQuantity(1);
            inventoryAdjustmentDTO.setWarehouseCode(warehouseCode);
            inventoryAdjustmentDTO.setSku(sku);
            inventoryAdjustmentDTO.setSellerCode(sellerCode);
            inventoryAdjustmentDTO.setFormReturn(true);
            inventoryAdjustmentDTO.setReceiptNo(returnProcessingReqDTO.getReturnNo());
            log.info("??????????????????????????????{}", JSONObject.toJSONString(inventoryAdjustmentDTO));
            inventoryFeignService.adjustment(inventoryAdjustmentDTO);
//            });
        }
    }

    private List<ReturnExpressGoodVO> merage(List<ReturnExpressGoodVO> details) {
        return new ArrayList<>(details.stream()
                .collect(Collectors.toMap(ReturnExpressGoodVO::getPutawaySku, a -> a, (o1, o2) -> {
                    o1.setPutawayQty(o1.getPutawayQty() + o2.getPutawayQty());
                    return o1;
                })).values());
    }

    /**
     * ?????????????????????
     *
     * @param expressUpdateDTO ????????????
     * @return ????????????
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public <T extends ReturnExpressAddDTO> int updateExpressInfo(ReturnExpressServiceAddDTO expressUpdateDTO) {
        //??????????????????????????????????????????????????????????????????????????????????????? ??????WMS??????????????????
        checkBeforeUpdate(expressUpdateDTO);
        String dealStatus = "";
        String dealStatusStr = "";

        if (expressUpdateDTO.getReturnSource().equals(configStatus.getReturnSource().getOmsReturn())) {
            if (StringUtils.isNotBlank(expressUpdateDTO.getProcessType())) {
                dealStatus = configStatus.getDealStatus().getWmsFinish();
                dealStatusStr = configStatus.getDealStatus().getWmsFinishStr();
            }
        } else {
            if (expressUpdateDTO.getProcessType().equals(configStatus.getUnpackingInspection())) {
                dealStatus = configStatus.getDealStatus().getWmsWaitReceive();
                dealStatusStr = configStatus.getDealStatus().getWmsWaitReceiveStr();
            } else {
                // ??????????????????
                dealStatus = configStatus.getDealStatus().getWmsReceivedDealWay();
                dealStatusStr = configStatus.getDealStatus().getWmsReceivedDealWayStr();
            }
        }
        ReturnExpressDetail returnExpressDetail = new ReturnExpressDetail();
        BeanUtils.copyProperties(expressUpdateDTO, returnExpressDetail);
        ReturnExpressServiceAddDTO expressUpdateServiceDTO = (ReturnExpressServiceAddDTO) expressUpdateDTO;

        if(StringUtils.isEmpty(returnExpressDetail.getSellerCode())){
            returnExpressDetail.setSellerCode(null);
        }

        int update = returnExpressMapper.update(returnExpressDetail, Wrappers.<ReturnExpressDetail>lambdaUpdate()
                .eq(ReturnExpressDetail::getId, expressUpdateDTO.getId())
                .eq(ReturnExpressDetail::getDealStatus, configStatus.getDealStatus().getWaitCustomerDeal())

                .set(ReturnExpressDetail::getProcessTime, new Date())
                .set(ReturnExpressDetail::getSku, expressUpdateDTO.getSku())
                .set(StringUtils.isNotBlank(dealStatus), ReturnExpressDetail::getDealStatus, dealStatus)
                .set(StringUtils.isNotBlank(dealStatusStr), ReturnExpressDetail::getDealStatusStr, dealStatusStr)
                .set(expressUpdateDTO.getProcessType() != null, ReturnExpressDetail::getProcessType, expressUpdateDTO.getProcessType())
                .set(expressUpdateDTO.getProcessTypeStr() != null, ReturnExpressDetail::getProcessTypeStr, expressUpdateDTO.getProcessTypeStr())
                .set(StringUtil.isNotBlank(expressUpdateServiceDTO.getFromOrderNoNew()), ReturnExpressDetail::getFromOrderNoNew, expressUpdateServiceDTO.getFromOrderNoNew())
                .last("LIMIT 1")
        );
        AssertUtil.isTrue(update == 1, "????????????,??????????????????!");
        if (!expressUpdateDTO.getReturnSource().equals(configStatus.getReturnSource().getOmsReturn())) {
            List<ReturnExpressGoodAddDTO> details = expressUpdateDTO.getGoodList();
            returnExpressGoodService.addOrUpdateGoodInfoBatch(details, expressUpdateDTO.getId());
            //??????????????????????????????????????????sku
            checkSku(expressUpdateDTO);
            //??????????????????WMS
            pushSkuDetailsToWMS(expressUpdateDTO, details);
        }
        return update;
    }

    /**
     * ?????????sku?????????????????????
     *
     * @param expressUpdateDTO
     */
    private void checkSku(ReturnExpressAddDTO expressUpdateDTO) {
        List<ReturnExpressGoodAddDTO> details = expressUpdateDTO.getGoodList();
        if (CollectionUtils.isEmpty(details)) {
            log.info("?????????????????????????????????sku");
        }
        List<String> skuIdList = details.stream().map(ReturnExpressGoodAddDTO::getPutawaySku).filter(StringUtils::isNotBlank).collect(Collectors.toList());
        log.info("??????????????????{}", skuIdList);
        if (CollectionUtils.isEmpty(skuIdList)) return;
        BaseProductConditionQueryDto baseProductConditionQueryDto = new BaseProductConditionQueryDto();
        String sellCode = getSellCode();
        baseProductConditionQueryDto.setSellerCode(sellCode);
        baseProductConditionQueryDto.setSkus(skuIdList);
        log.info("??????sku?????? {}", JSONObject.toJSONString(baseProductConditionQueryDto));
        R<List<BaseProduct>> listR = baseProductFeignService.queryProductList(baseProductConditionQueryDto);
        AssertUtil.isTrue(HttpStatus.SUCCESS == listR.getCode(), "??????sku?????????" + listR.getMsg());
        List<BaseProduct> data = listR.getData();
        List<String> returnIdList = data.stream().map(BaseProduct::getCode).collect(Collectors.toList());
        log.info("????????????sku?????????{}", returnIdList);
        skuIdList.removeAll(returnIdList);
        if (CollectionUtils.isNotEmpty(skuIdList)) {
            log.info("????????????????????????{}", skuIdList);
            throw new BaseException("???????????????SKU: " + String.join(" ", skuIdList) + "??????");
        }

    }


    /**
     * ???????????????
     *
     * @param expressUpdateDTO
     */
    private void checkBeforeUpdate(ReturnExpressAddDTO expressUpdateDTO) {
        log.info("?????????????????? req:{}", expressUpdateDTO);
        expressUpdateDTO.setSellerCode(getSellCode());
        AssertUtil.isTrue(expressUpdateDTO.getId() != null && expressUpdateDTO.getId() > 0, "???????????????");
        //??????????????????????????????????????? ????????????;
        ReturnExpressDetail returnExpressDetailCheck = returnExpressMapper.selectById(expressUpdateDTO.getId());
        AssertUtil.notNull(returnExpressDetailCheck, "???????????????!");
        boolean isOpenAndCheck = configStatus.getUnpackingInspection().equals(returnExpressDetailCheck.getProcessType());

        if (isOpenAndCheck) {
            //???????????????processType
            String processType = expressUpdateDTO.getProcessType();
            //??????????????? ???????????????????????????????????????
            boolean unpackAndPutOnTheShelf = configStatus.getPutawayByDetail().equals(processType);
            if (unpackAndPutOnTheShelf) {
                // ??????????????????????????????????????????????????????????????????????????????sku sku ????????????
                List<ReturnExpressGoodAddDTO> details = expressUpdateDTO.getGoodList();
                AssertUtil.isTrue(CollectionUtils.isNotEmpty(details), "??????????????????????????????????????????!");
                Optional.of(details).filter(CollectionUtils::isNotEmpty).ifPresent(x -> {
                    AtomicBoolean mustHadOne = new AtomicBoolean(false);
                    x.forEach(detail -> {
                        detail.check();
                        if (detail.getPutawayQty() != null && detail.getPutawayQty() > 0) {
                            mustHadOne.set(true);
                        }
                    });
                    AssertUtil.isTrue(mustHadOne.get(), "??????????????????????????????sku??????0");
                });
            }
            boolean b = configStatus.getDestroy().equals(processType) || unpackAndPutOnTheShelf;
            AssertUtil.isTrue(b, "????????????????????????????????????/??????");
        }
        // AssertUtil.isTrue(checkoutRefNo(expressUpdateDTO.getReturnNo()),"??????????????????");
    }

    /**
     * ?????????sku????????????WMS
     *
     * @param expressUpdateDTO
     * @param details
     */
    private void pushSkuDetailsToWMS(ReturnExpressAddDTO expressUpdateDTO, List<ReturnExpressGoodAddDTO> details) {
        ProcessingUpdateReqDTO processingUpdateReqDTO = new ProcessingUpdateReqDTO();
        processingUpdateReqDTO
                .setSku(expressUpdateDTO.getSku())
                .setProcessRemark(expressUpdateDTO.getProcessRemark())
                .setWarehouseCode(expressUpdateDTO.getWarehouseCode())
                .setOrderNo(expressUpdateDTO.getReturnNo())
                .setProcessType(configStatus.getPrCode(expressUpdateDTO.getProcessType()));
        List<ReturnDetailWMS> detailArrayList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(details)) {
            details.forEach(x -> {
                ReturnDetailWMS returnDetail = new ReturnDetailWMS();
                returnDetail
                        .setSku(x.getSku())
                        .setPutawaySku(x.getPutawaySku())
                        .setPutawayQty(x.getPutawayQty())
                        .setProcessRemark(x.getProcessRemark());
                detailArrayList.add(returnDetail);
            });
            processingUpdateReqDTO.setDetails(detailArrayList);
        }
        log.info("???????????????????????? {}", processingUpdateReqDTO);
        httpFeignClient.processingUpdate(processingUpdateReqDTO);
    }


    @Override
    public int expiredUnprocessedForecastOrder() {
        log.info("--------------????????????????????????????????? ??????--------------");
        LocalDate localDate = LocalDate.now().minusDays(configStatus.getExpirationDays());
        log.info("--------------????????????????????????????????? ?????? {} {} ??????--------------", configStatus.getExpirationDays(), localDate);
        int update = returnExpressMapper.update(null, Wrappers.<ReturnExpressDetail>lambdaUpdate()
                .eq(ReturnExpressDetail::getOverdue, 0)
                .isNull(ReturnExpressDetail::getExpireTime)
                .lt(BaseEntity::getUpdateTime, localDate)
                .set(ReturnExpressDetail::getOverdue, 1)
        );
        int update2 = returnExpressMapper.update(null, Wrappers.<ReturnExpressDetail>lambdaUpdate()
                .eq(ReturnExpressDetail::getOverdue, 0)
                .lt(ReturnExpressDetail::getExpireTime, localDate)
                .set(ReturnExpressDetail::getOverdue, 1)
        );
        log.info("--------------????????????????????????????????? ??????--------------");
        return update;
    }

    /**
     * ????????????????????????????????? ????????????????????????
     *
     * @param expectedNo
     * @return
     */
    private ReturnExpressVO getInfo(String expectedNo) {
        if (StringUtils.isBlank(expectedNo))
            return null;
        String sellerCode = Optional.ofNullable(SecurityUtils.getLoginUser()).map(LoginUser::getSellerCode).orElse("");

        ReturnExpressDetail returnExpressDetail = returnExpressMapper.selectOne(Wrappers.<ReturnExpressDetail>lambdaQuery()
                .eq(StringUtils.isNotBlank(sellerCode), ReturnExpressDetail::getSellerCode, sellerCode)
                .eq(ReturnExpressDetail::getDealStatus, configStatus.getDealStatus().getWaitCustomerDeal())
                .and(query ->
                        query.eq(ReturnExpressDetail::getExpectedNo, expectedNo)
                                .or().eq(ReturnExpressDetail::getFromOrderNo, expectedNo)
                                .or().eq(ReturnExpressDetail::getScanCode, expectedNo)
                                .or().eq(ReturnExpressDetail::getRefNo, expectedNo)


                ).last("LIMIT 1"));
        if (Objects.isNull(returnExpressDetail))
            return null;
        return getReturnExpressVO(returnExpressDetail);
    }

    @Override
    public ReturnExpressVO getInfo(Long id) {
        ReturnExpressDetail returnExpressDetail = returnExpressMapper.selectById(id);
        return getReturnExpressVO(returnExpressDetail);
    }

    private ReturnExpressVO getReturnExpressVO(ReturnExpressDetail returnExpressDetail) {
        Optional.ofNullable(returnExpressDetail).orElseThrow(() -> new BaseException("??????????????????"));
        ReturnExpressVO returnExpressVO = returnExpressDetail.convertThis(ReturnExpressVO.class);
        returnExpressVO.setGoodList(returnExpressGoodService.queryGoodListByExId(returnExpressVO.getId()));
        return returnExpressVO;
    }

    @Async
    @Override
    public void updateTrackNoByOutBoundNo(String outBoundNo, String trackNo) {
        if (StringUtils.isBlank(outBoundNo)) return;
        baseMapper.update(new ReturnExpressDetail(), Wrappers.<ReturnExpressDetail>lambdaUpdate()
                .eq(ReturnExpressDetail::getFromOrderNoNew, outBoundNo)
                .set(ReturnExpressDetail::getScanCodeNew, trackNo)
                .set(ReturnExpressDetail::getFinishTime, LocalDateTime.now())
        );
    }

    @Override
    public Boolean checkoutRefNo(String refNo) {
        if (StringUtils.isBlank(refNo)) return true;
        return baseMapper.selectCount(Wrappers.<ReturnExpressDetail>lambdaQuery().eq(ReturnExpressDetail::getReturnNo, refNo)) == 0;
    }

    @Override
    public void importByTemplate(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            List<ReturnExpressServiceAddDTO> basPackingAddList = EasyExcel.read(inputStream, ReturnExpressServiceAddDTO.class, new SyncReadListener()).sheet().doReadSync();

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            SecurityContextHolder.getContext().setAuthentication(authentication);

            AtomicInteger index = new AtomicInteger(1);
            List<CompletableFuture<String>> futureList = new ArrayList<>();
            Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
            List<String> errorMsgList = new ArrayList<>();

            for (int i = 0; i < basPackingAddList.size(); i++) {
                Set<ConstraintViolation<ReturnExpressServiceAddDTO>> set = validator.validate(basPackingAddList.get(i));
                StringBuilder errorMsg = new StringBuilder();
                for (ConstraintViolation<ReturnExpressServiceAddDTO> constraintViolation : set) {
                    errorMsg.append(constraintViolation.getMessage());
                }
                if (StringUtils.isNotBlank(errorMsg.toString())) {
                    errorMsgList.add(String.format("????????????%s?????????:?????????????????????%s", i + 1, errorMsg.toString()));
                }
            }
            if (CollectionUtils.isNotEmpty(errorMsgList)) {
                throw new RuntimeException(String.join("\n", errorMsgList));
            }
            basPackingAddList.forEach(x -> {
                int indexThis = index.getAndIncrement();

                CompletableFuture<String> errorMsgCompletable = CompletableFuture.supplyAsync(() -> {
                    StringBuilder errorMsg = new StringBuilder();
                    try {
                        x.setReturnSource(configStatus.getReturnSource().getOmsReturn());
                        x.setReturnSourceStr(configStatus.getReturnSource().getOmsReturnStr());
                        // ????????????
                        this.insertReturnExpressDetail(x);
                    } catch (Exception e) {
                        e.printStackTrace();
                        errorMsg.append("???").append(indexThis).append("??????????????????").append(e.getMessage()).append(";\n");
                    }
                    return errorMsg.toString();
                });
                futureList.add(errorMsgCompletable);
            });
            CompletableFuture<Void> voidCompletableFuture = CompletableFuture.allOf(futureList.toArray(new CompletableFuture[0]));
            Void unused = voidCompletableFuture.get();
            String errorMsg = futureList.stream().map(CompletableFuture::join).collect(Collectors.joining());
            AssertUtil.isTrue(StringUtils.isBlank(errorMsg), errorMsg);
        } catch (IOException e) {
            throw new RuntimeException("??????????????????");
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    /**
     * ???????????????
     *
     * @param multipartFile ??????
     */
    @SneakyThrows
    @Override
    public List<String> importByTemplateClient(MultipartFile multipartFile) {
        try (
                InputStream inputStream = multipartFile.getInputStream();
//                InputStream inputStream1 = multipartFile.getInputStream();
                InputStream inputStream2 = multipartFile.getInputStream();
        ) {
            log.info("??????????????????:1 ??????---");
            StopWatch importWatch = new StopWatch("????????????");
            importWatch.start("??????");
            CompletableFuture<List<ReturnExpressClientImportDelOutboundDto>> reassignListFuture = CompletableFuture.supplyAsync(() -> EasyExcel.read(inputStream2, ReturnExpressClientImportDelOutboundDto.class, new SyncReadListener()).headRowNumber(1).sheet(0).doReadSync());
            importWatch.stop();
            List<ReturnExpressClientImportDelOutboundDto> reassignList = reassignListFuture.get();
            Map<String, ReturnExpressClientImportDelOutboundDto> reassignListMap = reassignList.stream().collect(Collectors.toMap(ReturnExpressClientImportDelOutboundDto::getExpectedNo, x -> x, (x1, x2) -> x1));
            reassignList.clear();
            log.info("??????????????????:3 ????????????---");
            importWatch.start("????????????");
            // ????????????
            List<ReturnExpressClientImportBO> importBOList = new ArrayList<>(reassignListMap.size());
            reassignListMap.forEach((expectedNo, ressignInfo) -> {
                ReturnExpressClientImportBO returnExpressClientImportBO = new ReturnExpressClientImportBO();
                returnExpressClientImportBO.setExpectedNo(expectedNo);
                ReturnExpressClientImportBaseDTO returnExpressClientImportBaseDTO = new ReturnExpressClientImportBaseDTO();
                returnExpressClientImportBaseDTO.setExpectedNo(expectedNo);
                returnExpressClientImportBaseDTO.setProcessTypeStr(ressignInfo.getProcessTypeStr());
                returnExpressClientImportBO.setBaseDTO(returnExpressClientImportBaseDTO);
                returnExpressClientImportBO.setReassignDTO(ressignInfo);
                importBOList.add(returnExpressClientImportBO);
            });
            importWatch.stop();
            log.info("??????????????????:4 ??????????????????---");
            return execute(importBOList);
        } catch (IOException e) {
            throw new RuntimeException("??????????????????");
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoGeneratorFee() {

        //step 1.????????????????????????
        List<ReturnExpressDetail> returnExpressDetails = this.selectAutoGeneratorFee();

        if(CollectionUtils.isEmpty(returnExpressDetails)){
            return;
        }

        //????????????50?????????
        List<List<ReturnExpressDetail>> partReturnExpressList = Lists.partition(returnExpressDetails,50);

        for(List<ReturnExpressDetail> expressDetails : partReturnExpressList){
            new ReturnExpressAutoGeneratorFeeCmd(expressDetails).execute();
        }
    }

    @Override
    public void autoGeneratorDestoryFee() {

        //step 1.????????????????????????
        List<ReturnExpressDetail> returnExpressDetails = this.selectAutoGeneratorDestoryFee();

        if(CollectionUtils.isEmpty(returnExpressDetails)){
            return;
        }

        //????????????50?????????
        List<List<ReturnExpressDetail>> partReturnExpressList = Lists.partition(returnExpressDetails,50);

        for(List<ReturnExpressDetail> expressDetails : partReturnExpressList){
            new ReturnExpressAutoGeneratorDestoryFeeCmd(expressDetails).execute();
        }
    }

    private List<ReturnExpressDetail> selectAutoGeneratorFee(){

        List<ReturnExpressDetail> returnExpressDetails = baseMapper.selectList(Wrappers.<ReturnExpressDetail>query().lambda()
                .eq(ReturnExpressDetail::getReturnFeeStatus,0)
                .eq(ReturnExpressDetail::getDealStatus,"waitCustomerDeal")
        );

        return returnExpressDetails;
    }

    private List<ReturnExpressDetail> selectAutoGeneratorDestoryFee(){

        List<ReturnExpressDetail> returnExpressDetails = baseMapper.selectList(Wrappers.<ReturnExpressDetail>query().lambda()
                .eq(ReturnExpressDetail::getReturnFeeStatus,1)
                .eq(ReturnExpressDetail::getDestoryFeeStatus,0)
                .eq(ReturnExpressDetail::getDealStatus,"waitCustomerDeal")
        );

        return returnExpressDetails;
    }

    /**
     * ????????????
     *
     * @param importBOList
     * @return errorMsg
     */
    private List<String> execute(List<ReturnExpressClientImportBO> importBOList) throws ExecutionException, InterruptedException {
        int count = 20;
        int size = importBOList.size();
        int segments = size / count;
        segments = size % count == 0 ? segments : segments + 1;
        List<CompletableFuture<List<String>>> errorMsgList = new ArrayList<>();
        log.info("??????????????????:5 ????????????--- {}", importBOList.size());
        for (int i = 0; i < segments; i++) {
            List<ReturnExpressClientImportBO> importBOS;
            if (i == segments - 1) {
                importBOS = importBOList.subList(count * i, size);
            } else {
                importBOS = importBOList.subList(count * i, count * (i + 1));
            }
            errorMsgList.add(executeReal(importBOS));
        }

        CompletableFuture.allOf(errorMsgList.toArray(new CompletableFuture[0])).get();
        List<String> resultMsg = new ArrayList<>(importBOList.size());
        errorMsgList.forEach(x -> {
            try {
                resultMsg.addAll(x.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });
        log.info("??????????????????:5 ????????????---");
        return resultMsg;
    }

    private CompletableFuture<List<String>> executeReal(List<ReturnExpressClientImportBO> importBOList) {
        SecurityContext context = SecurityContextHolder.getContext();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        return CompletableFuture.supplyAsync(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            SecurityContextHolder.setContext(context);
            List<String> errorMsgList = new ArrayList<>();
            importBOList.forEach(x -> {
                String errorMsg = null;
                try {
                    errorMsg = executeReal(x);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    errorMsgList.add(e.getMessage());
                }
                if (StringUtils.isNotBlank(errorMsg))
                    errorMsgList.add(errorMsg);
            });
            return errorMsgList;
        }, returnThreadTaskPool);
    }

    private static final String EMPTY_STR = "\\";

    private String executeReal(ReturnExpressClientImportBO returnExpressClientImportBO) {
        log.info("??????????????????:6 ??????????????????--- {}", returnExpressClientImportBO);
        // ??????
        String expectedNo = returnExpressClientImportBO.getExpectedNo();
        ReturnExpressVO infoByNo = getInfo(expectedNo);
        log.info("??????????????????:7 ?????????????????????--- {}", infoByNo);
        if (infoByNo == null) return "????????????" + expectedNo + "?????????????????????????????????????????????";

        ReturnExpressClientImportBaseDTO baseDTO = returnExpressClientImportBO.getBaseDTO();
        String processTypeStr = baseDTO.getProcessTypeStr();

        String reassignCode = configStatus.getReassign();
        String destroyCode = configStatus.getDestroy();

        infoByNo.setProcessTypeStr(processTypeStr);
        if ("??????".equals(processTypeStr)) {
            infoByNo.setProcessType(reassignCode);
            ReturnExpressClientImportDelOutboundDto importReassignDTO = returnExpressClientImportBO.getReassignDTO();
            if (importReassignDTO == null) return "????????????" + expectedNo + "??????????????????????????????";
        } else if ("??????".equals(processTypeStr)) {
            infoByNo.setProcessType(destroyCode);
        } else {
            return "????????????" + expectedNo + "????????????[" + processTypeStr + "]?????????";
        }
        String errorMsg = "";
        if ("??????".equals(processTypeStr)) {
            log.info("??????????????????:8 ??????????????????--- {}", infoByNo);
            errorMsg = reassign(returnExpressClientImportBO, infoByNo);
            log.info("??????????????????:14 ???????????? ?????? --- {}", errorMsg);
        } else {
            log.info("??????????????????:8-1 ??????????????????--- {}", infoByNo);
            errorMsg = updateThis(returnExpressClientImportBO, infoByNo);
            log.info("??????????????????:9-1 ??????????????????--- {}", infoByNo);
        }
        return errorMsg;
    }

    private String updateThis(ReturnExpressClientImportBO returnExpressClientImportBO, ReturnExpressVO infoByNo) {
        ReturnExpressServiceAddDTO returnExpressAddDTO = new ReturnExpressServiceAddDTO();
        BeanUtils.copyProperties(infoByNo, returnExpressAddDTO);
        List<ReturnExpressClientImportSkuDTO> skuDTO = returnExpressClientImportBO.getSkuDTO();
        if (CollectionUtils.isNotEmpty(skuDTO)) {
            Map<String, ReturnExpressClientImportSkuDTO> skuDTOMap = skuDTO.stream().filter(x -> StringUtils.isNotBlank(x.getSku())).collect(Collectors.toMap(ReturnExpressClientImportSkuDTO::getSku, x -> x));
            List<ReturnExpressGoodAddDTO> goodList = returnExpressAddDTO.getGoodList();
            goodList.forEach(x -> {
                String sku = x.getSku();
                ReturnExpressClientImportSkuDTO importSkuDTO = skuDTOMap.get(sku);
                if (importSkuDTO != null) {
                    x.setProcessRemark(importSkuDTO.getRemark());
                    x.setPutawaySku(importSkuDTO.getPutawaySku());
                    x.setPutawayQty(importSkuDTO.getPutawayQty());
                }
            });
            return updateThis(returnExpressAddDTO);
        }
        return "";
    }

    /**
     * ??????????????????
     *
     * @param infoByNo
     * @return
     */
    private String updateThis(ReturnExpressServiceAddDTO returnExpressAddDTO) {
        try {
            log.info("???????????? ??????????????????:{}", JSONObject.toJSONString(returnExpressAddDTO));
            this.updateExpressInfo(returnExpressAddDTO);
            log.info("???????????? ????????????????????????:{}", JSONObject.toJSONString(returnExpressAddDTO));
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
        return null;
    }

    /**
     * ??????
     *
     * @param returnExpressClientImportBO
     * @param infoByNo
     * @return
     */
    private String reassign(ReturnExpressClientImportBO returnExpressClientImportBO, ReturnExpressVO infoByNo) {
        String expectedNo = returnExpressClientImportBO.getExpectedNo();
        ReturnExpressClientImportDelOutboundDto importReassignDTO = returnExpressClientImportBO.getReassignDTO();

        //???????????????
        String fromOrderNo = infoByNo.getFromOrderNo();
        // ?????????????????????
        R<DelOutboundVO> delOutboundVOR = delOutboundFeignService.getInfoByOrderNo(fromOrderNo);
        log.info("??????????????????:9 ???????????????--- {} - {}", infoByNo, delOutboundVOR);
        if ((delOutboundVOR == null || delOutboundVOR.getCode() != HttpStatus.SUCCESS || delOutboundVOR.getData() == null)) {
            return "????????????" + expectedNo + "??????????????????" + fromOrderNo + "?????????" + Optional.ofNullable(delOutboundVOR).map(R::getMsg).orElse("");
        }
        DelOutboundVO delOutboundVO = delOutboundVOR.getData();

        // ?????????????????????
        DelOutboundDto delOutboundDto = new DelOutboundDto();
        BeanUtils.copyProperties(delOutboundVO, delOutboundDto);
        //????????????
        delOutboundDto.setIsFirst("???".equals(getStrOrDefault(importReassignDTO.getIsFirstStr(), delOutboundDto.getIsFirst() ? "???" : "???")));
        delOutboundDto.setOldOrderNo(fromOrderNo);
        // refno = wms ????????? = ????????????????????????
        delOutboundDto.setRefNo(infoByNo.getReturnNo());
        if (importReassignDTO.getIoss()!=null){
            delOutboundDto.setIoss(importReassignDTO.getIoss());
        }
        if (importReassignDTO.getHouseNo()!=null){
            delOutboundDto.setHouseNo(importReassignDTO.getHouseNo());
        }
        delOutboundDto.setRefOrderNo(expectedNo);
        delOutboundDto.setShipmentRule(getStrOrDefault(importReassignDTO.getShipmentRule(), delOutboundVO.getShipmentRule()));
        delOutboundDto.setRemark(getStrOrDefault(importReassignDTO.getRemark(), delOutboundVO.getRemark()));
        BigDecimal codAmount = importReassignDTO.getCodAmount() == null ? Optional.ofNullable(delOutboundVO.getCodAmount()).orElse(BigDecimal.ZERO) : importReassignDTO.getCodAmount();
        delOutboundDto.setCodAmount(codAmount);
        //????????????
        DelOutboundAddressDto delOutboundAddressDto = Optional.ofNullable(delOutboundDto.getAddress()).orElse(new DelOutboundAddressDto());
        delOutboundDto.setAddress(delOutboundAddressDto);
        if (null != delOutboundVO.getCalcWeight()) {
            String calcWeightUnit = Optional.ofNullable(delOutboundVO.getCalcWeightUnit()).orElse("g");
            BigDecimal calcWeight = Optional.ofNullable(delOutboundVO.getCalcWeight()).orElse(BigDecimal.ZERO);
            // ??????????????? g
            if ("KG".equalsIgnoreCase(calcWeightUnit)) {
                calcWeight = calcWeight.multiply(new BigDecimal("1000"));
                delOutboundDto.setWeight(calcWeight.doubleValue());
            } else {
                delOutboundDto.setWeight(calcWeight.doubleValue());
            }
        }
        DelOutboundAddressDto addressDTO = delOutboundDto.getAddress();
        DelOutboundAddressVO addressVO = Optional.ofNullable(delOutboundVO.getAddress()).orElse(new DelOutboundAddressVO());
        addressDTO.setConsignee(getStrOrDefault(importReassignDTO.getConsignee(), addressVO.getConsignee()));
        String countryName = getStrOrDefault(importReassignDTO.getCountry(), addressVO.getCountry());
        BasRegionSelectListVO regionSelectListVO = null;
        try {
            regionSelectListVO = iRemoteApi.getCountryCode(countryName);
        } catch (Exception e) {
            return e.getMessage();
        }
        addressDTO.setCountry(regionSelectListVO.getName());
        addressDTO.setCountryCode(regionSelectListVO.getAddressCode());
        addressDTO.setZone("");
        addressDTO.setStateOrProvince(getStrOrDefault(importReassignDTO.getStateOrProvince(), addressVO.getStateOrProvince()));
        addressDTO.setCity(getStrOrDefault(importReassignDTO.getCity(), addressVO.getCity()));
        addressDTO.setStreet1(getStrOrDefault(importReassignDTO.getStreet1(), addressVO.getStreet1()));
        addressDTO.setStreet2(getStrOrDefault(importReassignDTO.getStreet2(), addressVO.getStreet2()));
        addressDTO.setStreet3("");
        addressDTO.setPostCode(getStrOrDefault(importReassignDTO.getPostCode(), addressVO.getPostCode()));
        addressDTO.setPhoneNo(getStrOrDefault(importReassignDTO.getPhoneNo(), addressVO.getPhoneNo()));
        addressDTO.setEmail(getStrOrDefault(importReassignDTO.getEmail(), addressVO.getEmail()));
        log.info("??????????????????:10 ?????????????????? ????????????????????????--- {}", delOutboundDto);
        log.info("?????????????????????" + JSONObject.toJSONString(delOutboundDto));
        R<DelOutboundAddResponse> reassignR = delOutboundFeignService.reassign(delOutboundDto);
        log.info("??????????????????:11 ?????????????????? ????????????????????????--- {}", reassignR);
        if ((reassignR == null || reassignR.getCode() != HttpStatus.SUCCESS || reassignR.getData() == null)) {
            return "????????????" + expectedNo + "????????????:" + Optional.ofNullable(reassignR).map(R::getMsg).orElse("");
        }
        DelOutboundAddResponse delOutboundAddResponse = reassignR.getData();
        String orderNoNew = delOutboundAddResponse.getOrderNo();
        infoByNo.setFromOrderNoNew(orderNoNew);
        ReturnExpressServiceAddDTO returnExpressAddDTO = new ReturnExpressServiceAddDTO();
        BeanUtils.copyProperties(infoByNo, returnExpressAddDTO);
        log.info("??????????????????:12 ?????????????????? ?????? --- {}", returnExpressAddDTO);
        String s = this.updateThis(returnExpressAddDTO);
        log.info("??????????????????:13 ?????????????????? ?????? --- {}", s);
        if (StringUtils.isNotBlank(s)) return s;
        return null;
    }

    /**
     * ????????? \ ????????? ""
     *
     * @param targetStr  ??????
     * @param defaultStr ?????????
     * @return ?????????
     */
    private static String getStrOrDefault(String targetStr, String defaultStr) {
        if (StringUtils.isBlank(targetStr)) {
            return defaultStr;
        } else {
            if (EMPTY_STR.equals(targetStr)) {
                return "";
            } else {
                return targetStr;
            }
        }
    }


}
