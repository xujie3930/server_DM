package com.szmsd.pack.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szmsd.bas.api.service.SerialNumberClientService;
import com.szmsd.chargerules.api.feign.OperationFeignService;
import com.szmsd.common.core.constant.Constants;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.utils.FileStream;
import com.szmsd.common.core.utils.SpringUtils;
import com.szmsd.common.security.domain.LoginUser;
import com.szmsd.common.security.utils.SecurityUtils;
import com.szmsd.delivery.vo.DelOutboundOperationDetailVO;
import com.szmsd.delivery.vo.DelOutboundOperationVO;
import com.szmsd.finance.api.feign.RechargesFeignService;
import com.szmsd.finance.dto.CusFreezeBalanceDTO;
import com.szmsd.http.api.service.IHtpCarrierClientService;
import com.szmsd.http.api.service.IHtpPickupPackageService;
import com.szmsd.http.api.service.IHtpPricedProductClientService;
import com.szmsd.http.api.service.IHtpRmiClientService;
import com.szmsd.http.dto.Package;
import com.szmsd.http.dto.*;
import com.szmsd.http.enums.DomainEnum;
import com.szmsd.http.vo.HttpResponseVO;
import com.szmsd.pack.constant.PackageCollectionConstants;
import com.szmsd.pack.constant.PackageCollectionOperationRecordConstants;
import com.szmsd.pack.domain.PackageCollection;
import com.szmsd.pack.domain.PackageCollectionDetail;
import com.szmsd.pack.domain.PackageCollectionOperationRecord;
import com.szmsd.pack.dto.PackageCollectionQueryDto;
import com.szmsd.pack.events.PackageCollectionCompletedEvent;
import com.szmsd.pack.events.PackageCollectionContextEvent;
import com.szmsd.pack.mapper.PackageCollectionMapper;
import com.szmsd.pack.service.IPackageCollectionDetailService;
import com.szmsd.pack.service.IPackageCollectionOperationRecordService;
import com.szmsd.pack.service.IPackageCollectionService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * package - 交货管理 - 揽收 服务实现类
 * </p>
 *
 * @author asd
 * @since 2022-02-17
 */
@Service
public class PackageCollectionServiceImpl extends ServiceImpl<PackageCollectionMapper, PackageCollection> implements IPackageCollectionService {
    private final Logger logger = LoggerFactory.getLogger(PackageCollectionServiceImpl.class);

    @Autowired
    private IPackageCollectionDetailService packageCollectionDetailService;
    @Autowired
    private SerialNumberClientService serialNumberClientService;
    @Autowired
    private IHtpPricedProductClientService htpPricedProductClientService;
    @Autowired
    private IHtpCarrierClientService htpCarrierClientService;
    @SuppressWarnings({"all"})
    @Autowired
    private RechargesFeignService rechargesFeignService;
    @SuppressWarnings({"all"})
    @Autowired
    private OperationFeignService operationFeignService;
    @Autowired
    private IHtpPickupPackageService httpPickupPackageService;
    @Autowired
    private IPackageCollectionOperationRecordService packageCollectionOperationRecordService;
    @Autowired
    private IHtpRmiClientService htpRmiClientService;

    /**
     * 查询package - 交货管理 - 揽收模块
     *
     * @param id package - 交货管理 - 揽收模块ID
     * @return package - 交货管理 - 揽收模块
     */
    @Override
    public PackageCollection selectPackageCollectionById(String id) {
        PackageCollection packageCollection = baseMapper.selectById(id);
        if (null != packageCollection) {
            packageCollection.setDetailList(this.packageCollectionDetailService.listByCollectionId(packageCollection.getId()));
        }
        return packageCollection;
    }

    @Override
    public PackageCollection selectPackageCollectionByNo(String no, String hasDetail) {
        LambdaQueryWrapper<PackageCollection> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(PackageCollection::getCollectionNo, no);
        PackageCollection packageCollection = super.getOne(queryWrapper);
        if (StringUtils.isNotEmpty(hasDetail)) {
            hasDetail = "Y";
        }
        if (null != packageCollection && "Y".equals(hasDetail)) {
            packageCollection.setDetailList(this.packageCollectionDetailService.listByCollectionId(packageCollection.getId()));
        }
        return packageCollection;
    }

    /**
     * 查询package - 交货管理 - 揽收模块列表
     *
     * @param packageCollection package - 交货管理 - 揽收模块
     * @return package - 交货管理 - 揽收模块
     */
    @Override
    public List<PackageCollection> selectPackageCollectionList(PackageCollection packageCollection) {
        QueryWrapper<PackageCollection> where = new QueryWrapper<>();
        return baseMapper.selectList(where);
    }

    /**
     * 新增package - 交货管理 - 揽收模块
     *
     * @param packageCollection package - 交货管理 - 揽收模块
     * @return 结果
     */
    @Transactional
    @Override
    public int insertPackageCollection(PackageCollection packageCollection) {
        this.logger.info(">>>insertPackageCollection: 开始新增");
        TimeInterval timer = DateUtil.timer();
        PackageCollectionContext packageCollectionContextCancel = new PackageCollectionContext(packageCollection, PackageCollectionContext.Type.CANCEL);
        try {
            List<PackageCollectionDetail> detailList = packageCollection.getDetailList();
            packageCollection.setTotalQty(this.countTotalQty(detailList));
            // 揽收单号
            String collectionNo = this.serialNumberClientService.generateNumber("COLLECTION_NO");
            packageCollection.setCollectionNo(collectionNo);
            // 设置状态
            if (PackageCollectionConstants.COLLECTION_PLAN_YES.equals(packageCollection.getCollectionPlan())) {
                packageCollection.setStatus(PackageCollectionConstants.Status.PLANNED.name());
            } else {
                packageCollection.setStatus(PackageCollectionConstants.Status.NO_PLAN.name());
            }
            this.logger.info(">>>insertPackageCollection: 初始化业务，耗时：{}", timer.intervalRestart());
            // PRC计算费用，冻结运费
            ResponseObject.ResponseObjectWrapper<ChargeWrapper, ProblemDetails> responseObject = this.pricing(packageCollection);
            this.logger.info(">>>insertPackageCollection: PRC计费，耗时：{}", timer.intervalRestart());
            if (null == responseObject) {
                // 返回值是空的
                throw new CommonException("400", "计算包裹费用失败，响应数据异常");
            } else {
                // 判断返回值
                if (responseObject.isSuccess()) {
                    // 计算成功了
                    ChargeWrapper chargeWrapper = responseObject.getObject();
                    ShipmentChargeInfo data = chargeWrapper.getData();
                    PricingPackageInfo packageInfo = data.getPackageInfo();
                    // 挂号服务
                    packageCollection.setShipmentService(data.getLogisticsRouteId());
                    // 物流商code
                    packageCollection.setLogisticsProviderCode(data.getLogisticsProviderCode());
                    // 包裹信息
                    Packing packing = packageInfo.getPacking();
                    BigDecimal length = packing.getLength();
                    if (null == length) {
                        length = BigDecimal.ZERO;
                    }
                    packageCollection.setLength(length.doubleValue());
                    BigDecimal width = packing.getWidth();
                    if (null == width) {
                        width = BigDecimal.ZERO;
                    }
                    packageCollection.setWidth(width.doubleValue());
                    BigDecimal height = packing.getHeight();
                    if (null == height) {
                        height = BigDecimal.ZERO;
                    }
                    packageCollection.setHeight(height.doubleValue());
                    // 计费重信息
                    Weight calcWeight = packageInfo.getCalcWeight();
                    packageCollection.setCalcWeight(calcWeight.getValue());
                    packageCollection.setCalcWeightUnit(calcWeight.getUnit());

                    if (data.getIsPickupPackageService() != null)
                        packageCollection.setIsPickupPackageService(data.getIsPickupPackageService());
                    if (data.getPickupPackageServiceName() != null)
                        packageCollection.setPickupPackageServiceName(data.getPickupPackageServiceName());

                    List<ChargeItem> charges = chargeWrapper.getCharges();
                    // 汇总费用
                    BigDecimal totalAmount = BigDecimal.ZERO;
                    String totalCurrencyCode = charges.get(0).getMoney().getCurrencyCode();
                    for (ChargeItem charge : charges) {
                        BigDecimal amount = BigDecimal.ZERO;
                        Money money = charge.getMoney();
                        if (null != money) {
                            Double amount1 = money.getAmount();
                            if (null != amount1) {
                                amount = BigDecimal.valueOf(amount1);
                            }
                        }
                        totalAmount = totalAmount.add(amount);
                    }
                    // 更新值
                    packageCollection.setAmount(totalAmount);
                    packageCollection.setCurrencyCode(totalCurrencyCode);
                } else {
                    // 计算失败
                    String exceptionMessage = ProblemDetails.getErrorMessageOrNull(responseObject.getError());
                    if (StringUtils.isNotEmpty(exceptionMessage)) {
                        exceptionMessage = "计算包裹费用失败";
                    }
                    throw new CommonException("400", exceptionMessage);
                }
            }
            CusFreezeBalanceDTO cusFreezeBalanceDTO = new CusFreezeBalanceDTO();
            cusFreezeBalanceDTO.setAmount(packageCollection.getAmount());
            cusFreezeBalanceDTO.setCurrencyCode(packageCollection.getCurrencyCode());
            cusFreezeBalanceDTO.setCusCode(packageCollection.getSellerCode());
            cusFreezeBalanceDTO.setNo(packageCollection.getCollectionNo());
            cusFreezeBalanceDTO.setOrderType("Freight");
            // 调用冻结费用接口
            R<?> freezeBalanceR = this.rechargesFeignService.freezeBalance(cusFreezeBalanceDTO);
            this.logger.info(">>>insertPackageCollection: 冻结费用，耗时：{}", timer.intervalRestart());
            if (null != freezeBalanceR) {
                if (Constants.SUCCESS != freezeBalanceR.getCode()) {
                    // 异常信息
                    String msg = freezeBalanceR.getMsg();
                    throw new CommonException("400", msg);
                }
            } else {
                // 异常信息
                throw new CommonException("400", "冻结费用信息失败，响应数据异常");
            }
            // 添加操作记录记录
            // this.packageCollectionOperationRecordService.add(packageCollection.getCollectionNo(), PackageCollectionOperationRecordConstants.Type.FREIGHT.name());
            packageCollectionContextCancel.addUndoType(PackageCollectionOperationRecordConstants.Type.FREIGHT);
            // 创建承运商订单，获取面单
            CreateShipmentOrderCommand createShipmentOrderCommand = new CreateShipmentOrderCommand();
            createShipmentOrderCommand.setWarehouseCode(packageCollection.getWarehouseCode());
            // 改成uuid
            createShipmentOrderCommand.setReferenceNumber(UUID.randomUUID().toString().replaceAll("-", "").toUpperCase());
            createShipmentOrderCommand.setOrderNumber(packageCollection.getCollectionNo());
            createShipmentOrderCommand.setClientNumber(packageCollection.getSellerCode());
            createShipmentOrderCommand.setReceiverAddress(new AddressCommand(packageCollection.getReceiverName(),
                    packageCollection.getReceiverPhone(),
                    "",
                    packageCollection.getReceiverAddress(),
                    "",
                    "",
                    packageCollection.getReceiverCity(),
                    packageCollection.getReceiverProvince(),
                    packageCollection.getReceiverPostCode(),
                    packageCollection.getReceiverCountry()));
            createShipmentOrderCommand.setReturnAddress(new AddressCommand(packageCollection.getCollectionName(),
                    packageCollection.getCollectionPhone(),
                    "",
                    packageCollection.getReceiverAddress(),
                    "",
                    "",
                    packageCollection.getCollectionCity(),
                    packageCollection.getCollectionProvince(),
                    packageCollection.getCollectionPostCode(),
                    packageCollection.getCollectionCountry()));
            // 包裹信息
            List<Package> packages = new ArrayList<>();
            List<PackageItem> packageItems = new ArrayList<>();
            BigDecimal weightInGram = BigDecimal.ZERO;
            for (PackageCollectionDetail detail : detailList) {
                String sku = detail.getSku();
                BigDecimal weight = detail.getWeight();
                if (null == weight) {
                    weight = BigDecimal.ZERO;
                }
                weightInGram = weightInGram.add(weight);
                BigDecimal length = detail.getLength();
                if (null == length) {
                    length = BigDecimal.ZERO;
                }
                BigDecimal width = detail.getWidth();
                if (null == width) {
                    width = BigDecimal.ZERO;
                }
                BigDecimal height = detail.getHeight();
                if (null == height) {
                    height = BigDecimal.ZERO;
                }
                BigDecimal declaredValue = detail.getDeclaredValue();
                if (null == declaredValue) {
                    declaredValue = BigDecimal.ZERO;
                }
                packageItems.add(new PackageItem(detail.getSku(), detail.getSkuName(), declaredValue.doubleValue(), weight.intValue(),
                        new Size(length.doubleValue(), width.doubleValue(), height.doubleValue()),
                        detail.getQty(), "", String.valueOf(detail.getId()), sku));
            }
            if (weightInGram.intValue() <= 0) {
                throw new CommonException("400", "包裹重量为0或者小于0，不能创建承运商物流订单");
            }
            String packageNumber = packageCollection.getCollectionNo();
            packages.add(new Package(packageNumber, String.valueOf(packageCollection.getId()),
                    new Size(packageCollection.getLength(), packageCollection.getWidth(), packageCollection.getHeight()),
                    weightInGram.intValue(), packageItems));
            createShipmentOrderCommand.setPackages(packages);
            createShipmentOrderCommand.setCarrier(new Carrier(packageCollection.getShipmentService()));
            ResponseObject<ShipmentOrderResult, ProblemDetails> responseObjectWrapper = this.htpCarrierClientService.shipmentOrder(createShipmentOrderCommand);
            this.logger.info(">>>insertPackageCollection: 创建承运商物流订单，耗时：{}", timer.intervalRestart());
            if (null == responseObjectWrapper) {
                throw new CommonException("400", "创建承运商物流订单失败，调用承运商系统无响应");
            }
            if (responseObjectWrapper.isSuccess()) {
                // 保存挂号
                // 判断结果集是不是正确的
                ShipmentOrderResult shipmentOrderResult = responseObjectWrapper.getObject();
                if (null == shipmentOrderResult) {
                    throw new CommonException("400", "创建承运商物流订单失败，调用承运商系统返回数据为空");
                }
                if (null == shipmentOrderResult.getSuccess() || !shipmentOrderResult.getSuccess()) {
                    // 判断有没有错误信息
                    ErrorDto error = shipmentOrderResult.getError();
                    StringBuilder builder = new StringBuilder();
                    if (null != error && StringUtils.isNotEmpty(error.getMessage())) {
                        if (StringUtils.isNotEmpty(error.getErrorCode())) {
                            builder.append("[")
                                    .append(error.getErrorCode())
                                    .append("]");
                        }
                        builder.append(error.getMessage());
                    } else {
                        builder.append("创建承运商物流订单失败，调用承运商系统失败，返回错误信息为空");
                    }
                    throw new CommonException("400", builder.toString());
                }
                packageCollection.setTrackingNo(shipmentOrderResult.getMainTrackingNumber());
                packageCollection.setShipmentOrderNumber(shipmentOrderResult.getOrderNumber());
                packageCollection.setShipmentOrderLabelUrl(shipmentOrderResult.getOrderLabelUrl());
            } else {
                String exceptionMessage = ProblemDetails.getErrorMessageOrNull(responseObjectWrapper.getError());
                if (StringUtils.isEmpty(exceptionMessage)) {
                    exceptionMessage = "创建承运商物流订单失败，调用承运商系统失败";
                }
                throw new CommonException("400", exceptionMessage);
            }
            // 添加操作记录记录
            // this.packageCollectionOperationRecordService.add(packageCollection.getCollectionNo(), PackageCollectionOperationRecordConstants.Type.CARRIER.name());
            packageCollectionContextCancel.addUndoType(PackageCollectionOperationRecordConstants.Type.CARRIER);
            // 保存揽收单
            this.logger.info(">>>insertPackageCollection: 开始-新增揽收单，耗时：{}", timer.intervalRestart());
            boolean save = super.save(packageCollection);
            this.logger.info(">>>insertPackageCollection: 结束-新增揽收单，耗时：{}", timer.intervalRestart());
            if (save) {
                // 保存明细
                this.saveDetail(packageCollection.getId(), detailList);
                this.logger.info(">>>insertPackageCollection: 新增揽收单明细，耗时：{}", timer.intervalRestart());
                // 冻结业务费
                DelOutboundOperationVO delOutboundOperationVO = new DelOutboundOperationVO();
                List<DelOutboundOperationDetailVO> detailVOList = new ArrayList<>(detailList.size());
                // 处理操作费用参数
                for (PackageCollectionDetail detail : detailList) {
                    String sku = detail.getSku();
                    // 操作费对象
                    DelOutboundOperationDetailVO detailVO = new DelOutboundOperationDetailVO();
                    detailVO.setSku(sku);
                    Integer qty = detail.getQty();
                    if (null == qty) {
                        qty = 0;
                    }
                    detailVO.setQty(Long.valueOf(qty));
                    BigDecimal weight = detail.getWeight();
                    if (null == weight) {
                        weight = BigDecimal.ZERO;
                    }
                    detailVO.setWeight(weight.doubleValue());
                    detailVOList.add(detailVO);
                }
                delOutboundOperationVO.setCustomCode(packageCollection.getSellerCode());
                delOutboundOperationVO.setOrderNo(packageCollection.getCollectionNo());
                delOutboundOperationVO.setDetails(detailVOList);
                delOutboundOperationVO.setOrderType("PackageCollection");
                R<?> r = operationFeignService.delOutboundFreeze(delOutboundOperationVO);
                this.logger.info(">>>insertPackageCollection: 冻结操作费用，耗时：{}", timer.intervalRestart());
                if (null == r) {
                    throw new CommonException("500", "冻结操作费用信息失败，响应数据异常");
                }
                if (Constants.SUCCESS != r.getCode()) {
                    String msg = r.getMsg();
                    if (StringUtils.isEmpty(msg)) {
                        msg = "冻结操作费用信息失败";
                    }
                    throw new CommonException("500", msg);
                }
                // 添加操作记录记录
                // this.packageCollectionOperationRecordService.add(packageCollection.getCollectionNo(), PackageCollectionOperationRecordConstants.Type.OPERATING_FEE.name());
                packageCollectionContextCancel.addUndoType(PackageCollectionOperationRecordConstants.Type.OPERATING_FEE);
                // 创建TrackingYee
                this.createTrackingYee(packageCollection);
                this.logger.info(">>>insertPackageCollection: 创建TY，耗时：{}", timer.intervalRestart());
                // 判断有没有揽收计划
                if (PackageCollectionConstants.COLLECTION_PLAN_YES.equals(packageCollection.getCollectionPlan())) {
                    // 通知创建入库单
                    PackageCollectionContext packageCollectionContextCreateReceiver = new PackageCollectionContext(packageCollection, PackageCollectionContext.Type.CREATE_RECEIVER, true);
                    PackageCollectionContextEvent.publishEvent(packageCollectionContextCreateReceiver);
                    operationFeignService.delOutboundFreeze(delOutboundOperationVO);
                }
                //通知提货商创建提货服务
                if (StringUtils.isNotEmpty(packageCollection.getPickupPackageServiceName())) {
                    createPackageService(packageCollection);
                }
            }
            return 1;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            // 通知保存揽收失败
            PackageCollectionContextEvent.publishEvent(packageCollectionContextCancel);
            if (e instanceof CommonException) {
                throw e;
            }
            throw new CommonException("500", e.getMessage());
        } finally {
            this.logger.info(">>>insertPackageCollection: 结束新增");
        }
    }

    //创建提货服务
    private void createPackageService(PackageCollection packageCollection) {
        CreatePickupPackageCommand createPickupPackageCommand = new CreatePickupPackageCommand().setReferenceNumber(packageCollection.getCollectionNo()).setPickupServiceName(packageCollection.getPickupPackageServiceName());

        Address4PackageService address = new Address4PackageService().setName(packageCollection.getCollectionName()).setCompanyName(packageCollection.getCollectionName()).setPhone(packageCollection.getCollectionPhone()).setEmail(null).setAddress1(packageCollection.getCollectionAddress()).setAddress2(null).setAddress3(null).setCity(packageCollection.getCollectionCity()).setProvince(packageCollection.getCollectionProvince()).setPostCode(packageCollection.getCollectionPostCode()).setCountry(packageCollection.getCollectionCountry());
        createPickupPackageCommand.setPickupAddress(address);

        PickupPieces pickupPieces = new PickupPieces();
        List<PickupPieceItemPickupPieceItem> list = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (PackageCollectionDetail detail : packageCollection.getDetailList()) {
            PickupPieceItemPickupPieceItem item = new PickupPieceItemPickupPieceItem();
            item.setQuantity(detail.getQty());
            item.setCustomerTag(detail.getSkuName());
            list.add(item);
            totalWeight.add(detail.getWeight());
        }
        pickupPieces.setPickupPieceItems(list);
        pickupPieces.setTotalWeight(totalWeight/*.divide(new BigDecimal("1000")*/.intValue());
        pickupPieces.setUnitOfMeasurement("KGS");
        createPickupPackageCommand.setPickupPieces(pickupPieces);

        PickupDateInfo pickupDateInfo = new PickupDateInfo();
        if (packageCollection.getCollectionDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            pickupDateInfo.setPickupDate(sdf.format(packageCollection.getCollectionDate()));
        }
        createPickupPackageCommand.setPickupDateInfo(pickupDateInfo);

        httpPickupPackageService.create(createPickupPackageCommand);
    }

    private ResponseObject.ResponseObjectWrapper<ChargeWrapper, ProblemDetails> pricing(PackageCollection packageCollection) {
        // 计算包裹费用
        CalcShipmentFeeCommand calcShipmentFeeCommand = new CalcShipmentFeeCommand();
        // true代表需要验证，false的话，主要是用于测算
        calcShipmentFeeCommand.setAddressValifition(true);
        // 产品代码就是选择的物流承运商
        calcShipmentFeeCommand.setProductCode(packageCollection.getCollectionServiceCode());
        calcShipmentFeeCommand.setClientCode(packageCollection.getSellerCode());
        calcShipmentFeeCommand.setShipmentType("");
        calcShipmentFeeCommand.setIoss("");
        List<PackageInfo> packageInfos = new ArrayList<>();
        List<PackageCollectionDetail> detailList = packageCollection.getDetailList();
        if (CollectionUtils.isNotEmpty(detailList)) {
            for (PackageCollectionDetail detail : detailList) {
                packageInfos.add(new PackageInfo(new Weight(detail.getWeight(), "g"),
                        new Packing(detail.getLength(), detail.getWidth(), detail.getHeight(), "cm"),
                        Math.toIntExact(detail.getQty()), packageCollection.getCollectionNo(), detail.getDeclaredValue(), ""));
            }
        }
        calcShipmentFeeCommand.setPackageInfos(packageInfos);
        // 收货地址
        calcShipmentFeeCommand.setToAddress(new Address(packageCollection.getReceiverAddress(),
                "",
                "",
                packageCollection.getReceiverPostCode(),
                packageCollection.getReceiverCity(),
                packageCollection.getReceiverProvince(),
                new CountryInfo(packageCollection.getReceiverCountryCode(), "", "", packageCollection.getReceiverCountry())
        ));
        // 发货地址
        calcShipmentFeeCommand.setFromAddress(new Address(packageCollection.getCollectionAddress(),
                "",
                "",
                packageCollection.getCollectionPostCode(),
                packageCollection.getCollectionCity(),
                packageCollection.getCollectionProvince(),
                new CountryInfo(packageCollection.getCollectionCountryCode(), "", "", packageCollection.getCollectionCountry())
        ));
        // 联系信息
        calcShipmentFeeCommand.setToContactInfo(new ContactInfo(packageCollection.getReceiverName(), packageCollection.getReceiverPhone(), "", ""));
        calcShipmentFeeCommand.setCalcTimeForDiscount(new Date());
        // 调用接口
        return this.htpPricedProductClientService.pricing(calcShipmentFeeCommand);
    }

    /**
     * 修改package - 交货管理 - 揽收模块
     *
     * @param packageCollection package - 交货管理 - 揽收模块
     * @return 结果
     */
    @Override
    public int updatePackageCollection(PackageCollection packageCollection) {
        packageCollection.setTotalQty(this.countTotalQty(packageCollection.getDetailList()));
        int update = baseMapper.updateById(packageCollection);
        if (update > 0) {
            LambdaQueryWrapper<PackageCollectionDetail> packageCollectionDetailLambdaQueryWrapper = Wrappers.lambdaQuery();
            packageCollectionDetailLambdaQueryWrapper.eq(PackageCollectionDetail::getCollectionId, packageCollection.getId());
            this.packageCollectionDetailService.remove(packageCollectionDetailLambdaQueryWrapper);
            this.saveDetail(packageCollection.getId(), packageCollection.getDetailList());
        }
        return update;
    }

    @Override
    public int updatePackageCollectionPlan(PackageCollection packageCollection) {
        List<Long> idList = packageCollection.getIdList();
        if (CollectionUtils.isNotEmpty(idList)) {
            PackageCollection updatePackageCollection = new PackageCollection();
            updatePackageCollection.setStatus(PackageCollectionConstants.Status.PLANNED.name());
            updatePackageCollection.setCollectionPlan(PackageCollectionConstants.COLLECTION_PLAN_YES);
            updatePackageCollection.setCollectionDate(packageCollection.getCollectionDate());
            updatePackageCollection.setHandleMode(packageCollection.getHandleMode());
            LambdaUpdateWrapper<PackageCollection> updateWrapper = Wrappers.lambdaUpdate();
            updateWrapper.in(PackageCollection::getId, idList);
            int update = super.baseMapper.update(updatePackageCollection, updateWrapper);
            if (update > 0) {
                List<PackageCollection> packageCollectionList = super.listByIds(idList);
                for (PackageCollection collection : packageCollectionList) {
                    // 通知创建入库单
                    PackageCollectionContext packageCollectionContextCreateReceiver = new PackageCollectionContext(collection, PackageCollectionContext.Type.CREATE_RECEIVER, false);
                    PackageCollectionContextEvent.publishEvent(packageCollectionContextCreateReceiver);
                }
                //查询出该提货计划列表
                LambdaQueryWrapper<PackageCollection> queryWrapper = Wrappers.lambdaQuery();
                queryWrapper.in(PackageCollection::getId, idList);
                List<PackageCollection> dataList = super.baseMapper.selectList(queryWrapper);
                dataList.stream().forEach(e -> {
                    e.setDetailList(this.packageCollectionDetailService.listByCollectionId(e.getId()));
                    //通知提货商创建提货服务
                    createPackageService(e);
                });
            }
            return update;
        }
        return 0;
    }

    @Override
    public int updateOutboundNo(PackageCollection packageCollection) {
        LambdaUpdateWrapper<PackageCollection> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.set(PackageCollection::getOutboundNo, packageCollection.getOutboundNo());
        updateWrapper.eq(PackageCollection::getCollectionNo, packageCollection.getCollectionNo());
        return super.baseMapper.update(null, updateWrapper);
    }

    @Override
    public int cancel(List<Long> idList) {
        List<PackageCollection> collectionList = super.listByIds(idList);
        if (CollectionUtils.isNotEmpty(collectionList)) {
            // 验证状态，只有状态为：有计划，没有计划的揽收单可以取消
            for (PackageCollection collection : collectionList) {
                if (!(PackageCollectionConstants.Status.PLANNED.name().equals(collection.getStatus())
                        || PackageCollectionConstants.Status.NO_PLAN.name().equals(collection.getStatus()))) {
                    throw new CommonException("500", collection.getCollectionNo() + "单据不可以取消，状态不符合");
                }
                // 取消运费冻结
                this.cancelFreight(collection);
                // 取消业务费冻结
                this.cancelOperatingFee(collection);
            }
            // 取消承运商订单
            this.cancelCarrier(collectionList);
            // 修改揽收单状态为已取消
            LambdaUpdateWrapper<PackageCollection> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
            lambdaUpdateWrapper.set(PackageCollection::getStatus, PackageCollectionConstants.Status.CANCELLED.name());
            lambdaUpdateWrapper.in(PackageCollection::getId, idList);
            super.update(lambdaUpdateWrapper);
            return collectionList.size();
        }
        return 0;
    }

    @Override
    public void notRecordCancel(PackageCollection packageCollection) {
        LambdaQueryWrapper<PackageCollectionOperationRecord> lambdaQueryWrapper = Wrappers.lambdaQuery();
        lambdaQueryWrapper.eq(PackageCollectionOperationRecord::getCollectionNo, packageCollection.getCollectionNo());
        List<PackageCollectionOperationRecord> operationRecordList = this.packageCollectionOperationRecordService.list(lambdaQueryWrapper);
        if (CollectionUtils.isNotEmpty(operationRecordList)) {
            for (PackageCollectionOperationRecord operationRecord : operationRecordList) {
                if (PackageCollectionOperationRecordConstants.Type.FREIGHT.name().equals(operationRecord.getType())) {
                    this.cancelFreight(packageCollection);
                } else if (PackageCollectionOperationRecordConstants.Type.CARRIER.name().equals(operationRecord.getType())) {
                    this.cancelCarrier(Collections.singletonList(packageCollection));
                } else if (PackageCollectionOperationRecordConstants.Type.OPERATING_FEE.name().equals(operationRecord.getType())) {
                    this.cancelOperatingFee(packageCollection);
                }
            }
            this.packageCollectionOperationRecordService.remove(lambdaQueryWrapper);
        }
    }

    private void createTrackingYee(PackageCollection collection) {
        boolean success = false;
        String responseBody;
        try {
            Map<String, Object> requestBodyMap = new HashMap<>();
            List<Map<String, Object>> shipments = new ArrayList<>();
            Map<String, Object> shipment = new HashMap<>();
            shipment.put("trackingNo", collection.getTrackingNo());
            shipment.put("carrierCode", collection.getLogisticsProviderCode());
            shipment.put("logisticsServiceProvider", collection.getLogisticsProviderCode());
            shipment.put("logisticsServiceName", collection.getLogisticsProviderCode());
            shipment.put("platformCode", "DM");
            shipment.put("shopName", "");
            Date createTime = collection.getCreateTime();
            if (null != createTime) {
                shipment.put("OrdersOn", DateFormatUtils.format(createTime, "yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
            }
            shipment.put("paymentTime", "");
            shipment.put("shippingOn", "");
            List<String> searchTags = new ArrayList<>();
            searchTags.add(collection.getSellerCode());
            searchTags.add(collection.getCollectionNo());
            shipment.put("searchTags", searchTags);
            shipment.put("orderNo", collection.getCollectionNo());
            Map<String, Object> senderAddress = new HashMap<>();
            senderAddress.put("country", collection.getCollectionCountry());
            senderAddress.put("province", collection.getCollectionProvince());
            senderAddress.put("city", collection.getCollectionCity());
            senderAddress.put("postcode", collection.getCollectionPostCode());
            senderAddress.put("street1", collection.getCollectionAddress());
            senderAddress.put("street2", "");
            senderAddress.put("street3", "");
            shipment.put("senderAddress", senderAddress);
            Map<String, Object> destinationAddress = new HashMap<>();
            destinationAddress.put("country", collection.getReceiverCountry());
            destinationAddress.put("province", collection.getReceiverProvince());
            destinationAddress.put("city", collection.getReceiverCity());
            destinationAddress.put("postcode", collection.getReceiverPostCode());
            destinationAddress.put("street1", collection.getReceiverAddress());
            destinationAddress.put("street2", "");
            destinationAddress.put("street3", "");
            shipment.put("destinationAddress", destinationAddress);
            Map<String, Object> recipientInfo = new HashMap<>();
            recipientInfo.put("recipient", collection.getReceiverName());
            recipientInfo.put("phoneNumber", collection.getReceiverPhone());
            recipientInfo.put("email", "");
            shipment.put("recipientInfo", recipientInfo);
            Map<String, Object> customFieldInfo = new HashMap<>();
            customFieldInfo.put("fieldOne", collection.getCollectionNo());
            customFieldInfo.put("fieldTwo", "");
            customFieldInfo.put("fieldThree", "");
            shipment.put("customFieldInfo", customFieldInfo);
            shipments.add(shipment);
            requestBodyMap.put("shipments", shipments);
            HttpRequestDto httpRequestDto = new HttpRequestDto();
            httpRequestDto.setMethod(HttpMethod.POST);
            String url = DomainEnum.TrackingYeeDomain.wrapper("/tracking/v1/shipments");
            httpRequestDto.setUri(url);
            httpRequestDto.setBody(requestBodyMap);
            HttpResponseVO httpResponseVO = htpRmiClientService.rmi(httpRequestDto);
            if (200 == httpResponseVO.getStatus() ||
                    201 == httpResponseVO.getStatus()) {
                success = true;
            }
            responseBody = (String) httpResponseVO.getBody();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            responseBody = e.getMessage();
            if (null == responseBody) {
                responseBody = "请求失败";
            }
        }
        // 请求成功，解析响应报文
        if (success) {
            try {
                // 解析响应报文，获取响应参数信息
                JSONObject jsonObject = JSON.parseObject(responseBody);
                // 判断状态是否为OK
                if ("OK".equals(jsonObject.getString("status"))) {
                    // 判断结果明细是不是成功的
                    JSONObject data = jsonObject.getJSONObject("data");
                    if (1 != data.getIntValue("successNumber")) {
                        // 返回的成功数量不是1，判定为异常
                        success = false;
                        // 获取异常信息
                        int failNumber = data.getIntValue("failNumber");
                        if (failNumber > 0) {
                            JSONArray failImportRowResults = data.getJSONArray("failImportRowResults");
                            JSONObject failImportRowResult = failImportRowResults.getJSONObject(0);
                            JSONObject errorInfo = failImportRowResult.getJSONObject("errorInfo");
                            String errorCode = errorInfo.getString("errorCode");
                            String errorMessage = errorInfo.getString("errorMessage");
                            throw new CommonException("500", "[" + errorCode + "]" + errorMessage);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                if (e instanceof CommonException) {
                    throw e;
                }
                // 解析失败，判定为异常
                success = false;
            }
        }
        if (!success) {
            throw new CommonException("500", "创建TrackingYee失败");
        }
    }

    private void cancelFreight(PackageCollection collection) {
        CusFreezeBalanceDTO cusFreezeBalanceDTO = new CusFreezeBalanceDTO();
        cusFreezeBalanceDTO.setAmount(collection.getAmount());
        cusFreezeBalanceDTO.setCurrencyCode(collection.getCurrencyCode());
        cusFreezeBalanceDTO.setCusCode(collection.getSellerCode());
        cusFreezeBalanceDTO.setNo(collection.getCollectionNo());
        cusFreezeBalanceDTO.setOrderType("Freight");
        R<?> thawBalanceR = this.rechargesFeignService.thawBalance(cusFreezeBalanceDTO);
        if (null == thawBalanceR) {
            throw new CommonException("400", "取消冻结费用失败，响应数据异常");
        }
        if (Constants.SUCCESS != thawBalanceR.getCode()) {
            String msg = thawBalanceR.getMsg();
            if (StringUtils.isEmpty(msg)) {
                msg = "取消冻结费用失败";
            }
            throw new CommonException("400", msg);
        }
    }

    private void cancelOperatingFee(PackageCollection collection) {
        DelOutboundOperationVO delOutboundOperationVO = new DelOutboundOperationVO();
        delOutboundOperationVO.setOrderType("PackageCollection");
        delOutboundOperationVO.setOrderNo(collection.getCollectionNo());
        R<?> r = this.operationFeignService.delOutboundThaw(delOutboundOperationVO);
        if (null == r) {
            throw new CommonException("400", "取消业务操作费用失败，响应数据异常");
        }
        if (Constants.SUCCESS != r.getCode()) {
            String msg = r.getMsg();
            if (StringUtils.isEmpty(msg)) {
                msg = "取消业务操作费用失败";
            }
            throw new CommonException("400", msg);
        }
    }

    private void cancelCarrier(List<PackageCollection> collectionList) {
        if (CollectionUtils.isEmpty(collectionList)) {
            return;
        }
        Map<String, List<PackageCollection>> collectionListMap = collectionList.stream().collect(Collectors.groupingBy(PackageCollection::getWarehouseCode));
        for (String warehouseCode : collectionListMap.keySet()) {
            try {
                CancelShipmentOrderCommand command = new CancelShipmentOrderCommand();
                command.setWarehouseCode(warehouseCode);
                command.setReferenceNumber(UUID.randomUUID().toString().replaceAll("-", ""));
                List<PackageCollection> list = collectionListMap.get(warehouseCode);
                if (CollectionUtils.isNotEmpty(list)) {
                    List<CancelShipmentOrder> cancelShipmentOrders = new ArrayList<>();
                    for (PackageCollection collection : list) {
                        cancelShipmentOrders.add(new CancelShipmentOrder(collection.getShipmentOrderNumber(), collection.getTrackingNo()));
                    }
                    command.setCancelShipmentOrders(cancelShipmentOrders);
                    this.htpCarrierClientService.cancellation(command);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    private int countTotalQty(List<PackageCollectionDetail> detailList) {
        int totalQty = 0;
        if (CollectionUtils.isNotEmpty(detailList)) {
            for (PackageCollectionDetail detail : detailList) {
                Integer qty = detail.getQty();
                if (null == qty) {
                    qty = 0;
                }
                totalQty += qty;
            }
        }
        return totalQty;
    }

    private void saveDetail(Long collectionId, List<PackageCollectionDetail> detailList) {
        if (CollectionUtils.isNotEmpty(detailList)) {
            int sort = 0;
            for (PackageCollectionDetail detail : detailList) {
                detail.setCollectionId(collectionId);
                detail.setSort(sort++);
            }
            this.packageCollectionDetailService.saveBatch(detailList);
        }
    }

    /**
     * 批量删除package - 交货管理 - 揽收模块
     *
     * @param ids 需要删除的package - 交货管理 - 揽收模块ID
     * @return 结果
     */
    @Override
    public int deletePackageCollectionByIds(List<String> ids) {
        return baseMapper.deleteBatchIds(ids);
    }

    /**
     * 删除package - 交货管理 - 揽收模块信息
     *
     * @param id package - 交货管理 - 揽收模块ID
     * @return 结果
     */
    @Override
    public int deletePackageCollectionById(String id) {
        return baseMapper.deleteById(id);
    }

    @Override
    public IPage<PackageCollection> page(PackageCollectionQueryDto dto) {
        IPage<PackageCollection> iPage = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<PackageCollection> queryWrapper = Wrappers.lambdaQuery();
        // 只查询自己的揽收单
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (null != loginUser) {
            String sellerCode = loginUser.getSellerCode();
            queryWrapper.eq(StringUtils.isNotEmpty(sellerCode), PackageCollection::getSellerCode, sellerCode);
        }
        // 子母单的查询 如果没有传值就只能才自己的
        if (Objects.nonNull(SecurityUtils.getLoginUser())) {
            String cusCode = StringUtils.isNotEmpty(SecurityUtils.getLoginUser().getSellerCode()) ? SecurityUtils.getLoginUser().getSellerCode() : "";
            if (StringUtils.isEmpty(dto.getCustomCode())) {
                dto.setCustomCode(cusCode);
            }
        }
        if (CollectionUtils.isNotEmpty(dto.getCustomCodeList())) {
            queryWrapper.in(PackageCollection::getSellerCode, dto.getCustomCodeList());
        }

        String queryNo = dto.getQueryNoOne();
        if (com.szmsd.common.core.utils.StringUtils.isNotEmpty(queryNo)) {
            List<String> queryNoList = splitToArray(queryNo, "[\n,]");
            queryWrapper.in(PackageCollection::getOutboundNo,queryNoList).or().in(PackageCollection::getTrackingNo,queryNoList);
        }
        // 揽收单号
        this.autoSettingListCondition(queryWrapper, PackageCollection::getCollectionNo, this.getTextList(dto.getCollectionNo()));
        // 跟踪号
        this.autoSettingListCondition(queryWrapper, PackageCollection::getTrackingNo, this.getTextList(dto.getTrackingNo()));
        // 创建时间
        this.autoSettingDateCondition(queryWrapper, PackageCollection::getCreateTime, dto.getCreateTimes());
        // 揽收时间
        this.autoSettingDateCondition(queryWrapper, PackageCollection::getCollectionDate, dto.getCollectionTimes());
        // 状态
        queryWrapper.eq(StringUtils.isNotEmpty(dto.getStatus()), PackageCollection::getStatus, dto.getStatus());
        // 揽收人
        queryWrapper.like(StringUtils.isNotEmpty(dto.getCollectionName()), PackageCollection::getCollectionName, dto.getCollectionName());
        // 揽收至仓库
        queryWrapper.eq(StringUtils.isNotEmpty(dto.getCollectionToWarehouse()), PackageCollection::getCollectionToWarehouse, dto.getCollectionToWarehouse());
        // 处理方式
        queryWrapper.eq(StringUtils.isNotEmpty(dto.getHandleMode()), PackageCollection::getHandleMode, dto.getHandleMode());
        queryWrapper.orderByDesc(PackageCollection::getCreateTime);
        IPage<PackageCollection> page = super.page(iPage, queryWrapper);
        List<PackageCollection> records = page.getRecords();
        if (CollectionUtils.isNotEmpty(records)) {
            List<Long> collectionIdList = records.stream().map(PackageCollection::getId).collect(Collectors.toList());
            Map<Long, String> collectionSkuNames = this.packageCollectionDetailService.getCollectionSkuNames(collectionIdList);
            if (MapUtils.isNotEmpty(collectionSkuNames)) {
                for (PackageCollection record : records) {
                    record.setSkuNames(collectionSkuNames.getOrDefault(record.getId(), "-"));
                }
            }
        }
        return page;
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

    @Override
    public int updateCollecting(String collectionNo) {
        PackageCollection packageCollection = this.getByCollectionNo(collectionNo);
        if (null != packageCollection) {
            if (PackageCollectionConstants.Status.PLANNED.name().equals(packageCollection.getStatus())) {
                PackageCollection updatePackageCollection = new PackageCollection();
                updatePackageCollection.setStatus(PackageCollectionConstants.Status.COLLECTING.name());
                updatePackageCollection.setId(packageCollection.getId());
                return super.baseMapper.updateById(updatePackageCollection);
            }
            // 状态不符合
            return -1;
        }
        // 单据不存在
        return -2;
    }

    @Override
    public int updateCollectingCompleted(String collectionNo) {
        PackageCollection packageCollection = this.getByCollectionNo(collectionNo);
        if (null != packageCollection) {
            if (PackageCollectionConstants.Status.PLANNED.name().equals(packageCollection.getStatus())
                    || PackageCollectionConstants.Status.COLLECTING.name().equals(packageCollection.getStatus())) {
                PackageCollection updatePackageCollection = new PackageCollection();
                updatePackageCollection.setStatus(PackageCollectionConstants.Status.COMPLETED.name());
                updatePackageCollection.setId(packageCollection.getId());
                int i = super.baseMapper.updateById(updatePackageCollection);
                if (i > 0) {
                    // 揽收单完成事件
                    PackageCollectionCompletedEvent.publishEvent(packageCollection);
                }
                return i;
            }
            // 状态不符合
            return -1;
        }
        // 单据不存在
        return -2;
    }

    private PackageCollection getByCollectionNo(String collectionNo) {
        LambdaQueryWrapper<PackageCollection> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(PackageCollection::getCollectionNo, collectionNo);
        return super.getOne(queryWrapper);
    }

    @Override
    public void collectionLabel(String collectionNo, HttpServletResponse response) {
        PackageCollection packageCollection = this.getByCollectionNo(collectionNo);
        if (null != packageCollection) {
            // 文件夹根目录
            String basedir = SpringUtils.getProperty("server.tomcat.basedir", "/u01/www/ck1/package/tmp");
            // 文件类型
            basedir += "/label";
            // 年月日
            Date createTime = packageCollection.getCreateTime();
            String datePath = DateFormatUtils.format(createTime, "yyyy/MM/dd");
            basedir += "/" + datePath;
            // 文件全路径
            String pathname = basedir + "/" + packageCollection.getCollectionNo() + ".pdf";
            // 判断文件是否存在
            File packageCollectionLabelFile = new File(pathname);
            if (packageCollectionLabelFile.exists()) {
                // 文件存在，获取文件信息，输出给前端
                try {
                    byte[] byteArray = FileUtils.readFileToByteArray(packageCollectionLabelFile);
                    this.output(response, byteArray, packageCollection.getCollectionNo() + ".pdf");
                    // end
                    return;
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    throw new CommonException("500", "标签文件已下载，获取输出文件流失败");
                }
            }
            // 获取标签
            CreateShipmentOrderCommand command = new CreateShipmentOrderCommand();
            command.setWarehouseCode(packageCollection.getWarehouseCode());
            command.setOrderNumber(packageCollection.getShipmentOrderNumber());
            command.setShipmentOrderLabelUrl(packageCollection.getShipmentOrderLabelUrl());
            logger.info("正在获取标签文件，揽收单号：{}，承运商订单号：{}", packageCollection.getCollectionNo(), packageCollection.getShipmentOrderNumber());
            ResponseObject<FileStream, ProblemDetails> responseObject = this.htpCarrierClientService.label(command);
            if (null != responseObject) {
                if (responseObject.isSuccess()) {
                    // 判断文件夹是否存在
                    File file = new File(basedir);
                    if (!file.exists()) {
                        try {
                            // 创建文件夹
                            FileUtils.forceMkdir(file);
                        } catch (IOException e) {
                            // 内部异常，不再重试，直接抛出去
                            throw new CommonException("500", "创建文件夹[" + file.getPath() + "]失败，Error：" + e.getMessage());
                        }
                    }
                    byte[] inputStream;
                    FileStream fileStream = responseObject.getObject();
                    if (null != fileStream && null != (inputStream = fileStream.getInputStream())) {
                        // 保存文件至本地
                        File labelFile = new File(pathname);
                        try {
                            FileUtils.writeByteArrayToFile(labelFile, inputStream, false);
                        } catch (IOException e) {
                            // 内部异常，不再重试，直接抛出去
                            throw new CommonException("500", "保存标签文件失败，Error：" + e.getMessage());
                        }
                        // 写出文件流
                        try {
                            this.output(response, inputStream, packageCollection.getCollectionNo() + ".pdf");
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            throw new CommonException("500", "标签文件已下载，获取输出文件流失败");
                        }
                        // end
                    }
                } else {
                    // 接口响应异常，继续重试
                    String exceptionMessage = ProblemDetails.getErrorMessageOrNull(responseObject.getError());
                    if (StringUtils.isNotEmpty(exceptionMessage)) {
                        exceptionMessage = "获取标签文件流失败，接口无错误信息";
                    }
                    throw new CommonException("500", exceptionMessage);
                }
            } else {
                // 接口响应异常继续重试
                throw new CommonException("500", "获取标签文件流失败，接口无响应");
            }
        } else {
            throw new CommonException("500", "揽收计划不存在");
        }
    }

    private void output(HttpServletResponse response, byte[] bytes, String name) {
        OutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
            //response为HttpServletResponse对象
            response.setContentType("application/pdf;charset=utf-8");
            //Loading plan.xls是弹出下载对话框的文件名，不能为中文，中文请自行编码
            response.setHeader("Content-Disposition", "attachment;filename=" + name);
            IOUtils.write(bytes, outputStream);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new CommonException("500", "标签文件已下载，写入输出流失败");
        } finally {
            if (null != outputStream) {
                IOUtils.closeQuietly(outputStream);
            }
        }
    }

    private void autoSettingDateCondition(LambdaQueryWrapper<PackageCollection> queryWrapper, SFunction<PackageCollection, ?> column, String[] dates) {
        if (ArrayUtils.isNotEmpty(dates) && dates.length == 2) {
            queryWrapper.between(column, dates[0] + " 00:00:00", dates[1] + " 23:59:59");
        }
    }

    private void autoSettingListCondition(LambdaQueryWrapper<PackageCollection> queryWrapper, SFunction<PackageCollection, ?> column, List<?> list) {
        if (CollectionUtils.isNotEmpty(list)) {
            if (list.size() == 1) {
                queryWrapper.eq(column, list.get(0));
            } else {
                queryWrapper.in(column, list);
            }
        }
    }

    private List<String> getTextList(String text) {
        if (StringUtils.isNotEmpty(text)) {
            List<String> textList;
            if (text.contains("\n") || text.contains("\r")) {
                if (text.contains("\r")) {
                    text = text.replaceAll("\r\n", "\n");
                    text = text.replaceAll("\r", "\n");
                }
                String[] textArray = text.split("\n");
                textList = Arrays.stream(textArray).collect(Collectors.toList());
            } else {
                textList = Collections.singletonList(text);
            }
            return textList;
        } else {
            return null;
        }
    }
}

