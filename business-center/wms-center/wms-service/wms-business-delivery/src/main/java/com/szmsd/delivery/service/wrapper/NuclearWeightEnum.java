package com.szmsd.delivery.service.wrapper;

import cn.hutool.core.util.ArrayUtil;
import com.alibaba.fastjson.JSONObject;
import com.szmsd.common.core.constant.Constants;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.utils.BigDecimalUtil;
import com.szmsd.common.core.utils.MessageUtil;
import com.szmsd.common.core.utils.SpringUtils;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.delivery.domain.DelOutbound;
import com.szmsd.delivery.domain.DelOutboundCharge;
import com.szmsd.delivery.domain.DelOutboundThirdParty;
import com.szmsd.delivery.enums.DelOutboundCompletedStateEnum;
import com.szmsd.delivery.enums.DelOutboundConstant;
import com.szmsd.delivery.enums.DelOutboundOrderTypeEnum;
import com.szmsd.delivery.enums.DelOutboundTrackingAcquireTypeEnum;
import com.szmsd.delivery.event.DelOutboundOperationLogEnum;
import com.szmsd.delivery.service.IDelOutboundChargeService;
import com.szmsd.delivery.service.IDelOutboundService;
import com.szmsd.delivery.service.IDelOutboundThirdPartyService;
import com.szmsd.delivery.util.Utils;
import com.szmsd.finance.api.feign.RechargesFeignService;
import com.szmsd.finance.dto.CusFreezeBalanceDTO;
import com.szmsd.http.dto.*;
import org.springframework.util.StopWatch;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public enum NuclearWeightEnum implements ApplicationState, ApplicationRegister{

    /**
     * 开始
     */
    BEGIN,

    // #1 PRC 计费        -无回滚
    PRC_PRICING,

    // 2. 解冻操作费       -解冻
    THAW_BALANCE,

    // #2 冻结物流费用    -有回滚
    FREEZE_BALANCE,

    // #5 创建承运商物流订单 -有回滚
    SHIPMENT_ORDER,

    /**
     * 结束
     */
    END,
    ;

    public static NuclearWeightEnum get(String name) {
        for (NuclearWeightEnum anEnum : NuclearWeightEnum.values()) {
            if (anEnum.name().equals(name)) {
                return anEnum;
            }
        }
        return null;
    }

    @Override
    public Map<String, ApplicationHandle> register() {

        Map<String, ApplicationHandle> map = new HashMap<>();
        map.put(BEGIN.name(), new NuclearWeightEnum.BeginHandle());
        map.put(PRC_PRICING.name(), new NuclearWeightEnum.PrcPricingHandle());
        map.put(THAW_BALANCE.name(),new NuclearWeightEnum.ThawBalanceHandle());
        map.put(FREEZE_BALANCE.name(), new NuclearWeightEnum.FreezeBalanceHandle());
        map.put(SHIPMENT_ORDER.name(), new NuclearWeightEnum.ShipmentOrderHandle());
        map.put(END.name(), new NuclearWeightEnum.EndHandle());
        return map;
    }

    static abstract class CommonApplicationHandle extends ApplicationHandle.AbstractApplicationHandle {

        @Override
        public boolean condition(ApplicationContext context, ApplicationState currentState) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            DelOutboundOrderTypeEnum orderTypeEnum = DelOutboundOrderTypeEnum.get(delOutbound.getOrderType());
            if (null == orderTypeEnum) {
                throw new CommonException("400", MessageUtil.to("不存在的类型[" + delOutbound.getOrderType() + "]", "Non-existent type ["+delOutbound. getOrderType()+"]"));
            }
            return otherCondition(context, currentState);
        }

        /**
         * 子级处理条件
         *
         * @param context      context
         * @param currentState currentState
         * @return boolean
         */
        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
            return true;
        }

        /**
         * 批量出库-自提出库
         *
         * @param context      context
         * @param currentState currentState
         * @return boolean
         */
        @SuppressWarnings({"all"})
        public boolean batchSelfPick(ApplicationContext context, ApplicationState currentState) {
            //批量出库->自提出库 不做prc
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


        }
    }

    static class BeginHandle extends NuclearWeightEnum.CommonApplicationHandle {

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

    static class PrcPricingHandle extends NuclearWeightEnum.CommonApplicationHandle {

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
            //批量出库->自提出库 不做prc
            return super.batchSelfPick(context, currentState);
        }

        @Override
        public void handle(ApplicationContext context) {
            StopWatch stopWatch = new StopWatch();

            IDelOutboundBringVerifyService delOutboundBringVerifyService = SpringUtils.getBean(IDelOutboundBringVerifyService.class);
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            logger.info(">>>>>[创建出库单{}]-开始执行Pricing", delOutbound.getOrderNo());

            stopWatch.start();
            ResponseObject<ChargeWrapper, ProblemDetails> responseObject = delOutboundBringVerifyService.pricing(delOutboundWrapperContext, PricingEnum.PACKAGE);
            stopWatch.stop();
            logger.info(">>>>>[创建出库单{}]-Pricing计算返回结果：耗时{}, 内容:{}", delOutbound.getOrderNo(), stopWatch.getLastTaskTimeMillis(),
                    JSONObject.toJSONString(responseObject));
            if (null == responseObject) {
                // 返回值是空的
                throw new CommonException("400", MessageUtil.to("计算包裹费用失败", "Failed to calculate the package fee"));
            }

            if (!responseObject.isSuccess()) {
                // 计算失败
                String exceptionMessage = Utils.defaultValue(ProblemDetails.getErrorMessageOrNull(responseObject.getError()), MessageUtil.to("计算包裹费用失败", "Failed to calculate the package fee")+ "2");
                throw new CommonException("400", exceptionMessage);
            }

            // 计算成功了
            ChargeWrapper chargeWrapper = responseObject.getObject();
            ShipmentChargeInfo data = chargeWrapper.getData();
            PricingPackageInfo packageInfo = data.getPackageInfo();


            delOutbound.setPrcInterfaceProductCode(data.getProductCode());
            delOutbound.setPrcTerminalCarrier(data.getTerminalCarrier());

            // 挂号服务
            delOutbound.setShipmentService(data.getLogisticsRouteId());
            // 物流商code
            delOutbound.setLogisticsProviderCode(data.getLogisticsProviderCode());
            // 发货规则，装箱规则
            delOutbound.setProductShipmentRule(data.getShipmentRule());
            delOutbound.setPackingRule(data.getPackingRule());

            // 临时传值
            delOutboundWrapperContext.setPrcProductCode(data.getProductCode());
            logger.info("记录临时传值字段，prcProductCode：{}", data.getProductCode());
            // 包裹信息
            Packing packing = packageInfo.getPacking();
            delOutbound.setLength(Utils.valueOf(packing.getLength()));
            delOutbound.setWidth(Utils.valueOf(packing.getWidth()));
            delOutbound.setHeight(Utils.valueOf(packing.getHeight()));
            delOutbound.setSupplierCalcType(data.getSupplierCalcType());
            delOutbound.setSupplierCalcId(data.getSupplierCalcId());

            if(StringUtils.isNotBlank(data.getAmazonLogisticsRouteId())){
                delOutbound.setAmazonLogisticsRouteId(data.getAmazonLogisticsRouteId());
            }
            // 计费重信息
            Weight calcWeight = packageInfo.getCalcWeight();
            delOutbound.setCalcWeight(calcWeight.getValue());
            delOutbound.setCalcWeightUnit(calcWeight.getUnit());
            List<ChargeItem> charges = chargeWrapper.getCharges();
            // 保存费用信息
            List<DelOutboundCharge> delOutboundCharges = new ArrayList<>();
            // 汇总费用
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
            // 保存出库单费用信息
            IDelOutboundChargeService delOutboundChargeService = SpringUtils.getBean(IDelOutboundChargeService.class);
            stopWatch.start();
            delOutboundChargeService.saveCharges(delOutboundCharges);
            stopWatch.stop();
            logger.info(">>>>>[创建出库单{}]-Pricing保存出库单费用信息：耗时{}", delOutbound.getOrderNo(), stopWatch.getLastTaskTimeMillis());
            // 更新值
            delOutbound.setAmount(totalAmount);
            delOutbound.setCurrencyCode(totalCurrencyCode);

            //分组计算货币金额
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
                    .map(e -> e.getValue() + e.getKey()).collect(Collectors.toList()).toArray(), "；");

            delOutbound.setCurrencyDescribe(currencyDescribe);
            delOutbound.setSpecifications(delOutbound.getLength() + "*" + delOutbound.getWidth() + "*" + delOutbound.getHeight());


            //更新PRC发货服务
            IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);
            delOutbound.setAmazonReferenceId(data.getAmazonLogisticsRouteId());
            delOutbound.setGrade(data.getGrade());

            delOutboundService.updateById(delOutbound);

            DelOutboundOperationLogEnum.BRV_PRC_PRICING.listener(delOutbound);
        }

        @Override
        public void rollback(ApplicationContext context) {
            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            IDelOutboundService delOutboundService = SpringUtils.getBean(IDelOutboundService.class);
            DelOutbound updateDelOutbound = new DelOutbound();
            updateDelOutbound.setId(delOutbound.getId());
            updateDelOutbound.setNuclearWeightState(BEGIN.name());

            // 提审失败
            delOutboundService.updateById(updateDelOutbound);
            super.rollback(context);
        }

        @Override
        public ApplicationState nextState() {
            return THAW_BALANCE;
        }
    }

    static class ThawBalanceHandle extends NuclearWeightEnum.CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return THAW_BALANCE;
        }

        @Override
        public ApplicationState quoState() {
            return FREEZE_BALANCE;
        }

        @Override
        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
            //批量出库->自提出库 不做prc
            return super.batchSelfPick(context, currentState);
        }

        public static final TimeUnit unit = TimeUnit.SECONDS;

        public static final long time = 3L;

        @Override
        public void handle(ApplicationContext context) {

            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();

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
                logger.info(">>>>>[创建出库单{}]取消冻结费用, 数据:{}",
                        delOutbound.getOrderNo(), JSONObject.toJSONString(cusFreezeBalanceDTO));
                if (null == thawBalanceR) {
                    throw new CommonException("400", MessageUtil.to("取消冻结费用失败", "Failed to cancel freezing expenses"));
                }
                if (Constants.SUCCESS != thawBalanceR.getCode()) {
                    throw new CommonException("400", Utils.defaultValue(thawBalanceR.getMsg(), MessageUtil.to("取消冻结费用失败", "Failed to cancel freezing expenses")+ "2"));
                }
            }
        }

        @Override
        public void rollback(ApplicationContext context) {

            super.rollback(context);
        }

        @Override
        public ApplicationState nextState() {
            return FREEZE_BALANCE;
        }
    }

    static class FreezeBalanceHandle extends NuclearWeightEnum.CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return THAW_BALANCE;
        }

        @Override
        public ApplicationState quoState() {
            return FREEZE_BALANCE;
        }

        @Override
        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
            //批量出库->自提出库 不做prc
            return super.batchSelfPick(context, currentState);
        }

        public static final TimeUnit unit = TimeUnit.SECONDS;

        public static final long time = 3L;

        @Override
        public void handle(ApplicationContext context) {

            StopWatch stopWatch = new StopWatch();

            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();
            logger.info("出库单{}-开始冻结费用：{}", delOutbound.getOrderNo(), JSONObject.toJSONString(delOutbound));
            DelOutboundOperationLogEnum.BRV_FREEZE_BALANCE.listener(delOutbound);

            try {
                /**
                 *  获取要冻结的费用数据，并按货币分组冻结
                 */
                IDelOutboundChargeService delOutboundChargeService = SpringUtils.getBean(IDelOutboundChargeService.class);
                RechargesFeignService rechargesFeignService = SpringUtils.getBean(RechargesFeignService.class);
                List<DelOutboundCharge> delOutboundChargeList = delOutboundChargeService.listCharges(delOutbound.getOrderNo());
                if (delOutboundChargeList.isEmpty()) {
                    throw new CommonException("400", MessageUtil.to("冻结费用信息失败，没有要冻结的费用明细", "Failed to freeze expense information. No expense details to be frozen"));
                }
                Map<String, List<DelOutboundCharge>> groupByCharge =
                        delOutboundChargeList.stream().collect(Collectors.groupingBy(DelOutboundCharge::getCurrencyCode));

                logger.info(">>>>>[创建出库单{}]冻结费用map, 数据:{}",delOutbound.getOrderNo(), JSONObject.toJSONString(groupByCharge));

                for (String currencyCode : groupByCharge.keySet()) {
                    BigDecimal bigDecimal = BigDecimal.ZERO;
                    for (DelOutboundCharge c : groupByCharge.get(currencyCode)) {
                        if (c.getAmount() != null) {
                            bigDecimal = bigDecimal.add(c.getAmount());
                        }
                    }
                    // 调用冻结费用接口
                    CusFreezeBalanceDTO cusFreezeBalanceDTO = new CusFreezeBalanceDTO();
                    cusFreezeBalanceDTO.setAmount(bigDecimal);
                    cusFreezeBalanceDTO.setCurrencyCode(currencyCode);
                    cusFreezeBalanceDTO.setCusCode(delOutbound.getSellerCode());
                    cusFreezeBalanceDTO.setNo(delOutbound.getOrderNo());
                    cusFreezeBalanceDTO.setOrderType("Freight");

                    //logger.info(">>>>>[创建出库单{}]冻结费用freezeBalance, 数据:{}",delOutbound.getOrderNo(), JSONObject.toJSONString(groupByCharge));

                    stopWatch.start();
                    R<?> freezeBalanceR = rechargesFeignService.freezeBalance(cusFreezeBalanceDTO);
                    stopWatch.stop();

                    if (null == freezeBalanceR) {
                        throw new CommonException("400", MessageUtil.to("冻结费用信息失败", "Failed to freeze expense information"));
                    }

                    if (Constants.SUCCESS != freezeBalanceR.getCode()) {
                        // 异常信息
                        String msg = Utils.defaultValue(freezeBalanceR.getMsg(), MessageUtil.to("冻结费用信息失败", "Failed to freeze expense information") + "2");
                        throw new CommonException("400", msg);
                    }

                    logger.info(">>>>>[创建出库单{}]结束冻结费用, 数据:{} ,耗时{}", delOutbound.getOrderNo(), JSONObject.toJSONString(cusFreezeBalanceDTO), stopWatch.getLastTaskInfo().getTimeMillis());
                }

            }catch (Exception e){
                logger.info("冻结费用异常，加锁失败");
                logger.info("异常信息:" + e.getMessage());
                throw new RuntimeException(e.getMessage());
            }finally {
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
                logger.info(">>>>>[创建出库单{}]取消冻结费用, 数据:{}",
                        delOutbound.getOrderNo(), JSONObject.toJSONString(cusFreezeBalanceDTO));
                if (null == thawBalanceR) {
                    throw new CommonException("400", MessageUtil.to("取消冻结费用失败", "Failed to cancel freezing expenses"));
                }
                if (Constants.SUCCESS != thawBalanceR.getCode()) {
                    throw new CommonException("400", Utils.defaultValue(thawBalanceR.getMsg(), MessageUtil.to("取消冻结费用失败", "Failed to cancel freezing expenses")+ "2"));
                }
            }

            super.rollback(context);
        }

        @Override
        public ApplicationState nextState() {
            return SHIPMENT_ORDER;
        }
    }

    static class ShipmentOrderHandle extends NuclearWeightEnum.CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return FREEZE_BALANCE;
        }

        @Override
        public ApplicationState quoState() {
            return SHIPMENT_ORDER;
        }

        @Override
        public boolean otherCondition(ApplicationContext context, ApplicationState currentState) {
            //批量出库->自提出库 不做prc
            return super.batchSelfPick(context, currentState);
        }

        @Override
        public void handle(ApplicationContext context) {
            StopWatch stopWatch = new StopWatch();

            DelOutboundWrapperContext delOutboundWrapperContext = (DelOutboundWrapperContext) context;
            DelOutbound delOutbound = delOutboundWrapperContext.getDelOutbound();

            String shipmentService = delOutbound.getShipmentService();
            String trackingAcquireType = delOutbound.getTrackingAcquireType();


            //8431 提审订单时，如果返回的物流服务是空的，跳过调用供应商
            if(StringUtils.isEmpty(shipmentService) || trackingAcquireType.equals(DelOutboundTrackingAcquireTypeEnum.NONE.getCode())){
                return;
            }

            IDelOutboundService iDelOutboundService = SpringUtils.getBean(IDelOutboundService.class);

            logger.info("{}-创建承运商物流订单：{}", delOutbound.getOrderNo(), JSONObject.toJSONString(delOutbound));
            // 判断是否需要创建物流订单
            if (DelOutboundTrackingAcquireTypeEnum.ORDER_SUPPLIER.getCode().equals(delOutbound.getTrackingAcquireType())) {
                // 创建承运商物流订单
                IDelOutboundBringVerifyService delOutboundBringVerifyService = SpringUtils.getBean(IDelOutboundBringVerifyService.class);
                stopWatch.start();
                ShipmentOrderResult shipmentOrderResult = delOutboundBringVerifyService.shipmentOrder(delOutboundWrapperContext);

                logger.info("{}-创建承运商物流订单返回：{}", delOutbound.getOrderNo(), JSONObject.toJSONString(shipmentOrderResult));
                stopWatch.stop();
                if(shipmentOrderResult != null){
                    delOutbound.setTrackingNo(shipmentOrderResult.getMainTrackingNumber());
                    delOutbound.setShipmentOrderNumber(shipmentOrderResult.getOrderNumber());
                    delOutbound.setShipmentOrderLabelUrl(shipmentOrderResult.getOrderLabelUrl());
                    delOutbound.setReferenceNumber(shipmentOrderResult.getReferenceNumber());

                    DelOutbound delOutboundUpd = new DelOutbound();
                    delOutboundUpd.setId(delOutbound.getId());
                    delOutboundUpd.setReferenceNumber(shipmentOrderResult.getReferenceNumber());
                    delOutboundUpd.setTrackingNo(shipmentOrderResult.getMainTrackingNumber());
                    delOutboundUpd.setShipmentOrderLabelUrl(shipmentOrderResult.getOrderLabelUrl());
                    delOutboundUpd.setShipmentOrderNumber(shipmentOrderResult.getOrderNumber());
                    iDelOutboundService.updateById(delOutboundUpd);
                }

                logger.info(">>>>>[创建出库单{}]创建承运商 耗时{}", delOutbound.getOrderNo(), stopWatch.getLastTaskInfo().getTimeMillis());

                DelOutboundOperationLogEnum.BRV_SHIPMENT_ORDER.listener(delOutbound);
            }


            if(StringUtils.isNotBlank(delOutbound.getAmazonLogisticsRouteId())){

                // 提交一个亚马逊获取标签的任务
                stopWatch.start();
                IDelOutboundThirdPartyService delOutboundThirdPartyService = SpringUtils.getBean(IDelOutboundThirdPartyService.class);
                DelOutboundThirdParty delOutboundThirdParty = new DelOutboundThirdParty();
                delOutboundThirdParty.setOrderNo(delOutbound.getOrderNo());
                delOutboundThirdParty.setState(DelOutboundCompletedStateEnum.INIT.getCode());
                delOutboundThirdParty.setOperationType(DelOutboundConstant.DELOUTBOUND_OPERATION_TYPE_THIRD_PARTY);
                delOutboundThirdParty.setKeyInfo(delOutbound.getAmazonLogisticsRouteId());
                delOutboundThirdPartyService.save(delOutboundThirdParty);
                stopWatch.stop();
                logger.info(">>>>>[创建出库单{}]提交一个第三方承运商订单任务,耗时{}", delOutbound.getOrderNo(), stopWatch.getLastTaskTimeMillis());

            }
        }

        @Override
        public void rollback(ApplicationContext context) {
            // 取消承运商物流订单
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
            return END;
        }
    }

    static class EndHandle extends NuclearWeightEnum.CommonApplicationHandle {

        @Override
        public ApplicationState preState() {
            return SHIPMENT_ORDER;
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
