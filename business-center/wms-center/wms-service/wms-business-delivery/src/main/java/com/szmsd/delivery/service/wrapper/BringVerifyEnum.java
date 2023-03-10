package com.szmsd.delivery.service.wrapper;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ArrayUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.szmsd.bas.api.domain.BasAttachment;
import com.szmsd.bas.api.domain.dto.BasAttachmentQueryDTO;
import com.szmsd.bas.api.enums.AttachmentTypeEnum;
import com.szmsd.bas.api.feign.RemoteAttachmentService;
import com.szmsd.bas.api.service.BaseProductClientService;
import com.szmsd.bas.domain.BaseProduct;
import com.szmsd.bas.dto.BaseProductConditionQueryDto;
import com.szmsd.chargerules.api.feign.ChargeFeignService;
import com.szmsd.chargerules.api.feign.OperationFeignService;
import com.szmsd.chargerules.domain.BasProductService;
import com.szmsd.chargerules.dto.BasProductServiceDao;
import com.szmsd.common.core.constant.Constants;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.utils.BigDecimalUtil;
import com.szmsd.common.core.utils.MessageUtil;
import com.szmsd.common.core.utils.SpringUtils;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.delivery.domain.*;
import com.szmsd.delivery.dto.BasShipmentRulesDto;
import com.szmsd.delivery.dto.DelOutboundLabelDto;
import com.szmsd.delivery.enums.*;
import com.szmsd.delivery.event.DelOutboundOperationLogEnum;
import com.szmsd.delivery.service.*;
import com.szmsd.delivery.service.impl.DelOutboundServiceImplUtil;
import com.szmsd.delivery.util.PdfUtil;
import com.szmsd.delivery.util.Utils;
import com.szmsd.delivery.vo.DelOutboundOperationDetailVO;
import com.szmsd.delivery.vo.DelOutboundOperationVO;
import com.szmsd.finance.api.feign.RechargesFeignService;
import com.szmsd.finance.dto.CusFreezeBalanceDTO;
import com.szmsd.http.api.service.IHtpOutboundClientService;
import com.szmsd.http.api.service.IHtpPricedProductClientService;
import com.szmsd.http.dto.ChargeCategory;
import com.szmsd.http.dto.ChargeItem;
import com.szmsd.http.dto.ChargeWrapper;
import com.szmsd.http.dto.Money;
import com.szmsd.http.dto.Packing;
import com.szmsd.http.dto.PricingPackageInfo;
import com.szmsd.http.dto.ProblemDetails;
import com.szmsd.http.dto.ResponseObject;
import com.szmsd.http.dto.ShipmentChargeInfo;
import com.szmsd.http.dto.ShipmentLabelChangeRequestDto;
import com.szmsd.http.dto.ShipmentOrderResult;
import com.szmsd.http.dto.TaskConfigInfo;
import com.szmsd.http.dto.Weight;
import com.szmsd.http.vo.PricedProductInfo;
import com.szmsd.http.vo.ResponseVO;
import com.szmsd.inventory.api.service.InventoryFeignClientService;
import com.szmsd.inventory.domain.dto.InventoryOperateDto;
import com.szmsd.inventory.domain.dto.InventoryOperateListDto;
import com.szmsd.pack.api.feign.PackageDeliveryConditionsFeignService;
import com.szmsd.pack.domain.PackageDeliveryConditions;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ?????????????????????
 *
 * @author zhangyuyuan
 * @date 2021-04-01 16:08
 */
public enum BringVerifyEnum implements ApplicationState, ApplicationRegister {

    /**
     * ??????
     */
    BEGIN,

    // #1 PRC ??????        - ?????????
    PRC_PRICING,

    //# 1 IOSS ??????     - ?????????
    IOSS_CHECK,

    // #2 ??????????????????    - ?????????
    FREEZE_BALANCE,

    // #3 ??????????????????    - ?????????
    PRODUCT_INFO,

    // #4 ??????/?????????????????? - ?????????
    SHIPMENT_RULE,

    // #5 ??????????????????????????? - ?????????
    SHIPMENT_ORDER,

    // #6 ????????????          - ?????????
    FREEZE_INVENTORY,

    // #7 ??????????????????       - ?????????
    FREEZE_OPERATION,

    // #8 ??????WMS             - ??????????????????????????????
    SHIPMENT_CREATE,

    // #9 ????????????             - ?????????
    SHIPMENT_LABEL,

    /**
     * ??????
     */
    END,
    ;

    public static BringVerifyEnum get(String name) {
        for (BringVerifyEnum anEnum : BringVerifyEnum.values()) {
            if (anEnum.name().equals(name)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * ??????
     *
     * @param e1 e1?????????
     * @param e2 e2??????e1
     * @return boolean
     */
    public static boolean gt(BringVerifyEnum e1, BringVerifyEnum e2) {
        if (null == e1 || null == e2) {
            throw new CommonException("400", "????????????????????????");
        }
        return e1.ordinal() < e2.ordinal();
    }

    @Override
    public Map<String, ApplicationHandle> register() {
        Map<String, ApplicationHandle> map = new HashMap<>();
        map.put(BEGIN.name(), new BeginHandle());
        map.put(PRC_PRICING.name(), new PrcPricingHandle());
        map.put(IOSS_CHECK.name(), new IossChekcHandle());
        map.put(FREEZE_BALANCE.name(), new FreezeBalanceHandle());
        map.put(PRODUCT_INFO.name(), new ProductInfoHandle());
        map.put(SHIPMENT_RULE.name(), new ShipmentRuleHandle());
        map.put(SHIPMENT_ORDER.name(), new ShipmentOrderHandle());
        map.put(FREEZE_INVENTORY.name(), new FreezeInventoryHandle());
        map.put(FREEZE_OPERATION.name(), new FreezeOperationHandle());
        map.put(SHIPMENT_CREATE.name(), new ShipmentCreateHandle());
        map.put(SHIPMENT_LABEL.name(), new ShipmentLabelHandle());
        map.put(END.name(), new EndHandle());
        return map;
    }

    static abstract class CommonApplicationHandle extends ApplicationHandle.AbstractApplicationHandle {

        @Override
        public boolean condition(ApplicationContext context, ApplicationState currentState) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            DelOutboundOrderTypeEnum orderTypeEnum = DelOutboundOrderTypeEnum.get(delOutbound.getOrderType());
            if (null == orderTypeEnum) {
                throw new CommonException("400", MessageUtil.to("??????????????????[" + delOutbound.getOrderType() + "]", "Non-existent type ["+delOutbound. getOrderType()+"]"));
            }
            boolean condition = ApplicationRuleConfig.bringVerifyCondition(orderTypeEnum, currentState.name());
            if (condition) {
                return otherCondition(context, currentState);
            }
            return false;
        }

        /**
         * ??????????????????
         *
         * @param context      context
         * @param currentState currentState
         * @return boolean
         */
        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
            return true;
        }

        /**
         * ????????????-????????????
         *
         * @param context      context
         * @param currentState currentState
         * @return boolean
         */
        @SuppressWarnings({"all"})
        public boolean batchSelfPick(ApplicationContext context, ApplicationState currentState) {
            //????????????->???????????? ??????prc
            if (context instanceof DelOutboundWrapperContext) {
                DelOutbound delOutbound = ((DelOutboundWrapperContext) context).getDelOutbound();
                String orderType = StringUtils.nvl(delOutbound.getOrderType(), "");
                String shipmentChannel = StringUtils.nvl(delOutbound.getShipmentChannel(), "");
                if (orderType.equals(DelOutboundOrderTypeEnum.BATCH.getCode()) && "SelfPick".equals(shipmentChannel)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void errorHandler(ApplicationContext context, Throwable throwable, ApplicationState currentState) {



            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);
            DelOutbound updateDelOutbound = new DelOutbound();
            updateDelOutbound.setId(delOutbound.getId());
            // ????????????
            String exceptionMessage = Utils.defaultValue(throwable.getMessage(), MessageUtil.to("??????????????????", "Review operation failed"));

            if(BringVerifyEnum.SHIPMENT_CREATE.equals(currentState) || BringVerifyEnum.SHIPMENT_LABEL.equals(currentState)){
                // ??????WMS
                if(delOutbound.getRefOrderNo() == null){
                    delOutbound.setRefOrderNo("");
                }
                updateDelOutbound.setExceptionStateWms(DelOutboundExceptionStateEnum.ABNORMAL.getCode());
                updateDelOutbound.setExceptionMessageWms(exceptionMessage);
                updateDelOutbound.setRefOrderNo(delOutbound.getRefOrderNo());
                delOutboundService.updateByIdTransactional(updateDelOutbound);
                return;
            }


            if(currentState.name().equals(SHIPMENT_ORDER.name())){
                //?????????????????????????????????????????????
                updateDelOutbound.setBringVerifyState(BEGIN.name());
            }else if(currentState.name().equals(FREEZE_OPERATION.name())){
                updateDelOutbound.setBringVerifyState(FREEZE_INVENTORY.name());
            }else{
                updateDelOutbound.setBringVerifyState(currentState.name());
            }


            updateDelOutbound.setExceptionMessage(exceptionMessage);
            // PRC??????
            updateDelOutbound.setLength(delOutbound.getLength());
            updateDelOutbound.setWidth(delOutbound.getWidth());
            updateDelOutbound.setHeight(delOutbound.getHeight());
            updateDelOutbound.setSupplierCalcType(delOutbound.getSupplierCalcType());
            updateDelOutbound.setSupplierCalcId(delOutbound.getSupplierCalcId());
            // ????????????*???*???
            updateDelOutbound.setSpecifications(delOutbound.getLength() + "*" + delOutbound.getWidth() + "*" + delOutbound.getHeight());
            updateDelOutbound.setCalcWeight(delOutbound.getCalcWeight());
            updateDelOutbound.setCalcWeightUnit(delOutbound.getCalcWeightUnit());
            updateDelOutbound.setAmount(delOutbound.getAmount());
            updateDelOutbound.setCurrencyCode(delOutbound.getCurrencyCode());
            // ????????????
            updateDelOutbound.setTrackingAcquireType(delOutbound.getTrackingAcquireType());
            updateDelOutbound.setShipmentService(delOutbound.getShipmentService());
            updateDelOutbound.setLogisticsProviderCode(delOutbound.getLogisticsProviderCode());
            updateDelOutbound.setProductShipmentRule(delOutbound.getProductShipmentRule());
            updateDelOutbound.setPackingRule(delOutbound.getPackingRule());
            // ???????????????????????????
            updateDelOutbound.setTrackingNo(delOutbound.getTrackingNo());
            updateDelOutbound.setShipmentOrderNumber(delOutbound.getShipmentOrderNumber());
            updateDelOutbound.setShipmentOrderLabelUrl(delOutbound.getShipmentOrderLabelUrl());

            updateDelOutbound.setCurrencyDescribe(delOutbound.getCurrencyDescribe());


            delOutboundService.bringVerifyFail(updateDelOutbound);
        }
    }

    static class BeginHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return BEGIN;
        }

        @Override
        public ApplicationState quoState() {
            return BEGIN;
        }

        @Override
        public void handle(ApplicationContext context) {

        }

        @Override
        public ApplicationState nextState() {
            return PRC_PRICING;
        }
    }

    static class PrcPricingHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return BEGIN;
        }

        @Override
        public ApplicationState quoState() {
            return PRC_PRICING;
        }

        @Override
        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
            //????????????->???????????? ??????prc
            return super.batchSelfPick(context, currentState);
        }

        @Override
        public void handle(ApplicationContext context) {
            StopWatch stopWatch = new StopWatch();

            IDelOutboundBringVerifyService delOutboundBringVerifyService = SpringUtils.getBean(IDelOutboundBringVerifyService.class);
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            logger.info(">>>>>[???????????????{}]-????????????Pricing", delOutbound.getOrderNo());

            PricingEnum pricingEnum;
            if (DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
                // ??????????????????
                pricingEnum = PricingEnum.PACKAGE;
            } else {
                pricingEnum = PricingEnum.SKU;
            }
            stopWatch.start();
            delOutboundWrapperContext.setBringVerifyFlag("0");
            ResponseObject<ChargeWrapper, ProblemDetails> responseObject = delOutboundBringVerifyService.pricing(delOutboundWrapperContext, pricingEnum);
            stopWatch.stop();
            logger.info(">>>>>[???????????????{}]-Pricing???????????????????????????{}, ??????:{}", delOutbound.getOrderNo(), stopWatch.getLastTaskTimeMillis(),
                    JSONObject.toJSONString(responseObject));
            if (null == responseObject) {
                // ??????????????????
                throw new CommonException("400", MessageUtil.to("????????????????????????", "Failed to calculate the package fee"));
            }

            if (!responseObject.isSuccess()) {
                // ????????????
                String exceptionMessage = Utils.defaultValue(ProblemDetails.getErrorMessageOrNull(responseObject.getError()), MessageUtil.to("????????????????????????", "Failed to calculate the package fee")+ "2");
                throw new CommonException("400", exceptionMessage);
            }

            // ???????????????
            ChargeWrapper chargeWrapper = responseObject.getObject();
            ShipmentChargeInfo data = chargeWrapper.getData();
            PricingPackageInfo packageInfo = data.getPackageInfo();


            delOutbound.setPrcInterfaceProductCode(data.getProductCode());
            delOutbound.setPrcTerminalCarrier(data.getTerminalCarrier());

            // ????????????
            delOutbound.setShipmentService(data.getLogisticsRouteId());
            // ?????????code
            delOutbound.setLogisticsProviderCode(data.getLogisticsProviderCode());
            // ???????????????????????????
            delOutbound.setProductShipmentRule(data.getShipmentRule());
            delOutbound.setPackingRule(data.getPackingRule());

            // ????????????
            delOutboundWrapperContext.setPrcProductCode(data.getProductCode());
            logger.info("???????????????????????????prcProductCode???{}", data.getProductCode());
            // ????????????
            Packing packing = packageInfo.getPacking();
            delOutbound.setLength(Utils.valueOf(packing.getLength()));
            delOutbound.setWidth(Utils.valueOf(packing.getWidth()));
            delOutbound.setHeight(Utils.valueOf(packing.getHeight()));
            delOutbound.setSupplierCalcType(data.getSupplierCalcType());
            delOutbound.setSupplierCalcId(data.getSupplierCalcId());
            delOutbound.setGrade(data.getGrade());

            if(StringUtils.isNotBlank(data.getAmazonLogisticsRouteId())){
                delOutbound.setAmazonLogisticsRouteId(data.getAmazonLogisticsRouteId());
            }
            // ???????????????
            Weight calcWeight = packageInfo.getCalcWeight();
            delOutbound.setCalcWeight(calcWeight.getValue());
            delOutbound.setCalcWeightUnit(calcWeight.getUnit());
            List<ChargeItem> charges = chargeWrapper.getCharges();
            // ??????????????????
            List<DelOutboundCharge> delOutboundCharges = new ArrayList<>();
            // ????????????
            BigDecimal totalAmount = BigDecimal.ZERO;
            String totalCurrencyCode = charges.get(0).getMoney().getCurrencyCode();
            for (ChargeItem charge : charges) {
                DelOutboundCharge delOutboundCharge = new DelOutboundCharge();
                ChargeCategory chargeCategory = charge.getChargeCategory();
                delOutboundCharge.setOrderNo(delOutbound.getOrderNo());
                delOutboundCharge.setBillingNo(chargeCategory.getBillingNo());
                delOutboundCharge.setChargeNameCn(chargeCategory.getChargeNameCN());
                delOutboundCharge.setChargeNameEn(chargeCategory.getChargeNameEN());
                delOutboundCharge.setParentBillingNo(chargeCategory.getParentBillingNo());
                Money money = charge.getMoney();
                BigDecimal amount = Utils.valueOf(money.getAmount());
                delOutboundCharge.setAmount(amount);
                delOutboundCharge.setCurrencyCode(money.getCurrencyCode());
                delOutboundCharge.setRemark(charge.getRemark());
                delOutboundCharges.add(delOutboundCharge);
                totalAmount = totalAmount.add(amount);
            }
            // ???????????????????????????
            IDelOutboundChargeService delOutboundChargeService = SpringUtils.getBean(IDelOutboundChargeService.class);
            stopWatch.start();
            delOutboundChargeService.saveCharges(delOutboundCharges);
            stopWatch.stop();
            logger.info(">>>>>[???????????????{}]-Pricing????????????????????????????????????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskTimeMillis());
            // ?????????
            delOutbound.setAmount(totalAmount);
            delOutbound.setCurrencyCode(totalCurrencyCode);

            //????????????????????????
            Map<String, BigDecimal> currencyMap = new HashMap<String, BigDecimal>();
            for (DelOutboundCharge charge: delOutboundCharges){

                String currencyCode = charge.getCurrencyCode();
                BigDecimal amount = BigDecimalUtil.setScale(charge.getAmount(),3);

                if(currencyMap.containsKey(currencyCode)){
                    BigDecimal chargeamount = currencyMap.get(currencyCode).add(amount);
                    currencyMap.put(currencyCode, chargeamount);
                }else{
                    currencyMap.put(currencyCode, amount);
                }
            }

            String currencyDescribe = ArrayUtil.join(currencyMap.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getValue))
                    .map(e -> e.getValue() + e.getKey()).collect(Collectors.toList()).toArray(), "???");

            delOutbound.setCurrencyDescribe(currencyDescribe);

            ChargeFeignService chargeFeignService = SpringUtils.getBean(ChargeFeignService.class);

            BasProductServiceDao basProductServiceDao = new BasProductServiceDao();
            basProductServiceDao.setProductCode(delOutbound.getPrcInterfaceProductCode());
            basProductServiceDao.setCustomCode(delOutbound.getSellerCode());
            logger.info("selectBasProductServiceeOne ??????:{}",JSON.toJSONString(basProductServiceDao));
            R<BasProductService> basProductServiceR = chargeFeignService.selectBasProductServiceeOne(basProductServiceDao);
            logger.info("selectBasProductServiceeOne ??????:{}",JSON.toJSONString(basProductServiceR));

            //??????PRC????????????
            IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);
            DelOutbound updateDelOutbound = new DelOutbound();
            updateDelOutbound.setId(delOutbound.getId());
            updateDelOutbound.setProductShipmentRule(data.getShipmentRule());
            updateDelOutbound.setPackingRule(delOutbound.getPackingRule());
            updateDelOutbound.setPrcInterfaceProductCode(delOutbound.getPrcInterfaceProductCode());
            updateDelOutbound.setPrcTerminalCarrier(delOutbound.getPrcTerminalCarrier());
            updateDelOutbound.setAmazonReferenceId(data.getAmazonLogisticsRouteId());
            updateDelOutbound.setGrade(data.getGrade());
            updateDelOutbound.setZoneName(data.getZoneName());

            if(basProductServiceR != null && basProductServiceR.getCode() == 200){

                BasProductService basProductService = basProductServiceR.getData();
                if(basProductService != null){
                    updateDelOutbound.setEndTagState(DelOutboundEndTagStateEnum.REVIEWED.getCode());
                }
            }

            delOutboundService.updateByIdTransactional(updateDelOutbound);

            DelOutboundOperationLogEnum.BRV_PRC_PRICING.listener(delOutbound);
        }

        @Override
        public void rollback(ApplicationContext context) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);
            DelOutbound updateDelOutbound = new DelOutbound();
            updateDelOutbound.setId(delOutbound.getId());
            updateDelOutbound.setBringVerifyState(BEGIN.name());
            // PRC??????
            updateDelOutbound.setCalcWeight(BigDecimal.ZERO);
            updateDelOutbound.setCalcWeightUnit("");
            updateDelOutbound.setAmount(BigDecimal.ZERO);
            updateDelOutbound.setCurrencyCode("");
            updateDelOutbound.setCurrencyDescribe("");

            updateDelOutbound.setSupplierCalcType("");
            updateDelOutbound.setSupplierCalcId("");
            // ????????????
            updateDelOutbound.setTrackingAcquireType("");
            updateDelOutbound.setShipmentService("");
            updateDelOutbound.setLogisticsProviderCode("");
            updateDelOutbound.setProductShipmentRule("");
            updateDelOutbound.setPackingRule("");
            // ???????????????????????????
            updateDelOutbound.setTrackingNo("");
            updateDelOutbound.setShipmentOrderNumber("");
            updateDelOutbound.setShipmentOrderLabelUrl("");
            // ??????WMS
            updateDelOutbound.setRefOrderNo("");

            // ????????????
            updateDelOutbound.setState(DelOutboundStateEnum.AUDIT_FAILED.getCode());
            delOutboundService.updateById(updateDelOutbound);
            super.rollback(context);
        }

        @Override
        public ApplicationState nextState() {
            return IOSS_CHECK;
        }
    }

    static class IossChekcHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return PRC_PRICING;
        }

        @Override
        public ApplicationState quoState() {
            return IOSS_CHECK;
        }

        @Override
        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
            //????????????->???????????? ??????prc
            return super.batchSelfPick(context, currentState);
        }

        @Override
        public void handle(ApplicationContext context) {
            StopWatch stopWatch = new StopWatch();

            //IDelOutboundBringVerifyService delOutboundBringVerifyService = SpringUtils.getBean(IDelOutboundBringVerifyService.class);
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            logger.info(">>>>>[???????????????{}]-????????????IOSS_CHECK", delOutbound.getOrderNo());

            BasShipmenRulesService basShipmenRulesService = SpringUtils.getBean(BasShipmenRulesService.class);

            String customCode = delOutbound.getSellerCode();
            String shipmentService = delOutbound.getShipmentService();
            String ioss = delOutbound.getIoss();

            stopWatch.start();

            BasShipmentRulesDto basShipmentRulesDto = new BasShipmentRulesDto();
            basShipmentRulesDto.setCustomCode(customCode);
            basShipmentRulesDto.setServiceChannelName(shipmentService);

            logger.info(">>>>>[???????????????{}]-IOSS_CHECK???????????????{}",delOutbound.getOrderNo(), JSON.toJSONString(basShipmentRulesDto));

            boolean checkIoss = basShipmenRulesService.selectbasShipmentRules(basShipmentRulesDto);

            stopWatch.stop();
            logger.info(">>>>>[???????????????{}]-IOSS_CHECK?????????????????????{}", delOutbound.getOrderNo(), checkIoss);

            if (checkIoss) {
                if(StringUtils.isEmpty(ioss)) {

                    String exceptionMessage = "["+delOutbound.getOrderNo()+"]????????????????????????????????????IOSS";
                    throw new CommonException("400", exceptionMessage);
                }
            }

            //DelOutboundOperationLogEnum.BRV_PRC_PRICING.listener(delOutbound);
        }

        @Override
        public void rollback(ApplicationContext context) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);
            DelOutbound updateDelOutbound = new DelOutbound();
            updateDelOutbound.setId(delOutbound.getId());
            updateDelOutbound.setBringVerifyState(BEGIN.name());
            // PRC??????
            updateDelOutbound.setCalcWeight(BigDecimal.ZERO);
            updateDelOutbound.setCalcWeightUnit("");
            updateDelOutbound.setAmount(BigDecimal.ZERO);
            updateDelOutbound.setCurrencyCode("");
            updateDelOutbound.setCurrencyDescribe("");

            updateDelOutbound.setSupplierCalcType("");
            updateDelOutbound.setSupplierCalcId("");
            // ????????????
            updateDelOutbound.setTrackingAcquireType("");
            updateDelOutbound.setShipmentService("");
            updateDelOutbound.setLogisticsProviderCode("");
            updateDelOutbound.setProductShipmentRule("");
            updateDelOutbound.setPackingRule("");
            // ???????????????????????????
            updateDelOutbound.setTrackingNo("");
            updateDelOutbound.setShipmentOrderNumber("");
            updateDelOutbound.setShipmentOrderLabelUrl("");
            // ??????WMS
            updateDelOutbound.setRefOrderNo("");

            // ????????????
            updateDelOutbound.setState(DelOutboundStateEnum.AUDIT_FAILED.getCode());
            delOutboundService.updateById(updateDelOutbound);
            super.rollback(context);
        }

        @Override
        public ApplicationState nextState() {
            return FREEZE_BALANCE;
        }
    }

    static class FreezeBalanceHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return IOSS_CHECK;
        }

        @Override
        public ApplicationState quoState() {
            return FREEZE_BALANCE;
        }

        @Override
        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
            //????????????->???????????? ??????prc
            return super.batchSelfPick(context, currentState);
        }

        public static final TimeUnit unit = TimeUnit.SECONDS;

        public static final long time = 3L;

        @Override
        public void handle(ApplicationContext context) {

            StopWatch stopWatch = new StopWatch();

            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            logger.info("?????????{}-?????????????????????{}", delOutbound.getOrderNo(), JSONObject.toJSONString(delOutbound));
            DelOutboundOperationLogEnum.BRV_FREEZE_BALANCE.listener(delOutbound);

            //RedissonClient redissonClient = SpringUtils.getBean(RedissonClient.class);

//            String key = "bringVerify-fss-freeze-balance" + delOutbound.getCustomCode() + ":" + delOutbound.getOrderNo();
//
//            RLock lock = redissonClient.getLock(key);
//
            try {
//
//                lock.tryLock(time, unit);

                /**
                 *  ?????????????????????????????????????????????????????????
                 */
                IDelOutboundChargeService delOutboundChargeService = SpringUtils.getBean(IDelOutboundChargeService.class);
                RechargesFeignService rechargesFeignService = SpringUtils.getBean(RechargesFeignService.class);
                List<DelOutboundCharge> delOutboundChargeList = delOutboundChargeService.listCharges(delOutbound.getOrderNo());
                if (delOutboundChargeList.isEmpty()) {
                    throw new CommonException("400", MessageUtil.to("?????????????????????????????????????????????????????????", "Failed to freeze expense information. No expense details to be frozen"));
                }
                Map<String, List<DelOutboundCharge>> groupByCharge =
                        delOutboundChargeList.stream().collect(Collectors.groupingBy(DelOutboundCharge::getCurrencyCode));

                logger.info(">>>>>[???????????????{}]????????????map, ??????:{}",delOutbound.getOrderNo(), JSONObject.toJSONString(groupByCharge));

                for (String currencyCode : groupByCharge.keySet()) {
                    BigDecimal bigDecimal = BigDecimal.ZERO;
                    for (DelOutboundCharge c : groupByCharge.get(currencyCode)) {
                        if (c.getAmount() != null) {
                            bigDecimal = bigDecimal.add(c.getAmount());
                        }
                    }
                    // ????????????????????????
                    CusFreezeBalanceDTO cusFreezeBalanceDTO = new CusFreezeBalanceDTO();
                    cusFreezeBalanceDTO.setAmount(bigDecimal);
                    cusFreezeBalanceDTO.setCurrencyCode(currencyCode);
                    cusFreezeBalanceDTO.setCusCode(delOutbound.getSellerCode());
                    cusFreezeBalanceDTO.setNo(delOutbound.getOrderNo());
                    cusFreezeBalanceDTO.setOrderType("Freight");

                    //logger.info(">>>>>[???????????????{}]????????????freezeBalance, ??????:{}",delOutbound.getOrderNo(), JSONObject.toJSONString(groupByCharge));

                    stopWatch.start();
                    R<?> freezeBalanceR = rechargesFeignService.freezeBalance(cusFreezeBalanceDTO);
                    stopWatch.stop();

                    if (null == freezeBalanceR) {
                        throw new CommonException("400", MessageUtil.to("????????????????????????", "Failed to freeze expense information"));
                    }

                    if (Constants.SUCCESS != freezeBalanceR.getCode()) {
                        // ????????????
                        String msg = Utils.defaultValue(freezeBalanceR.getMsg(), MessageUtil.to("????????????????????????", "Failed to freeze expense information") + "2");
                        throw new CommonException("400", msg);
                    }

                    logger.info(">>>>>[???????????????{}]??????????????????, ??????:{} ,??????{}", delOutbound.getOrderNo(), JSONObject.toJSONString(cusFreezeBalanceDTO), stopWatch.getLastTaskInfo().getTimeMillis());
                }

            }catch (Exception e){
                logger.info("?????????????????????????????????");
                logger.info("????????????:" + e.getMessage());
                throw new RuntimeException(e.getMessage());
            }finally {
//                if (lock.isLocked() && lock.isHeldByCurrentThread()) {
//                    lock.unlock();
//                }
            }

        }

        @Override
        public void rollback(ApplicationContext context) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            DelOutboundOperationLogEnum.RK_BRV_FREEZE_BALANCE.listener(delOutbound);


            IDelOutboundChargeService delOutboundChargeService = SpringUtils.getBean(IDelOutboundChargeService.class);
            RechargesFeignService rechargesFeignService = SpringUtils.getBean(RechargesFeignService.class);
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
                R<?> thawBalanceR = rechargesFeignService.thawBalance(cusFreezeBalanceDTO);
                logger.info(">>>>>[???????????????{}]??????????????????, ??????:{}",
                        delOutbound.getOrderNo(), JSONObject.toJSONString(cusFreezeBalanceDTO));
                if (null == thawBalanceR) {
                    throw new CommonException("400", MessageUtil.to("????????????????????????", "Failed to cancel freezing expenses"));
                }
                if (Constants.SUCCESS != thawBalanceR.getCode()) {
                    throw new CommonException("400", Utils.defaultValue(thawBalanceR.getMsg(), MessageUtil.to("????????????????????????", "Failed to cancel freezing expenses")+ "2"));
                }
            }

            super.rollback(context);
        }

        @Override
        public ApplicationState nextState() {
            return PRODUCT_INFO;
        }
    }

    static class ProductInfoHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return FREEZE_BALANCE;
        }

        @Override
        public ApplicationState quoState() {
            return PRODUCT_INFO;
        }

        @Override
        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
            //????????????->???????????? ??????prc
            return super.batchSelfPick(context, currentState);
        }

        @Override
        public void handle(ApplicationContext context) {
            StopWatch stopWatch = new StopWatch();

            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            String productCode = delOutbound.getShipmentRule();
            String prcProductCode = delOutboundWrapperContext.getPrcProductCode();
            logger.info("{}-?????????????????????{}??????????????????{}", delOutbound.getOrderNo(), JSONObject.toJSONString(delOutbound), prcProductCode);
            logger.info("???????????????????????????prcProductCode???{}", prcProductCode);
            if (StringUtils.isNotEmpty(prcProductCode)) {
                productCode = prcProductCode;
            }
            // ??????????????????
            IHtpPricedProductClientService htpPricedProductClientService = SpringUtils.getBean(IHtpPricedProductClientService.class);

            stopWatch.start();
            PricedProductInfo pricedProductInfo = htpPricedProductClientService.infoAndSubProducts(productCode);
            stopWatch.stop();

            logger.info(">>>>>[???????????????{}]?????????????????? ??????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskInfo().getTimeMillis());
            if (null == pricedProductInfo) {
                throw new CommonException("400", MessageUtil.to("????????????[" + productCode + "]????????????","Failed to query product ["+productCode+"] information" ));
            }

            // ???PRC?????????????????????????????????
            // delOutbound.setShipmentService(pricedProductInfo.getLogisticsRouteId());
            delOutbound.setTrackingAcquireType(pricedProductInfo.getTrackingAcquireType());
            // ??????????????????PRC???????????????
            // delOutbound.setProductShipmentRule(pricedProductInfo.getShipmentRule());
            // delOutbound.setPackingRule(pricedProductInfo.getPackingRule());
            delOutbound.setPrcProductCode(prcProductCode);
            DelOutboundOperationLogEnum.BRV_PRODUCT_INFO.listener(delOutbound);
        }

        @Override
        public void rollback(ApplicationContext context) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            delOutbound.setTrackingAcquireType("");
            super.rollback(context);
        }

        @Override
        public ApplicationState nextState() {
            return SHIPMENT_RULE;
        }
    }

    static class ShipmentRuleHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return PRODUCT_INFO;
        }

        @Override
        public ApplicationState quoState() {
            return SHIPMENT_RULE;
        }

        // ???????????????????????????????????????????????????????????????????????????/?????????
//        @Override
//        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
//            //????????????->???????????? ??????prc
//            return super.batchSelfPick(context, currentState);
//        }

        @Override
        public void handle(ApplicationContext context) {
            StopWatch stopWatch = new StopWatch();

            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            logger.info("{}-?????????????????????{}", delOutbound.getOrderNo(), JSONObject.toJSONString(delOutbound));
            IDelOutboundBringVerifyService delOutboundBringVerifyService = SpringUtils.getBean(IDelOutboundBringVerifyService.class);

            stopWatch.start();
            delOutboundBringVerifyService.shipmentRule(delOutbound);
            stopWatch.stop();

            logger.info(">>>>>[???????????????{}]?????????????????? ??????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskInfo().getTimeMillis());

        }

        @Override
        public ApplicationState nextState() {
            return SHIPMENT_ORDER;
        }
    }

    static class ShipmentOrderHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return SHIPMENT_RULE;
        }

        @Override
        public ApplicationState quoState() {
            return SHIPMENT_ORDER;
        }

        @Override
        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
            //????????????->???????????? ??????prc
            return super.batchSelfPick(context, currentState);
        }

        @Override
        public void handle(ApplicationContext context) {
            StopWatch stopWatch = new StopWatch();

            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();

            String shipmentService = delOutbound.getShipmentService();
            String trackingAcquireType = delOutbound.getTrackingAcquireType();

            //BasShipmenRulesService basShipmenRulesService = SpringUtils.getBean(BasShipmenRulesService.class);

            //8431 ??????????????????????????????????????????????????????????????????????????????
            if(StringUtils.isEmpty(shipmentService) || trackingAcquireType.equals(DelOutboundTrackingAcquireTypeEnum.NONE.getCode())){
                return;
            }

//            BasShipmentRulesDto paramBasShipmentRulesDto = new BasShipmentRulesDto();
//            paramBasShipmentRulesDto.setCustomCode(delOutbound.getSellerCode());
//            paramBasShipmentRulesDto.setServiceChannelName(shipmentService);
//            List<BasShipmentRules> list = basShipmenRulesService.selectBasShipmentRules(paramBasShipmentRulesDto);
//            if(CollectionUtils.isNotEmpty(list)){
//                return;
//            }

            IDelOutboundService iDelOutboundService = SpringUtils.getBean(IDelOutboundService.class);

            logger.info("{}-??????????????????????????????{}", delOutbound.getOrderNo(), JSONObject.toJSONString(delOutbound));
            // ????????????????????????????????????
            if (DelOutboundTrackingAcquireTypeEnum.ORDER_SUPPLIER.getCode().equals(delOutbound.getTrackingAcquireType())) {
                // ???????????????????????????
                IDelOutboundBringVerifyService delOutboundBringVerifyService = SpringUtils.getBean(IDelOutboundBringVerifyService.class);
                stopWatch.start();
                ShipmentOrderResult shipmentOrderResult = delOutboundBringVerifyService.shipmentOrder(delOutboundWrapperContext);
                stopWatch.stop();
                if(shipmentOrderResult != null){
                    delOutbound.setTrackingNo(shipmentOrderResult.getMainTrackingNumber());
                    delOutbound.setShipmentOrderNumber(shipmentOrderResult.getOrderNumber());
                    delOutbound.setShipmentOrderLabelUrl(shipmentOrderResult.getOrderLabelUrl());
                    delOutbound.setReferenceNumber(shipmentOrderResult.getReferenceNumber());
                    IDelOutboundRetryLabelService iDelOutboundRetryLabelService = SpringUtils.getBean(IDelOutboundRetryLabelService.class);
                    if(StringUtils.isNotEmpty(shipmentOrderResult.getOrderLabelUrl())){
                        iDelOutboundRetryLabelService.saveAndPushLabel(delOutbound.getOrderNo(), "",DelOutboundRetryLabelStateEnum.WAIT.name(),"");
                    }
                }

                DelOutbound delOutboundUpd = new DelOutbound();
                delOutboundUpd.setId(delOutbound.getId());
                delOutboundUpd.setReferenceNumber(delOutbound.getReferenceNumber());
                delOutboundUpd.setTrackingNo(delOutbound.getTrackingNo());
                delOutboundUpd.setShipmentOrderNumber(delOutbound.getShipmentOrderNumber());
                delOutboundUpd.setShipmentOrderLabelUrl(delOutbound.getShipmentOrderLabelUrl());
                iDelOutboundService.updateById(delOutboundUpd);

                logger.info(">>>>>[???????????????{}]??????????????? ??????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskInfo().getTimeMillis());

                DelOutboundOperationLogEnum.BRV_SHIPMENT_ORDER.listener(delOutbound);
            }


            if(StringUtils.isNotBlank(delOutbound.getAmazonLogisticsRouteId())){

                // ??????????????????????????????????????????
                stopWatch.start();
                IDelOutboundThirdPartyService delOutboundThirdPartyService = SpringUtils.getBean(IDelOutboundThirdPartyService.class);
                DelOutboundThirdParty delOutboundThirdParty = new DelOutboundThirdParty();
                delOutboundThirdParty.setOrderNo(delOutbound.getOrderNo());
                delOutboundThirdParty.setState(DelOutboundCompletedStateEnum.INIT.getCode());
                delOutboundThirdParty.setOperationType(DelOutboundConstant.DELOUTBOUND_OPERATION_TYPE_THIRD_PARTY);
                delOutboundThirdParty.setKeyInfo(delOutbound.getAmazonLogisticsRouteId());
                delOutboundThirdPartyService.save(delOutboundThirdParty);
                stopWatch.stop();
                logger.info(">>>>>[???????????????{}]??????????????????????????????????????????,??????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskTimeMillis());

            }




        }

        @Override
        public void rollback(ApplicationContext context) {
            // ???????????????????????????
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            DelOutboundOperationLogEnum.RK_BRV_SHIPMENT_ORDER.listener(delOutbound);
            String shipmentOrderNumber = delOutbound.getShipmentOrderNumber();
            String trackingNo = delOutbound.getTrackingNo();
            if (StringUtils.isNotEmpty(shipmentOrderNumber) && StringUtils.isNotEmpty(trackingNo)) {
                String referenceNumber = String.valueOf(delOutbound.getId());
                IDelOutboundBringVerifyService delOutboundBringVerifyService = SpringUtils.getBean(IDelOutboundBringVerifyService.class);
                delOutboundBringVerifyService.cancellation(delOutbound.getWarehouseCode(), referenceNumber, shipmentOrderNumber, trackingNo);
                delOutbound.setTrackingNo("");
                delOutbound.setShipmentOrderNumber("");
                delOutbound.setShipmentOrderLabelUrl("");
            }
            super.rollback(context);
        }

        @Override
        public ApplicationState nextState() {
            return FREEZE_INVENTORY;
        }
    }

    static class FreezeInventoryHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return SHIPMENT_ORDER;
        }

        @Override
        public ApplicationState quoState() {
            return FREEZE_INVENTORY;
        }

        @Override
        public void handle(ApplicationContext context) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            logger.info("{}-???????????????{}", delOutbound.getOrderNo(), JSONObject.toJSONString(delOutbound));
            String orderType = delOutbound.getOrderType();
            if (DelOutboundServiceImplUtil.noOperationInventory(orderType)) {
                return;
            }
            // ???????????????????????????
            if (DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
                return;
            }
            List<DelOutboundDetail> details = delOutboundWrapperContext.getDetailList();
            if (CollectionUtils.isEmpty(details)) {
                return;
            }
            InventoryOperateListDto operateListDto = new InventoryOperateListDto();
            operateListDto.setInvoiceNo(delOutbound.getOrderNo());
            operateListDto.setWarehouseCode(delOutbound.getWarehouseCode());
            ArrayList<InventoryOperateDto> operateList;
            // ??????SKU???????????????????????????SKU????????????????????????????????????????????????
            if (DelOutboundOrderTypeEnum.SPLIT_SKU.getCode().equals(delOutbound.getOrderType())) {
                InventoryOperateDto inventoryOperateDto = new InventoryOperateDto();
                inventoryOperateDto.setSku(delOutbound.getNewSku());
                inventoryOperateDto.setNum(Math.toIntExact(delOutbound.getBoxNumber()));
                operateList = new ArrayList<>(1);
                operateList.add(inventoryOperateDto);
            } else {
                Map<String, InventoryOperateDto> inventoryOperateDtoMap = new HashMap<>();
                for (DelOutboundDetail detail : details) {
                    DelOutboundServiceImplUtil.handlerInventoryOperate(detail, inventoryOperateDtoMap);
                }
                Collection<InventoryOperateDto> inventoryOperateDtos = inventoryOperateDtoMap.values();
                operateList = new ArrayList<>(inventoryOperateDtos);
            }
            operateListDto.setOperateList(operateList);
            // ????????????????????????
            if (DelOutboundOrderTypeEnum.COLLECTION.getCode().equals(orderType)) {
                operateListDto.setFreeType(1);
            }
            operateListDto.setCusCode(delOutbound.getSellerCode());
            try {
                DelOutboundOperationLogEnum.BRV_FREEZE_INVENTORY.listener(new Object[]{delOutbound, operateList});
                InventoryFeignClientService inventoryFeignClientService = SpringUtils.getBean(InventoryFeignClientService.class);
                inventoryFeignClientService.freeze(operateListDto);
            } catch (CommonException e) {
                logger.error(e.getMessage(), e);
                throw e;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                throw new CommonException("400", MessageUtil.to("???????????????????????????" + e.getMessage(), "Failed to freeze the inventory,"+e.getMessage()));
            }
        }

        @Override
        public void rollback(ApplicationContext context) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            DelOutboundOperationLogEnum.RK_BRV_FREEZE_INVENTORY.listener(delOutbound);
            IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);
            delOutboundService.unFreeze(delOutbound);
            super.rollback(context);
        }

        @Override
        public ApplicationState nextState() {
            return FREEZE_OPERATION;
        }
    }

    static class FreezeOperationHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return FREEZE_INVENTORY;
        }

        @Override
        public ApplicationState quoState() {
            return FREEZE_OPERATION;
        }

        @Override
        public void handle(ApplicationContext context) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            logger.info("{}-??????????????????{}", delOutbound.getOrderNo(), JSONObject.toJSONString(delOutbound));
            DelOutboundOperationVO delOutboundOperationVO = new DelOutboundOperationVO();
            delOutboundOperationVO.setOrderType(delOutbound.getOrderType());
            delOutboundOperationVO.setOrderNo(delOutbound.getOrderNo());
            delOutboundOperationVO.setWarehouseCode(delOutbound.getWarehouseCode());
            delOutboundOperationVO.setCustomCode(delOutbound.getCustomCode());
            // ????????????
            List<DelOutboundDetail> details = delOutboundWrapperContext.getDetailList();
            List<DelOutboundOperationDetailVO> detailVOList = new ArrayList<>(details.size());
            if (DelOutboundOrderTypeEnum.PACKAGE_TRANSFER.getCode().equals(delOutbound.getOrderType())) {
                DelOutboundOperationDetailVO detailVO = new DelOutboundOperationDetailVO();
                // ??????sku?????????
                long qty = details.stream().mapToLong(DelOutboundDetail::getQty).sum();
                detailVO.setQty(qty);
                detailVO.setWeight(delOutbound.getWeight());
                detailVOList.add(detailVO);
            } else {
                // ??????????????????
                Set<String> skus = new HashSet<>();
                for (DelOutboundDetail detail : details) {
                    // sku??????
                    skus.add(detail.getSku());
                    // sku????????????
                    if (StringUtils.isNotEmpty(detail.getBindCode())) {
                        skus.add(detail.getBindCode());
                    }
                }
                Map<String, BaseProduct> productMap = null;
                if (CollectionUtils.isNotEmpty(skus)) {
                    BaseProductConditionQueryDto baseProductConditionQueryDto = new BaseProductConditionQueryDto();
                    baseProductConditionQueryDto.setSkus(new ArrayList<>(skus));
                    BaseProductClientService baseProductClientService = SpringUtils.getBean(BaseProductClientService.class);
                    List<BaseProduct> productList = baseProductClientService.queryProductList(baseProductConditionQueryDto);
                    if (CollectionUtils.isNotEmpty(productList)) {
                        productMap = productList.stream().collect(Collectors.toMap(BaseProduct::getCode, v -> v, (v1, v2) -> v1));
                    }
                }
                // ???????????????SKU??????
                if (null == productMap) {
                    throw new CommonException("400", MessageUtil.to("??????SKU????????????", "Failed to query SKU information"));
                }
                // ????????????????????????
                for (DelOutboundDetail detail : details) {
                    String sku = detail.getSku();
                    BaseProduct product = productMap.get(sku);
                    if (null == product) {
                        throw new CommonException("400", MessageUtil.to("SKU[" + sku + "]???????????????", "SKU ["+sku+"] information does not exist"));
                    }
                    // ???????????????
                    DelOutboundOperationDetailVO detailVO = new DelOutboundOperationDetailVO();
                    detailVO.setSku(sku);
                    detailVO.setQty(detail.getQty());
                    detailVO.setWeight(Utils.defaultValue(product.getWeight()));
                    detailVOList.add(detailVO);
                    // ?????????????????????
                    String bindCode = detail.getBindCode();
                    if (StringUtils.isNotEmpty(bindCode) && productMap.containsKey(bindCode)) {
                        BaseProduct baseProduct = productMap.get(bindCode);
                        DelOutboundOperationDetailVO vo = new DelOutboundOperationDetailVO();
                        vo.setSku(bindCode);
                        vo.setQty(detail.getQty());
                        vo.setWeight(Utils.defaultValue(baseProduct.getWeight()));
                        detailVOList.add(vo);
                    }
                }
            }
            delOutboundOperationVO.setDetails(detailVOList);
            DelOutboundOperationLogEnum.BRV_FREEZE_OPERATION.listener(new Object[]{delOutbound, detailVOList});

            StopWatch stopWatch = new StopWatch();

            stopWatch.start();
            OperationFeignService operationFeignService = SpringUtils.getBean(OperationFeignService.class);
            //???????????? - ??????????????????
            R<?> r = operationFeignService.delOutboundFreeze(delOutboundOperationVO);
            stopWatch.stop();

            logger.info(">>>>>[???????????????{}]?????????????????? ??????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskInfo().getTimeMillis());

            DelOutboundServiceImplUtil.freezeOperationThrowErrorMessage(r);

                IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);

                DelOutbound updateDelOutbound = new DelOutbound();


                updateDelOutbound.setId(delOutbound.getId());
                updateDelOutbound.setBringVerifyState(END.name());
                // PRC??????
                updateDelOutbound.setLength(delOutbound.getLength());
                updateDelOutbound.setWidth(delOutbound.getWidth());
                updateDelOutbound.setHeight(delOutbound.getHeight());
                updateDelOutbound.setSupplierCalcType(delOutbound.getSupplierCalcType());
                updateDelOutbound.setSupplierCalcId(delOutbound.getSupplierCalcId());
                // ????????????*???*???
                updateDelOutbound.setSpecifications(delOutbound.getLength() + "*" + delOutbound.getWidth() + "*" + delOutbound.getHeight());
                updateDelOutbound.setCalcWeight(delOutbound.getCalcWeight());
                updateDelOutbound.setCalcWeightUnit(delOutbound.getCalcWeightUnit());
                updateDelOutbound.setAmount(delOutbound.getAmount());
                updateDelOutbound.setCurrencyCode(delOutbound.getCurrencyCode());
                updateDelOutbound.setCurrencyDescribe(delOutbound.getCurrencyDescribe());

            // ????????????
                updateDelOutbound.setTrackingAcquireType(delOutbound.getTrackingAcquireType());
                updateDelOutbound.setShipmentService(delOutbound.getShipmentService());
                updateDelOutbound.setLogisticsProviderCode(delOutbound.getLogisticsProviderCode());
                updateDelOutbound.setProductShipmentRule(delOutbound.getProductShipmentRule());
                updateDelOutbound.setPackingRule(delOutbound.getPackingRule());
                // ???????????????????????????
                updateDelOutbound.setTrackingNo(delOutbound.getTrackingNo());
                updateDelOutbound.setShipmentOrderNumber(delOutbound.getShipmentOrderNumber());
                updateDelOutbound.setShipmentOrderLabelUrl(delOutbound.getShipmentOrderLabelUrl());

                if(!DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())){
                    //??????????????????????????????WMS
                    if(delOutboundService.serviceChannelNamePushWMS(delOutbound, updateDelOutbound)){
                        return;
                    }


                    // ????????????WMS??????
                    stopWatch.start();
                    IDelOutboundThirdPartyService delOutboundThirdPartyService = SpringUtils.getBean(IDelOutboundThirdPartyService.class);
                    DelOutboundThirdParty delOutboundThirdParty = new DelOutboundThirdParty();
                    delOutboundThirdParty.setOrderNo(delOutbound.getOrderNo());
                    delOutboundThirdParty.setState(DelOutboundCompletedStateEnum.INIT.getCode());
                    delOutboundThirdParty.setOperationType(DelOutboundConstant.DELOUTBOUND_OPERATION_TYPE_WMS);
                    delOutboundThirdPartyService.save(delOutboundThirdParty);
                    stopWatch.stop();
                    logger.info(">>>>>[???????????????{}]????????????WMS????????????,??????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskTimeMillis());
                }
            delOutboundService.bringVerifySuccess(updateDelOutbound);



        }

        @Override
        public void rollback(ApplicationContext context) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            DelOutboundOperationLogEnum.RK_BRV_FREEZE_OPERATION.listener(delOutbound);
            DelOutboundOperationVO delOutboundOperationVO = new DelOutboundOperationVO();
            delOutboundOperationVO.setOrderType(delOutbound.getOrderType());
            delOutboundOperationVO.setOrderNo(delOutbound.getOrderNo());
            OperationFeignService operationFeignService = SpringUtils.getBean(OperationFeignService.class);
            R<?> r = operationFeignService.delOutboundThaw(delOutboundOperationVO);

            logger.info(">>>>>[???????????????{}]?????????????????????????????? {}",delOutbound.getOrderNo(),JSON.toJSONString(delOutbound));

            IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);

            DelOutbound updateDelOutbound = new DelOutbound();
            updateDelOutbound.setId(delOutbound.getId());

            updateDelOutbound.setBringVerifyState(FREEZE_INVENTORY.name());

            updateDelOutbound.setState(DelOutboundStateEnum.AUDIT_FAILED.getCode());
            boolean upd  = delOutboundService.updateById(updateDelOutbound);

            logger.info(">>>>>[???????????????{}]?????????????????????????????????????????? {}",delOutbound.getOrderNo(),upd);

            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            DelOutboundServiceImplUtil.thawOperationThrowCommonException(r);

            stopWatch.stop();
            logger.info(">>>>>[???????????????{}]???????????????????????? ??????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskInfo().getTimeMillis());

            super.rollback(context);
        }

        @Override
        public ApplicationState nextState() {
            return END;
        }
    }

    static class ShipmentCreateHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return SHIPMENT_CREATE;
        }

        @Override
        public ApplicationState quoState() {
            return SHIPMENT_CREATE;
        }

        @Override
        public void handle(ApplicationContext context) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);
            String refOrderNo = "";
            logger.info("{}-?????????WMS???{}", delOutbound.getOrderNo(), JSONObject.toJSONString(delOutbound));

            DelOutbound delOutboundCheck =delOutboundService.getByOrderNo(delOutbound.getOrderNo());

            if(delOutboundCheck != null && DelOutboundStateEnum.CANCELLED.getCode().equals(delOutboundCheck.getState())){
                logger.info("{}-?????????WMS???{}", delOutbound.getOrderNo(),"???????????????????????????");
                return;
            }

            // ?????????WMS
            // ???????????????????????????
            if (!DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
                IDelOutboundBringVerifyService delOutboundBringVerifyService = SpringUtils.getBean(IDelOutboundBringVerifyService.class);

                StopWatch stopWatch = new StopWatch();
                stopWatch.start();
                refOrderNo = delOutboundBringVerifyService.shipmentCreate(delOutboundWrapperContext, delOutbound.getTrackingNo());

                stopWatch.stop();
                logger.info(">>>>>[???????????????{}]??????WMS ??????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskInfo().getTimeMillis());


                delOutbound.setRefOrderNo(refOrderNo);
                DelOutboundOperationLogEnum.BRV_SHIPMENT_CREATE.listener(delOutbound);
            }

            // ????????????
            DelOutbound updateDelOutbound = new DelOutbound();
            updateDelOutbound.setId(delOutbound.getId());
            // ??????WMS
            updateDelOutbound.setRefOrderNo(refOrderNo);
            updateDelOutbound.setExceptionStateWms(DelOutboundExceptionStateEnum.NORMAL.getCode());
            updateDelOutbound.setExceptionMessageWms("");
            delOutboundService.updateByIdTransactional(updateDelOutbound);

            // ??????????????????
            TaskConfigInfo taskConfigInfo = delOutboundWrapperContext.getTaskConfigInfo();
            if (null != taskConfigInfo) {
                if ("NotReceive".equals(taskConfigInfo.getReceiveShippingType())) {
                    // ????????????????????????????????????????????????????????????
                    IDelOutboundCompletedService delOutboundCompletedService = SpringUtils.getBean(IDelOutboundCompletedService.class);
                    delOutboundCompletedService.add(delOutbound.getOrderNo(), DelOutboundOperationTypeEnum.SHIPMENT_PACKING.getCode());
                }
            }
        }

        @Override
        public ApplicationState nextState() {
            return SHIPMENT_LABEL;
        }
    }

    static class ShipmentLabelHandle extends CommonApplicationHandle {
        @Override
        public ApplicationState preState() {
            return SHIPMENT_CREATE;
        }

        @Override
        public ApplicationState quoState() {
            return SHIPMENT_LABEL;
        }

        @Override
        public void handle(ApplicationContext context) {

            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();

            String productCode = delOutbound.getShipmentRule();
            String prcProductCode = delOutboundWrapperContext.getPrcProductCode();
            if (com.szmsd.common.core.utils.StringUtils.isNotEmpty(prcProductCode)) {
                productCode = prcProductCode;
            }
            // ??????????????????
            if (org.apache.commons.lang3.StringUtils.isNotEmpty(delOutbound.getWarehouseCode())
                    && org.apache.commons.lang3.StringUtils.isNotEmpty(productCode)) {

                PackageDeliveryConditions packageDeliveryConditions = new PackageDeliveryConditions();
                packageDeliveryConditions.setWarehouseCode(delOutbound.getWarehouseCode());
                packageDeliveryConditions.setProductCode(productCode);
                PackageDeliveryConditionsFeignService packageDeliveryConditionsFeignService = SpringUtils.getBean(PackageDeliveryConditionsFeignService.class);
                R<PackageDeliveryConditions> packageDeliveryConditionsR = packageDeliveryConditionsFeignService.info(packageDeliveryConditions);
                PackageDeliveryConditions packageDeliveryConditionsRData = null;
                if (null != packageDeliveryConditionsR && Constants.SUCCESS == packageDeliveryConditionsR.getCode()) {
                    packageDeliveryConditionsRData = packageDeliveryConditionsR.getData();
                }
                if (null != packageDeliveryConditionsRData && "AfterMeasured".equals(packageDeliveryConditionsRData.getCommandNodeCode())) {
                    //????????????????????????????????? ????????????????????????
                    return;
                }
            }
            DelOutboundOperationLogEnum.BRV_SHIPMENT_LABEL.listener(delOutbound);
            logger.info("???????????????{}?????????",delOutbound.getOrderNo());
            R<List<BasAttachment>> listR = null;
            // ????????????????????????
            try{
                RemoteAttachmentService remoteAttachmentService = SpringUtils.getBean(RemoteAttachmentService.class);
                BasAttachmentQueryDTO basAttachmentQueryDTO = new BasAttachmentQueryDTO();
                basAttachmentQueryDTO.setBusinessCode(AttachmentTypeEnum.DEL_OUTBOUND_DOCUMENT.getBusinessCode());
                basAttachmentQueryDTO.setBusinessNo(delOutbound.getOrderNo());
                listR = remoteAttachmentService.list(basAttachmentQueryDTO);
                logger.info("???????????????{}??????,????????????{}",delOutbound.getOrderNo(), listR.getData());

            }catch (Exception e){
                e.printStackTrace();
                throw new RuntimeException(e);
            }


            String filePath = null;
            if (listR != null && listR.getData() != null && CollectionUtils.isNotEmpty(listR.getData())) {
                BasAttachment attachment = listR.getData().get(0);
                filePath = attachment.getAttachmentPath() + "/" + attachment.getAttachmentName() + attachment.getAttachmentFormat();

                File labelFile = new File(filePath);
                if (!labelFile.exists()) {
                    throw new CommonException("500", MessageUtil.to("????????????????????????", "The delivery order file does not exist"));
                }
            }
            IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);
            String selfPickLabelFilePath = null;
            if (DelOutboundOrderTypeEnum.SELF_PICK.getCode().equals(delOutbound.getOrderType())) {
                DelOutboundLabelDto dto = new DelOutboundLabelDto();
                dto.setId(delOutbound.getId());

                //???????????????????????????????????????????????????
                delOutboundService.labelSelfPick(null, dto);
                selfPickLabelFilePath = DelOutboundServiceImplUtil.getSelfPickLabelFilePath(delOutbound) + "/" + delOutbound.getOrderNo() + ".pdf";
            }
            String uploadBoxLabel = null;
            if("Y".equals(delOutbound.getUploadBoxLabel())) {
                IDelOutboundBringVerifyService delOutboundBringVerifyService = SpringUtils.getBean(IDelOutboundBringVerifyService.class);

                uploadBoxLabel = delOutboundBringVerifyService.getBoxLabel(delOutbound);


            }
            logger.info("???????????????{}??????,??????{},????????????{},??????{}",delOutbound.getOrderNo(), filePath, selfPickLabelFilePath, uploadBoxLabel);
            if(selfPickLabelFilePath != null || uploadBoxLabel != null || filePath != null){
                String mergeFileDirPath = DelOutboundServiceImplUtil.getLabelMergeFilePath(delOutbound);
                File mergeFileDir = new File(mergeFileDirPath);

                File labelFile = null;
                if (!mergeFileDir.exists()) {
                    try {
                        FileUtils.forceMkdir(mergeFileDir);
                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                        throw new CommonException("500", MessageUtil.to("????????????????????????", "Failed to create folder,") + e.getMessage());
                    }
                }
                String mergeFilePath = mergeFileDirPath + "/" + delOutbound.getOrderNo();
                try {
                    if (PdfUtil.merge(mergeFilePath, filePath, uploadBoxLabel, selfPickLabelFilePath)) {
                        labelFile = new File(mergeFilePath);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    logger.error(e.getMessage(), e);
                    throw new CommonException("500", MessageUtil.to("???????????????????????????", "Failed to merge the delivery order file"));
                }

                if(labelFile == null){
                    return;
                }

                //13040 ????????????????????????-?????????WMS???????????????
                TaskConfigInfo taskConfigInfo = delOutboundWrapperContext.getTaskConfigInfo();
                if (null != taskConfigInfo) {
                    boolean flag = "AfterMeasured".equals(taskConfigInfo.getReceiveShippingType()) && DelOutboundTrackingAcquireTypeEnum.ORDER_SUPPLIER.getCode().equals(delOutbound.getTrackingAcquireType());
                    logger.info("{},???????????????{}??????,taskConfigInfo:{},trackingAcquireType:{}",delOutbound.getOrderNo(),flag,taskConfigInfo.getReceiveShippingType(),delOutbound.getTrackingAcquireType());
                    if (flag) {
                        return;
                    }
                }

                try {
                    byte[] byteArray = FileUtils.readFileToByteArray(labelFile);
                    String encode = Base64.encode(byteArray);
                    ShipmentLabelChangeRequestDto shipmentLabelChangeRequestDto = new ShipmentLabelChangeRequestDto();
                    shipmentLabelChangeRequestDto.setWarehouseCode(delOutbound.getWarehouseCode());
                    shipmentLabelChangeRequestDto.setOrderNo(delOutbound.getOrderNo());
                    shipmentLabelChangeRequestDto.setLabelType("ShipmentLabel");
                    shipmentLabelChangeRequestDto.setLabel(encode);
                    IHtpOutboundClientService htpOutboundClientService = SpringUtils.getBean(IHtpOutboundClientService.class);
                    ResponseVO responseVO = htpOutboundClientService.shipmentLabel(shipmentLabelChangeRequestDto);
                    if (null == responseVO || null == responseVO.getSuccess()) {
                        throw new CommonException("500", MessageUtil.to("??????????????????", "Failed to update label"));
                    }
                    if (!responseVO.getSuccess()) {
                        throw new CommonException("500", Utils.defaultValue(responseVO.getMessage(), MessageUtil.to("??????????????????", "Failed to update label")+"2"));
                    }
                } catch (IOException e) {
                    logger.error("????????????????????????, {}", e.getMessage(), e);
                    throw new CommonException("500", MessageUtil.to("????????????????????????", "Failed to read label file"));
                }

            }



        }

        @Override
        public boolean condition(ApplicationContext context, ApplicationState currentState) {
            boolean condition = super.condition(context, currentState);
            if (condition) {
                DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
                DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
                // ???????????? ?????? ???????????????????????????????????????
                return DelOutboundOrderTypeEnum.SELF_PICK.getCode().equals(delOutbound.getOrderType())
                        || (DelOutboundOrderTypeEnum.BATCH.getCode().equals(delOutbound.getOrderType()) && "SelfPick".equals(delOutbound.getShipmentChannel()));
            }
            return false;
        }

        @Override
        public ApplicationState nextState() {
            return END;
        }
    }

    static class EndHandle extends CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return SHIPMENT_LABEL;
        }

        @Override
        public ApplicationState quoState() {
            return END;
        }

        @Override
        public void handle(ApplicationContext context) {

        }

        @Override
        public ApplicationState nextState() {
            return END;
        }
    }
}
