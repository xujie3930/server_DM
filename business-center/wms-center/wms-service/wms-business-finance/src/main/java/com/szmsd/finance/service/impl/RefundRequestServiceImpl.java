package com.szmsd.finance.service.impl;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.thread.NamedThreadFactory;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.event.SyncReadListener;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szmsd.bas.api.domain.BasSub;
import com.szmsd.common.core.constant.HttpStatus;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.AssertUtil;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.datascope.annotation.DataScope;
import com.szmsd.common.security.domain.LoginUser;
import com.szmsd.common.security.utils.SecurityUtils;
import com.szmsd.delivery.api.feign.DelOutboundFeignService;
import com.szmsd.finance.compont.ConfigData;
import com.szmsd.finance.compont.IRemoteApi;
import com.szmsd.finance.config.FileVerifyUtil;
import com.szmsd.finance.domain.AccountSerialBill;
import com.szmsd.finance.domain.BasRefundRequest;
import com.szmsd.finance.domain.FssRefundRequest;
import com.szmsd.finance.dto.*;
import com.szmsd.finance.enums.BillEnum;
import com.szmsd.finance.enums.PrcStateEnum;
import com.szmsd.finance.enums.RefundProcessEnum;
import com.szmsd.finance.enums.RefundStatusEnum;
import com.szmsd.finance.mapper.AccountSerialBillMapper;
import com.szmsd.finance.mapper.BasRefundRequestMapper;
import com.szmsd.finance.mapper.RefundRequestMapper;
import com.szmsd.finance.service.IAccountBalanceService;
import com.szmsd.finance.service.IRefundRequestService;
import com.szmsd.finance.vo.RefundRequestListVO;
import com.szmsd.finance.vo.RefundRequestVO;
import com.szmsd.inventory.api.feign.InventoryFeignService;
import com.szmsd.inventory.domain.dto.QueryFinishListDTO;
import com.szmsd.inventory.domain.vo.QueryFinishListVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.groups.Default;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * <p>
 * ??????????????? ???????????????
 * </p>
 *
 * @author 11
 * @since 2021-08-13
 */
@Slf4j
@Service
public class RefundRequestServiceImpl extends ServiceImpl<RefundRequestMapper, FssRefundRequest> implements IRefundRequestService {
    @Resource
    private IRemoteApi remoteApi;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private IAccountBalanceService accountBalanceService;

    @Autowired
    private BasRefundRequestMapper basRefundRequestMapper;

    @Autowired
    private AccountSerialBillMapper accountSerialBillMapper;

    @Override
    @DataScope(value = "cus_code")
    public List<RefundRequestListVO> selectRequestList(RefundRequestQueryDTO queryDTO) {
        return baseMapper.selectRequestList(queryDTO);
    }

    @Override
    public RefundRequestVO selectDetailInfoById(String id) {
        FssRefundRequest fssRefundRequest = baseMapper.selectById(id);
        AssertUtil.notNull(fssRefundRequest, "???????????????!");
        RefundRequestVO refundRequestVO = new RefundRequestVO();
        BeanUtils.copyProperties(fssRefundRequest, refundRequestVO);
        return refundRequestVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertRefundRequest(RefundRequestListDTO addDTO) {
        List<RefundRequestDTO> refundRequestList = addDTO.getRefundRequestList();
        return this.insertBatchRefundRequest(refundRequestList);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertBatchRefundRequest(List<RefundRequestDTO> addList) {
        List<String> strings = remoteApi.genNo(addList.size());
        AtomicInteger noLine = new AtomicInteger(0);
        List<FssRefundRequest> collect = addList.stream().map(x -> {
            FssRefundRequest fssRefundRequest = new FssRefundRequest();
            BeanUtils.copyProperties(x, fssRefundRequest);
            if(fssRefundRequest.getAuditStatus() == null) {
                fssRefundRequest.setAuditStatus(RefundStatusEnum.BRING_INTO_COURT.getStatus())
                        .setProcessNo(strings.get(noLine.getAndIncrement()));
            }
            return fssRefundRequest;
        }).collect(Collectors.toList());


        return this.saveBatch(collect) ? addList.size() : 0;
    }






    @Transactional(rollbackFor = Exception.class)
    public int insertBatchRefundRequestimport(List<RefundRequestDTO> addList) {
        List<String> strings = remoteApi.genNo(addList.size());
        AtomicInteger noLine = new AtomicInteger(0);
        List<FssRefundRequest> collect = addList.stream().map(x -> {
            FssRefundRequest fssRefundRequest = new FssRefundRequest();
            BeanUtils.copyProperties(x, fssRefundRequest);
            fssRefundRequest.setAuditStatus(RefundStatusEnum.BRING_INTO_COURT.getStatus())
                    .setProcessNo(strings.get(noLine.getAndIncrement()));
            return fssRefundRequest;
        }).collect(Collectors.toList());
        int a=this.saveBatch(collect) ? addList.size() : 0;
        List<String> ids=collect.stream().map(x->String.valueOf(x.getId())).collect(Collectors.toList());
        BasRefundRequest basRefundRequest=new BasRefundRequest();
        ids.forEach(x->{
            basRefundRequest.setFssRefundId(Integer.parseInt(x));
            basRefundRequestMapper.insertSelective(basRefundRequest);
        });
//        RefundReviewDTO refundReviewDTO=new RefundReviewDTO();
//        refundReviewDTO.setIdList(ids);
//        refundReviewDTO.setStatus(RefundStatusEnum.valueOf("COMPLETE"));
//        approve(refundReviewDTO);
        return a;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateRefundRequest(RefundRequestDTO updateDTO) {
        AssertUtil.notNull(updateDTO.getId(), "id is require");
        FssRefundRequest fssRefundRequest = new FssRefundRequest();
        BeanUtils.copyProperties(updateDTO, fssRefundRequest);
        fssRefundRequest.setAuditStatus(RefundStatusEnum.BRING_INTO_COURT.getStatus());
        return baseMapper.update(fssRefundRequest, Wrappers.<FssRefundRequest>lambdaUpdate()
                .in(FssRefundRequest::getAuditStatus, RefundStatusEnum.BRING_INTO_COURT.getStatus()
                        , RefundStatusEnum.INITIAL.getStatus()).eq(FssRefundRequest::getId, updateDTO.getId()));
    }

    @Override
    public int deleteRefundRequestByIds(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) return 1;
        return baseMapper.delete(Wrappers.<FssRefundRequest>lambdaUpdate().
                in(FssRefundRequest::getId, ids)
                .in(FssRefundRequest::getAuditStatus, RefundStatusEnum.BRING_INTO_COURT.getStatus(), RefundStatusEnum.INITIAL.getStatus()));
    }

    //??????????????????
    @Override
//    @Transactional(rollbackFor = Exception.class)
    public int importByTemplate(MultipartFile file) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(8, 16, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000), new NamedThreadFactory("???RefundImport???==", false), new ThreadPoolExecutor.CallerRunsPolicy());
        try (InputStream inputStream = file.getInputStream()) {
            List<RefundRequestDTO> basPackingAddList = EasyExcel.read(inputStream, RefundRequestDTO.class, new SyncReadListener()).sheet().doReadSync();
            int count = 500;
            int size = basPackingAddList.size();
            int segments = size / count;
            segments = size % count == 0 ? segments : segments + 1;
            CountDownLatch countDownLatch = new CountDownLatch(segments);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < segments; i++) {
                List<RefundRequestDTO> refundRequestDTOS;
                if (i == segments - 1) {
                    refundRequestDTOS = basPackingAddList.subList(count * i, size);
                } else {
                    refundRequestDTOS = basPackingAddList.subList(count * i, count * (i + 1));
                }
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                Future<String> submit = threadPoolExecutor.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    LoginUser loginUser = SecurityUtils.getLoginUser();
                    String errorMsg = "";
                    try {
                            handleInsertData(refundRequestDTOS, true);
                        this.insertBatchRefundRequestimport(refundRequestDTOS);
                    } catch (Exception e) {
                        e.printStackTrace();
                        errorMsg = e.getMessage();
                        log.error("=================????????????=================???\n{} \n", JSONObject.toJSONString(refundRequestDTOS), e);
                    } finally {
                        countDownLatch.countDown();
                    }
                    return errorMsg;
                });
                futures.add(submit);
            }


            countDownLatch.await();
            StringBuilder stringBuilder = new StringBuilder();
            futures.forEach(errorMsg -> {
                String s = "";
                try {
                    s = errorMsg.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    log.error("?????????????????????", e);
                    s = e.getMessage();
                }
                stringBuilder.append(s);
            });
            AssertUtil.isTrue(StringUtils.isBlank(stringBuilder.toString()), stringBuilder.toString());
            return 1;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
            log.error("??????????????????22???", e);
        } finally {
            threadPoolExecutor.shutdown();
        }
        return 1;

    }


    //??????
    @Override
    public int importByTemplateus(MultipartFile file) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(8, 16, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1000), new NamedThreadFactory("???RefundImport???==", false), new ThreadPoolExecutor.CallerRunsPolicy());
        try (InputStream inputStream = file.getInputStream()) {
            List<RefundRequestDTO> basPackingAddList = EasyExcel.read(inputStream, RefundRequestDTO.class, new SyncReadListener()).sheet().doReadSync();
            int count = 500;
            int size = basPackingAddList.size();
            int segments = size / count;
            segments = size % count == 0 ? segments : segments + 1;
            CountDownLatch countDownLatch = new CountDownLatch(segments);
            List<Future<String>> futures = new ArrayList<>();

            for (int i = 0; i < segments; i++) {
                List<RefundRequestDTO> refundRequestDTOS;
                if (i == segments - 1) {
                    refundRequestDTOS = basPackingAddList.subList(count * i, size);
                } else {
                    refundRequestDTOS = basPackingAddList.subList(count * i, count * (i + 1));
                }
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                Future<String> submit = threadPoolExecutor.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    LoginUser loginUser = SecurityUtils.getLoginUser();
                    String errorMsg = "";
                    try {
                        handleInsertData(refundRequestDTOS, true);
                        this.insertBatchRefundRequest(refundRequestDTOS);
                    } catch (Exception e) {
                        e.printStackTrace();
                        errorMsg = e.getMessage();
                        log.error("=================????????????=================???\n{} \n", JSONObject.toJSONString(refundRequestDTOS), e);
                    } finally {
                        countDownLatch.countDown();
                    }
                    return errorMsg;
                });
                futures.add(submit);
            }


            countDownLatch.await();
            StringBuilder stringBuilder = new StringBuilder();
            futures.forEach(errorMsg -> {
                String s = "";
                try {
                    s = errorMsg.get();
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    log.error("?????????????????????", e);
                    s = e.getMessage();
                }
                stringBuilder.append(s);
            });
            AssertUtil.isTrue(StringUtils.isBlank(stringBuilder.toString()), stringBuilder.toString());
            return 1;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
            log.error("??????????????????22???", e);
        } finally {
            threadPoolExecutor.shutdown();
        }
        return 1;
    }

    @Resource
    private ConfigData configData;

    public void handleInsertData(List<RefundRequestDTO> basPackingAddList, Boolean isExport) {
        AssertUtil.isTrue(CollectionUtils.isNotEmpty(basPackingAddList), "????????????,???????????????!");
        if (isExport) {
            //????????????
            AtomicInteger importNo = new AtomicInteger(1);
            List<String> errorMsg = new LinkedList<>();
            StopWatch stopWatch = new StopWatch("??????");
            stopWatch.start("??????????????????");
            basPackingAddList.forEach(basSellAccountPeriodAddDTO -> {
                if (StringUtils.isNotBlank(basSellAccountPeriodAddDTO.getTreatmentProperties()) && "??????".equals(basSellAccountPeriodAddDTO.getTreatmentProperties())) {
                    FileVerifyUtil.validate(basSellAccountPeriodAddDTO, importNo, errorMsg, ICompensateCheck.class);
                } else {
                    FileVerifyUtil.validate(basSellAccountPeriodAddDTO, importNo, errorMsg, Default.class);
                }
            });
            stopWatch.stop();
            stopWatch.start("????????????");
            AssertUtil.isTrue(CollectionUtils.isEmpty(errorMsg), String.join("\n", errorMsg));
            basPackingAddList.forEach(x -> {
                AssertUtil.isTrue(remoteApi.checkCusCode(x.getCusCode()), "??????" + x.getCusCode() + "?????????");
                // ??????????????????????????????
                // ????????????	????????????	???????????? ????????????	????????????	????????????	???????????? ??????
                ConfigData.MainSubCode mainSubCode = configData.getMainSubCode();

                x.setTreatmentPropertiesCode(remoteApi.getSubCode(mainSubCode.getTreatmentProperties(), x.getTreatmentProperties()));
                x.setWarehouseName(remoteApi.getWareHouseName(x.getWarehouseCode()));

//                x.setResponsibilityAreaCode(remoteApi.getSubCode(mainSubCode.getResponsibilityArea(), x.getResponsibilityArea()));

                x.setBusinessTypeCode(remoteApi.getSubCodeObjSubCode(mainSubCode.getBusinessType(), x.getBusinessTypeName()));
//                String subValue = businessTypeObj.getSubValue();
//                x.setBusinessDetailsCode(remoteApi.getSubCode(subValue, x.getBusinessDetails()));

                x.setFeeTypeCode(remoteApi.getSubCodeObjSubCode(mainSubCode.getTypesOfFee(), x.getFeeTypeName()));
//                String feeTypeSubValue = feeTypeSubCodeObj.getSubValue();
//                x.setFeeCategoryCode(remoteApi.getSubCode(feeTypeSubValue, x.getFeeCategoryName()));
                String attributesCode = remoteApi.getSubCode(mainSubCode.getProperty(), x.getAttributes());
                if (StringUtils.isNotBlank(attributesCode)) {
                    x.setAttributesCode(attributesCode);
                } else {
                    x.setAttributes(null);
                }

                // ???????????????????????????
                x.setCompensationPaymentFlag((StringUtils.isNotBlank(x.getCompensationPaymentFlag()) && "?????????".equals(x.getCompensationPaymentFlag())) ? "1" : "0");
                x.setCompensationPaymentArrivedFlag((StringUtils.isNotBlank(x.getCompensationPaymentFlag()) && "???".equals(x.getCompensationPaymentFlag())) ? "1" : "0");

                x.setCurrencyName(remoteApi.getSubNameByValue(mainSubCode.getCurrency(), x.getCurrencyCode()));

                // ????????????
                if (StringUtils.isNotBlank(x.getCompensationPaymentCurrencyCode())) {
                    String compensationPaymentCurrency = remoteApi.getSubNameByValue(mainSubCode.getCurrency(), x.getCompensationPaymentCurrencyCode());
                    x.setCompensationPaymentCurrency(compensationPaymentCurrency);
                    if (StringUtils.isBlank(compensationPaymentCurrency)) x.setCompensationPaymentCurrency(null);
                }
            });
            stopWatch.stop();
            stopWatch.start("????????????");
            StringBuilder errorMsgBuilder = new StringBuilder();
            //?????????-?????????????????? ?????????????????????????????????????????????
            Map<String, List<String>> collect = basPackingAddList.stream().filter(x -> StringUtils.isNotBlank(x.getOrderNo())).collect(Collectors.groupingBy(RefundRequestDTO::getCusCode, Collectors.mapping(RefundRequestDTO::getOrderNo, Collectors.toList())));
            collect.forEach((cusCode, processNoList) -> {
                processNoList = processNoList.stream().distinct().collect(Collectors.toList());
                Map<Integer, List<String>> ck = processNoList.stream().collect(Collectors.groupingBy(x -> (x.startsWith("CK")||x.startsWith("RECK")) ? 1 : 0));


                ck.forEach((type, list) -> {
                    QueryFinishListDTO queryFinishListDTO = new QueryFinishListDTO();
                    queryFinishListDTO.setCusCode(cusCode);
                    queryFinishListDTO.setNoList(list);
                    queryFinishListDTO.setType(type);
                    queryFinishListDTO.setPageNum(1);
                    queryFinishListDTO.setPageSize(999);
                    log.info("???????????????{}", JSONObject.toJSONString(queryFinishListDTO));
                    TableDataInfo<QueryFinishListVO> queryFinishListVOTableDataInfo = this.queryFinishList(queryFinishListDTO);
                    log.info("?????????????????????{}", JSONObject.toJSONString(queryFinishListVOTableDataInfo));
                    AssertUtil.isTrue(queryFinishListVOTableDataInfo.getCode() == HttpStatus.SUCCESS, "??????????????????");
                    if (queryFinishListVOTableDataInfo.getTotal() != list.size()) {
                        List<String> collect1 = queryFinishListVOTableDataInfo.getRows().stream().map(QueryFinishListVO::getNo).collect(Collectors.toList());
                        list.removeAll(collect1);
                        errorMsgBuilder.append("??????????????????").append(cusCode).append("????????????:").append(StringUtils.join(list, ",")).append("???????????????/??????????????????;").append("\n");
                    }
                });
            });
            stopWatch.stop();
            log.info(stopWatch.prettyPrint());
            if (StringUtils.isNotBlank(errorMsgBuilder.toString())) {
                throw new RuntimeException(errorMsgBuilder.toString());
            }
        }
    }

    public static final long time = 90L;
    public static final TimeUnit unit = TimeUnit.SECONDS;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approve(RefundReviewDTO refundReviewDTO) {
        String key = "cky-test-fss-approve";
        RLock lock = redissonClient.getLock(key);
        try {
            if (lock.tryLock(time, unit)) {
                List<String> ids = refundReviewDTO.getIdList();
                RefundStatusEnum status = refundReviewDTO.getStatus();
                String reviewRemark = refundReviewDTO.getReviewRemark();
                if (!refundReviewDTO.getReviewRemark().equals("??????????????????")){


                    LoginUser loginUser = SecurityUtils.getLoginUser();
                //??????????????????????????????????????????????????????
                List<FssRefundRequest> fssRefundRequests = baseMapper.selectList(Wrappers.<FssRefundRequest>lambdaQuery().in(FssRefundRequest::getId, ids).eq(FssRefundRequest::getAuditStatus,1));

                int update = baseMapper.update(null, Wrappers.<FssRefundRequest>lambdaUpdate()
                        .in(FssRefundRequest::getId, ids)
                        .eq(FssRefundRequest::getAuditStatus, RefundStatusEnum.BRING_INTO_COURT.getStatus())

                        .set(FssRefundRequest::getReviewRemark, reviewRemark)
                        .set(FssRefundRequest::getAuditStatus, status.getStatus())
                        .set(FssRefundRequest::getAuditTime, LocalDateTime.now())
                        .set(FssRefundRequest::getReviewerId, loginUser.getUserId())
                        .set(FssRefundRequest::getReviewerCode, loginUser.getSellerCode())
                        .set(FssRefundRequest::getReviewerName, loginUser.getUsername())
                );
                    AssertUtil.isTrue(update == ids.size(), "????????????!");
                    //????????????????????????
                    this.afterApprove(status, fssRefundRequests);
                    return update;
                }else if (refundReviewDTO.getReviewRemark().equals("??????????????????")){
                    //??????????????????????????????????????????????????????
                    List<FssRefundRequest> fssRefundRequests = baseMapper.selectList(Wrappers.<FssRefundRequest>lambdaQuery().in(FssRefundRequest::getId, ids).eq(FssRefundRequest::getAuditStatus,1));

                    int update = baseMapper.update(null, Wrappers.<FssRefundRequest>lambdaUpdate()
                            .in(FssRefundRequest::getId, ids)
                            .eq(FssRefundRequest::getAuditStatus, RefundStatusEnum.BRING_INTO_COURT.getStatus())

                            .set(FssRefundRequest::getReviewRemark, reviewRemark)
                            .set(FssRefundRequest::getAuditStatus, status.getStatus())
                            .set(FssRefundRequest::getAuditTime, LocalDateTime.now())
                            .set(FssRefundRequest::getReviewerId, 1)
                            .set(FssRefundRequest::getReviewerCode, "admin")
                            .set(FssRefundRequest::getReviewerName, "??????")
                    );
                    AssertUtil.isTrue(update == ids.size(), "????????????!");
                    //????????????????????????
                    this.afterApprove(status, fssRefundRequests);
                    return update;
                }

            } else {
                log.error("????????????????????????,???????????????{}", JSONObject.toJSONString(refundReviewDTO));
                throw new RuntimeException("????????????????????????,???????????????");
            }
        } catch (InterruptedException e) {
            log.error("????????????????????????,???????????????{}", JSONObject.toJSONString(refundReviewDTO));
            log.error("????????????????????????,???????????????", e);
            throw new RuntimeException("????????????????????????,???????????????");
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("????????????RefundPayFactory ????????????--");
            }
            log.info("????????????RefundPayFactory --??????--");
        }
        return 0;
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * ????????????????????????????????????????????????
     * <p>
     * ?????????????????????????????????????????????
     *
     * @param status ????????????
     * @param idList ??????id??????
     */
    @Transactional(rollbackFor = Exception.class)
    public void afterApprove(RefundStatusEnum status,  List<FssRefundRequest> fssRefundRequests) {
        if (RefundStatusEnum.COMPLETE != status){
            return;
        }
        log.info("????????????-???????????????????????? {}");

        fssRefundRequests.forEach(x->{
          List<Map> list =baseMapper.selectOutbounds(x.getOrderNo());
          log.info("afterApprove:{}", JSON.toJSONString(list));
          if (list.size()>0){
              Map map = list.get(0);
              if (map.get("trackingNo") != null){
                  x.setTrackingNo(map.get("trackingNo").toString());
              }
              if (map.get("shipmentRule") != null){
                  x.setShipmentRule(map.get("shipmentRule").toString());
              }
              if (String.valueOf(list.get(0).get("calcWeight"))!=null&&!String.valueOf(list.get(0).get("calcWeight")).equals("")){
                  x.setCalcWeight((BigDecimal) list.get(0).get("calcWeight"));
              }
              if (String.valueOf(list.get(0).get("weight"))!=null&&!String.valueOf(list.get(0).get("weight")).equals("")){
                  x.setWeight((Double) list.get(0).get("weight"));
              }
              if (String.valueOf(list.get(0).get("specifications"))!=null&&!String.valueOf(list.get(0).get("specifications")).equals("")){
                  x.setSpecifications(String.valueOf(list.get(0).get("specifications")));
              }
              if (list.get(0).get("createTime")!=null){
                  x.setDelOucreateTime((Date) list.get(0).get("createTime"));
              }
              if (list.get(0).get("createTime")!=null){
                  x.setDelOucreateTime((Date) list.get(0).get("createTime"));
              }
              if (list.get(0).get("shipmentRuleName")!=null){
                  x.setShipmentRuleName(String.valueOf(list.get(0).get("shipmentRuleName")));
              }
              if (String.valueOf(list.get(0).get("amazonReferenceId"))!=null){
                  x.setAmazonReferenceId(String.valueOf(list.get(0).get("amazonReferenceId")));
              }

              if (String.valueOf(list.get(0).get("country"))!=null){
                  x.setCountry(String.valueOf(list.get(0).get("country")));
              }
              if (String.valueOf(list.get(0).get("countryCode"))!=null){
                  x.setCountryCode(String.valueOf(list.get(0).get("countryCode")));
              }
          }
        });
        Map<RefundProcessEnum, List<FssRefundRequest>> collect = fssRefundRequests.stream().collect(Collectors.groupingBy(x -> {
            ConfigData.MainSubCode mainSubCode = configData.getMainSubCode();
            BasSub subCodeObj = remoteApi.getSubCodeObj(mainSubCode.getTreatmentProperties(), x.getTreatmentProperties());
            String subValue = subCodeObj.getSubValue();
            return RefundProcessEnum.getProcessStrategy(subValue);
        }));
        log.info("????????????{}", JSONObject.toJSONString(collect));
        // TODO ??????????????????????????????????????????/???????????????????????????????????????
        collect.forEach((processEnum, list) -> {
            switch (processEnum) {
                case ADD:
                    log.info("ADD--{}", list);
                    list.forEach(x -> {
                        String remark = String.format("?????????%s,????????????", x.getProcessNo());
                        CustPayDTO custPayDTO = getCustPayDTO(x, remark);
                        //custPayDTO.setRemark(remark);
                        R r = accountBalanceService.refund(custPayDTO);
                        AssertUtil.isTrue(r.getCode() == HttpStatus.SUCCESS, r.getMsg());
                        log.info("ADD--{}--{}", list, JSONObject.toJSONString(r));
                    });
                    return;
                case SUBTRACT:
                    log.info("SUBTRACT--{}", list);
                    list.forEach(x -> {
                        String remark = String.format("?????????%s,????????????", x.getProcessNo());
                        CustPayDTO custPayDTO = getCustPayDTO(x, remark);
                        custPayDTO.setAmount(x.getAmount().multiply(new BigDecimal("-1")));
                        //custPayDTO.setRemark(remark);
                        R r = accountBalanceService.refund(custPayDTO);
                        AssertUtil.isTrue(r.getCode() == HttpStatus.SUCCESS, r.getMsg() + "??????????????????????????????????????????");
                        log.info("SUBTRACT--{}--{}", list, JSONObject.toJSONString(r));
                    });
                    return;
                default:
                    log.info("?????????--{}", list);
                    return;
            }
        });
    }

    private CustPayDTO getCustPayDTO(FssRefundRequest x, String remark) {
        CustPayDTO custPayDTO = new CustPayDTO();
        custPayDTO.setAmount(x.getAmount());
        custPayDTO.setNo(Optional.ofNullable(x.getOrderNo()).orElse(""));
        custPayDTO.setCurrencyCode(x.getCurrencyCode());
        custPayDTO.setCurrencyName(x.getCurrencyName());
        custPayDTO.setCusCode(x.getCusCode());
        custPayDTO.setCusId((long) x.getCusId());
        custPayDTO.setCusName(x.getCusName());
        custPayDTO.setWarehouseCode(x.getWarehouseCode());
        custPayDTO.setWarehouseName(x.getWarehouseName());
        List<AccountSerialBillDTO> accountSerialBillList = new ArrayList<>();
        AccountSerialBillDTO accountSerialBillDTO = new AccountSerialBillDTO();
        accountSerialBillDTO.setChargeType(x.getFeeCategoryCode());
        accountSerialBillDTO.setPayMethod(BillEnum.PayMethod.REFUND);
//        accountSerialBillDTO.setBusinessCategory(BillEnum.CostCategoryEnum.REFUND.getName());
        accountSerialBillDTO.setBusinessCategory(x.getTreatmentProperties());
        accountSerialBillDTO.setChargeCategory(x.getFeeTypeName());
        accountSerialBillDTO.setAmount(x.getAmount());
        accountSerialBillDTO.setCusCode(x.getCusCode());
        accountSerialBillDTO.setCusName(x.getCusName());
        accountSerialBillDTO.setCurrencyCode(x.getCurrencyCode());
        accountSerialBillDTO.setCurrencyName(x.getCurrencyName());
        accountSerialBillDTO.setTrackingNo(x.getTrackingNo());

        accountSerialBillDTO.setWeight(x.getWeight());
        accountSerialBillDTO.setCalcWeight(x.getCalcWeight());
        accountSerialBillDTO.setSpecifications(x.getSpecifications());
        accountSerialBillDTO.setCreateTime(x.getDelOucreateTime());

//        if (x.getTreatmentProperties().equals("??????")||x.getTreatmentProperties().equals("??????")||x.getTreatmentProperties().equals("??????")){
//         accountSerialBillDTO.setNature("??????/??????/??????");
//        }else {
            accountSerialBillDTO.setNature(x.getTreatmentProperties());
//        }
        accountSerialBillDTO.setBusinessType(x.getBusinessTypeName());
        accountSerialBillDTO.setChargeCategoryChange(x.getFeeTypeName());


        accountSerialBillDTO.setProductCode(x.getShipmentRule());
        accountSerialBillDTO.setRemark(x.getRemark());
        accountSerialBillDTO.setAmazonLogisticsRouteId(x.getAmazonReferenceId());
        accountSerialBillDTO.setCountry(x.getCountry());
        accountSerialBillDTO.setCountryCode(x.getCountryCode());
        accountSerialBillDTO.setShipmentRuleName(x.getShipmentRuleName());
        accountSerialBillDTO.setShipmentRule(x.getShipmentRule());
        accountSerialBillDTO.setNote(x.getNoteAppended());
        accountSerialBillDTO.setChargeType(x.getFeeCategoryName());
        accountSerialBillDTO.setPrcState(x.getPrcState());
        accountSerialBillDTO.setGrade(x.getGradeName());
        accountSerialBillList.add(accountSerialBillDTO);
        custPayDTO.setSerialBillInfoList(accountSerialBillList);
        custPayDTO.setRemark(x.getRemark());
        custPayDTO.setSerialNumber(x.getProcessNo());

        return custPayDTO;
    }

    @Override
    public int confirmOperation(ConfirmOperationDTO confirmOperationDTO) {
        //TODO ??????????????????????????????????????????/???????????????????????????????????????

        return 0;
    }

    @Resource
    private DelOutboundFeignService delOutboundFeignService;
    @Resource
    private InventoryFeignService inventoryFeignService;

    @Override
    public TableDataInfo<QueryFinishListVO> queryFinishList(QueryFinishListDTO queryFinishListDTO) {
        TableDataInfo<QueryFinishListVO> result;
        if (queryFinishListDTO.getType() == 1) {
            result = delOutboundFeignService.queryFinishList(queryFinishListDTO);
        } else {
            result = inventoryFeignService.queryFinishList(queryFinishListDTO);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public R autoRefund(RefundRequestListDTO addDTO) {

        log.info("???????????????????????????{}",JSON.toJSONString(addDTO));

        List<RefundRequestDTO> refundRequestDTOS = addDTO.getRefundRequestList();

        if(CollectionUtils.isEmpty(refundRequestDTOS)){
            return R.failed("????????????");
        }

        List<Long> accountSerialBillIds = refundRequestDTOS.stream().map(RefundRequestDTO::getAccountSerialBillId).collect(Collectors.toList());

        if(CollectionUtils.isNotEmpty(accountSerialBillIds)){
            LambdaUpdateWrapper<AccountSerialBill> update = Wrappers.lambdaUpdate();
            update.set(AccountSerialBill::getPrcState, PrcStateEnum.SECOND.getCode()).in(AccountSerialBill::getId, accountSerialBillIds);
            accountSerialBillMapper.update(null,update);
        }

        int add = this.insertBatchRefundRequest(refundRequestDTOS);

        if(add == 0){
            return R.failed("??????????????????");
        }

        List<String> orderNoList = refundRequestDTOS.stream().map(RefundRequestDTO::getOrderNo).collect(Collectors.toList());
        log.info("????????????????????????orderNoList???{}",JSON.toJSONString(orderNoList));
        if(CollectionUtils.isEmpty(orderNoList) || orderNoList.size() == 0){
            throw new RuntimeException("????????????order No ??????");
        }

        List<FssRefundRequest> fssRefundRequests = baseMapper.selectList(Wrappers.<FssRefundRequest>query().lambda().in(FssRefundRequest::getOrderNo,orderNoList));

        if(CollectionUtils.isEmpty(fssRefundRequests)){
            throw new RuntimeException("????????????????????????");
        }

        List<String> ids = new ArrayList<>();
        for(FssRefundRequest fssRefundRequest : fssRefundRequests){
            ids.add(fssRefundRequest.getId().toString());
        }

        try {
            //??????????????????????????????????????????????????????
            //List<FssRefundRequest> fssRefundRequests = baseMapper.selectList(Wrappers.<FssRefundRequest>lambdaQuery().in(FssRefundRequest::getId, ids).eq(FssRefundRequest::getAuditStatus,1));
            this.afterApprove(RefundStatusEnum.COMPLETE, fssRefundRequests);
        }catch (Exception e){
            throw new RuntimeException("??????????????????:"+e.getMessage());
        }

        return R.ok();
    }
}

