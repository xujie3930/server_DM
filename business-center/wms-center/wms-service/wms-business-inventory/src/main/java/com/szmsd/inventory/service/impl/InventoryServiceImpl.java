package com.szmsd.inventory.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.szmsd.bas.api.service.BaseProductClientService;
import com.szmsd.bas.domain.BaseProduct;
import com.szmsd.bas.dto.BaseProductConditionQueryDto;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.AssertUtil;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.language.enums.LocalLanguageEnum;
import com.szmsd.common.core.language.enums.LocalLanguageTypeEnum;
import com.szmsd.common.core.utils.DateUtils;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.core.web.domain.BaseEntity;
import com.szmsd.common.security.domain.LoginUser;
import com.szmsd.common.security.utils.SecurityUtils;
import com.szmsd.http.api.feign.HtpRmiFeignService;
import com.szmsd.http.config.CkConfig;
import com.szmsd.http.config.CkThreadPool;
import com.szmsd.http.dto.HttpRequestDto;
import com.szmsd.http.dto.HttpRequestSyncDTO;
import com.szmsd.http.enums.DomainEnum;
import com.szmsd.http.enums.RemoteConstant;
import com.szmsd.http.util.DomainInterceptorUtil;
import com.szmsd.http.vo.HttpResponseVO;
import com.szmsd.inventory.component.RemoteComponent;
import com.szmsd.inventory.domain.Inventory;
import com.szmsd.inventory.domain.InventoryOccupy;
import com.szmsd.inventory.domain.InventoryRecord;
import com.szmsd.inventory.domain.dto.*;
import com.szmsd.inventory.domain.vo.*;
import com.szmsd.inventory.mapper.InventoryMapper;
import com.szmsd.inventory.service.IInventoryRecordService;
import com.szmsd.inventory.service.IInventoryService;
import com.szmsd.inventory.service.InventoryOccupyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.szmsd.common.core.language.enums.LocalLanguageEnum.*;

@Slf4j
@Service
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory> implements IInventoryService {

    @Lazy
    @Resource
    private IInventoryRecordService iInventoryRecordService;

    @Resource
    private RemoteComponent remoteComponent;
    @Autowired
    private BaseProductClientService baseProductClientService;
    @Autowired
    private InventoryOccupyService inventoryOccupyService;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private CkConfig ckConfig;
    @Resource
    private CkThreadPool ckThreadPool;
    @Resource
    private HtpRmiFeignService htpRmiFeignService;
    private final static long WAIT_TIME = 10L;

    /**
     * ???????????? - Inbound - /api/inbound/receiving #B1 ?????????????????? - ????????????
     *
     * @param inboundInventoryDTO
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void inbound(InboundInventoryDTO inboundInventoryDTO) {
        log.info("???????????????{}", inboundInventoryDTO);
        String lockKey = Optional.ofNullable(SecurityUtils.getLoginUser()).map(LoginUser::getSellerCode).orElse("");
        RLock lock = redissonClient.getLock("InventoryServiceImpl#inbound" + lockKey);
        // ?????????
        try {
            if (lock.tryLock(WAIT_TIME, TimeUnit.SECONDS)) {
                // ???????????? ???????????? + SKU
                String sku = inboundInventoryDTO.getSku();
                String warehouseCode = inboundInventoryDTO.getWarehouseCode();
                Integer qty = inboundInventoryDTO.getQty();
                // before inventory
                Inventory beforeInventory = baseMapper.selectOne(new QueryWrapper<Inventory>().eq("warehouse_code", warehouseCode).eq("sku", sku));
                Inventory afterInventory = new Inventory();
                if (beforeInventory == null) {
                    beforeInventory = new Inventory().setSku(sku).setWarehouseCode(warehouseCode).setTotalInventory(0).setAvailableInventory(0).setAvailableInventory(0).setTotalInbound(0);
                    BaseProduct sku1 = remoteComponent.getSku(sku);
                    beforeInventory.setCusCode(sku1.getSellerCode());
                    afterInventory.setCusCode(sku1.getSellerCode());
                }
                //07-16 ???????????????????????????????????????????????????????????????????????????????????????????????????
                String orderNo = inboundInventoryDTO.getOrderNo();
                // 08-17 ?????????????????????
               /* //??????????????????????????????????????????
                InboundReceiptInfoVO receiptInfo = remoteComponent.getReceiptInfo(orderNo);
                String purchaseOrder = receiptInfo.getOrderNo();
                if (StringUtils.isNotBlank(purchaseOrder)) {
                    log.info("????????? ?????? -- ?????? ?????? {}", purchaseOrder);
                    List<InboundReceiptVO> receiptInfoList = remoteComponent.getReceiptInfoList(purchaseOrder);
                    List<String> warehouseNoList = receiptInfoList.stream().map(InboundReceiptVO::getWarehouseNo).collect(Collectors.toList());

                    //?????????????????????????????????????????? ???????????????????????? ????????????sku??????????????????
                    List<InventoryRecord> inventoryRecordVOS = iInventoryRecordService.getBaseMapper()
                            .selectList(Wrappers.<InventoryRecord>lambdaQuery().eq(InventoryRecord::getSku, sku).in(InventoryRecord::getReceiptNo, warehouseNoList));

                    //?????????????????????
                    int beforeFreeze = inventoryRecordVOS.stream().map(InventoryRecord::getOperator).mapToInt(Integer::parseInt).reduce(Integer::sum).orElse(0);
                    //????????????????????????????????? ??????sku??????????????????sku??????
                    PurchaseInfoVO purchaseInfoVO = iPurchaseService.selectPurchaseByPurchaseNo(purchaseOrder);
                    List<PurchaseInfoDetailVO> purchaseDetailsAddList = purchaseInfoVO.getPurchaseDetailsAddList();
                    //??????????????? = ?????????sku??????
                    Integer purchaseNum = purchaseDetailsAddList.stream().filter(x -> x.getSku().equals(sku)).findAny()
                            .map(PurchaseInfoDetailVO::getPurchaseQuantity).orElse(0);
                    // ?????????????????????A  SKU   M?????????????????????WMS??????N????????????SKU???????????????????????????+N??????????????????+N-M??????????????????+M????????????+N
                    //????????????????????? = ????????????-??????????????????
                    int canFreeze = purchaseNum - beforeFreeze;
                    int afterTotalInventory = beforeInventory.getTotalInventory() + qty;
                    int afterTotalInbound = beforeInventory.getTotalInbound() + qty;
                    int afterAvailableInventory = beforeInventory.getAvailableInventory();
                    if (canFreeze > 0) {
                        //?????????????????????
                        int freezeInventory = Optional.ofNullable(afterInventory.getFreezeInventory()).orElse(0) + qty;
                        //????????? = ??????++
                        if (qty >= canFreeze) {
                            //?????????>???????????? ??? ????????? = ????????????
                            afterAvailableInventory += (qty - canFreeze);
                             freezeInventory += canFreeze;
                        } else {
                            //?????????<???????????? ????????????????????? + ?????????
                             freezeInventory += qty;
                        }
                        afterInventory.setFreezeInventory(freezeInventory).setId(beforeInventory.getId()).setSku(sku).setWarehouseCode(warehouseCode).setTotalInventory(afterTotalInventory).setAvailableInventory(afterAvailableInventory).setTotalInbound(afterTotalInbound);
                        afterInventory.setLastInboundTime(DateUtils.dateTime("yyyy-MM-dd'T'HH:mm:ss", inboundInventoryDTO.getOperateOn()));
                        this.saveOrUpdate(afterInventory);
                    } else {
                        //????????? = ??????++
                        afterAvailableInventory += qty;
                        afterInventory.setId(beforeInventory.getId()).setSku(sku).setWarehouseCode(warehouseCode).setTotalInventory(afterTotalInventory).setAvailableInventory(afterAvailableInventory).setTotalInbound(afterTotalInbound);
                        this.saveOrUpdate(afterInventory);
                    }
                } else */
                {
                    // after inventory
                    int afterTotalInventory = beforeInventory.getTotalInventory() + qty;
                    int afterAvailableInventory = beforeInventory.getAvailableInventory() + qty;
                    int afterTotalInbound = beforeInventory.getTotalInbound() + qty;
                    afterInventory.setId(beforeInventory.getId()).setSku(sku).setWarehouseCode(warehouseCode).setTotalInventory(afterTotalInventory).setAvailableInventory(afterAvailableInventory).setTotalInbound(afterTotalInbound);
                    afterInventory.setLastInboundTime(DateUtils.dateTime("yyyy-MM-dd'T'HH:mm:ss", inboundInventoryDTO.getOperateOn()));
                    this.saveOrUpdate(afterInventory);
                }

                // ??????????????????
                iInventoryRecordService.saveLogs(INVENTORY_RECORD_TYPE_1.getKey(), beforeInventory, afterInventory, inboundInventoryDTO.getOrderNo(), inboundInventoryDTO.getOperator(), inboundInventoryDTO.getOperateOn(), qty);
            } else {
                throw new RuntimeException("????????????,???????????????");
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            log.error("?????????????????????", e);
            throw new RuntimeException("??????????????????,???????????????");
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

    /**
     * ?????????????????? - ???????????? - ??????
     *
     * @param inventorySkuQueryDTO
     * @return
     */
    @Override
    public List<InventorySkuVO> selectList(InventorySkuQueryDTO inventorySkuQueryDTO) {
        String sku = inventorySkuQueryDTO.getSku();
        if (StringUtils.isNotEmpty(sku)) {
            List<String> skuSplit = Arrays.asList(sku.split(","));
            List<String> skuList = ListUtils.emptyIfNull(inventorySkuQueryDTO.getSkuList());
            inventorySkuQueryDTO.setSkuList(Stream.of(skuSplit, skuList).flatMap(Collection::stream).distinct().collect(Collectors.toList()));
        }
        //???????????????????????????uat
//        if (Objects.nonNull(SecurityUtils.getLoginUser())) {
//            String cusCode = StringUtils.isNotEmpty(SecurityUtils.getLoginUser().getSellerCode()) ? SecurityUtils.getLoginUser().getSellerCode() : "";
//            if (StringUtils.isEmpty(inventorySkuQueryDTO.getCusCode())) {
//                inventorySkuQueryDTO.setCusCode(cusCode);
//            }
//        }
        return baseMapper.selectListVO(inventorySkuQueryDTO);
    }

    private void handlerSkuQueryWrapper(QueryWrapper<InventoryAvailableQueryDto> queryWrapper, InventoryAvailableQueryDto queryDto) {
        queryWrapper.eq("t.warehouse_code", queryDto.getWarehouseCode());
        queryWrapper.eq("t.cus_code", queryDto.getCusCode());
        if (StringUtils.isNotEmpty(queryDto.getSku())) {
            queryWrapper.like("t.sku", queryDto.getSku());
        }
        if (StringUtils.isNotEmpty(queryDto.getEqSku())) {
            queryWrapper.eq("t.sku", queryDto.getEqSku());
        }
        if (CollectionUtils.isNotEmpty(queryDto.getSkus())) {
            queryWrapper.in("t.sku", queryDto.getSkus());
        }
        if (null != queryDto.getQueryType() && 1 == queryDto.getQueryType()) {
            // ??????????????????0
            queryWrapper.gt("t.available_inventory", 0);
        }
        if ("SKU".equalsIgnoreCase(queryDto.getQuerySku())) {
            // ??????????????????SKU???????????????????????????
            // ?????????SKU?????????
            queryWrapper.eq("sku.category", "SKU");
        }
        if ("084002".equals(queryDto.getSource())) {
            queryWrapper.eq("sku.source", "084002");
        }
    }

    private void handlerQueryWrapper(QueryWrapper<InventoryAvailableQueryDto> queryWrapper, InventoryAvailableQueryDto queryDto) {
        queryWrapper.eq("warehouse_code", queryDto.getWarehouseCode());
        queryWrapper.eq("cus_code", queryDto.getCusCode());
        if (StringUtils.isNotEmpty(queryDto.getSku())) {
            queryWrapper.like("sku", queryDto.getSku());
        }
        if (StringUtils.isNotEmpty(queryDto.getEqSku())) {
            queryWrapper.eq("sku", queryDto.getEqSku());
        }
        if (CollectionUtils.isNotEmpty(queryDto.getSkus())) {
            queryWrapper.in("sku", queryDto.getSkus());
        }
        if (null != queryDto.getQueryType() && 1 == queryDto.getQueryType()) {
            // ??????????????????0
            queryWrapper.gt("available_inventory", 0);
        }
    }

    @Override
    public List<InventoryAvailableListVO> queryAvailableList(InventoryAvailableQueryDto queryDto) {
        if (StringUtils.isEmpty(queryDto.getWarehouseCode()) || StringUtils.isEmpty(queryDto.getCusCode())) {
            return Collections.emptyList();
        }
        QueryWrapper<InventoryAvailableQueryDto> queryWrapper = Wrappers.query();
        this.handlerSkuQueryWrapper(queryWrapper, queryDto);
        List<InventoryAvailableListVO> voList = this.baseMapper.queryAvailableList(queryWrapper);
        /*if (CollectionUtils.isNotEmpty(voList)) {
            // ??????SKU????????????
            List<String> skus = voList.stream().map(InventoryAvailableListVO::getSku).collect(Collectors.toList());
            BaseProductConditionQueryDto conditionQueryDto = new BaseProductConditionQueryDto();
            conditionQueryDto.setSkus(skus);
            List<BaseProduct> productList = this.baseProductClientService.queryProductList(conditionQueryDto);
            Map<String, BaseProduct> productMap;
            if (CollectionUtils.isNotEmpty(productList)) {
                productMap = productList.stream().collect(Collectors.toMap(BaseProduct::getCode, (v) -> v, (v1, v2) -> v1));
            } else {
                productMap = Collections.emptyMap();
            }
            for (InventoryAvailableListVO vo : voList) {
                this.setFieldValue(vo, productMap.get(vo.getSku()));
            }
        }*/
        return voList;
    }

    @Override
    public InventoryAvailableListVO queryOnlyAvailable(InventoryAvailableQueryDto queryDto) {
        if (StringUtils.isEmpty(queryDto.getWarehouseCode())) {
            return null;
        }
        QueryWrapper<InventoryAvailableQueryDto> queryWrapper = Wrappers.query();
        this.handlerQueryWrapper(queryWrapper, queryDto);
        InventoryAvailableListVO vo = this.baseMapper.queryOnlyAvailable(queryWrapper);
        if (Objects.nonNull(vo)) {
            // ??????SKU????????????
            BaseProductConditionQueryDto conditionQueryDto = new BaseProductConditionQueryDto();
            conditionQueryDto.setSkus(Collections.singletonList(vo.getSku()));
            List<BaseProduct> productList = this.baseProductClientService.queryProductList(conditionQueryDto);
            Map<String, BaseProduct> productMap;
            if (CollectionUtils.isNotEmpty(productList)) {
                productMap = productList.stream().collect(Collectors.toMap(BaseProduct::getCode, (v) -> v, (v1, v2) -> v1));
            } else {
                productMap = Collections.emptyMap();
            }
            this.setFieldValue(vo, productMap.get(vo.getSku()));
        }
        return vo;
    }

    private void setFieldValue(InventoryAvailableListVO vo, BaseProduct product) {
        if (Objects.nonNull(product)) {
            vo.setCode(product.getCode());
            vo.setProductName(product.getProductName());
            vo.setInitWeight(product.getInitWeight());
            vo.setInitLength(product.getInitLength());
            vo.setInitWidth(product.getInitWidth());
            vo.setInitHeight(product.getInitHeight());
            vo.setInitVolume(product.getInitVolume());
            vo.setWeight(product.getWeight());
            vo.setLength(product.getLength());
            vo.setWidth(product.getWidth());
            vo.setHeight(product.getHeight());
            vo.setVolume(product.getVolume());
            vo.setBindCode(product.getBindCode());
            vo.setBindCodeName(product.getBindCodeName());
        }
    }

    @Override
    public List<InventoryVO> querySku(InventoryAvailableQueryDto queryDto) {
        if (StringUtils.isEmpty(queryDto.getWarehouseCode())) {
            return Collections.emptyList();
        }
        QueryWrapper<InventoryAvailableQueryDto> queryWrapper = Wrappers.query();
        this.handlerQueryWrapper(queryWrapper, queryDto);
        return this.baseMapper.querySku(queryWrapper);
    }

    @Override
    public InventoryVO queryOnlySku(InventoryAvailableQueryDto queryDto) {
        if (StringUtils.isEmpty(queryDto.getWarehouseCode())) {
            return null;
        }
        QueryWrapper<InventoryAvailableQueryDto> queryWrapper = Wrappers.query();
        this.handlerQueryWrapper(queryWrapper, queryDto);
        return this.baseMapper.queryOnlySku(queryWrapper);
    }

    @Transactional
    @Override
    public int freeze(String invoiceNo, String warehouseCode, String sku, Integer num, Integer freeType, String cusCode) {
        /*return this.doWorker(invoiceNo, warehouseCode, sku, num, (queryWrapperConsumer) -> {
            // ?????????????????????
            if (null == freeType) {
                // >=
                queryWrapperConsumer.ge(Inventory::getAvailableInventory, num);
            }
        }, (dbInventory) -> {
            // ????????????????????????????????????????????????????????????????????????
            if (null != freeType && null == dbInventory) {
                Inventory inventory = new Inventory();
                inventory.setCusCode(cusCode)
                        .setSku(sku)
                        .setWarehouseCode(warehouseCode)
                        .setTotalInventory(0)
                        .setAvailableInventory(0)
                        .setFreezeInventory(0)
                        .setTotalInbound(0)
                        .setTotalOutbound(0);
                baseMapper.insert(inventory);
                return inventory;
            }
            return dbInventory;
        }, (updateConsumer) -> {
            updateConsumer.setAvailableInventory(updateConsumer.getAvailableInventory() - num);
            updateConsumer.setFreezeInventory(updateConsumer.getFreezeInventory() + num);
        }, LocalLanguageEnum.INVENTORY_RECORD_TYPE_3);*/
        // ?????????SKU????????????????????????????????????????????????1
        if (StringUtils.isEmpty(warehouseCode)
                || StringUtils.isEmpty(sku)
                || Objects.isNull(num)
                || num < 1) {
            throw new CommonException("500", "????????????????????????");
        }
        try {
            // ????????????????????????????????????
            InventoryOccupy occupyInfo = this.inventoryOccupyService.getOccupyInfo(cusCode, warehouseCode, sku, invoiceNo);
            if (null != occupyInfo) {
                return 1;
            }
            // ???????????????SKU??????????????????
            LambdaQueryWrapper<Inventory> queryWrapper = Wrappers.lambdaQuery();
            queryWrapper.eq(Inventory::getWarehouseCode, warehouseCode);
            queryWrapper.eq(Inventory::getSku, sku);
            // ????????????????????????freeType????????????????????????????????????????????????????????????????????????????????????????????????
            if (null == freeType) {
                // >=
                queryWrapper.ge(Inventory::getAvailableInventory, num);
            }
            List<Inventory> list = this.list(queryWrapper);
            Inventory dbInventory = null;
            // ??????????????????????????????????????????????????????SKU??????????????????
            if (CollectionUtils.isNotEmpty(list)) {
                dbInventory = list.get(0);
            }
            // ??????????????????????????????????????????freeType?????????????????????????????????????????????
            if (null != freeType && null == dbInventory) {
                dbInventory = new Inventory();
                dbInventory.setCusCode(cusCode)
                        .setSku(sku)
                        .setWarehouseCode(warehouseCode)
                        .setTotalInventory(0)
                        .setAvailableInventory(0)
                        .setFreezeInventory(0)
                        .setTotalInbound(0)
                        .setTotalOutbound(0);
                // ?????????????????????id???dbInventory?????????
                baseMapper.insert(dbInventory);
            }
            // ????????????????????????
            if (null == dbInventory) {
                throw new CommonException("500", "[" + sku + "]??????????????????");
            }
            // ????????????????????????
            Inventory updateInventory = new Inventory();
            updateInventory.setWarehouseCode(dbInventory.getWarehouseCode());
            updateInventory.setSku(dbInventory.getSku());
            updateInventory.setTotalInventory(dbInventory.getTotalInventory());
            updateInventory.setAvailableInventory(dbInventory.getAvailableInventory());
            updateInventory.setFreezeInventory(dbInventory.getFreezeInventory());
            updateInventory.setTotalInbound(dbInventory.getTotalInbound());
            updateInventory.setTotalOutbound(dbInventory.getTotalOutbound());
            // 1.??????????????????2.??????????????????
            updateInventory.setAvailableInventory(dbInventory.getAvailableInventory() - num);
            updateInventory.setFreezeInventory(dbInventory.getFreezeInventory() + num);
            updateInventory.setId(dbInventory.getId());
            int update = baseMapper.updateById(updateInventory);
            if (update < 1) {
                throw new CommonException("500", "[" + sku + "]????????????????????????");
            }
            // ????????????????????????
            InventoryOccupy inventoryOccupy = new InventoryOccupy();
            inventoryOccupy.setCusCode(cusCode);
            inventoryOccupy.setWarehouseCode(warehouseCode);
            inventoryOccupy.setSku(sku);
            inventoryOccupy.setQuantity(num);
            inventoryOccupy.setReceiptNo(invoiceNo);
            inventoryOccupy.setInventoryId(dbInventory.getId());
            this.inventoryOccupyService.save(inventoryOccupy);
            // ????????????
            iInventoryRecordService.saveLogs(LocalLanguageEnum.INVENTORY_RECORD_TYPE_3.getKey(), dbInventory, updateInventory, invoiceNo, null, null, num, "");
            return update;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (e instanceof CommonException) {
                throw e;
            }
            throw new CommonException("500", "?????????????????????????????????" + invoiceNo + "????????????" + warehouseCode + "???SKU???" + sku + "??????????????????" + e.getMessage());
        }
    }

    @Transactional
    @Override
    public int unFreeze(String invoiceNo, String warehouseCode, String sku, Integer num, Integer freeType, String cusCode) {
        /*return this.doWorker(invoiceNo, warehouseCode, sku, num, (queryWrapperConsumer) -> {
                    if (null == freeType) {
                        // >=
                        queryWrapperConsumer.ge(Inventory::getFreezeInventory, num);
                    }
                }, null,
                (updateConsumer) -> {
                    updateConsumer.setAvailableInventory(updateConsumer.getAvailableInventory() + num);
                    updateConsumer.setFreezeInventory(updateConsumer.getFreezeInventory() - num);
                }, LocalLanguageEnum.INVENTORY_RECORD_TYPE_8);*/
        // ?????????SKU????????????????????????????????????????????????1
        if (StringUtils.isEmpty(warehouseCode)
                || StringUtils.isEmpty(sku)
                || Objects.isNull(num)
                || num < 1) {
            throw new CommonException("500", "????????????????????????");
        }
        try {
            InventoryOccupy occupyInfo = this.inventoryOccupyService.getOccupyInfo(cusCode, warehouseCode, sku, invoiceNo);
            if (null == occupyInfo) {
                // throw new CommonException("500", "?????????????????????????????????????????????????????????" + invoiceNo + "????????????" + warehouseCode + "???SKU???" + sku);
                // ??????????????????????????????????????????
                return 1;
            }
            // ??????????????????????????????
            if (!num.equals(occupyInfo.getQuantity())) {
                throw new CommonException("500", "?????????????????????????????????????????????????????????");
            }
            // ????????????????????????
            Inventory dbInventory = super.getById(occupyInfo.getInventoryId());
            if (null == dbInventory) {
                throw new CommonException("500", "?????????????????????????????????????????????????????????????????????");
            }
            Inventory updateInventory = new Inventory();
            // ???????????????????????????
            // 1.??????????????????2.??????????????????
            updateInventory.setAvailableInventory(dbInventory.getAvailableInventory() + num);
            updateInventory.setFreezeInventory(dbInventory.getFreezeInventory() - num);
            updateInventory.setId(dbInventory.getId());
            int update = baseMapper.updateById(updateInventory);
            if (update < 1) {
                throw new CommonException("500", "[" + sku + "]?????????????????????????????????????????????");
            }
            // ????????????????????????
            this.inventoryOccupyService.removeById(occupyInfo.getId());
            // ???????????????????????????????????????????????????????????????
            updateInventory.setWarehouseCode(dbInventory.getWarehouseCode());
            updateInventory.setSku(dbInventory.getSku());
            updateInventory.setTotalInventory(dbInventory.getTotalInventory());
            updateInventory.setTotalInbound(dbInventory.getTotalInbound());
            updateInventory.setTotalOutbound(dbInventory.getTotalOutbound());
            // ????????????
            iInventoryRecordService.saveLogs(LocalLanguageEnum.INVENTORY_RECORD_TYPE_8.getKey(), dbInventory, updateInventory, invoiceNo, null, null, num, "");
            return update;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (e instanceof CommonException) {
                throw e;
            }
            throw new CommonException("500", "???????????????????????????" + invoiceNo + "????????????" + warehouseCode + "???SKU???" + sku + "??????????????????" + e.getMessage());
        }
    }

    @Transactional
    @Override
    public int deduction(String invoiceNo, String warehouseCode, String sku, Integer num, String cusCode) {
        /*return this.doWorker(invoiceNo, warehouseCode, sku, num, (queryWrapperConsumer) -> {
                    // >=
                    queryWrapperConsumer.ge(Inventory::getFreezeInventory, num);
                }, null,
                (updateConsumer) -> {
                    updateConsumer.setTotalInventory(updateConsumer.getTotalInventory() - num);
                    updateConsumer.setFreezeInventory(updateConsumer.getFreezeInventory() - num);
                    updateConsumer.setTotalOutbound(updateConsumer.getTotalOutbound() + num);
                }, LocalLanguageEnum.INVENTORY_RECORD_TYPE_2);*/
        if (StringUtils.isEmpty(warehouseCode)
                || StringUtils.isEmpty(sku)
                || Objects.isNull(num)
                || num < 1) {
            throw new CommonException("500", "????????????????????????");
        }
        try {
            InventoryOccupy occupyInfo = this.inventoryOccupyService.getOccupyInfo(cusCode, warehouseCode, sku, invoiceNo);
            if (null == occupyInfo) {
                // throw new CommonException("500", "?????????????????????????????????????????????????????????" + invoiceNo + "????????????" + warehouseCode + "???SKU???" + sku);
                // ?????????????????????????????????????????????
                return 1;
            }
            // ??????????????????????????????
            if (!num.equals(occupyInfo.getQuantity())) {
                throw new CommonException("500", "?????????????????????????????????????????????????????????");
            }
            // ????????????????????????
            Inventory dbInventory = super.getById(occupyInfo.getInventoryId());
            if (null == dbInventory) {
                throw new CommonException("500", "?????????????????????????????????????????????????????????????????????");
            }
            Inventory updateInventory = new Inventory();
            // 1.??????????????????2.?????????????????????3.??????????????????
            updateInventory.setTotalInventory(dbInventory.getTotalInventory() - num);
            updateInventory.setFreezeInventory(dbInventory.getFreezeInventory() - num);
            updateInventory.setTotalOutbound(dbInventory.getTotalOutbound() + num);
            updateInventory.setId(dbInventory.getId());
            int update = baseMapper.updateById(updateInventory);
            if (update < 1) {
                throw new CommonException("500", "[" + sku + "]?????????????????????????????????????????????");
            }
            // ????????????????????????
            this.inventoryOccupyService.removeById(occupyInfo.getId());
            // ???????????????????????????????????????????????????????????????
            updateInventory.setWarehouseCode(dbInventory.getWarehouseCode());
            updateInventory.setSku(dbInventory.getSku());
            updateInventory.setAvailableInventory(dbInventory.getAvailableInventory());
            updateInventory.setTotalInbound(dbInventory.getTotalInbound());
            // ????????????
            iInventoryRecordService.saveLogs(LocalLanguageEnum.INVENTORY_RECORD_TYPE_2.getKey(), dbInventory, updateInventory, invoiceNo, null, null, num, "");
            return update;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (e instanceof CommonException) {
                throw e;
            }
            throw new CommonException("500", "?????????????????????????????????" + invoiceNo + "????????????" + warehouseCode + "???SKU???" + sku + "??????????????????" + e.getMessage());
        }
    }

    @Transactional
    @Override
    public int unDeduction(String invoiceNo, String warehouseCode, String sku, Integer num, String cusCode) {
        /*return this.doWorker(invoiceNo, warehouseCode, sku, num, (queryWrapperConsumer) -> {
                }, null,
                (updateConsumer) -> {
                    updateConsumer.setTotalInventory(updateConsumer.getTotalInventory() + num);
                    updateConsumer.setFreezeInventory(updateConsumer.getFreezeInventory() + num);
                    updateConsumer.setTotalOutbound(updateConsumer.getTotalOutbound() - num);
                }, LocalLanguageEnum.INVENTORY_RECORD_TYPE_1);*/
        if (StringUtils.isEmpty(warehouseCode)
                || StringUtils.isEmpty(sku)
                || Objects.isNull(num)
                || num < 1) {
            throw new CommonException("500", "??????????????????????????????");
        }
        try {
            // ???????????????SKU??????????????????
            LambdaQueryWrapper<Inventory> queryWrapper = Wrappers.lambdaQuery();
            queryWrapper.eq(Inventory::getWarehouseCode, warehouseCode);
            queryWrapper.eq(Inventory::getSku, sku);
            List<Inventory> list = this.list(queryWrapper);
            Inventory dbInventory = null;
            // ??????????????????????????????????????????????????????SKU??????????????????
            if (CollectionUtils.isNotEmpty(list)) {
                dbInventory = list.get(0);
            }
            // ????????????????????????
            if (null == dbInventory) {
                throw new CommonException("500", "[" + sku + "]????????????????????????");
            }
            // ????????????????????????
            Inventory updateInventory = new Inventory();
            // 1.???????????????2.??????????????????3.???????????????
            updateInventory.setTotalInventory(dbInventory.getTotalInventory() + num);
            updateInventory.setFreezeInventory(dbInventory.getFreezeInventory() + num);
            updateInventory.setTotalOutbound(dbInventory.getTotalOutbound() - num);
            updateInventory.setId(dbInventory.getId());
            int update = baseMapper.updateById(updateInventory);
            if (update < 1) {
                throw new CommonException("500", "[" + sku + "]???????????????????????????????????????????????????");
            }
            // ????????????????????????
            InventoryOccupy inventoryOccupy = new InventoryOccupy();
            inventoryOccupy.setCusCode(cusCode);
            inventoryOccupy.setWarehouseCode(warehouseCode);
            inventoryOccupy.setSku(sku);
            inventoryOccupy.setQuantity(num);
            inventoryOccupy.setReceiptNo(invoiceNo);
            inventoryOccupy.setInventoryId(dbInventory.getId());
            this.inventoryOccupyService.save(inventoryOccupy);
            // ???????????????????????????????????????????????????????????????
            updateInventory.setWarehouseCode(dbInventory.getWarehouseCode());
            updateInventory.setSku(dbInventory.getSku());
            updateInventory.setAvailableInventory(dbInventory.getAvailableInventory());
            updateInventory.setTotalInbound(dbInventory.getTotalInbound());
            // ????????????
            iInventoryRecordService.saveLogs(LocalLanguageEnum.INVENTORY_RECORD_TYPE_1.getKey(), dbInventory, updateInventory, invoiceNo, null, null, num, "");
            return update;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            if (e instanceof CommonException) {
                throw e;
            }
            throw new CommonException("500", "???????????????????????????????????????" + invoiceNo + "????????????" + warehouseCode + "???SKU???" + sku + "??????????????????" + e.getMessage());
        }
    }

    /**
     * ??????????????????????????????OMS?????????????????????????????????????????????WMS
     *
     * @param inventoryAdjustmentDTO
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void adjustment(InventoryAdjustmentDTO inventoryAdjustmentDTO) {

        String sku = inventoryAdjustmentDTO.getSku();
        String warehouseCode = inventoryAdjustmentDTO.getWarehouseCode();
        Integer quantity = inventoryAdjustmentDTO.getQuantity();

        AssertUtil.isTrue(quantity != null && quantity > 0, warehouseCode + "???[" + sku + "]????????????????????????1");

        String adjustment = inventoryAdjustmentDTO.getAdjustment();

        LocalLanguageEnum localLanguageEnum = LocalLanguageEnum.getLocalLanguageEnum(LocalLanguageTypeEnum.INVENTORY_RECORD_TYPE, adjustment);
        boolean increase = INVENTORY_RECORD_TYPE_5 == localLanguageEnum;
        boolean reduce = LocalLanguageEnum.INVENTORY_RECORD_TYPE_6 == localLanguageEnum;
        AssertUtil.isTrue(increase || reduce, "??????????????????");
        quantity = increase ? quantity : -quantity;
        if (null != inventoryAdjustmentDTO.getFormReturn() && inventoryAdjustmentDTO.getFormReturn())
            localLanguageEnum = INVENTORY_RECORD_TYPE_7;
        Lock lock = new ReentrantLock(true);
        try {
            lock.lock();

            Inventory before = this.getOne(new QueryWrapper<Inventory>().lambda().eq(Inventory::getSku, sku).eq(Inventory::getWarehouseCode, warehouseCode));
            //AssertUtil.notNull(before, warehouseCode + "?????????[" + sku + "]????????????");
            if (increase && null == before) {
                //String loginSellerCode = Optional.ofNullable(remoteComponent.getLoginUserInfo()).map(SysUser::getSellerCode).orElseThrow(() -> new BaseException("????????????????????????!"));
                String loginSellerCode = inventoryAdjustmentDTO.getSellerCode();
                Integer addQut = inventoryAdjustmentDTO.getQuantity();
                Inventory inventory = new Inventory();
                inventory.setSku(inventoryAdjustmentDTO.getSku())
                        .setWarehouseCode(inventoryAdjustmentDTO.getWarehouseCode())
                        .setAvailableInventory(addQut)
                        .setCusCode(loginSellerCode)

                        .setTotalInventory(addQut)
                        .setTotalInbound(addQut);
                baseMapper.insert(inventory);

                log.info(warehouseCode + "?????????[" + sku + "]???????????? ??????sku ?????? {}", JSONObject.toJSONString(inventory));
                before = new Inventory();
                BeanUtils.copyProperties(inventory, before);
                before.setTotalInventory(0).setTotalInbound(0);
                // ??????????????????
                //iInventoryRecordService.saveLogs(localLanguageEnum.getKey(), before, inventory, quantity);
                iInventoryRecordService.saveLogs(localLanguageEnum.getKey(), before, inventory, quantity, inventoryAdjustmentDTO.getRelevanceNumber());
                return;
            }
            int afterTotalInventory = before.getTotalInventory() + quantity;
            int afterAvailableInventory = before.getAvailableInventory() + quantity;
            AssertUtil.isTrue(afterTotalInventory >= 0 && afterAvailableInventory >= 0, warehouseCode + "???[" + sku + "]??????????????????????????????[" + before.getAvailableInventory() + "]");

            Inventory after = new Inventory();
            after.setId(before.getId()).setCusCode(before.getCusCode()).setSku(sku).setWarehouseCode(warehouseCode).setTotalInventory(afterTotalInventory).setAvailableInventory(afterAvailableInventory);
           if (inventoryAdjustmentDTO.getRemark()!=null){
               after.setRemark(inventoryAdjustmentDTO.getRemark());
           }
           if (inventoryAdjustmentDTO.getRelevanceNumber()!=null)
            after.setRelevanceNumber(inventoryAdjustmentDTO.getRelevanceNumber());

            this.updateById(after);
            // ??????????????????
            iInventoryRecordService.saveLogs(localLanguageEnum.getKey(), before, after, quantity, inventoryAdjustmentDTO.getRelevanceNumber());
            adjustInventoryCK1(inventoryAdjustmentDTO);
        } finally {
            lock.unlock();
        }
    }

    /**
     * ???????????? ??????CK1
     */
    private void adjustInventoryCK1(InventoryAdjustmentDTO inventoryAdjustmentDTO) {
        LoginUser loginUser=SecurityUtils.getLoginUser();
        CompletableFuture<HttpRequestDto> httpRequestDtoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            HttpRequestSyncDTO httpRequestDto = new HttpRequestSyncDTO();
            httpRequestDto.setMethod(HttpMethod.POST);
            httpRequestDto.setBinary(false);
            httpRequestDto.setBody(CkAdjustInventoryDTO.createCkAdjustInventoryDTO(inventoryAdjustmentDTO));
            httpRequestDto.setHeaders(DomainInterceptorUtil.genSellerCodeHead(inventoryAdjustmentDTO.getSellerCode()));
            httpRequestDto.setUri(DomainEnum.Ck1OpenAPIDomain.wrapper(ckConfig.getAdjustInventoryUrl()));
            httpRequestDto.setRemoteTypeEnum(RemoteConstant.RemoteTypeEnum.ADJUST_INVENTORY);
            if (loginUser!=null){
                httpRequestDto.setUserName(loginUser.getUsername());

            }
            R<HttpResponseVO> rmi = htpRmiFeignService.rmiSync(httpRequestDto);
            log.info("?????????CK1???????????????{} ?????? {}", httpRequestDto, JSONObject.toJSONString(rmi));
            HttpResponseVO dataAndException = R.getDataAndException(rmi);
            // dataAndException.checkStatus();
            return httpRequestDto;
        }, ckThreadPool);
        try {
            HttpRequestDto httpRequestDto = httpRequestDtoCompletableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
    }

    private int doWorker(String invoiceNo, String warehouseCode, String sku, Integer num,
                         Consumer<LambdaQueryWrapper<Inventory>> queryWrapperConsumer,
                         Function<Inventory, Inventory> beforeHandler,
                         Consumer<Inventory> updateConsumer,
                         LocalLanguageEnum type) {
        // ?????????SKU????????????????????????????????????????????????1
        if (StringUtils.isEmpty(warehouseCode)
                || StringUtils.isEmpty(sku)
                || Objects.isNull(num)
                || num < 1) {
            throw new CommonException("999", "????????????2");
        }
        // ???????????????SKU??????????????????
        LambdaQueryWrapper<Inventory> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(Inventory::getWarehouseCode, warehouseCode);
        queryWrapper.eq(Inventory::getSku, sku);
        // ????????????????????????freeType????????????????????????????????????????????????????????????????????????????????????????????????
        queryWrapperConsumer.accept(queryWrapper);
        List<Inventory> list = this.list(queryWrapper);
        Inventory dbInventory = null;
        // ??????????????????????????????????????????????????????SKU??????????????????
        if (CollectionUtils.isNotEmpty(list)) {
            dbInventory = list.get(0);
        }
        // ??????????????????????????????????????????freeType?????????????????????????????????????????????
        if (null != beforeHandler) {
            dbInventory = beforeHandler.apply(dbInventory);
        }
        // ????????????????????????
        if (null == dbInventory) {
            throw new CommonException("999", "[" + sku + "]????????????");
        }
        // ????????????????????????
        Inventory updateInventory = new Inventory();
        updateInventory.setWarehouseCode(dbInventory.getWarehouseCode());
        updateInventory.setSku(dbInventory.getSku());
        updateInventory.setTotalInventory(dbInventory.getTotalInventory());
        updateInventory.setAvailableInventory(dbInventory.getAvailableInventory());
        updateInventory.setFreezeInventory(dbInventory.getFreezeInventory());
        updateInventory.setTotalInbound(dbInventory.getTotalInbound());
        updateInventory.setTotalOutbound(dbInventory.getTotalOutbound());
        updateInventory.setId(dbInventory.getId());
        updateConsumer.accept(updateInventory);
        int update = baseMapper.updateById(updateInventory);
        if (update < 1) {
            throw new CommonException("999", "[" + sku + "]??????????????????");
        }
        // ????????????
        iInventoryRecordService.saveLogs(type.getKey(), dbInventory, updateInventory, invoiceNo, null, null, num, "");
        return update;
    }

    @Override
    public List<Inventory> getWarehouseSku() {
        LambdaQueryWrapper<Inventory> query = Wrappers.lambdaQuery();
        query.gt(Inventory::getAvailableInventory, 0);
        return this.list(query);
    }

    @Override
    public List<QueryFinishListVO> queryFinishList(QueryFinishListDTO queryFinishListDTO) {
        return baseMapper.queryFinishList(queryFinishListDTO);
    }

    /**
     * ???????????? -?????????????????? ????????????????????? ???????????????
     * ??????????????? ??? ?????? ????????????
     *
     * @param warehouseCode
     * @param sku
     * @return
     */
    @Override
    public List<SkuInventoryAgeVo> queryInventoryAgeBySku(String warehouseCode, String sku) {
        Inventory inventory = baseMapper.selectOne(Wrappers.<Inventory>lambdaQuery().eq(Inventory::getSku, sku).eq(Inventory::getWarehouseCode, warehouseCode));
        if (null == inventory) return new ArrayList<>();
        int availableInventory = Optional.ofNullable(inventory.getAvailableInventory()).orElse(0);
        log.info("???????????? {}", availableInventory);
        if (availableInventory <= 0) return new ArrayList<>();
        //?????? ??????????????????
        List<InventoryRecord> inventoryRecords = iInventoryRecordService.getBaseMapper().selectList(Wrappers.<InventoryRecord>lambdaQuery()
                .eq(InventoryRecord::getWarehouseCode, warehouseCode)
                .eq(InventoryRecord::getSku, sku)
                .in(InventoryRecord::getType, INVENTORY_RECORD_TYPE_1.getKey(), INVENTORY_RECORD_TYPE_5.getKey(), INVENTORY_RECORD_TYPE_7.getKey())
                .orderByDesc(BaseEntity::getCreateTime));
        SkuInventoryAgeVo skuInventoryAgeVo = new SkuInventoryAgeVo();
        skuInventoryAgeVo.setSku(sku);
        if (CollectionUtils.isEmpty(inventoryRecords)) return new ArrayList<>();
        //???????????????????????? + ????????????
        //??????????????? ???????????? ??????  1 5 7 ??? ?????????
        Queue<SkuInventoryAgeVo> input = inventoryRecords.stream().map(x -> {
            SkuInventoryAgeVo skuInventoryAge = new SkuInventoryAgeVo();
            //????????????????????? ??????
            skuInventoryAge.setSku(x.getSku()).setWarehouseCode(x.getWarehouseCode())
                    .setNum(x.getQuantity()).setType(x.getType())

                    .setCreateTime(x.getCreateTime());
            return skuInventoryAge;
        }).filter(x -> x.getNum() != null && x.getNum() > 0).collect(Collectors.toCollection(LinkedBlockingDeque::new));

        List<SkuInventoryAgeVo> skuInventoryAgeVos = new ArrayList<>(input.size());
        processingQuantity(input, availableInventory, skuInventoryAgeVos);
        Collection<SkuInventoryAgeVo> values = skuInventoryAgeVos.stream().collect(Collectors.toMap(SkuInventoryAgeVo::getStorageAge, x -> x, (x1, x2) -> {
            x1.setNum(x1.getNum() + x2.getNum());
            return x1;
        })).values();

        return new ArrayList<>(values);
    }

    /**
     * ????????????
     *
     * @param queue
     * @param
     */
    public static void processingQuantity(Queue<SkuInventoryAgeVo> queue, int totalNum, List<SkuInventoryAgeVo> resultList) {
        SkuInventoryAgeVo element = queue.poll();
        if (null == element) {
            log.error("??????????????????,???????????? ?????? ????????????");
            return;
        }
        Integer nowNum = element.getNum();
        if (nowNum < totalNum) {
            resultList.add(element);
            totalNum -= nowNum;
            processingQuantity(queue, totalNum, resultList);
        } else {
            element.setNum(nowNum);
            resultList.add(element);
        }
    }


}