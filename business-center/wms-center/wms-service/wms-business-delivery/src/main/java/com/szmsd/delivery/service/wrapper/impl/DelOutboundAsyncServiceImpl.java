package com.szmsd.delivery.service.wrapper.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.szmsd.bas.api.feign.BasWarehouseFeignService;
import com.szmsd.bas.api.service.BasePackingClientService;
import com.szmsd.bas.api.service.BaseProductClientService;
import com.szmsd.bas.domain.BasWarehouse;
import com.szmsd.bas.domain.BasePacking;
import com.szmsd.bas.domain.BaseProduct;
import com.szmsd.bas.dto.BasePackingConditionQueryDto;
import com.szmsd.bas.dto.BaseProductConditionQueryDto;
import com.szmsd.chargerules.api.feign.OperationFeignService;
import com.szmsd.common.core.constant.Constants;
import com.szmsd.common.core.constant.HttpStatus;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.delivery.domain.*;
import com.szmsd.delivery.dto.DelOutboundFurtherHandlerDto;
import com.szmsd.delivery.enums.*;
import com.szmsd.delivery.event.*;
import com.szmsd.delivery.service.*;
import com.szmsd.delivery.service.impl.DelOutboundServiceImplUtil;
import com.szmsd.delivery.service.wrapper.*;
import com.szmsd.delivery.util.Utils;
import com.szmsd.delivery.vo.DelOutboundOperationVO;
import com.szmsd.exception.api.feign.ExceptionInfoFeignService;
import com.szmsd.exception.dto.ExceptionInfoStateDto;
import com.szmsd.exception.dto.ProcessExceptionOrderRequest;
import com.szmsd.exception.enums.StateSubEnum;
import com.szmsd.finance.api.feign.RechargesFeignService;
import com.szmsd.finance.dto.AccountSerialBillDTO;
import com.szmsd.finance.dto.CusFreezeBalanceDTO;
import com.szmsd.finance.dto.CustPayDTO;
import com.szmsd.finance.enums.BillEnum;
import com.szmsd.http.api.feign.HtpCarrierFeignService;
import com.szmsd.http.dto.ShipmentOrderSubmissionParam;
import com.szmsd.http.dto.TaskConfigInfo;
import com.szmsd.http.enums.HttpRechargeConstants;
import com.szmsd.http.util.DomainInterceptorUtil;
import com.szmsd.inventory.api.service.InventoryFeignClientService;
import com.szmsd.inventory.domain.dto.InventoryOperateDto;
import com.szmsd.inventory.domain.dto.InventoryOperateListDto;
import com.szmsd.pack.api.feign.PackageDeliveryConditionsFeignService;
import com.szmsd.pack.domain.PackageDeliveryConditions;
import com.szmsd.putinstorage.api.feign.InboundReceiptFeignService;
import com.szmsd.putinstorage.domain.dto.CreateInboundReceiptDTO;
import com.szmsd.putinstorage.domain.dto.InboundReceiptDetailDTO;
import com.szmsd.putinstorage.domain.dto.ReceiptRequest;
import com.szmsd.putinstorage.enums.InboundReceiptEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author zhangyuyuan
 * @date 2021-03-30 16:29
 */
@Service
public class DelOutboundAsyncServiceImpl implements IDelOutboundAsyncService {
    private final Logger logger = LoggerFactory.getLogger(DelOutboundAsyncServiceImpl.class);

    @Autowired
    private IDelOutboundService delOutboundService;

    @Autowired
    private IDelOutboundAddressService iDelOutboundAddressService;

    @Autowired
    @Lazy
    private IDelOutboundBringVerifyService delOutboundBringVerifyService;
    @Autowired
    private IDelOutboundDetailService delOutboundDetailService;
    @Autowired
    private InventoryFeignClientService inventoryFeignClientService;
    @SuppressWarnings({"all"})
    @Autowired
    private RechargesFeignService rechargesFeignService;
    @Autowired
    private IDelOutboundChargeService delOutboundChargeService;
    @SuppressWarnings({"all"})
    @Autowired
    private OperationFeignService operationFeignService;
    @Autowired
    private BasePackingClientService basePackingClientService;
    @SuppressWarnings({"all"})
    @Autowired
    private InboundReceiptFeignService inboundReceiptFeignService;
    @Autowired
    private BaseProductClientService baseProductClientService;
    @SuppressWarnings({"all"})
    @Autowired
    private BasWarehouseFeignService basWarehouseFeignService;
    @Value("${spring.application.name}")
    private String applicationName;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private IDelOutboundExceptionService delOutboundExceptionService;
    @Autowired
    private IDelOutboundCompletedService delOutboundCompletedService;

    @SuppressWarnings({"all"})
    @Autowired
    private PackageDeliveryConditionsFeignService packageDeliveryConditionsFeignService;
    @Autowired
    private IDelTrackService delTrackService;
    @Autowired
    private ExceptionInfoFeignService exceptionInfoFeignService;

    @Autowired
    private HtpCarrierFeignService htpCarrierFeignService;
    @Override
    public int shipmentPacking(Long id) {
        return this.shipmentPacking(id, true);
    }

    @Override
    public int shipmentPacking(String orderNo) {
        DelOutbound delOutbound = this.delOutboundService.getByOrderNo(orderNo);
        if (Objects.isNull(delOutbound)) {
            throw new CommonException("400", "???????????????");
        }
        return this.shipmentPacking(delOutbound, true, null);
    }
    @Override
    public int shipmentPacking(Long id, boolean shipmentShipping){
        return shipmentPacking(id, shipmentShipping, null);
    }

    @Override
    public int shipmentPacking(Long id, boolean shipmentShipping, DelOutboundFurtherHandlerDto dto) {
        // ???????????????????????????
        DelOutbound delOutbound = this.delOutboundService.getById(id);
        if (Objects.isNull(delOutbound)) {
            throw new CommonException("400", "???????????????");
        }
        return this.shipmentPacking(delOutbound, shipmentShipping, dto);
    }

    private int shipmentPacking(DelOutbound delOutbound, boolean shipmentShipping, DelOutboundFurtherHandlerDto dto) {
        String key = applicationName + ":DelOutbound:shipmentPacking:" + delOutbound.getOrderNo();
        RLock lock = this.redissonClient.getLock(key);
        try {
            if (lock.tryLock(0, TimeUnit.SECONDS)) {
                TimeInterval timer = DateUtil.timer();
                logger.info("(1)????????????????????????delOutbound:{}, timer:{}", JSON.toJSONString(delOutbound), timer.intervalRestart());
                // ?????????????????????WHSE_PROCESSING,PROCESSING????????????
                /*if (!(DelOutboundStateEnum.WHSE_PROCESSING.getCode().equals(delOutbound.getState())
                        || DelOutboundStateEnum.NOTIFY_WHSE_PROCESSING.getCode().equals(delOutbound.getState())
                        || DelOutboundStateEnum.PROCESSING.getCode().equals(delOutbound.getState())
                        || DelOutboundStateEnum.DELIVERED.getCode().equals(delOutbound.getState()))) {
                    logger.info("(1.1)???????????????????????????????????????????????????????????????{}", delOutbound.getState());
                    return 0;
                }*/
                if (DelOutboundStateEnum.COMPLETED.getCode().equals(delOutbound.getState())){
                    logger.info("(1.1)???????????????????????????????????????????????????????????????{}", delOutbound.getState());
                    return 0;
                }

                // ??????????????????
                if (!DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
                    logger.info("(1.1.1)????????????????????????????????????????????????{}", delOutbound.getOrderNo());
                    // ????????????????????????
                    TaskConfigInfo taskConfigInfo1 = TaskConfigInfoAdapter.getTaskConfigInfo(delOutbound.getOrderType());
                    String receiveShippingType = "";
                    // ?????? - ????????????????????????
                    if (null == taskConfigInfo1
                            && StringUtils.isNotEmpty(delOutbound.getWarehouseCode())
                            && StringUtils.isNotEmpty(delOutbound.getShipmentRule())) {
                        PackageDeliveryConditions packageDeliveryConditions = new PackageDeliveryConditions();
                        packageDeliveryConditions.setWarehouseCode(delOutbound.getWarehouseCode());
                        packageDeliveryConditions.setProductCode(delOutbound.getShipmentRule());
                        R<PackageDeliveryConditions> packageDeliveryConditionsR = this.packageDeliveryConditionsFeignService.info(packageDeliveryConditions);
                        PackageDeliveryConditions packageDeliveryConditionsRData = null;
                        if (null != packageDeliveryConditionsR && Constants.SUCCESS == packageDeliveryConditionsR.getCode()) {
                            packageDeliveryConditionsRData = packageDeliveryConditionsR.getData();
                        }
                        if (null != packageDeliveryConditionsRData) {
                            receiveShippingType = packageDeliveryConditionsRData.getCommandNodeCode();
                        }
                    /*else {
                        throw new CommonException("500", "??????????????????????????????????????????????????????" + delOutbound.getWarehouseCode() + "??????????????????" + delOutbound.getShipmentRule());
                    }*/
                    } else if (null != taskConfigInfo1) {
                        receiveShippingType = taskConfigInfo1.getReceiveShippingType();
                    }
                    logger.info("(1.1.2)??????????????????????????????{}????????????{}", delOutbound.getOrderNo(), receiveShippingType);
                    // ????????????????????????NotReceive
                    // ????????????????????????????????????AfterMeasured
                    if ("NotReceive".equals(receiveShippingType)) {
                        // ???????????????????????????
                        logger.info("(1.1.3)????????????????????????????????????????????????????????????{}", delOutbound.getOrderNo());
                        return 10;
                    }
                }
                DelOutboundWrapperContext context = this.delOutboundBringVerifyService.initContext(delOutbound, dto);
                logger.info("(2)???????????????????????????timer:{}", timer.intervalRestart());
                if (context != null) {
                    context.setShipmentShipping(shipmentShipping);
                    context.setExecShipmentShipping(true);
                }
                ShipmentEnum currentState;
                String shipmentState = delOutbound.getShipmentState();
                if (StringUtils.isEmpty(shipmentState)) {
                    currentState = ShipmentEnum.BEGIN;
                } else if(shipmentState.equals(ShipmentEnum.SHIPMENT_ORDER.toString())
                        || shipmentState.equals(ShipmentEnum.FREEZE_BALANCE.toString())
                        || shipmentState.equals(ShipmentEnum.FREEZE_INVENTORY.toString())){
                    //?????????????????????PRC????????????
                    currentState = ShipmentEnum.PRC_PRICING;
                }else {
                    currentState = ShipmentEnum.get(shipmentState);
                }
                try {
                    ApplicationContainer applicationContainer = new ApplicationContainer(context, currentState, ShipmentEnum.END, ShipmentEnum.BEGIN);
                    applicationContainer.action();
                    if (DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
                        this.delOutboundExceptionService.updateTrackNoByOutBoundNo(delOutbound.getOrderNo());
                        // ??????????????????
                        // ????????????????????????????????????????????????????????????
                        List<String> orderNoList = new ArrayList<>();
                        orderNoList.add(delOutbound.getOrderNo());
                        this.delOutboundCompletedService.add(orderNoList, DelOutboundOperationTypeEnum.SHIPPED.getCode());
                    }
                    logger.info("(3)???????????????timer:{}", timer.intervalRestart());
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    logger.info("(3.1)??????????????????????????????timer:{}", timer.intervalRestart());
                    throw e;
                }
                return 1;
            }
        } catch (Exception e) {
            this.logger.error(e.getMessage(), e);
            // ?????????
            try {
                throw e;
            } catch (InterruptedException interruptedException) {
                logger.error(interruptedException.getMessage(), interruptedException);
            }
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return 0;
    }

    @Transactional
    @Override
    public void processing(String orderNo) {
        // ?????????????????????????????????
        LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.set(DelOutbound::getState, DelOutboundStateEnum.WHSE_PROCESSING.getCode());
        updateWrapper.eq(DelOutbound::getOrderNo, orderNo);
        updateWrapper.eq(DelOutbound::getState, DelOutboundStateEnum.NOTIFY_WHSE_PROCESSING.getCode());
        boolean update = this.delOutboundService.update(updateWrapper);
        // ????????????????????????
        if (update) {
            DelOutbound delOutbound = new DelOutbound();
            delOutbound.setOrderNo(orderNo);
            delOutbound.setState(DelOutboundStateEnum.WHSE_PROCESSING.getCode());
            DelOutboundOperationLogEnum.OPN_SHIPMENT.listener(delOutbound);


        }


        ReceiptRequest receiptRequest = new ReceiptRequest();
        receiptRequest.setPackageOrderNo(orderNo);

        receiptRequest.setOperateOn(DateUtil.format(new Date(), "yyyy-MM-dd HH:mm:ss"));
        R r =inboundReceiptFeignService.receipt(receiptRequest);
        logger.info("{}inbound/receipt:{}" ,orderNo, JSON.toJSONString(r));
    }

    @Override
    public void completed(String orderNo, String type) {
        // ????????????
        // 1.????????????              DE
        // 2.1????????????             FEE_DE
        // 2.2??????????????????         OP_FEE_DE
        // 2.3???????????????           PM_FEE_DE
        // 3.????????????????????????       MODIFY
        // 4.??????                  END
        DelOutbound delOutbound = this.delOutboundService.getByOrderNo(orderNo);
        if (null == delOutbound) {
            return;
        }
        // ????????????
        if (DelOutboundStateEnum.COMPLETED.getCode().equals(delOutbound.getState())) {
            return;
        }
        // ?????????????????????
        String completedState = this.defaultValue(delOutbound.getCompletedState());
        String originCompletedState = completedState;
        String key = applicationName + ":DelOutbound:completed:" + orderNo;
        RLock lock = this.redissonClient.getLock(key);
        try {
            if (lock.tryLock(180,180, TimeUnit.SECONDS)) {
                // ??????????????????
                if (StringUtils.isEmpty(completedState)) {
                    // ????????????????????????
                    if (!DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
                        logger.info("????????????????????????:{}",delOutbound.getOrderNo());
                        // ??????????????????
                        try {
                            this.deduction(delOutbound);
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            throw new CommonException("500", "???????????????????????????" + e.getMessage());
                        }
                        logger.info("????????????????????????:{}",delOutbound.getOrderNo());
                    }
                    completedState = "FEE_DE";
                }
                // ?????????????????????SKU??????????????????
                boolean fee = true;
                if (DelOutboundOrderTypeEnum.DESTROY.getCode().equals(delOutbound.getOrderType())
                        || DelOutboundOrderTypeEnum.SELF_PICK.getCode().equals(delOutbound.getOrderType())
                        || DelOutboundOrderTypeEnum.NEW_SKU.getCode().equals(delOutbound.getOrderType())) {
                    fee = false;
                }
                if ("FEE_DE".equals(completedState)) {
                    if (fee) {

                        List<DelOutboundCharge> chargeList = this.delOutboundChargeService.listCharges(orderNo);

                        //?????????????????????????????????????????????
                        List<DelOutboundAddress> addresses = this.iDelOutboundAddressService.list(Wrappers.<DelOutboundAddress>query().lambda().eq(DelOutboundAddress::getOrderNo,orderNo));
                        DelOutboundAddress address = new DelOutboundAddress();
                        if(CollectionUtils.isNotEmpty(addresses)){
                            address = addresses.get(0);
                        }

                        Map<String, List<DelOutboundCharge>> groupByCharge =
                                chargeList.stream().collect(Collectors.groupingBy(DelOutboundCharge::getCurrencyCode));
                        for (String currencyCode: groupByCharge.keySet()) {
                            BigDecimal bigDecimal = new BigDecimal(0);
                            for (DelOutboundCharge c : groupByCharge.get(currencyCode)) {
                                if (c.getAmount() != null) {
                                    bigDecimal = bigDecimal.add(c.getAmount());
                                }
                            }

                            // ????????????
                            CustPayDTO custPayDTO = new CustPayDTO();
                            custPayDTO.setCusCode(delOutbound.getSellerCode());
                            custPayDTO.setCurrencyCode(currencyCode);
                            custPayDTO.setAmount(bigDecimal);
                            custPayDTO.setNo(delOutbound.getOrderNo());
                            custPayDTO.setPayMethod(BillEnum.PayMethod.BALANCE_DEDUCTIONS);
                            // ??????????????????
                            if (CollectionUtils.isNotEmpty(groupByCharge.get(currencyCode))) {
                                List<AccountSerialBillDTO> serialBillInfoList = new ArrayList<>(chargeList.size());
                                for (DelOutboundCharge charge : groupByCharge.get(currencyCode)) {
                                    AccountSerialBillDTO serialBill = new AccountSerialBillDTO();
                                    serialBill.setNo(orderNo);
                                    serialBill.setTrackingNo(delOutbound.getTrackingNo());
                                    serialBill.setCusCode(delOutbound.getSellerCode());
                                    serialBill.setCurrencyCode(charge.getCurrencyCode());
                                    serialBill.setAmount(charge.getAmount());
                                    serialBill.setWarehouseCode(delOutbound.getWarehouseCode());
                                    serialBill.setChargeCategory(charge.getChargeNameCn());
                                    serialBill.setChargeType(charge.getChargeNameCn());
                                    serialBill.setOrderTime(delOutbound.getCreateTime());
                                    serialBill.setPaymentTime(delOutbound.getShipmentsTime());
                                    serialBill.setProductCode(delOutbound.getShipmentRule());
                                    serialBill.setShipmentRule(delOutbound.getShipmentRule());
                                    serialBill.setShipmentRuleName(delOutbound.getShipmentRuleName());
                                    serialBill.setRemark(delOutbound.getRemark());
                                    serialBill.setAmazonLogisticsRouteId(delOutbound.getAmazonLogisticsRouteId());
                                    serialBill.setCountry(address.getCountry());
                                    serialBill.setCountryCode(address.getCountryCode());
                                    serialBill.setGrade(delOutbound.getGrade());
                                    serialBill.setWeight(delOutbound.getWeight());
                                    serialBill.setCalcWeight(delOutbound.getCalcWeight());
                                    serialBill.setRefNo(delOutbound.getRefNo());
                                    serialBill.setShipmentService(delOutbound.getShipmentService());

                                    serialBillInfoList.add(serialBill);
                                }
                                custPayDTO.setSerialBillInfoList(serialBillInfoList);
                            }
                            custPayDTO.setOrderType("Freight");
                            R<?> r = this.rechargesFeignService.feeDeductions(custPayDTO);
                            if (null == r || Constants.SUCCESS != r.getCode()) {
                                String message;
                                if (null != r) {
                                    message = "??????"+custPayDTO.getCurrencyCode()+"???????????????" + r.getMsg();
                                } else {
                                    message = "??????"+custPayDTO.getCurrencyCode()+"????????????";
                                }
                                throw new CommonException("500", message);
                            }

                        }


                    }
                    completedState = "OP_FEE_DE";
                }
                if ("OP_FEE_DE".equals(completedState)) {
                    DelOutboundOperationVO delOutboundOperationVO = new DelOutboundOperationVO();
                    delOutboundOperationVO.setOrderType(delOutbound.getOrderType());
                    delOutboundOperationVO.setOrderNo(orderNo);
                    R<?> r = this.operationFeignService.delOutboundCharge(delOutboundOperationVO);
                    DelOutboundServiceImplUtil.chargeOperationThrowCommonException(r);
                    completedState = "PM_FEE_DE";
                }
                if ("PM_FEE_DE".equalsIgnoreCase(completedState)) {

                    //?????????????????????????????????????????????
                    List<DelOutboundAddress> addresses = this.iDelOutboundAddressService.list(Wrappers.<DelOutboundAddress>query().lambda().eq(DelOutboundAddress::getOrderNo,orderNo));
                    DelOutboundAddress address = new DelOutboundAddress();
                    if(CollectionUtils.isNotEmpty(addresses)){
                        address = addresses.get(0);
                    }

                    // ?????????????????????????????????????????????????????????
                    String packingMaterial = delOutbound.getPackingMaterial();
                    if (StringUtils.isNotEmpty(packingMaterial)) {
                        BasePackingConditionQueryDto conditionQueryDto = new BasePackingConditionQueryDto();
                        conditionQueryDto.setCode(packingMaterial);
                        // ??????????????????
                        BasePacking basePacking = this.basePackingClientService.queryByCode(conditionQueryDto);
                        if (null != basePacking && null != basePacking.getPrice()) {
                            CustPayDTO custPayDTO = new CustPayDTO();
                            custPayDTO.setCusCode(delOutbound.getSellerCode());
                            custPayDTO.setPayType(BillEnum.PayType.PAYMENT_NO_FREEZE);
                            custPayDTO.setPayMethod(BillEnum.PayMethod.BALANCE_DEDUCTIONS);
                            custPayDTO.setCurrencyCode(HttpRechargeConstants.RechargeCurrencyCode.CNY.name());
                            custPayDTO.setAmount(BigDecimal.valueOf(basePacking.getPrice()));
                            custPayDTO.setNo(delOutbound.getOrderNo());
                            custPayDTO.setOrderType(delOutbound.getOrderType());
                            List<AccountSerialBillDTO> list = new ArrayList<>();
                            AccountSerialBillDTO dto = new AccountSerialBillDTO();
                            dto.setCurrencyCode(custPayDTO.getCurrencyCode());
                            dto.setAmount(custPayDTO.getAmount());
                            dto.setProductCategory(BillEnum.PayMethod.BALANCE_DEDUCTIONS.getPaymentName());
                            dto.setChargeCategory(BillEnum.CostCategoryEnum.MATERIAL_COST.getName());
                            dto.setWarehouseCode(basePacking.getWarehouseCode());
                            dto.setShipmentRule(delOutbound.getShipmentRule());
                            dto.setShipmentRuleName(delOutbound.getShipmentRuleName());
                            dto.setNote(delOutbound.getRemark());
                            R<List<BasWarehouse>> listR = basWarehouseFeignService.queryByWarehouseCodes(Collections.singletonList(basePacking.getWarehouseCode()));
                            if (listR.getCode() == HttpStatus.SUCCESS) {
                                List<BasWarehouse> data = listR.getData();
                                BasWarehouse basWarehouse = Optional.ofNullable(data).orElse(new ArrayList<>()).get(0);
                                String warehouseName = Optional.ofNullable(basWarehouse).map(BasWarehouse::getWarehouseNameCn).orElse("");
                                dto.setWarehouseName(warehouseName);
                            }
                            dto.setChargeType(BillEnum.FeeTypeEnum.BALANCE_DEDUCTIONS.getName());

                            dto.setAmazonLogisticsRouteId(delOutbound.getAmazonLogisticsRouteId());
                            dto.setCountryCode(address.getCountryCode());
                            dto.setCountry(address.getCountry());
                            dto.setGrade(delOutbound.getGrade());
                            dto.setWeight(delOutbound.getWeight());
                            dto.setCalcWeight(delOutbound.getCalcWeight());
                            dto.setRefNo(delOutbound.getRefNo());
                            dto.setShipmentService(delOutbound.getShipmentService());

                            list.add(dto);
                            custPayDTO.setSerialBillInfoList(list);
                            R<?> r = this.rechargesFeignService.feeDeductions(custPayDTO);
                            DelOutboundServiceImplUtil.chargeOperationThrowCommonException2(r);
                        }
                    }
                    completedState = "MODIFY";
                }
                if ("MODIFY".equals(completedState)) {
                    // ?????????????????????????????????
                    this.delOutboundService.completed(delOutbound.getId());
                    delOutbound.setState(DelOutboundStateEnum.COMPLETED.getCode());
                    DelOutboundOperationLogEnum.OPN_SHIPMENT.listener(delOutbound);
                    // ??????????????????
                    if (DelOutboundExceptionStateEnum.ABNORMAL.getCode().equals(delOutbound.getExceptionState())) {
                        this.delOutboundService.exceptionFix(delOutbound.getId());
                    }
                    completedState = "END";
                }
                // END
                // ????????????????????????1
                if ("END".equals(completedState)
                        && !DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
                    // ?????????CK1
                    if (DelOutboundOrderTypeEnum.NORMAL.getCode().equals(delOutbound.getOrderType())
                            || DelOutboundOrderTypeEnum.SELF_PICK.getCode().equals(delOutbound.getOrderType())) {
                        DelCk1RequestLog ck1RequestLog = new DelCk1RequestLog();
                        ck1RequestLog.setOrderNo(delOutbound.getOrderNo());
                        Map<String, String> headers = new HashMap<>();
                        headers.put(DomainInterceptorUtil.KEYWORD, delOutbound.getSellerCode());
                        ck1RequestLog.setRemark(JSON.toJSONString(headers));
                        ck1RequestLog.setType(DelCk1RequestLogConstant.Type.finished.name());
                        EventUtil.publishEvent(new DelCk1RequestLogEvent(ck1RequestLog));
                    }
                    // ?????????TY
                    this.pushTy(delOutbound);
                    // ???????????????SKU?????????SKU????????????????????????
                    if ((DelOutboundOrderTypeEnum.NEW_SKU.getCode().equals(delOutbound.getOrderType())
                            || DelOutboundOrderTypeEnum.SPLIT_SKU.getCode().equals(delOutbound.getOrderType()))) {

                        // ????????????
                        int totalDeclareQty = 0;
                        //??????SKU????????????
                        ArrayList<InboundReceiptDetailDTO> inboundReceiptDetailAddList = new ArrayList<>();
                        if (DelOutboundOrderTypeEnum.NEW_SKU.getCode().equals(delOutbound.getOrderType())) {
                            // ??????sku??????
                            BaseProductConditionQueryDto conditionQueryDto = new BaseProductConditionQueryDto();
                            ArrayList<String> skus = new ArrayList<>();
                            skus.add(delOutbound.getNewSku());
                            conditionQueryDto.setSkus(skus);
                            List<BaseProduct> productList = this.baseProductClientService.queryProductList(conditionQueryDto);
                            String skuName = "";
                            if (CollectionUtils.isNotEmpty(productList)) {
                                skuName = getSkuName(productList.get(0));
                            }
                            totalDeclareQty = Math.toIntExact(delOutbound.getBoxNumber());
                            InboundReceiptDetailDTO inboundReceiptDetailDTO = new InboundReceiptDetailDTO();
                            inboundReceiptDetailDTO
                                    .setDeclareQty(totalDeclareQty)
                                    .setSku(delOutbound.getNewSku())
                                    .setDeliveryNo(delOutbound.getOrderNo())
                                    .setSkuName(skuName);
                            inboundReceiptDetailAddList.add(inboundReceiptDetailDTO);
                        }
                        if (DelOutboundOrderTypeEnum.SPLIT_SKU.getCode().equals(delOutbound.getOrderType())) {
                            // ??????
                            List<DelOutboundDetail> detailList = this.delOutboundDetailService.listByOrderNo(delOutbound.getOrderNo());
                            List<String> skus = new ArrayList<>();
                            for (DelOutboundDetail detail : detailList) {
                                skus.add(detail.getSku());
                            }
                            BaseProductConditionQueryDto conditionQueryDto = new BaseProductConditionQueryDto();
                            conditionQueryDto.setSkus(skus);
                            List<BaseProduct> productList = this.baseProductClientService.queryProductList(conditionQueryDto);
                            Map<String, BaseProduct> productMap;
                            if (CollectionUtils.isNotEmpty(productList)) {
                                productMap = productList.stream().collect(Collectors.toMap(BaseProduct::getCode, v -> v, (v, v2) -> v));
                            } else {
                                productMap = new HashMap<>();
                            }
                            for (DelOutboundDetail detail : detailList) {
                                InboundReceiptDetailDTO inboundReceiptDetailDTO = new InboundReceiptDetailDTO();
                                int declareQty = Math.toIntExact(detail.getQty());
                                inboundReceiptDetailDTO
                                        .setDeclareQty(declareQty)
                                        .setSku(detail.getSku())
                                        .setDeliveryNo(delOutbound.getOrderNo())
                                        .setSkuName(getSkuName(productMap.get(detail.getSku())));
                                inboundReceiptDetailAddList.add(inboundReceiptDetailDTO);
                                totalDeclareQty += declareQty;
                            }
                        }
                        // ?????????????????? ???????????????????????????????????????????????????????????????????????????SKU
                        CreateInboundReceiptDTO createInboundReceiptDTO = new CreateInboundReceiptDTO();
                        createInboundReceiptDTO
                                .setDeliveryNo("")
                                .setWarehouseMethodCode("055006")
                                .setOrderNo("")
                                .setCusCode(delOutbound.getSellerCode())
                                .setVat("")
                                .setWarehouseCode(delOutbound.getWarehouseCode())
                                .setOrderType(InboundReceiptEnum.OrderType.NEW_SKU.getValue())
                                .setWarehouseCategoryCode("056001")
                                .setDeliveryWayCode("053002")
                                .setTotalDeclareQty(totalDeclareQty)
                                .setTotalPutQty(0)
                                .setRemark(delOutbound.getRemark());
                        createInboundReceiptDTO.setInboundReceiptDetails(inboundReceiptDetailAddList);
                        this.inboundReceiptFeignService.saveOrUpdate(createInboundReceiptDTO);
                    }

                    /**
                     * ??????SRM??????????????????
                     */
                    this.pushSrmCost(delOutbound);

                    //??????????????????????????????
                    try {
                        ProcessExceptionOrderRequest request = new ProcessExceptionOrderRequest();
                        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                        request.setOperateOn(df.format(new Date()));
                        request.setOrderNo(delOutbound.getOrderNo());
                        request.setRemark("The system completes automatically");
                        exceptionInfoFeignService.processByOrderNo(request);
                    }catch (Exception e){
                        logger.error("?????????{}????????????", delOutbound.getOrderNo());
                        e.printStackTrace();
                    }


                    delTrackService.addData(new DelTrack()
                            .setOrderNo(delOutbound.getOrderNo())
                            .setTrackingNo(delOutbound.getTrackingNo())
                            .setTrackingStatus("WarehouseShipped")
                            .setDescription("DMF, Departure Scan"));

                    if(StringUtils.equals(type, "pushDate")){
                        //?????????wms?????????
                        ShipmentOrderSubmissionParam param = new ShipmentOrderSubmissionParam();
                        param.setReferenceNumber(delOutbound.getReferenceNumber());
                        try {

                            logger.info("??????submission???????????????{},{}",delOutbound.getOrderNo(),JSONUtil.toJsonStr(param));

                            //??????????????????????????????????????????????????????
                            R r = htpCarrierFeignService.submission(param);

                            logger.info("??????submission???????????????{},{}",delOutbound.getOrderNo(),JSONUtil.toJsonStr(r.getData()));

                            if(r == null || r.getCode() != 200){
                                logger.error("{}??????????????????????????????{}", delOutbound.getOrderNo(), JSONUtil.toJsonStr(r.getData()));
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                        }

                    }
                }
            }
        } catch (Exception e) {
            this.logger.error(e.getMessage(), e);
            // ????????????
            this.delOutboundService.exceptionMessage(delOutbound.getId(), e.getMessage());
            // ?????????
            try {
                throw e;
            } catch (InterruptedException interruptedException) {
                logger.error(interruptedException.getMessage(), interruptedException);
            }
        } finally {
            // ??????????????????
            if (!originCompletedState.equals(completedState)) {
                this.delOutboundService.updateCompletedState(delOutbound.getId(), completedState);
            }
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void pushSrmCost(DelOutbound delOutbound) {

        if (DelOutboundOrderTypeEnum.DESTROY.getCode().equals(delOutbound.getOrderType())
                || DelOutboundOrderTypeEnum.SELF_PICK.getCode().equals(delOutbound.getOrderType())
                || DelOutboundOrderTypeEnum.NEW_SKU.getCode().equals(delOutbound.getOrderType())
                || DelOutboundOrderTypeEnum.SPLIT_SKU.getCode().equals(delOutbound.getOrderType())

        ) {
            //??????????????????????????????SKU?????????????????????SKU????????????   ???????????????srm??????
            return;
        }
        // ??????????????????????????????
        DelSrmCostLog delSrmCostLog = new DelSrmCostLog();
        delSrmCostLog.setOrderNo(delOutbound.getOrderNo());
        delSrmCostLog.setType(DelSrmCostLogEnum.Type.create.name());
        EventUtil.publishEvent(new DelSrmCostLogEvent(delSrmCostLog));
    }

    private void pushTy(DelOutbound delOutbound) {
        // ??????????????????????????????
        if (StringUtils.isEmpty(delOutbound.getTrackingNo())) {
            // ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
            return;
        }
        // ??????????????????????????????
        DelTyRequestLog tyRequestLog = new DelTyRequestLog();
        tyRequestLog.setOrderNo(delOutbound.getOrderNo());
        tyRequestLog.setType(DelTyRequestLogConstant.Type.shipments.name());
        EventUtil.publishEvent(new DelTyRequestLogEvent(tyRequestLog));
    }

    private String getSkuName(BaseProduct baseProduct) {
        if (null != baseProduct) {
            return baseProduct.getProductName();
        }
        return "";
    }

    /**
     * ????????????
     *
     * @param orderNo       orderNo
     * @param warehouseCode warehouseCode
     * @param orderType     orderType
     */
    private void deduction(String orderNo, String warehouseCode, String orderType, String cusCode) {
        // ?????????????????????????????????????????????????????????????????????????????????????????????
        if (DelOutboundServiceImplUtil.noOperationInventory(orderType)) {
            return;
        }
        // ????????????
        List<DelOutboundDetail> details = this.delOutboundDetailService.listByOrderNo(orderNo);
        InventoryOperateListDto inventoryOperateListDto = new InventoryOperateListDto();
        Map<String, InventoryOperateDto> inventoryOperateDtoMap = new HashMap<>();
        for (DelOutboundDetail detail : details) {
            DelOutboundServiceImplUtil.handlerInventoryOperate(detail, inventoryOperateDtoMap);
        }
        inventoryOperateListDto.setInvoiceNo(orderNo);
        inventoryOperateListDto.setWarehouseCode(warehouseCode);
        List<InventoryOperateDto> operateList = new ArrayList<>(inventoryOperateDtoMap.values());
        inventoryOperateListDto.setOperateList(operateList);
        inventoryOperateListDto.setCusCode(cusCode);
        this.deduction(inventoryOperateListDto);
    }

    /**
     * ????????????
     *
     * @param inventoryOperateListDto inventoryOperateListDto
     */
    private void deduction(InventoryOperateListDto inventoryOperateListDto) {
        // ????????????
        Integer deduction = this.inventoryFeignClientService.deduction(inventoryOperateListDto);
        logger.info("???????????????deduction  ??????{}",deduction);
        if (null == deduction || deduction < 1) {
            throw new CommonException("400", "??????????????????");
        }
    }

    /**
     * ????????????
     *
     * @param delOutbound delOutbound
     */
    private void deduction(DelOutbound delOutbound) {
        String orderNo = delOutbound.getOrderNo();
        String warehouseCode = delOutbound.getWarehouseCode();
        String orderType = delOutbound.getOrderType();
        String cusCode = delOutbound.getSellerCode();
        if (DelOutboundOrderTypeEnum.SPLIT_SKU.getCode().equals(orderType)) {
            InventoryOperateDto inventoryOperateDto = new InventoryOperateDto();
            inventoryOperateDto.setSku(delOutbound.getNewSku());
            inventoryOperateDto.setNum(Math.toIntExact(delOutbound.getBoxNumber()));
            List<InventoryOperateDto> operateList = new ArrayList<>(1);
            operateList.add(inventoryOperateDto);
            InventoryOperateListDto inventoryOperateListDto = new InventoryOperateListDto();
            inventoryOperateListDto.setInvoiceNo(orderNo);
            inventoryOperateListDto.setWarehouseCode(warehouseCode);
            inventoryOperateListDto.setOperateList(operateList);
            inventoryOperateListDto.setCusCode(cusCode);
            logger.info("???????????????{},{}",delOutbound.getOrderNo(),JSON.toJSONString(inventoryOperateListDto));
            this.deduction(inventoryOperateListDto);
        } else {
            this.deduction(orderNo, warehouseCode, orderType, cusCode);
        }
    }

    @Override
    public void cancelled(String orderNo) {
        //???????????????????????????
        // ????????????
        // 1.??????????????????                                 UN_FREEZE
        // 2.1??????????????????                                UN_FEE
        // 2.2????????????????????????                             UN_OP_FEE
        // 3.???????????????????????????????????????????????????????????????????????? UN_CARRIER
        // 4.????????????????????????                              MODIFY
        // 5.??????                                         END
        DelOutbound delOutbound = this.delOutboundService.getByOrderNo(orderNo);
        if (null == delOutbound) {
            return;
        }

        logger.info("?????????????????????{},{}",orderNo,delOutbound.getState());

        // ????????????
        if (DelOutboundStateEnum.COMPLETED.getCode().equals(delOutbound.getState())) {
            return;
        }
        // ????????????
        if (DelOutboundStateEnum.CANCELLED.getCode().equals(delOutbound.getState())) {
            return;
        }
        // ?????????????????????
        String cancelledState = this.defaultValue(delOutbound.getCancelledState());

        logger.info("?????????????????????{},{}",orderNo,cancelledState);

        String originCancelledState = cancelledState;
        String key = applicationName + ":DelOutbound:cancelled:" + orderNo;
        RLock lock = this.redissonClient.getLock(key);
        try {
            if (lock.tryLock(0, TimeUnit.SECONDS)) {
                if (StringUtils.isEmpty(cancelledState)) {
                    // ???????????????????????????
                    if (!DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
                        this.delOutboundService.unFreeze(delOutbound);
                    }
                    cancelledState = "UN_FEE";
                    logger.info("??????????????????--->,{}",orderNo);
                }
                // ?????????????????????SKU??????????????????
                boolean fee = true;
                if (DelOutboundOrderTypeEnum.DESTROY.getCode().equals(delOutbound.getOrderType())
                        || DelOutboundOrderTypeEnum.SELF_PICK.getCode().equals(delOutbound.getOrderType())
                        || DelOutboundOrderTypeEnum.NEW_SKU.getCode().equals(delOutbound.getOrderType())) {
                    fee = false;
                }
                if ("UN_FEE".equals(cancelledState)) {
                    if (fee) {



                        // ????????????
                        if (null != delOutbound.getAmount() && delOutbound.getAmount().doubleValue() > 0.0D) {

                            List<DelOutboundCharge> delOutboundChargeList = delOutboundChargeService.listCharges(delOutbound.getOrderNo());
                            Map<String, List<DelOutboundCharge>> groupByCharge =
                                    delOutboundChargeList.stream().collect(Collectors.groupingBy(DelOutboundCharge::getCurrencyCode));
                            for (String currencyCode: groupByCharge.keySet()) {
                                BigDecimal bigDecimal = new BigDecimal(0);
                                for (DelOutboundCharge c : groupByCharge.get(currencyCode)) {
                                    if (c.getAmount() != null) {
                                        bigDecimal = bigDecimal.add(c.getAmount());
                                    }
                                }
                                CusFreezeBalanceDTO cusFreezeBalanceDTO = new CusFreezeBalanceDTO();
                                cusFreezeBalanceDTO.setAmount(bigDecimal);
                                cusFreezeBalanceDTO.setCurrencyCode(currencyCode);
                                cusFreezeBalanceDTO.setCusCode(delOutbound.getSellerCode());
                                cusFreezeBalanceDTO.setNo(delOutbound.getOrderNo());
                                cusFreezeBalanceDTO.setOrderType("Freight");
                                R<?> thawBalanceR = this.rechargesFeignService.thawBalance(cusFreezeBalanceDTO);
                                if (null == thawBalanceR) {
                                    throw new CommonException("400", "????????????????????????");
                                }
                                if (Constants.SUCCESS != thawBalanceR.getCode()) {
                                    throw new CommonException("400", Utils.defaultValue(thawBalanceR.getMsg(), "????????????????????????2"));
                                }

                            }



                        }
                    }
                    cancelledState = "UN_OP_FEE";
                    logger.info("2.1??????????????????--->,{}",orderNo);
                }
                if ("UN_OP_FEE".equals(cancelledState)) {
                    DelOutboundOperationVO delOutboundOperationVO = new DelOutboundOperationVO();
                    delOutboundOperationVO.setOrderType(delOutbound.getOrderType());
                    delOutboundOperationVO.setOrderNo(orderNo);
                    R<?> r = this.operationFeignService.delOutboundThaw(delOutboundOperationVO);
                    DelOutboundServiceImplUtil.thawOperationThrowCommonException(r);
                    cancelledState = "UN_CARRIER";
                    logger.info("2.2????????????????????????--->,{}",orderNo);
                }
                if ("UN_CARRIER".equals(cancelledState)) {
//                    // ???????????????????????????
//                    String shipmentOrderNumber = delOutbound.getShipmentOrderNumber();
//                    String trackingNo = delOutbound.getTrackingNo();
//                    if (StringUtils.isNotEmpty(shipmentOrderNumber) && StringUtils.isNotEmpty(trackingNo)) {
//                        String referenceNumber = delOutbound.getReferenceNumber();
//                        this.delOutboundBringVerifyService.cancellation(delOutbound.getWarehouseCode(), referenceNumber, shipmentOrderNumber, trackingNo);
//                    }
                    cancelledState = "MODIFY";
                }
                if ("MODIFY".equals(cancelledState)) {
                    // ?????????????????????
                    this.delOutboundService.updateState(delOutbound.getId(), DelOutboundStateEnum.CANCELLED);
                    // ??????????????????
                    delOutbound.setState(DelOutboundStateEnum.CANCELLED.getCode());
                    DelOutboundOperationLogEnum.OPN_SHIPMENT.listener(delOutbound);
                    // ??????????????????
                    if (DelOutboundExceptionStateEnum.ABNORMAL.getCode().equals(delOutbound.getExceptionState())) {
                        this.delOutboundService.exceptionFix(delOutbound.getId());
                    }

                    logger.info("????????????????????????,??????????????????????????????-?????????{}",delOutbound.getOrderNo());

                    ExceptionInfoStateDto exceptionInfoStateDto = new ExceptionInfoStateDto();
                    exceptionInfoStateDto.setState(StateSubEnum.YIWANCHENG.getCode());
                    exceptionInfoStateDto.setOrderNos(Arrays.asList(delOutbound.getOrderNo()));
                    exceptionInfoFeignService.updExceptionInfoState(exceptionInfoStateDto);

                    logger.info("????????????????????????,??????????????????????????????-?????????{}",delOutbound.getOrderNo());

                    cancelledState = "END";
                    logger.info("????????????????????????--->,{}",orderNo);
                }
                // ??????????????????CK1
                if ("END".equals(cancelledState)
                        && !DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
                    if (DelOutboundOrderTypeEnum.NORMAL.getCode().equals(delOutbound.getOrderType())
                            || DelOutboundOrderTypeEnum.SELF_PICK.getCode().equals(delOutbound.getOrderType())) {
                        DelCk1RequestLog ck1RequestLog = new DelCk1RequestLog();
                        ck1RequestLog.setOrderNo(delOutbound.getOrderNo());
                        Map<String, String> headers = new HashMap<>();
                        headers.put(DomainInterceptorUtil.KEYWORD, delOutbound.getSellerCode());
                        ck1RequestLog.setRemark(JSON.toJSONString(headers));
                        ck1RequestLog.setType(DelCk1RequestLogConstant.Type.cancel.name());
                        EventUtil.publishEvent(new DelCk1RequestLogEvent(ck1RequestLog));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("??????--->,{}",orderNo);
            this.logger.error(e.getMessage(), e);
            // ????????????
            this.delOutboundService.exceptionMessage(delOutbound.getId(), e.getMessage());
            // ?????????
            try {
                throw e;
            } catch (InterruptedException interruptedException) {
                this.logger.error(interruptedException.getMessage(), interruptedException);
            }
        } finally {
            if (!originCancelledState.equals(cancelledState)) {
                this.delOutboundService.updateCancelledState(delOutbound.getId(), cancelledState);
            }
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public String getReassignType(String orderNo) {
        // ??????????????????????????????
        LambdaQueryWrapper<DelOutbound> lambdaQueryWrapper = Wrappers.lambdaQuery();
        lambdaQueryWrapper.select(DelOutbound::getReassignType);
        lambdaQueryWrapper.eq(DelOutbound::getOrderNo, orderNo);
        lambdaQueryWrapper.last("LIMIT 1");
        DelOutbound delOutbound = this.delOutboundService.getOne(lambdaQueryWrapper);
        if (null != delOutbound) {
            return delOutbound.getReassignType();
        }
        return null;
    }

    private String defaultValue(String str) {
        if (null == str) {
            return "";
        }
        return str;
    }

}
