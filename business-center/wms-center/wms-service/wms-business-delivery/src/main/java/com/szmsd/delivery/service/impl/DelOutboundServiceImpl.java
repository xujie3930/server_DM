package com.szmsd.delivery.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.IoUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.szmsd.bas.api.client.BasSubClientService;
import com.szmsd.bas.api.domain.BasAttachment;
import com.szmsd.bas.api.domain.BasEmployees;
import com.szmsd.bas.api.domain.dto.AttachmentDTO;
import com.szmsd.bas.api.domain.dto.AttachmentDataDTO;
import com.szmsd.bas.api.domain.dto.BasAttachmentQueryDTO;
import com.szmsd.bas.api.domain.vo.BasRegionSelectListVO;
import com.szmsd.bas.api.enums.AttachmentTypeEnum;
import com.szmsd.bas.api.feign.*;
import com.szmsd.bas.api.service.BasWarehouseClientService;
import com.szmsd.bas.api.service.BaseProductClientService;
import com.szmsd.bas.api.service.SerialNumberClientService;
import com.szmsd.bas.constant.SerialNumberConstant;
import com.szmsd.bas.domain.BasSeller;
import com.szmsd.bas.domain.BasWarehouse;
import com.szmsd.bas.domain.BaseProduct;
import com.szmsd.bas.dto.BaseProductConditionQueryDto;
import com.szmsd.bas.dto.EmailDto;
import com.szmsd.bas.dto.EmailObjectDto;
import com.szmsd.bas.plugin.vo.BasSubWrapperVO;
import com.szmsd.bas.vo.BasSellerInfoVO;
import com.szmsd.chargerules.api.feign.ChargeFeignService;
import com.szmsd.chargerules.api.feign.OperationFeignService;
import com.szmsd.chargerules.domain.BasProductService;
import com.szmsd.common.core.constant.HttpStatus;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.exception.web.BaseException;
import com.szmsd.common.core.utils.DateUtils;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import com.szmsd.common.core.utils.bean.BeanUtils;
import com.szmsd.common.security.utils.SecurityUtils;
import com.szmsd.delivery.config.AsyncThreadObject;
import com.szmsd.delivery.convert.DelOutboundChargeConvert;
import com.szmsd.delivery.domain.*;
import com.szmsd.delivery.dto.*;
import com.szmsd.delivery.enums.*;
import com.szmsd.delivery.event.DelOutUpdWeightEvent;
import com.szmsd.delivery.event.DelOutboundOperationLogEnum;
import com.szmsd.delivery.event.EventUtil;
import com.szmsd.delivery.mapper.*;
import com.szmsd.delivery.service.*;
import com.szmsd.delivery.service.wrapper.*;
import com.szmsd.delivery.util.PackageInfo;
import com.szmsd.delivery.util.PackageUtil;
import com.szmsd.delivery.util.Utils;
import com.szmsd.delivery.vo.*;
import com.szmsd.finance.dto.QueryChargeDto;
import com.szmsd.finance.vo.QueryChargeVO;
import com.szmsd.http.api.feign.HtpOutboundFeignService;
import com.szmsd.http.api.feign.HtpPricedProductFeignService;
import com.szmsd.http.api.service.IHtpOutboundClientService;
import com.szmsd.http.api.service.IHtpRmiClientService;
import com.szmsd.http.dto.DirectExpressOrderApiDTO;
import com.szmsd.http.dto.HttpRequestDto;
import com.szmsd.http.dto.ShipmentCancelRequestDto;
import com.szmsd.http.dto.ShipmentOrderResult;
import com.szmsd.http.enums.DomainEnum;
import com.szmsd.http.vo.HttpResponseVO;
import com.szmsd.http.vo.PricedProductInfo;
import com.szmsd.http.vo.ResponseVO;
import com.szmsd.inventory.api.service.InventoryFeignClientService;
import com.szmsd.inventory.domain.dto.InventoryAvailableQueryDto;
import com.szmsd.inventory.domain.dto.InventoryOperateDto;
import com.szmsd.inventory.domain.dto.InventoryOperateListDto;
import com.szmsd.inventory.domain.dto.QueryFinishListDTO;
import com.szmsd.inventory.domain.vo.InventoryAvailableListVO;
import com.szmsd.inventory.domain.vo.QueryFinishListVO;
import com.szmsd.putinstorage.domain.dto.AttachmentFileDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.BatchResult;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Base64Utils;
import org.springframework.util.StopWatch;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * ????????? ???????????????
 * </p>
 *
 * @author asd
 * @since 2021-03-05
 */
@Service
@Slf4j
public class DelOutboundServiceImpl extends ServiceImpl<DelOutboundMapper, DelOutbound> implements IDelOutboundService {
    private Logger logger = LoggerFactory.getLogger(DelOutboundServiceImpl.class);

    @Value("${spring.application.name}")
    private String applicationName;

    @Autowired
    private IDelOutboundAddressService delOutboundAddressService;
    @Autowired
    private IDelOutboundDetailService delOutboundDetailService;
    @Autowired
    private SerialNumberClientService serialNumberClientService;
    @Autowired
    private BaseProductClientService baseProductClientService;
    @Autowired
    private InventoryFeignClientService inventoryFeignClientService;
    @Autowired
    private IHtpOutboundClientService htpOutboundClientService;
    @SuppressWarnings({"all"})
    @Autowired
    private RemoteAttachmentService remoteAttachmentService;
    @Autowired
    private IDelOutboundCompletedService delOutboundCompletedService;
    @Autowired
    private IDelOutboundChargeService delOutboundChargeService;
    @Autowired
    private IDelOutboundAsyncService delOutboundAsyncService;
    @SuppressWarnings({"all"})
    @Autowired
    private OperationFeignService operationFeignService;
    @Autowired
    private IDelOutboundPackingService delOutboundPackingService;
    @Resource
    private BasRegionFeignService basRegionFeignService;
    @Autowired
    private IDelOutboundCombinationService delOutboundCombinationService;
    @Autowired
    private IDelOutboundExceptionService delOutboundExceptionService;
    @Autowired
    private IDelOutboundDocService delOutboundDocService;

    @Autowired
    private BasSubClientService basSubClientService;
    @Autowired
    @Lazy
    private IDelOutboundBringVerifyService delOutboundBringVerifyService;

    @Autowired
    private IHtpRmiClientService htpRmiClientService;

    @Autowired
    private DelOutboundTarckOnMapper delOutboundTarckOnMapper;

    @Autowired
    private HtpOutboundFeignService htpOutboundFeignService;
    @Autowired
    private BasWarehouseClientService basWarehouseClientService;

    @Autowired
    private DelBasFileMapper basFileMapper;

    @Autowired
    private DelOutboundTarckErrorMapper delOutboundTarckErrorMapper;

    @Autowired
    private BasFeignService basFeignService;
    @Autowired
    private EmailFeingService emailFeingService;

    @Autowired
    private HtpPricedProductFeignService htpPricedProductFeignService;
    @Resource
    private ChargeFeignService chargeFeignService;
    @Resource
    private BasSellerFeignService basSellerFeignService;

    @Autowired
    private BasShipmenRulesService basShipmenRulesService;

    @Autowired
    private BasTrackingPushMapper basTrackingPushMapper;

    @Autowired
    private OfflineDeliveryImportMapper offlineDeliveryImportMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private IDelOutboundThirdPartyService delOutboundThirdPartyService;

    /**
     * ?????????????????????
     *
     * @param id ???????????????ID
     * @return ???????????????
     */
    @Override
    public DelOutboundVO selectDelOutboundById(String id) {
        DelOutbound delOutbound = baseMapper.selectById(id);
        return this.selectDelOutboundVO(delOutbound);
    }

    @Override
    public DelOutboundVO selectDelOutboundByOrderNo(String orderNo) {
        LambdaQueryWrapper<DelOutbound> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(DelOutbound::getOrderNo, orderNo);
        DelOutbound delOutbound = super.getOne(queryWrapper);
        return this.selectDelOutboundVO(delOutbound);
    }

    @Override
    public DelOutboundVO selectDelOutboundByOrderNous(String orderNo,int operationType) {
        LambdaQueryWrapper<DelOutbound> queryWrapper = Wrappers.lambdaQuery();
        if (operationType==1||operationType==3){
            queryWrapper.isNotNull(DelOutbound::getShipmentsTime);
            queryWrapper.isNotNull(DelOutbound::getTrackingTime);
        }

//        queryWrapper.eq(DelOutbound::getOrderNo, orderNo).or().eq(DelOutbound::getRefNo,orderNo).or().eq(DelOutbound::getTrackingNo,orderNo);
//        queryWrapper.and((wrapper)->{
//
//
//
//        });

        queryWrapper.and(wrapper ->
                wrapper.eq(DelOutbound::getOrderNo, orderNo).or().eq(DelOutbound::getRefNo,orderNo).or().eq(DelOutbound::getTrackingNo,orderNo)
        );


        queryWrapper.last("LIMIT 1");
        DelOutbound delOutbound = super.getOne(queryWrapper);
        if (operationType==3&&delOutbound==null){
            return null;
        }
        return this.selectDelOutboundVO(delOutbound);
    }

    @Override
    public R<DelOutboundThirdPartyVO> getInfoForThirdParty(DelOutboundVO vo) {
        LambdaQueryWrapper<DelOutbound> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(DelOutbound::getSellerCode, vo.getSellerCode());
        queryWrapper.eq(DelOutbound::getOrderNo, vo.getOrderNo());
        DelOutbound delOutbound = super.getOne(queryWrapper);
        if(delOutbound == null){
            throw new CommonException("400", "Order does not exist");
        }

        String state = delOutbound.getState();

        if(DelOutboundStateEnum.AUDIT_FAILED.getCode().equals(state)){
            throw new CommonException("400", delOutbound.getExceptionMessage());
        }

        String amazonLogisticsRouteId1 = delOutbound.getAmazonLogisticsRouteId();
        String amazonReferenceId = delOutbound.getAmazonReferenceId();

        if(StringUtils.isEmpty(amazonLogisticsRouteId1) && StringUtils.isNotEmpty(amazonReferenceId)){
            return R.failed(200,"The order number is being obtained");
        }

        DelOutboundThirdPartyVO delOutboundThirdPartyVO =
                BeanMapperUtil.map(delOutbound, DelOutboundThirdPartyVO.class);

        Map<String, List<BasSubWrapperVO>> listMap = this.basSubClientService.getSub("065");
        Map<String, String> map = listMap.get("065").stream().collect(Collectors.toMap(BasSubWrapperVO::getSubValue,
                BasSubWrapperVO::getSubName, (key1, key2) -> key2));
        delOutboundThirdPartyVO.setStateName(map.get(delOutboundThirdPartyVO.getState()));

        //??????amazonReferenceId?????????,??????amazonLogisticsRouteId1 ?????????amazonLogisticsRouteId1???????????? 285?????????
        //??????amazonReferenceId?????? ??????tracking_no ???????????? amazonLogisticsRouteId1??????????????????
        if (StringUtils.isNotEmpty(amazonReferenceId)) {
            delOutboundThirdPartyVO.setTrackingNo(amazonLogisticsRouteId1);
        }

        return R.ok(delOutboundThirdPartyVO);
    }


    private DelOutboundVO selectDelOutboundVO(DelOutbound delOutbound) {
        if (Objects.isNull(delOutbound)) {
            throw new CommonException("400", "???????????????");
        }
        //??????????????????????????????

        DelOutboundVO delOutboundVO = BeanMapperUtil.map(delOutbound, DelOutboundVO.class);

        if (Optional.ofNullable(delOutbound.getShipmentsTime()).isPresent()&&Optional.ofNullable(delOutbound.getTrackingTime()).isPresent()){
            Date a=new Date(System.currentTimeMillis());
            long s=(a.getTime()-delOutbound.getShipmentsTime().getTime());
            TimeUnit time=TimeUnit.DAYS;
            long day1=time.convert(s,TimeUnit.MILLISECONDS);

            long q=(a.getTime()-delOutbound.getTrackingTime().getTime());

            long day2=time.convert(q,TimeUnit.MILLISECONDS);
            delOutboundVO.setDelDays(day1);
            delOutboundVO.setTrackingDays(day2);

            //???????????????????????? ???????????????????????????
            if (Optional.ofNullable(delOutbound.getShipmentRule()).isPresent()){

                Map  mapSettings=baseMapper.selectQuerySettings(delOutbound.getShipmentRule());
                logger.info("???????????????{}",mapSettings);
                logger.info("?????????????????????{}",delOutbound.getOrderNo());
                if (mapSettings!=null) {
                    //????????????????????????
                    Long queryseShipmentDays = Long.valueOf(mapSettings.get("shipmentDays").toString());
                    //????????????????????????
                    Long querysetrackStayDays = Long.valueOf(mapSettings.get("trackStayDays").toString());
                    delOutboundVO.setQueryseShipmentDays(queryseShipmentDays);
                    delOutboundVO.setQuerysetrackStayDays(querysetrackStayDays);
                    if ( queryseShipmentDays>delOutboundVO.getDelDays() && querysetrackStayDays>delOutboundVO.getTrackingDays() ) {
                        delOutboundVO.setCheckFlag(0L);
                    } else {
                        delOutboundVO.setCheckFlag(1L);
                    }
                }else {
                    delOutboundVO.setCheckFlag(1L);
                }

            }



        }
        String orderNo = delOutbound.getOrderNo();
        DelOutboundAddress delOutboundAddress = delOutboundAddressService.getByOrderNo(orderNo);
        if (Objects.nonNull(delOutboundAddress)) {
            delOutboundVO.setAddress(BeanMapperUtil.map(delOutboundAddress, DelOutboundAddressVO.class));
        }
        List<DelOutboundDetail> delOutboundDetailList = delOutboundDetailService.listByOrderNo(orderNo);
        if (CollectionUtils.isNotEmpty(delOutboundDetailList)) {
            List<DelOutboundDetailVO> detailDtos = new ArrayList<>(delOutboundDetailList.size());
            List<String> skus = new ArrayList<>(delOutboundDetailList.size());
            for (DelOutboundDetail detail : delOutboundDetailList) {
                detailDtos.add(BeanMapperUtil.map(detail, DelOutboundDetailVO.class));
                skus.add(detail.getSku());
            }

            if(DelOutboundOrderTypeEnum.PACKAGE_TRANSFER.getCode().equals(delOutbound.getOrderType())){
                //??????????????????????????????????????????????????????


            }else if(DelOutboundOrderTypeEnum.COLLECTION.getCode().equals(delOutbound.getOrderType())){
                //?????????sku?????????
                BaseProductConditionQueryDto conditionQueryDto = new BaseProductConditionQueryDto();
                conditionQueryDto.setSkus(skus);
                conditionQueryDto.setSellerCode(delOutbound.getSellerCode());
                List<BaseProduct> baseProductList = this.baseProductClientService.queryProductList(conditionQueryDto);
                if (CollectionUtils.isNotEmpty(baseProductList)) {
                    Map<String, BaseProduct>  baseProductMap = baseProductList.stream().collect(Collectors.toMap(BaseProduct::getCode, v -> v, (a, b) -> a));
                    for (DelOutboundDetailVO vo : detailDtos) {
                        BaseProduct available = baseProductMap.get(vo.getSku());
                        if (null != available) {
                            BeanMapperUtil.map(available, vo);
                        }
                    }
                }
            }else {
                InventoryAvailableQueryDto inventoryAvailableQueryDto = new InventoryAvailableQueryDto();
                inventoryAvailableQueryDto.setWarehouseCode(delOutbound.getWarehouseCode());
                inventoryAvailableQueryDto.setCusCode(delOutbound.getSellerCode());
                inventoryAvailableQueryDto.setSkus(skus);
                // ???????????????0????????????????????????
                inventoryAvailableQueryDto.setQueryType(2);
               /* // ???????????????
                if (DelOutboundOrderTypeEnum.COLLECTION.getCode().equals(delOutbound.getOrderType())) {
                    inventoryAvailableQueryDto.setSource("084002");
                }*/
                List<InventoryAvailableListVO> availableList = this.inventoryFeignClientService.queryAvailableList2(inventoryAvailableQueryDto);
                Map<String, InventoryAvailableListVO> availableMap = new HashMap<>();
                if (CollectionUtils.isNotEmpty(availableList)) {
                    for (InventoryAvailableListVO vo : availableList) {
                        availableMap.put(vo.getSku(), vo);
                    }
                }
                for (DelOutboundDetailVO vo : detailDtos) {
                    InventoryAvailableListVO available = availableMap.get(vo.getSku());
                    if (null != available) {
                        BeanMapperUtil.map(available, vo);
                    }
                }
            }




            delOutboundVO.setDetails(detailDtos);
        }
        // ????????????
        if (DelOutboundOrderTypeEnum.BATCH.getCode().equals(delOutbound.getOrderType())) {
            // ??????????????????
            delOutboundVO.setPackings(this.delOutboundPackingService.listByOrderNo(orderNo, DelOutboundPackingTypeConstant.TYPE_1));
            // ??????????????????
            Integer containerState = delOutbound.getContainerState();
            if (null == containerState) {
                containerState = DelOutboundConstant.CONTAINER_STATE_0;
            }
            if (DelOutboundConstant.CONTAINER_STATE_1 == containerState) {
                delOutboundVO.setContainerList(this.delOutboundPackingService.listByOrderNo(orderNo, DelOutboundPackingTypeConstant.TYPE_2));
            }
        }
        // ???????????????SKU
        if (DelOutboundOrderTypeEnum.NEW_SKU.getCode().equals(delOutbound.getOrderType())
                || DelOutboundOrderTypeEnum.SPLIT_SKU.getCode().equals(delOutbound.getOrderType())) {
            delOutboundVO.setCombinations(this.delOutboundCombinationService.listByOrderNo(orderNo));
            // ??????SKU????????????
            if (DelOutboundOrderTypeEnum.SPLIT_SKU.getCode().equals(delOutbound.getOrderType())) {
                InventoryAvailableQueryDto inventoryAvailableQueryDto = new InventoryAvailableQueryDto();
                inventoryAvailableQueryDto.setWarehouseCode(delOutbound.getWarehouseCode());
                inventoryAvailableQueryDto.setCusCode(delOutbound.getSellerCode());
                List<String> skus = new ArrayList<>();
                skus.add(delOutbound.getNewSku());
                inventoryAvailableQueryDto.setSkus(skus);
                // ???????????????0????????????????????????
                inventoryAvailableQueryDto.setQueryType(2);
                List<InventoryAvailableListVO> availableList = this.inventoryFeignClientService.queryAvailableList2(inventoryAvailableQueryDto);
                if (CollectionUtils.isNotEmpty(availableList)) {
                    InventoryAvailableListVO availableListVO = availableList.get(0);
                    if (null != availableListVO) {
                        delOutboundVO.setAvailableInventory(availableListVO.getAvailableInventory());
                    }
                }
            }
        }
        if (DelOutboundOrderTypeEnum.BULK_ORDER.getCode().equals(delOutbound.getOrderType())) {
            BasAttachmentQueryDTO dto = new BasAttachmentQueryDTO();
            dto.setBusinessNo(delOutbound.getOrderNo());
            dto.setAttachmentType(AttachmentTypeEnum.BULK_ORDER_DETAIL.getAttachmentType());
            List<BasAttachment> attachment = ListUtils.emptyIfNull(remoteAttachmentService.list(dto).getData());
            for (BasAttachment basAttachment : attachment) {
                for (DelOutboundDetailVO detailVO : delOutboundVO.getDetails()) {
                    if (StringUtils.equals("" + detailVO.getId(), basAttachment.getBusinessItemNo())) {
                            detailVO.setSkuFile(Arrays.asList(
                            new AttachmentFileDTO().setId(basAttachment.getId()).setAttachmentName(basAttachment.getAttachmentName()).
                                    setAttachmentUrl(basAttachment.getAttachmentUrl())));

                    }
                }
            }


        }

        if (DelOutboundOrderTypeEnum.MULTIPLE_PIECES.getCode().equals(delOutbound.getOrderType())) {
            //????????????
            BasAttachmentQueryDTO dto = new BasAttachmentQueryDTO();
            dto.setBusinessNo(delOutbound.getOrderNo());
            List<BasAttachment> attachment = ListUtils.emptyIfNull(remoteAttachmentService.list(dto).getData());

            List<AttachmentDataDTO> attachmentDataDTOList = new ArrayList();
            for (BasAttachment basAttachment : attachment) {
                if (AttachmentTypeEnum.MULTIPLE_PIECES_BOX_MARK.getAttachmentType().equals(basAttachment.getAttachmentType())
                        || AttachmentTypeEnum.MULTIPLE_PIECES_INVOICE.getAttachmentType().equals(basAttachment.getAttachmentType())) {
                    attachmentDataDTOList.add(new AttachmentDataDTO().setId(basAttachment.getId()).setAttachmentName(basAttachment.getAttachmentName())
                            .setAttachmentType(basAttachment.getAttachmentType())
                            .setAttachmentUrl(basAttachment.getAttachmentUrl()));
                } else {

                    //??????????????????
                    for (DelOutboundDetailVO detailVO : delOutboundVO.getDetails()) {
                        if (StringUtils.equals("" + detailVO.getId(), basAttachment.getBusinessItemNo())) {
                            if (AttachmentTypeEnum.MULTIPLE_PIECES_BOX_DETAIL.getAttachmentType().equals(basAttachment.getAttachmentType())) {
                                detailVO.setBoxMarkFile(Arrays.asList(
                                        new AttachmentFileDTO().setId(basAttachment.getId()).setAttachmentName(basAttachment.getAttachmentName()).
                                                setAttachmentUrl(basAttachment.getAttachmentUrl())));
                            } else if (AttachmentTypeEnum.MULTIPLE_PIECES_SKU.getAttachmentType().equals(basAttachment.getAttachmentType())) {
                                detailVO.setSkuFile(Arrays.asList(new AttachmentFileDTO().setId(basAttachment.getId()).
                                        setAttachmentName(basAttachment.getAttachmentName()).setAttachmentUrl(basAttachment.getAttachmentUrl())));
                            }
                        }
                    }


                }
            }

            delOutboundVO.setDocumentsFiles(attachmentDataDTOList);
        }
        return delOutboundVO;
    }

    @Override
    public List<DelOutboundDetailVO> getTransshipmentProductData(List<String> idList) {
        return createPurchaseOrderListByIdList(idList, DelOutboundOrderTypeEnum.PACKAGE_TRANSFER);
    }

    @Override
    public List<DelOutboundDetailVO> createPurchaseOrderListByIdList(List<String> idList) {
        return createPurchaseOrderListByIdList(idList, DelOutboundOrderTypeEnum.COLLECTION);
    }

    /**
     * ??????-??????????????????
     *
     * @param idList
     * @return
     */
    private List<DelOutboundDetailVO> createPurchaseOrderListByIdList(List<String> idList, DelOutboundOrderTypeEnum typeEnum) {
        //??????????????????????????????
        List<DelOutbound> delOutbounds = baseMapper.selectList(Wrappers.<DelOutbound>lambdaQuery()
                .in(DelOutbound::getId, idList)
                .eq(DelOutbound::getOrderType, typeEnum.getCode())
                .eq(DelOutbound::getDelFlag, "0")
        );
        if (CollectionUtils.isEmpty(delOutbounds)) {
            return new ArrayList<DelOutboundDetailVO>();
        }

        //????????? sellerCode??????
        String sellerCode = delOutbounds.stream().map(DelOutbound::getSellerCode).findAny().orElseThrow(() -> new BaseException("?????????????????????sellerCode??????"));
        Map<String, List<DelOutbound>> baseInfoList = delOutbounds.stream().collect(Collectors.groupingBy(DelOutbound::getWarehouseCode));

        //??????????????????sku??????
        List<String> collect1 = delOutbounds.stream().map(DelOutbound::getOrderNo).collect(Collectors.toList());
        List<DelOutboundDetail> delOutboundDetailList = delOutboundDetailService.listByOrderNos(collect1);

        List<DelOutboundDetailVO> resultList = new ArrayList<>();
        //???????????? ????????????????????????????????????????????????code??????
        baseInfoList.forEach((warehouseCode, dealOutBoundList) -> {

            //??????sku????????????
            // ??????sku ????????????
            List<DelOutboundDetailVO> detailDtos = new ArrayList<>(delOutboundDetailList.size());
            List<String> skus = new ArrayList<>(delOutboundDetailList.size());
            for (DelOutboundDetail detail : delOutboundDetailList) {
                detailDtos.add(BeanMapperUtil.map(detail, DelOutboundDetailVO.class));
                skus.add(detail.getSku());
            }
            //?????? ?????????????????????????????????
            InventoryAvailableQueryDto inventoryAvailableQueryDto = new InventoryAvailableQueryDto();
            inventoryAvailableQueryDto.setWarehouseCode(warehouseCode);
            inventoryAvailableQueryDto.setCusCode(sellerCode);
            inventoryAvailableQueryDto.setSkus(skus);
            inventoryAvailableQueryDto.setQueryType(2);
            //????????????????????????????????? ??????????????????
            List<InventoryAvailableListVO> availableList = this.inventoryFeignClientService.queryAvailableList2(inventoryAvailableQueryDto);

            Map<String, InventoryAvailableListVO> availableMap = new HashMap<>();
            if (CollectionUtils.isNotEmpty(availableList)) {
                for (InventoryAvailableListVO vo : availableList) {
                    availableMap.put(vo.getSku(), vo);
                }
            }
            for (DelOutboundDetailVO vo : detailDtos) {
                InventoryAvailableListVO available = availableMap.get(vo.getSku());
                if (null != available) {
                    BeanMapperUtil.map(available, vo);
                }
            }
            resultList.addAll(detailDtos);
        });
        //????????????
        resultList.forEach(x -> {
            BasAttachmentQueryDTO basAttachmentQueryDTO = new BasAttachmentQueryDTO().setAttachmentType(AttachmentTypeEnum.SKU_IMAGE.getAttachmentType()).setBusinessNo(x.getSku());
            List<BasAttachment> attachment = ListUtils.emptyIfNull(remoteAttachmentService.list(basAttachmentQueryDTO).getData());
            if (CollectionUtils.isNotEmpty(attachment)) {
                x.setEditionImage(new AttachmentFileDTO().setId(attachment.get(0).getId()).setAttachmentName(attachment.get(0).getAttachmentName()).setAttachmentUrl(attachment.get(0).getAttachmentUrl()));
            }
        });
        logger.info("????????????sku??????{}", resultList);
        return resultList;
    }

    /**
     * ???????????????????????????
     *
     * @param queryDto ???????????????
     * @return ???????????????
     */
    @Override
    public List<DelOutboundListVO> selectDelOutboundList(DelOutboundListQueryDto queryDto) {
        QueryWrapper<DelOutboundListQueryDto> queryWrapper = new QueryWrapper<>();
        if (Objects.nonNull(SecurityUtils.getLoginUser())) {
            String cusCode = StringUtils.isNotEmpty(SecurityUtils.getLoginUser().getSellerCode()) ? SecurityUtils.getLoginUser().getSellerCode() : "";
            if (StringUtils.isEmpty(queryDto.getCustomCode())) {
                queryDto.setCustomCode(cusCode);
            }
        }

        logger.info("????????????????????????????????????{}",JSON.toJSONString(queryDto));

        DelOutboundServiceImplUtil.handlerQueryWrapper(queryWrapper, queryDto);

        List<DelOutboundListVO> delOutboundListVOS = baseMapper.pageList(queryWrapper);

        for(DelOutboundListVO outboundVO : delOutboundListVOS){

            BigDecimal amount = outboundVO.getAmount();

            if(amount != null) {
                BigDecimal changeAmount = amount.setScale(2,BigDecimal.ROUND_UP);
                outboundVO.setAmount(changeAmount);
            }

        }

        return delOutboundListVOS;
    }

    /**
     * ?????????????????????
     *
     * @param dto ???????????????
     * @return ??????
     */
    @Transactional
    @Override
    public DelOutboundAddResponse insertDelOutbound(DelOutboundDto dto) {
        if (!DelOutboundOrderTypeEnum.has(dto.getOrderType())) {
            throw new CommonException("400", "?????????????????????");
        }
        if(StringUtils.isEmpty(dto.getSourceType())){
            // ???????????????
            dto.setSourceType(DelOutboundConstant.SOURCE_TYPE_ADD);
        }
        return this.createDelOutbound(dto);
    }

    @Transactional
    @Override
    public DelOutboundAddResponse insertDelOutboundShopify(DelOutboundDto dto) {
        if (!DelOutboundOrderTypeEnum.has(dto.getOrderType())) {
            throw new CommonException("400", "?????????????????????");
        }
        // ?????????Shopify
        dto.setSourceType(DelOutboundConstant.SOURCE_TYPE_SHOPIFY);
        return this.createDelOutbound(dto);
    }

    private void docValid(DelOutboundDto dto) {
        if (DelOutboundConstant.SOURCE_TYPE_DOC.equals(dto.getSourceType())) {
            List<DelOutboundDetailDto> details = dto.getDetails();
            if (CollectionUtils.isEmpty(details)) {
                throw new CommonException("400", "????????????????????????");
            }


            if (org.apache.commons.lang3.StringUtils.isNotEmpty(dto.getWarehouseCode())) {
                String warehouseCode = dto.getWarehouseCode();

                Long s = System.currentTimeMillis();

                BasWarehouse warehouse = this.basWarehouseClientService.queryByWarehouseCode(warehouseCode);

                Long e = System.currentTimeMillis();

                logger.info("basWarehouseClientService.queryByWarehouseCode,{}",e-s);

                if (null == warehouse) {
                    throw new CommonException("400", "?????????????????????");
                }else if(!"1".equals(warehouse.getStatus())){
                    throw new CommonException("400", "Warehouse not enabled");
                }
            }

            List<String> skus = details.stream().map(DelOutboundDetailDto::getSku).distinct().collect(Collectors.toList());
            // ??????????????????????????????????????????
            DelOutboundAddressDto addressDto = dto.getAddress();
            if (null != addressDto && StringUtils.isNotEmpty(dto.getShipmentRule()) && StringUtils.isNotEmpty(addressDto.getCountryCode())) {
                // ????????????????????????
                DelOutboundOtherInServiceDto inServiceDto = new DelOutboundOtherInServiceDto();
                inServiceDto.setClientCode(dto.getSellerCode());
                inServiceDto.setCountryCode(addressDto.getCountryCode());
                inServiceDto.setWarehouseCode(dto.getWarehouseCode());
                inServiceDto.setSkus(skus);
                String shipmentRule = dto.getShipmentRule();

                Long s = System.currentTimeMillis();
                boolean f = !this.delOutboundDocService.inServiceValid(inServiceDto, shipmentRule);
                Long e = System.currentTimeMillis();

                logger.info("delOutboundDocService.inServiceValid,{}",e-s);
                if (f) {
                    throw new CommonException("400", "????????????[" + shipmentRule + "]?????????");
                }
            }
            if (DelOutboundOrderTypeEnum.PACKAGE_TRANSFER.getCode().equals(dto.getOrderType())) {
                // ?????????????????????????????????SKU???????????????
                return;
            }
            // ??????sku
            BaseProductConditionQueryDto productConditionQueryDto = new BaseProductConditionQueryDto();
            productConditionQueryDto.setSkus(skus);
            productConditionQueryDto.setSellerCode(dto.getSellerCode());
            if (DelOutboundOrderTypeEnum.COLLECTION.getCode().equals(dto.getOrderType())) {
                // ??????????????????????????????SKU
                productConditionQueryDto.setSource("084002");
            }

            Long s = System.currentTimeMillis();
            List<BaseProduct> productList = this.baseProductClientService.queryProductList(productConditionQueryDto);
            Long e = System.currentTimeMillis();

            logger.info("baseProductClientService.queryProductList,{}",e-s);

            if (CollectionUtils.isEmpty(productList)) {
                throw new CommonException("400", "??????SKU????????????");
            }
            Map<String, BaseProduct> productMap = new HashMap<>(productList.size());
            for (BaseProduct product : productList) {
                productMap.put(product.getCode(), product);
                if (StringUtils.isNotEmpty(product.getBindCode())) {
                    // ???SKU????????????????????????????????????
                    skus.add(product.getBindCode());
                }
            }
            if (DelOutboundOrderTypeEnum.NORMAL.getCode().equals(dto.getOrderType())
                    || DelOutboundOrderTypeEnum.SELF_PICK.getCode().equals(dto.getOrderType())
                    || DelOutboundOrderTypeEnum.BATCH.getCode().equals(dto.getOrderType())
                    || DelOutboundOrderTypeEnum.DESTROY.getCode().equals(dto.getOrderType())) {


                InventoryAvailableQueryDto inventoryAvailableQueryDto = new InventoryAvailableQueryDto();
                String warehouseCode = dto.getWarehouseCode();
                inventoryAvailableQueryDto.setWarehouseCode(warehouseCode);
                inventoryAvailableQueryDto.setCusCode(dto.getSellerCode());
                inventoryAvailableQueryDto.setSkus(skus);
                // ?????????0???????????????????????????????????????????????????????????????SKU??????????????????
                inventoryAvailableQueryDto.setQueryType(2);

                Long st = System.currentTimeMillis();
                List<InventoryAvailableListVO> availableList = this.inventoryFeignClientService.queryAvailableList2(inventoryAvailableQueryDto);
                Long en = System.currentTimeMillis();
                logger.info("inventoryFeignClientService.queryAvailableList2,{}",en-st);

                Map<String, InventoryAvailableListVO> availableMap = new HashMap<>();
                Map<String, Integer> availableInventoryMap = new HashMap<>();
                if (CollectionUtils.isNotEmpty(availableList)) {
                    for (InventoryAvailableListVO vo : availableList) {
                        availableMap.put(vo.getSku(), vo);
                    }
                }
                for (DelOutboundDetailDto detail : details) {
                    String sku = detail.getSku();
                    BaseProduct product = productMap.get(sku);
                    if (null == product) {
                        throw new CommonException("400", "SKU[" + sku + "]?????????????????????");
                    }
                    detail.setLength(product.getLength());
                    detail.setWidth(product.getWidth());
                    detail.setHeight(product.getHeight());
                    detail.setWeight(product.getWeight());
                    InventoryAvailableListVO vo = availableMap.get(sku);
                    if (null == vo) {
                        throw new CommonException("400", "SKU[" + sku + "]???[" + warehouseCode + "]????????????????????????");
                    }
                    int aiq = availableInventoryMap.getOrDefault(sku, 0);
                    Integer inventory = vo.getAvailableInventory();
                    Integer outQty = Math.toIntExact(detail.getQty() + aiq);
                    if (outQty > inventory) {
                        throw new CommonException("400", "SKU[" + sku + "]????????????????????????????????????" + outQty + "??????????????????" + inventory);
                    }
                    availableInventoryMap.put(sku, outQty);
                    // ??????????????????
                    String bindCode = product.getBindCode();
                    if (StringUtils.isNotEmpty(bindCode)) {
                        // ????????????????????????
                        detail.setBindCode(bindCode);
                        vo = availableMap.get(bindCode);
                        if (null == vo) {
                            throw new CommonException("400", "SKU[" + sku + "]?????????[" + bindCode + "]?????????");
                        }
                        if (outQty > vo.getAvailableInventory()) {
                            throw new CommonException("400", "SKU[" + sku + "]?????????[" + bindCode + "]????????????????????????????????????" + outQty + "??????????????????" + vo.getAvailableInventory());
                        }
                    }
                }
                if (DelOutboundOrderTypeEnum.NORMAL.getCode().equals(dto.getOrderType())
                        || DelOutboundOrderTypeEnum.SELF_PICK.getCode().equals(dto.getOrderType())
                        || DelOutboundOrderTypeEnum.DESTROY.getCode().equals(dto.getOrderType())) {

                    //????????????????????????????????????????????????
                    for (DelOutboundDetailDto detail : details) {
                        BaseProduct product = productMap.get(detail.getSku());
                        if(StringUtils.isEmpty(detail.getProductName())){
                            detail.setProductName(product.getProductName());
                        }
                        if(StringUtils.isEmpty(detail.getProductName())){
                            detail.setProductNameChinese(product.getProductNameChinese());
                        }
                        if(detail.getDeclaredValue() != null){
                            detail.setDeclaredValue(product.getDeclaredValue());
                        }
                    }
                }
            } else if (DelOutboundOrderTypeEnum.COLLECTION.getCode().equals(dto.getOrderType())) {
                for (DelOutboundDetailDto detail : details) {
                    String sku = detail.getSku();
                    BaseProduct product = productMap.get(sku);
                    if (null == product) {
                        throw new CommonException("400", "SKU[" + sku + "]?????????????????????");
                    }
                    detail.setLength(product.getLength());
                    detail.setWidth(product.getWidth());
                    detail.setHeight(product.getHeight());
                    detail.setWeight(product.getWeight());
                    detail.setBindCode(product.getBindCode());
                }
            }
        }
    }

    private void checkRefNo(DelOutboundDto dto, Long id) {

        String refNo = dto.getRefNo();

        if (StringUtils.isNotEmpty(refNo)) {

            Object refNoState = redisTemplate.opsForValue().get(refNo);

            logger.info("refNo:{},{}",refNo,refNoState);

            if(refNoState != null){
                throw new RuntimeException("refNo:"+refNo+"????????????,?????????????????????");
            }

            redisTemplate.opsForValue().set(refNo,1,120L,TimeUnit.SECONDS);

            LambdaQueryWrapper<DelOutbound> queryWrapper = new LambdaQueryWrapper<DelOutbound>();
            queryWrapper.eq(DelOutbound::getRefNo, refNo);
            if (id != null) {
                queryWrapper.ne(DelOutbound::getId, dto.getId());
            }
            String states[] = {
                    DelOutboundStateEnum.DELIVERED.getCode(),
                    DelOutboundStateEnum.REVIEWED.getCode(),
                    DelOutboundStateEnum.WHSE_CANCELING.getCode(),
                    DelOutboundStateEnum.AUDIT_FAILED.getCode(),
                    DelOutboundStateEnum.COMPLETED.getCode(),
                    DelOutboundStateEnum.PROCESSING.getCode(),
                    DelOutboundStateEnum.NOTIFY_WHSE_PROCESSING.getCode(),
                    DelOutboundStateEnum.REVIEWED_DOING.getCode(),
                    DelOutboundStateEnum.WHSE_PROCESSING.getCode()
            };
            queryWrapper.in(DelOutbound::getState, states);
            queryWrapper.eq(DelOutbound::getDelFlag, "0");
            Integer size = baseMapper.selectCount(queryWrapper);
            if (size > 0) {

                redisTemplate.opsForValue().set(refNo,1,120L,TimeUnit.SECONDS);

                throw new CommonException("400", "Refno ???????????????" + dto.getRefNo());
            }
        }
    }

    private DelOutboundAddResponse createDelOutbound(DelOutboundDto dto) {
        StopWatch stopWatch = new StopWatch();
        logger.info(">>>>>[???????????????]1.0 ?????????????????????");
        TimeInterval timer = DateUtil.timer();
        DelOutboundAddResponse response = new DelOutboundAddResponse();
        String orderNo;
        //??????????????????????????????
        logger.info(">>>>>[???????????????]1.1 ??????Refno");
        try {
            this.checkRefNo(dto, null);
            logger.info(">>>>>[???????????????]1.2 ??????Refno?????????{}", timer.intervalRestart());
        }catch (CommonException e){
            logger.info(">>>>>[???????????????]1.2 ??????Refno?????????{}", timer.intervalRestart());
            response.setStatus(false);
            response.setMessage(e.getMessage());
            return response;
        }
        // ???????????????
        try {
            // DOC?????????SKU
            logger.info(">>>>>[???????????????]2.0 doc??????");
            this.docValid(dto);

            logger.info(">>>>>[???????????????]2.1 doc???????????????{}", timer.intervalRestart());

//            if (!StringUtils.equals(dto.getSourceType(), DelOutboundConstant.SOURCE_TYPE_ADD)) {
//                //??????????????????????????????
//                logger.info(">>>>>[???????????????]2.2 ??????Refno");
//                this.checkRefNo(dto, null);
//                logger.info(">>>>>[???????????????]2.3 ??????Refno?????????{}", timer.intervalRestart());
//            }
            logger.info(">>>>>[???????????????]3.0 ??????????????????????????????");
            DelOutbound delOutbound = BeanMapperUtil.map(dto, DelOutbound.class);
            if (null == delOutbound.getCodAmount()) {
                delOutbound.setCodAmount(BigDecimal.ZERO);
            }

            if(StringUtils.isNotEmpty(dto.getShipmentRule())){
                //????????????????????????
                R<PricedProductInfo> info = htpPricedProductFeignService.info(dto.getShipmentRule());
                if(info.getCode() == 200 && info.getData() != null){
                    delOutbound.setShipmentRuleName(info.getData().getName());
                }
                //?????????????????????????????????
                R<List<BasProductService>> r= chargeFeignService.selectBasProductService(Arrays.asList(dto.getShipmentRule()));
                if(r.getCode() == 200 && r.getData()!= null && r.getData().size() > 0){
                    Boolean isInService = r.getData().get(0).getInService();
                    if(isInService != null && isInService == false){
                        throw new CommonException("400", "Logistics services are not available");
                    }
                }
            }



            // ??????????????????
            // ??????????????????CK + ???????????? + ???????????? + 8????????????
            String sellerCode;
            if (StringUtils.isNotEmpty(delOutbound.getCustomCode())) {
                sellerCode = delOutbound.getCustomCode();
            } else {
                sellerCode = delOutbound.getSellerCode();
                // ??????
                delOutbound.setCustomCode(sellerCode);
            }
            if (StringUtils.isEmpty(sellerCode)) {
                throw new CommonException("400", "???????????????????????????????????????");
            }
            String prefix = "CK";
            if (DelOutboundConstant.REASSIGN_TYPE_Y.equals(dto.getReassignType())) {
                prefix = "RE" + prefix;
                //??????????????????????????????????????????
                updateField(dto, delOutbound);
            }
            long shettTime = System.currentTimeMillis();

            delOutbound.setOrderNo(orderNo = (prefix + sellerCode + this.serialNumberClientService.generatorNumber(SerialNumberConstant.DEL_OUTBOUND_NO)));
            logger.info(">>>>>[???????????????{}]3.0???????????????????????????????????????{}", delOutbound.getOrderNo(), System.currentTimeMillis()-shettTime);


            // ????????????
            delOutbound.setState(DelOutboundStateEnum.REVIEWED.getCode());
            // ??????????????????
            delOutbound.setExceptionState(DelOutboundExceptionStateEnum.NORMAL.getCode());
//            logger.info(">>>>>[???????????????{}]3.1 ??????????????????????????????????????????{}", delOutbound.getOrderNo(), timer.intervalRestart());
            // ??????????????????
            long shipmentTypeTime = System.currentTimeMillis();

            delOutbound.setShipmentType(this.buildShipmentType(dto));
            logger.info(">>>>>[???????????????{}]3.1.5sku???????????????????????????{}", delOutbound.getOrderNo(), System.currentTimeMillis()-shipmentTypeTime);

            logger.info(">>>>>[???????????????{}]3.2 ?????????????????????{}", delOutbound.getOrderNo(), timer.intervalRestart());
            // ??????????????????
            this.countPackageSize(delOutbound, dto);
            logger.info(">>>>>[???????????????{}]3.3 ?????????????????????{}", delOutbound.getOrderNo(), timer.intervalRestart());

            //??????????????????????????????
            delOutbound.setForecastWeight(delOutbound.getWeight());

            delOutbound.setThridPartStatus(0);
            delOutbound.setThridPartCount(0);
            
            // ???????????????
            int insert = baseMapper.insert(delOutbound);
            logger.info(">>>>>[???????????????{}]3.4 ??????????????????{}", delOutbound.getOrderNo(), timer.intervalRestart());
            if (insert == 0) {
                throw new CommonException("400", "????????????????????????");
            }
            DelOutboundOperationLogEnum.CREATE.listener(delOutbound);
            // ????????????
            this.saveAddress(dto, delOutbound.getOrderNo());
            logger.info(">>>>>[???????????????{}]3.5 ?????????????????????{}", delOutbound.getOrderNo(), timer.intervalRestart());

            if (DelOutboundOrderTypeEnum.MULTIPLE_PIECES.getCode().equals(delOutbound.getOrderType())) {
                //????????????????????????????????????????????????
                stopWatch.start();

                for (DelOutboundDetailDto detailDto : dto.getDetails()) {
                    if (StringUtils.isEmpty(detailDto.getBoxMark())) {
                        detailDto.setBoxMark(this.serialNumberClientService.generatorNumber(SerialNumberConstant.BOX_MARK));
                    }
                }
                stopWatch.stop();
                logger.info(">>>>>[???????????????{}]3.55???????????????{}, ??????{}", delOutbound.getOrderNo(), timer.intervalRestart(), stopWatch.getLastTaskInfo().getTimeMillis());

            }

            // ????????????
            stopWatch.start();
            this.saveDetail(dto, delOutbound.getOrderNo());
            stopWatch.stop();
            logger.info(">>>>>[???????????????{}]3.6 ?????????????????????{}, ??????{}", delOutbound.getOrderNo(), timer.intervalRestart(), stopWatch.getLastTaskInfo().getTimeMillis());


            if(DelOutboundOrderTypeEnum.BULK_ORDER.getCode().equals(delOutbound.getOrderType())){
                //??????????????????
                AttachmentDTO attachmentDTO = AttachmentDTO.builder().businessNo(orderNo).businessItemNo(null).fileList(dto.getBatchLabels()).attachmentTypeEnum(AttachmentTypeEnum.BULK_ORDER_BOX).build();
                this.remoteAttachmentService.saveAndUpdate(attachmentDTO);
                //????????????????????????
                for (DelOutboundDetailDto detail : dto.getDetails()) {
                    if (detail.getSkuFile() != null && detail.getSkuFile().size() > 0) {
                        AttachmentDataDTO attachmentDataDTO = detail.getSkuFile().get(0);
                        // ????????????
                        AttachmentDTO boxMarkDetailFiels = AttachmentDTO.builder().businessNo(orderNo).businessItemNo("" + detail.getId()).fileList(
                                        Arrays.asList(new AttachmentDataDTO().setAttachmentUrl(attachmentDataDTO.getAttachmentUrl())
                                                .setAttachmentName(attachmentDataDTO.getAttachmentName()))).
                                attachmentTypeEnum(AttachmentTypeEnum.BULK_ORDER_DETAIL).build();
                        this.remoteAttachmentService.saveAndUpdate(boxMarkDetailFiels);

                    }
                }
            }
            // ????????????
            if (!DelOutboundOrderTypeEnum.MULTIPLE_PIECES.getCode().equals(delOutbound.getOrderType())
            && !DelOutboundOrderTypeEnum.BULK_ORDER.getCode().equals(delOutbound.getOrderType())
            ) {
                AttachmentDTO attachmentDTO = AttachmentDTO.builder().businessNo(orderNo).businessItemNo(null).fileList(dto.getDocumentsFiles()).attachmentTypeEnum(AttachmentTypeEnum.DEL_OUTBOUND_DOCUMENT).build();

                logger.info(">>>>>[???????????????]3.7 ????????????????????????:{}",JSON.toJSONString(attachmentDTO));

                this.remoteAttachmentService.saveAndUpdate(attachmentDTO);
            }
            logger.info(">>>>>[???????????????]3.7 ?????????????????????{}", timer.intervalRestart());

            // ??????????????????????????????
            if (DelOutboundOrderTypeEnum.BATCH.getCode().equals(delOutbound.getOrderType())
                    || DelOutboundOrderTypeEnum.NORMAL.getCode().equals(delOutbound.getOrderType())
                    || DelOutboundOrderTypeEnum.PACKAGE_TRANSFER.getCode().equals(delOutbound.getOrderType())
            ) {
                // ????????????
                List<DelOutboundPackingDto> packings = dto.getPackings();
                this.delOutboundPackingService.save(orderNo, packings, false);
                // ????????????
                AttachmentDTO batchLabel = AttachmentDTO.builder().businessNo(orderNo).businessItemNo(null).fileList(dto.getBatchLabels()).attachmentTypeEnum(AttachmentTypeEnum.DEL_OUTBOUND_BATCH_LABEL).build();
                this.remoteAttachmentService.saveAndUpdate(batchLabel);
                logger.info(">>>>>[???????????????]3.8 ?????????????????????????????????{}", timer.intervalRestart());
            }
            if (DelOutboundOrderTypeEnum.NEW_SKU.getCode().equals(delOutbound.getOrderType())
                    || DelOutboundOrderTypeEnum.SPLIT_SKU.getCode().equals(delOutbound.getOrderType())) {
                // ????????????
                this.delOutboundCombinationService.save(orderNo, dto.getCombinations());
                logger.info(">>>>>[???????????????]3.9 ??????NEW_SKU/SPLIT_SKU???????????????{}", timer.intervalRestart());
            }

            if (DelOutboundOrderTypeEnum.MULTIPLE_PIECES.getCode().equals(delOutbound.getOrderType())) {
                //??????????????????
                //??????
                List<AttachmentDataDTO> batchLabels = new ArrayList();
                //?????????
                List<AttachmentDataDTO> documentsFiles = new ArrayList();
                if (dto.getDocumentsFiles() != null) {
                    for (AttachmentDataDTO vo : dto.getDocumentsFiles()) {
                        if (AttachmentTypeEnum.MULTIPLE_PIECES_BOX_MARK.getAttachmentType().equals(vo.getAttachmentType())) {
                            batchLabels.add(vo);
                        } else if (AttachmentTypeEnum.MULTIPLE_PIECES_INVOICE.getAttachmentType().equals(vo.getAttachmentType())) {
                            documentsFiles.add(vo);
                        }
                    }
                }
                // ????????????
                AttachmentDTO batchLabel = AttachmentDTO.builder().businessNo(orderNo).businessItemNo(null).fileList(batchLabels)
                        .attachmentTypeEnum(AttachmentTypeEnum.MULTIPLE_PIECES_BOX_MARK).build();
                this.remoteAttachmentService.saveAndUpdate(batchLabel);
                // ???????????????
                AttachmentDTO invoiceBatchLabel = AttachmentDTO.builder().businessNo(orderNo).businessItemNo(null).fileList(documentsFiles)
                        .attachmentTypeEnum(AttachmentTypeEnum.MULTIPLE_PIECES_INVOICE).build();
                this.remoteAttachmentService.saveAndUpdate(invoiceBatchLabel);
                for (DelOutboundDetailDto detail : dto.getDetails()) {
                    if (detail.getBoxMarkFile() != null && detail.getBoxMarkFile().size() > 0) {
                        AttachmentDataDTO attachmentDataDTO = detail.getBoxMarkFile().get(0);

                        // ????????????
                        AttachmentDTO boxMarkDetailFiels = AttachmentDTO.builder().businessNo(orderNo).businessItemNo("" + detail.getId()).fileList(
                                Arrays.asList(new AttachmentDataDTO().setAttachmentUrl(attachmentDataDTO.getAttachmentUrl())
                                        .setAttachmentName(attachmentDataDTO.getAttachmentName()))).
                                attachmentTypeEnum(AttachmentTypeEnum.MULTIPLE_PIECES_BOX_DETAIL).build();
                        this.remoteAttachmentService.saveAndUpdate(boxMarkDetailFiels);

                    }
                    if (detail.getSkuFile() != null && detail.getSkuFile().size() > 0) {
                        AttachmentDataDTO attachmentDataDTO = detail.getSkuFile().get(0);
                        // SKU
                        AttachmentDTO skuFiles = AttachmentDTO.builder().businessNo(orderNo).businessItemNo("" + detail.getId()).fileList(
                                Arrays.asList(new AttachmentDataDTO().setAttachmentUrl(attachmentDataDTO.getAttachmentUrl()).
                                        setAttachmentName(attachmentDataDTO.getAttachmentName()))).
                                attachmentTypeEnum(AttachmentTypeEnum.MULTIPLE_PIECES_SKU).build();
                        this.remoteAttachmentService.saveAndUpdate(skuFiles);
                    }

                }
                logger.info(">>>>>[???????????????]3.10 ?????????????????????????????????{}", timer.intervalRestart());
            }
            response.setStatus(true);
            response.setId(delOutbound.getId());
            response.setOrderNo(orderNo);
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e);
            // ????????????????????????
            response.setStatus(false);
            response.setMessage(e.getMessage());
            if(StringUtils.contains(e.getMessage(), "???????????????")){
                return response;
            }
            // return response;
            // ???????????????????????????
            throw e;
        }
    }

    private void updateField(DelOutboundDto dto, DelOutbound delOutbound) {
        List<DelOutbound> oldDels = baseMapper.selectorderNos(Arrays.asList(dto.getOldOrderNo()));
        if (CollectionUtils.isNotEmpty(oldDels)){
            DelOutbound oldDel = oldDels.get(0);
            delOutbound.setLength(oldDel.getLength());
            delOutbound.setWidth(oldDel.getWidth());
            delOutbound.setHeight(oldDel.getHeight());
            if (null != oldDel.getCalcWeight()) {
                String calcWeightUnit = Optional.ofNullable(oldDel.getCalcWeightUnit()).orElse("g");
                BigDecimal calcWeight = Optional.ofNullable(oldDel.getCalcWeight()).orElse(BigDecimal.ZERO);
                // ??????????????? g
                if ("KG".equalsIgnoreCase(calcWeightUnit)) {
                    calcWeight = calcWeight.multiply(new BigDecimal("1000"));
                    delOutbound.setWeight(calcWeight.doubleValue());
                } else {
                    delOutbound.setWeight(calcWeight.doubleValue());
                }
            }
        }
    }

    /**
     * ????????????????????????
     *
     * @param orderNo   orderNo
     * @param orderType orderType
     */
    private void unfreezeOperation(String orderNo, String orderType) {
        DelOutboundOperationVO delOutboundOperationVO = new DelOutboundOperationVO();
        delOutboundOperationVO.setOrderType(orderType);
        delOutboundOperationVO.setOrderNo(orderNo);
        R<?> ur = this.operationFeignService.delOutboundThaw(delOutboundOperationVO);
        DelOutboundServiceImplUtil.thawOperationThrowCommonException(ur);
    }

    @Transactional
    @Override
    public List<DelOutboundAddResponse> insertDelOutbounds(List<DelOutboundDto> dtoList) {
        List<DelOutboundAddResponse> result = new ArrayList<>();
        int index = 1;
        List<String> refNoList = new ArrayList<String>();
        for (DelOutboundDto dto : dtoList) {
            DelOutboundAddResponse delOutbound = null;
            if (StringUtils.isNotEmpty(dto.getRefNo()) && refNoList.contains(dto.getRefNo())) {
                delOutbound = new DelOutboundAddResponse();
                logger.error("?????????????????????Refno ???????????????" + dto.getRefNo());
                // ????????????????????????
                delOutbound.setStatus(false);
                delOutbound.setMessage("?????????????????????Refno ???????????????" + dto.getRefNo());
            } else {
                refNoList.add(dto.getRefNo());
                delOutbound = this.createDelOutbound(dto);
            }
            delOutbound.setIndex(index);
            result.add(delOutbound);
            index++;
        }
        return result;
    }

    /**
     * ??????????????????
     *
     * @param delOutbound delOutbound
     * @param dto         dto
     */
    private void countPackageSize(DelOutbound delOutbound, DelOutboundDto dto) {
        // ??????????????????????????????
        if (DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
            return;
        }
        // ??????????????????
        List<DelOutboundDetailDto> details = dto.getDetails();
        if (DelOutboundOrderTypeEnum.PACKAGE_TRANSFER.getCode().equals(delOutbound.getOrderType())) {
            logger.info(">>>>>[???????????????]3.4.5  ???????????????????????????????????????");

            double length = Utils.defaultValue(dto.getLength());
            double width = Utils.defaultValue(dto.getWidth());
            double height = Utils.defaultValue(dto.getHeight());
            double weight = Utils.defaultValue(dto.getWeight());
            delOutbound.setLength(length);
            delOutbound.setWidth(width);
            delOutbound.setHeight(height);
            delOutbound.setWeight(weight);
            // ????????????*???*???
            delOutbound.setSpecifications(length + "*" + width + "*" + height);
        } else if (DelOutboundOrderTypeEnum.MULTIPLE_PIECES.getCode().equals(delOutbound.getOrderType())) {
            delOutbound.setLength(0D);
            delOutbound.setWidth(0D);
            delOutbound.setHeight(0D);
            delOutbound.setWeight(0D);
            // ????????????*???*???
            delOutbound.setSpecifications(0 + "*" + 0 + "*" + 0);
        } else if(DelOutboundOrderTypeEnum.BULK_ORDER.getCode().equals(delOutbound.getOrderType())){
            BigDecimal length = BigDecimal.ZERO;
            BigDecimal width = BigDecimal.ZERO;
            BigDecimal height = BigDecimal.ZERO;
            BigDecimal weight = BigDecimal.ZERO;
            Set<String> set = new HashSet<>();

            for (DelOutboundDetailDto detail : details) {
                if (!set.contains(detail.getBoxMark())) {
                    length = length.add(new BigDecimal(detail.getBoxLength()));
                    width = width.add(new BigDecimal(detail.getBoxWidth()));
                    height = height.add(new BigDecimal(detail.getBoxHeight()));
                    weight = weight.add(new BigDecimal(detail.getBoxWeight()));
                    set.add(detail.getBoxMark());
                }
            }
            delOutbound.setLength(length.doubleValue());
            delOutbound.setWidth(width.doubleValue());
            delOutbound.setHeight(height.doubleValue());
            delOutbound.setWeight(weight.doubleValue());

        }else {
            logger.info(">>>>>[???????????????]3.4.5  ???????????????????????????????????????else???????????????");

            // ?????????????????????
            Set<String> skus = new HashSet<>();
            for (DelOutboundDetailDto detail : details) {
                // sku????????????
                if (StringUtils.isNotEmpty(detail.getBindCode())) {
                    skus.add(detail.getBindCode());
                }
            }
            Map<String, BaseProduct> productMap = null;
            if (!skus.isEmpty()) {
                BaseProductConditionQueryDto baseProductConditionQueryDto = new BaseProductConditionQueryDto();
                baseProductConditionQueryDto.setSkus(new ArrayList<>(skus));
                List<BaseProduct> productList = this.baseProductClientService.queryProductList(baseProductConditionQueryDto);
                if (CollectionUtils.isNotEmpty(productList)) {
                    productMap = productList.stream().collect(Collectors.toMap(BaseProduct::getCode, v -> v, (v1, v2) -> v1));
                }
            }
            if (null == productMap) {
                productMap = Collections.emptyMap();
            }
            double weight = 0.0;
            List<PackageInfo> packageInfoList = new ArrayList<>();
            for (DelOutboundDetailDto detail : details) {
                // sku??????
                weight += Utils.defaultValue(detail.getWeight());
                packageInfoList.add(new PackageInfo(detail.getLength(), detail.getWidth(), detail.getHeight()));
                // ????????????
                if (StringUtils.isNotEmpty(detail.getBindCode())) {
                    BaseProduct product = productMap.get(detail.getBindCode());
                    if (null != product) {
                        weight += Utils.defaultValue(product.getWeight());
                        packageInfoList.add(new PackageInfo(product.getLength(), product.getWidth(), product.getHeight()));
                    }
                }
            }
            delOutbound.setWeight(weight);
            PackageInfo packageInfo = PackageUtil.count(packageInfoList);
            delOutbound.setLength(packageInfo.getLength());
            delOutbound.setWidth(packageInfo.getWidth());
            delOutbound.setHeight(packageInfo.getHeight());
            // ????????????*???*???
            delOutbound.setSpecifications(packageInfo.getLength() + "*" + packageInfo.getWidth() + "*" + packageInfo.getHeight());
        }
    }

    private String buildShipmentType(DelOutboundDto dto) {
        List<DelOutboundDetailDto> details = dto.getDetails();
        return this.baseProductClientService.buildShipmentType(dto.getSellerCode(), details.stream().map(DelOutboundDetailDto::getSku).collect(Collectors.toList()));
    }

    /**
     * ????????????
     *
     * @param dto     dto
     * @param orderNo orderNo
     */
    private void saveAddress(DelOutboundDto dto, String orderNo) {
        DelOutboundAddressDto address = dto.getAddress();
        if (Objects.isNull(address)) {
            return;
        }

        if(StringUtils.isNotEmpty(address.getCountryCode())){
            //??????/???????????????????????????????????????
            R<BasRegionSelectListVO> countryR = this.basRegionFeignService.queryByCountryCode(address.getCountryCode());
            BasRegionSelectListVO country = R.getDataAndException(countryR);
            if (null == country) {
                throw new CommonException("400", "?????????????????????");
            }
            address.setCountry(country.getEnName());
        }


        DelOutboundAddress delOutboundAddress = BeanMapperUtil.map(address, DelOutboundAddress.class);
        if (Objects.nonNull(delOutboundAddress)) {
            delOutboundAddress.setOrderNo(orderNo);
            this.delOutboundAddressService.save(delOutboundAddress);
        }
    }

    /**
     * ????????????
     *
     * @param dto     dto
     * @param orderNo orderNo
     */
    private void saveDetail(DelOutboundDto dto, String orderNo) {
        List<DelOutboundDetailDto> details = dto.getDetails();
        if (CollectionUtils.isEmpty(details)) {
            return;
        }
        List<DelOutboundDetail> delOutboundDetailList = BeanMapperUtil.mapList(details, DelOutboundDetail.class);
        if (CollectionUtils.isNotEmpty(delOutboundDetailList)) {
            for (DelOutboundDetail delOutboundDetail : delOutboundDetailList) {
                delOutboundDetail.setOrderNo(orderNo);
            }
            this.delOutboundDetailService.saveBatch(delOutboundDetailList);
            for (int i = 0; i < delOutboundDetailList.size(); i++) {
                details.get(i).setId(delOutboundDetailList.get(i).getId());
            }
        }
    }

    /**
     * ????????????
     *
     * @param orderNo orderNo
     */
    private void deleteAddress(String orderNo) {
        LambdaQueryWrapper<DelOutboundAddress> addressLambdaQueryWrapper = Wrappers.lambdaQuery();
        addressLambdaQueryWrapper.eq(DelOutboundAddress::getOrderNo, orderNo);
        this.delOutboundAddressService.remove(addressLambdaQueryWrapper);
    }

    /**
     * ????????????
     *
     * @param orderNo orderNo
     */
    private void deleteDetail(String orderNo) {
        LambdaQueryWrapper<DelOutboundDetail> detailLambdaQueryWrapper = Wrappers.lambdaQuery();
        detailLambdaQueryWrapper.eq(DelOutboundDetail::getOrderNo, orderNo);
        this.delOutboundDetailService.remove(detailLambdaQueryWrapper);
    }

    /**
     * ?????????????????????
     *
     * @param dto ???????????????
     * @return ??????
     */
    @Transactional
    @Override
    public int updateDelOutbound(DelOutboundDto dto) {
        DelOutbound inputDelOutbound = BeanMapperUtil.map(dto, DelOutbound.class);
        DelOutbound delOutbound = this.getById(inputDelOutbound.getId());
        if (null == delOutbound) {
            throw new CommonException("400", "???????????????");
        }
        // ????????????????????????????????????????????????
        if (!(DelOutboundStateEnum.REVIEWED.getCode().equals(delOutbound.getState())
                || DelOutboundStateEnum.AUDIT_FAILED.getCode().equals(delOutbound.getState()))) {
            throw new CommonException("400", "??????????????????");
        }
        if (null == delOutbound.getCodAmount()) {
            delOutbound.setCodAmount(BigDecimal.ZERO);
        }
        //??????????????????????????????
        this.checkRefNo(dto, dto.getId());

        if(StringUtils.isNotEmpty(dto.getShipmentRule())){
            //????????????????????????
            R<PricedProductInfo> info = htpPricedProductFeignService.info(dto.getShipmentRule());
            if(info.getCode() == 200 && info.getData() != null){
                delOutbound.setShipmentRuleName(info.getData().getName());
            }
            //?????????????????????????????????
            R<List<BasProductService>> r= chargeFeignService.selectBasProductService(Arrays.asList(dto.getShipmentRule()));
            if(r.getCode() == 200 && r.getData()!= null && r.getData().size() > 0){
                Boolean isInService = r.getData().get(0).getInService();
                if(isInService != null && isInService == false){
                    throw new CommonException("400", "Logistics services are not available");
                }
            }
        }

        // ???????????????????????????
        // ????????????
        String orderNo = delOutbound.getOrderNo();
        try {
            // ????????????
            this.deleteAddress(orderNo);
            this.deleteDetail(orderNo);
            // ????????????
            this.saveAddress(dto, orderNo);
            // ????????????
            this.saveDetail(dto, orderNo);
            // ??????????????????
            inputDelOutbound.setShipmentType(this.buildShipmentType(dto));
            // ????????????
            if (!DelOutboundOrderTypeEnum.MULTIPLE_PIECES.getCode().equals(delOutbound.getOrderType()) && !DelOutboundOrderTypeEnum.BULK_ORDER.getCode().equals(delOutbound.getOrderType())) {
                AttachmentDTO attachmentDTO = AttachmentDTO.builder().businessNo(delOutbound.getOrderNo()).businessItemNo(null).fileList(dto.getDocumentsFiles()).attachmentTypeEnum(AttachmentTypeEnum.DEL_OUTBOUND_DOCUMENT).build();
                this.remoteAttachmentService.saveAndUpdate(attachmentDTO);
            }

            if(DelOutboundOrderTypeEnum.BULK_ORDER.getCode().equals(delOutbound.getOrderType())){
                //??????????????????
                AttachmentDTO attachmentDTO = AttachmentDTO.builder().businessNo(orderNo).businessItemNo(null).fileList(dto.getBatchLabels()).attachmentTypeEnum(AttachmentTypeEnum.BULK_ORDER_BOX).build();
                this.remoteAttachmentService.saveAndUpdate(attachmentDTO);
                //????????????????????????
                for (DelOutboundDetailDto detail : dto.getDetails()) {
                    if (detail.getSkuFile() != null && detail.getSkuFile().size() > 0) {
                        AttachmentDataDTO attachmentDataDTO = detail.getSkuFile().get(0);
                        // ????????????
                        AttachmentDTO boxMarkDetailFiels = AttachmentDTO.builder().businessNo(orderNo).businessItemNo("" + detail.getId()).fileList(
                                        Arrays.asList(new AttachmentDataDTO().setAttachmentUrl(attachmentDataDTO.getAttachmentUrl())
                                                .setAttachmentName(attachmentDataDTO.getAttachmentName()))).
                                attachmentTypeEnum(AttachmentTypeEnum.BULK_ORDER_DETAIL).build();
                        this.remoteAttachmentService.saveAndUpdate(boxMarkDetailFiels);

                    }
                }
            }

            // ??????????????????
            this.countPackageSize(inputDelOutbound, dto);
            // ??????????????????????????????
            if (DelOutboundOrderTypeEnum.BATCH.getCode().equals(delOutbound.getOrderType())
                    || DelOutboundOrderTypeEnum.NORMAL.getCode().equals(delOutbound.getOrderType())
                    || DelOutboundOrderTypeEnum.PACKAGE_TRANSFER.getCode().equals(delOutbound.getOrderType())) {
                // ????????????
                List<DelOutboundPackingDto> packings = dto.getPackings();
                this.delOutboundPackingService.save(orderNo, packings, true);
                // ????????????
                AttachmentDTO batchLabel = AttachmentDTO.builder().businessNo(orderNo).businessItemNo(null).fileList(dto.getBatchLabels()).attachmentTypeEnum(AttachmentTypeEnum.DEL_OUTBOUND_BATCH_LABEL).build();
                this.remoteAttachmentService.saveAndUpdate(batchLabel);
            }
            if (DelOutboundOrderTypeEnum.NEW_SKU.getCode().equals(delOutbound.getOrderType())
                    || DelOutboundOrderTypeEnum.SPLIT_SKU.getCode().equals(delOutbound.getOrderType())) {
                // ????????????
                this.delOutboundCombinationService.update(orderNo, dto.getCombinations());
            }

            if (DelOutboundOrderTypeEnum.MULTIPLE_PIECES.getCode().equals(delOutbound.getOrderType())) {
                //??????????????????
                //??????
                List<AttachmentDataDTO> batchLabels = new ArrayList();
                //?????????
                List<AttachmentDataDTO> documentsFiles = new ArrayList();
                if (dto.getDocumentsFiles() != null) {
                    for (AttachmentDataDTO vo : dto.getDocumentsFiles()) {
                        if (AttachmentTypeEnum.MULTIPLE_PIECES_BOX_MARK.getAttachmentType().equals(vo.getAttachmentType())) {
                            batchLabels.add(vo);
                        } else if (AttachmentTypeEnum.MULTIPLE_PIECES_INVOICE.getAttachmentType().equals(vo.getAttachmentType())) {
                            documentsFiles.add(vo);
                        }
                    }
                }
                // ????????????
                AttachmentDTO batchLabel = AttachmentDTO.builder().businessNo(orderNo).businessItemNo(null).fileList(batchLabels)
                        .attachmentTypeEnum(AttachmentTypeEnum.MULTIPLE_PIECES_BOX_MARK).build();
                this.remoteAttachmentService.saveAndUpdate(batchLabel);
                // ???????????????
                AttachmentDTO invoiceBatchLabel = AttachmentDTO.builder().businessNo(orderNo).businessItemNo(null).fileList(documentsFiles)
                        .attachmentTypeEnum(AttachmentTypeEnum.MULTIPLE_PIECES_INVOICE).build();
                this.remoteAttachmentService.saveAndUpdate(invoiceBatchLabel);

                // ?????????????????????
                this.remoteAttachmentService.deleteByBusinessNo(AttachmentDTO.builder().businessNo(orderNo).
                        attachmentTypeEnum(AttachmentTypeEnum.MULTIPLE_PIECES_BOX_DETAIL).build());
                this.remoteAttachmentService.deleteByBusinessNo(AttachmentDTO.builder().businessNo(orderNo).
                        attachmentTypeEnum(AttachmentTypeEnum.MULTIPLE_PIECES_SKU).build());


                for (DelOutboundDetailDto detail : dto.getDetails()) {
                    if (detail.getBoxMarkFile() != null && detail.getBoxMarkFile().size() > 0) {
                        AttachmentDataDTO attachmentDataDTO = detail.getBoxMarkFile().get(0);
                        attachmentDataDTO.setId(null);
                        // ????????????
                        AttachmentDTO boxMarkDetailFiels = AttachmentDTO.builder().businessNo(orderNo).businessItemNo("" + detail.getId()).fileList(
                                Arrays.asList(attachmentDataDTO)).attachmentTypeEnum(AttachmentTypeEnum.MULTIPLE_PIECES_BOX_DETAIL).build();
                        this.remoteAttachmentService.saveAndUpdate(boxMarkDetailFiels);

                    }
                    if (detail.getSkuFile() != null && detail.getSkuFile().size() > 0) {
                        AttachmentDataDTO attachmentDataDTO = detail.getSkuFile().get(0);
                        attachmentDataDTO.setId(null);
                        // SKU
                        AttachmentDTO skuFiles = AttachmentDTO.builder().businessNo(orderNo).businessItemNo("" + detail.getId()).fileList(
                                Arrays.asList(attachmentDataDTO)).attachmentTypeEnum(AttachmentTypeEnum.MULTIPLE_PIECES_SKU).build();
                        this.remoteAttachmentService.saveAndUpdate(skuFiles);
                    }

                }
            }
            //??????????????????????????????
            inputDelOutbound.setForecastWeight(inputDelOutbound.getWeight());

            // ??????
            int i = baseMapper.updateById(inputDelOutbound);
            DelOutboundOperationLogEnum.UPDATE.listener(delOutbound);
            return i;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw e;
        }
    }

    /**
     * ????????????
     *
     * @param orderType     orderType
     * @param orderNo       orderNo
     * @param warehouseCode warehouseCode
     * @param cusCode       cusCode
     */
    @Override
    public void unFreeze(String orderType, String orderNo, String warehouseCode, String cusCode) {
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
        if (DelOutboundOrderTypeEnum.COLLECTION.getCode().equals(orderType)) {
            inventoryOperateListDto.setFreeType(1);
        }
        inventoryOperateListDto.setCusCode(cusCode);
        this.unFreeze(inventoryOperateListDto);
    }

    @Transactional
    @Override
    public void unFreeze(DelOutbound delOutbound) {
        String orderType = delOutbound.getOrderType();
        String orderNo = delOutbound.getOrderNo();
        String warehouseCode = delOutbound.getWarehouseCode();
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
            inventoryOperateListDto.setCusCode(delOutbound.getSellerCode());
            this.unFreeze(inventoryOperateListDto);
        } else {
            this.unFreeze(orderType, orderNo, warehouseCode, delOutbound.getSellerCode());
        }
    }

    @Transactional
    @Override
    public boolean toPrint(DelOutboundToPrintDto dto) {
        LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.set(DelOutbound::getIsPrint, true);
        if (dto.isBatch()) {
            updateWrapper.in(DelOutbound::getId, dto.getIds());
        } else {
            updateWrapper.eq(DelOutbound::getId, dto.getId());
        }
        return super.update(updateWrapper);
    }

    @Transactional
    @Override
    public List<Map<String, Object>> batchUpdateTrackingNo(List<DelOutboundBatchUpdateTrackingNoDto> list) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        Map map1=new HashMap();
        //?????????????????????
        List<DelOutboundBatchUpdateTrackingNoDto> list1=new ArrayList<>();
        int a=0;
        int b=0;
        for (int i = 0; i < list.size(); i++) {
            DelOutboundBatchUpdateTrackingNoDto updateTrackingNoDto = list.get(i);
            if (StringUtils.isEmpty(updateTrackingNoDto.getOrderNo())) {
                Map<String, Object> result = new HashMap<>();
                result.put("msg", "??? " + (i + 1) + " ?????????????????????????????????");
                resultList.add(result);
                continue;
            }
            if (StringUtils.isEmpty(updateTrackingNoDto.getTrackingNo())) {
                Map<String, Object> result = new HashMap<>();
                result.put("msg", "??? " + (i + 1) + " ???????????????????????????");
                resultList.add(result);
                continue;
            }
            //??????????????????????????????????????????
            baseMapper.updateFssAccountSerial(list.get(i));

            //??????????????????????????????????????????
            if (StringUtils.isNotEmpty(updateTrackingNoDto.getAmazonLogisticsRouteId())){
                baseMapper.updateamazonLogisticsRouteId(list.get(i));

            }
            if (StringUtils.isEmpty(updateTrackingNoDto.getEmailType())){
               list.get(i).setEmailType("???");
            }



            //?????????????????????
            DelOutbound delOutbound=baseMapper.selectTrackingNo(updateTrackingNoDto.getOrderNo());
            if (delOutbound!=null){
                a=a+1;
                DelOutboundTarckOn delOutboundTarckOn=new DelOutboundTarckOn();
                delOutboundTarckOn.setOrderNo(delOutbound.getOrderNo());
                delOutboundTarckOn.setTrackingNo(delOutbound.getTrackingNo());
                delOutboundTarckOn.setUpdateTime(new Date());
                delOutboundTarckOn.setTrackingNoNew(updateTrackingNoDto.getTrackingNo());
                delOutboundTarckOnMapper.insertSelective(delOutboundTarckOn);
                BasTrackingPush basTrackingPush=new BasTrackingPush();
                basTrackingPush.setTrackingNo(updateTrackingNoDto.getTrackingNo());
                basTrackingPush.setOrderNo(delOutbound.getOrderNo());
                basTrackingPush.setWarehouseCode(delOutbound.getWarehouseCode());
                basTrackingPushMapper.insertSelective(basTrackingPush);
                updateTrackingNoDto.setRefNo(delOutbound.getRefNo());
                list1.add(updateTrackingNoDto);
//                ShipmentTrackingChangeRequestDto
//                R<ResponseVO> r= htpOutboundFeignService.shipmentTracking(shipmentTrackingChangeRequestDto);
            }else if (delOutbound==null){
                b=b+1;
                DelOutboundTarckError delOutboundTarckError=new DelOutboundTarckError();
                BeanUtils.copyProperties(updateTrackingNoDto,delOutboundTarckError);
                delOutboundTarckError.setErrorReason("?????????????????????");
                delOutboundTarckErrorMapper.insertSelective(delOutboundTarckError);
            }
            //???????????????
            map1.put("list1",list1);


//            if (u < 1) {
//                Map<String, Object> result = new HashMap<>();
//                result.put("msg", "??? " + (i + 1) + " ??????????????????????????????");
//                resultList.add(result);
//            }
        }
        Map map=new HashMap();
        map.put("successNumber",a);
        map.put("errorNumber",b);
        resultList.add(map);
        resultList.add(map1);
        /*
        int size = list.size();
        executeBatch(sqlSession -> {
            int i = 0;
            for (DelOutboundBatchUpdateTrackingNoDto dto : list) {
                sqlSession.update("updateTrackingNo", dto);
                if ((i % 100 == 0) || i == size) {
                    sqlSession.flushStatements();
                }
                i++;
            }
        });*/
        return resultList;
    }

    /**
     * ????????????
     * @param list list
     */
    @Override
    public void emailBatchUpdateTrackingNo(List<Map<String, Object>> list,String filepath) {
        //?????????????????????
        logger.info("??????????????????-----");
        Map map=list.get(1);
        List<DelOutboundBatchUpdateTrackingNoDto> list1= (List<DelOutboundBatchUpdateTrackingNoDto>) map.get("list1");
        if (list1.size()>0) {
            //???????????????????????????????????????????????????
            List<String> orderNos = list1.stream().map(DelOutboundBatchUpdateTrackingNoDto::getOrderNo).collect(Collectors.toList());
            List<DelOutbound> delOutboundlist2 = baseMapper.selectorderNos(orderNos);
            logger.info("delOutboundlist2???????????????:{}",delOutboundlist2);
            //????????????????????????????????????????????????????????????code
           for (DelOutboundBatchUpdateTrackingNoDto dto:list1){
               delOutboundlist2.stream().filter(x->x.getOrderNo().equals(dto.getOrderNo())).findFirst().ifPresent(i -> {
                   if (i.getCustomCode()!=null&&!i.getCustomCode().equals("")){
                       dto.setCustomCode(i.getCustomCode());
                   }
                   if (i.getTrackingNo()!=null&&!i.getTrackingNo().equals("")){
                       dto.setNoTrackingNo(i.getTrackingNo());
                   }


               });



               }

           //??????????????????????????????
            List<BasSeller> basSellerList= baseMapper.selectdelsellerCodes();
           List<DelOutboundBatchUpdateTrackingNoEmailDto> delOutboundBatchUpdateTrackingNoEmailDtoList=new ArrayList<>();

            for (DelOutboundBatchUpdateTrackingNoDto dto:list1) {

                basSellerList.stream().filter(x -> x.getSellerCode().equals(dto.getCustomCode())).findFirst().ifPresent(basSeller -> {

                    DelOutboundBatchUpdateTrackingNoEmailDto delOutboundBatchUpdateTrackingNoEmailDto = new DelOutboundBatchUpdateTrackingNoEmailDto();
                    BeanUtils.copyProperties(dto, delOutboundBatchUpdateTrackingNoEmailDto);
                    //????????????????????????
                    //delOutboundBatchUpdateTrackingNoEmailDto.setSellerEmail(basSeller.getEmail());

                    if (basSeller.getServiceManagerName() != null && !basSeller.getServiceManagerName().equals("")) {
                        if (!basSeller.getServiceManagerName().equals(basSeller.getServiceStaffName())) {




//                            delOutboundBatchUpdateTrackingNoEmailDto.setEmpCode(basSeller.getServiceManagerName());
//                            delOutboundBatchUpdateTrackingNoEmailDto.setServiceManagerName(basSeller.getServiceManagerName());
                            if (basSeller.getServiceStaffName() != null && !basSeller.getServiceStaffName().equals("")) {
                                delOutboundBatchUpdateTrackingNoEmailDto.setServiceStaffName(basSeller.getServiceStaffName());
                            }

                        }
                    }
                    if (basSeller.getServiceStaffName() != null && !basSeller.getServiceStaffName().equals("")) {

//                        delOutboundBatchUpdateTrackingNoEmailDto.setEmpCode(basSeller.getServiceStaffName());
//                        delOutboundBatchUpdateTrackingNoEmailDto.setServiceStaffName(basSeller.getServiceStaffName());

                        if (basSeller.getServiceManagerName() != null && !basSeller.getServiceManagerName().equals("")) {
                            delOutboundBatchUpdateTrackingNoEmailDto.setServiceManagerName(basSeller.getServiceManagerName());
                        }
//                        delOutboundBatchUpdateTrackingNoEmailDtoList.add(delOutboundBatchUpdateTrackingNoEmailDto);

                    }



                    delOutboundBatchUpdateTrackingNoEmailDtoList.add(delOutboundBatchUpdateTrackingNoEmailDto);


                });


                boolean a = basSellerList.stream().filter(x -> x.getSellerCode().equals(dto.getCustomCode())).findFirst().isPresent();

                if (a == false) {
                        DelOutboundBatchUpdateTrackingNoEmailDto delOutboundBatchUpdateTrackingNoEmailDto = new DelOutboundBatchUpdateTrackingNoEmailDto();
                        delOutboundBatchUpdateTrackingNoEmailDto.setOrderNo(dto.getOrderNo());
                        delOutboundBatchUpdateTrackingNoEmailDto.setTrackingNo(dto.getTrackingNo());
                        int u = super.baseMapper.updateTrackingNo(delOutboundBatchUpdateTrackingNoEmailDto);
                    //manualTrackingYees(dto.getOrderNo());


                }
                if (delOutboundBatchUpdateTrackingNoEmailDtoList.size()==0){
                    DelOutboundBatchUpdateTrackingNoEmailDto delOutboundBatchUpdateTrackingNoEmailDto = new DelOutboundBatchUpdateTrackingNoEmailDto();
                    delOutboundBatchUpdateTrackingNoEmailDto.setOrderNo(dto.getOrderNo());
                    delOutboundBatchUpdateTrackingNoEmailDto.setTrackingNo(dto.getTrackingNo());
                    int u = super.baseMapper.updateTrackingNo(delOutboundBatchUpdateTrackingNoEmailDto);
                    //manualTrackingYees(dto.getOrderNo());
                }

            }

            BasEmployees basEmployees=new BasEmployees();
            //???????????????
            R<List<BasEmployees>> basEmployeesR= basFeignService.empList(basEmployees);

            List<BasEmployees> basEmployeesList=basEmployeesR.getData();
            //????????????????????????
            for (DelOutboundBatchUpdateTrackingNoEmailDto dto:delOutboundBatchUpdateTrackingNoEmailDtoList){
                List<String> serviceManagerStaffName=new ArrayList<>();
                basEmployeesList.stream().filter(x->x.getEmpCode().equals(dto.getServiceManagerName())).findFirst().ifPresent(i -> {
                    if (i.getEmail()!=null&&!i.getEmail().equals("")){
                        serviceManagerStaffName.add(i.getEmail());
                    }
                    dto.setServiceManagerName(i.getEmpName());
                });
                basEmployeesList.stream().filter(x->x.getEmpCode().equals(dto.getServiceStaffName())).findFirst().ifPresent(i -> {
                    if (i.getEmail()!=null&&!i.getEmail().equals("")){
                        serviceManagerStaffName.add(i.getEmail());
                    }


                    dto.setServiceStaffName(i.getEmpName());

                });
                //????????????????????????
                //serviceManagerStaffName.add(dto.getSellerEmail());
                //??????????????????
                List<String> listWithoutDuplicates = serviceManagerStaffName.stream().distinct().collect(Collectors.toList());

                String email = StringUtils.join(listWithoutDuplicates,",");
                dto.setEmail(email);
               boolean a= basEmployeesList.stream().filter(x->x.getEmpCode().equals(dto.getEmpCode())).findFirst().isPresent();
                if (a==false){
                    DelOutboundBatchUpdateTrackingNoEmailDto delOutboundBatchUpdateTrackingNoEmailDto=new DelOutboundBatchUpdateTrackingNoEmailDto();
                    delOutboundBatchUpdateTrackingNoEmailDto.setOrderNo(dto.getOrderNo());
                    delOutboundBatchUpdateTrackingNoEmailDto.setTrackingNo(dto.getTrackingNo());
                    int u = super.baseMapper.updateTrackingNo(delOutboundBatchUpdateTrackingNoEmailDto);

                    //manualTrackingYees(dto.getOrderNo());
                }
                if (dto.getEmail()==null){
                    DelOutboundBatchUpdateTrackingNoEmailDto delOutboundBatchUpdateTrackingNoEmailDto=new DelOutboundBatchUpdateTrackingNoEmailDto();
                    delOutboundBatchUpdateTrackingNoEmailDto.setOrderNo(dto.getOrderNo());
                    delOutboundBatchUpdateTrackingNoEmailDto.setTrackingNo(dto.getTrackingNo());
                    int u = super.baseMapper.updateTrackingNo(delOutboundBatchUpdateTrackingNoEmailDto);

                    //manualTrackingYees(dto.getOrderNo());
                }
            }

            //?????????????????? ?????????Map<List> (?????????key,????????????????????????????????????)
           Map<String,List<DelOutboundBatchUpdateTrackingNoEmailDto>> delOutboundBatchUpdateTrackingNoEmailDtoMap=delOutboundBatchUpdateTrackingNoEmailDtoList.stream().collect(Collectors.groupingBy(DelOutboundBatchUpdateTrackingNoEmailDto::getCustomCode));
            //??????map??????????????????????????? ????????????excel????????????
            for (Map.Entry<String, List<DelOutboundBatchUpdateTrackingNoEmailDto>> entry : delOutboundBatchUpdateTrackingNoEmailDtoMap.entrySet()) {
                System.out.println("key = " + entry.getKey() + ", value = " + entry.getValue());
                logger.info("???????????????{}",entry.getValue());
                ExcleDelOutboundBatchUpdateTracking(entry.getValue(),entry.getKey(), filepath);
            }

        }

    }

    public void ExcleDelOutboundBatchUpdateTracking(List<DelOutboundBatchUpdateTrackingNoEmailDto> list,String customCode,String filepath){
        logger.info("??????????????????1???{}",list);
        EmailDto emailDto=new EmailDto();
        //????????????
        emailDto.setModularType(0);
        //emailDto.setSubject("Notice on Update of Tracking Number-"+list.get(0).getCustomCode()+"-"+simpleDateFormat.format(new Date()));
        //???????????????
        //emailDto.setTo(email);
        //emailDto.setText("customer:"+list.get(0).getCustomCode()+"on"+"["+simpleDateFormat.format(new Date())+"]"+"Total number of updated tracking numbers"+"["+list.size()+"]"+"Please download the attachment for details");
        List<EmailObjectDto> emailObjectDtoList= BeanMapperUtil.mapList(list, EmailObjectDto.class);
        emailDto.setList(emailObjectDtoList);
        if(customCode!=null&&!customCode.equals("")){
            R r= emailFeingService.sendEmail(emailDto);
            if (r.getCode()== HttpStatus.SUCCESS){

            }
        }
        logger.info("??????????????????2???{}",list);
        list.forEach(x->{
            int u = super.baseMapper.updateTrackingNo(x);

            //manualTrackingYees(x.getOrderNo());

        });

    }



    /**
     * ???????????????????????????
     *
     * @param ids ??????????????????????????????ID
     * @return ??????
     */
    @Transactional
    @Override
    public int deleteDelOutboundByIds(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        LambdaQueryWrapper<DelOutbound> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.select(DelOutbound::getOrderNo, DelOutbound::getWarehouseCode, DelOutbound::getState);
        queryWrapper.in(DelOutbound::getId, ids);
        List<DelOutbound> list = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(list)) {
            return 0;
        }
        if (list.size() != ids.size()) {
            throw new CommonException("400", "?????????????????????????????????????????????");
        }
        DelOutbound delOutbound = list.get(0);
        Map<String, DelOutbound> delOutboundMap = new HashMap<>();
        for (DelOutbound delOutbound1 : list) {
            // ?????????????????????????????????????????????
            if (!(DelOutboundStateEnum.REVIEWED.getCode().equals(delOutbound1.getState())
                    || DelOutboundStateEnum.AUDIT_FAILED.getCode().equals(delOutbound1.getState()))) {
                throw new CommonException("400", "?????????????????????????????????????????????");
            }
            if (!delOutbound.getWarehouseCode().equals(delOutbound1.getWarehouseCode())) {
                throw new CommonException("400", "?????????????????????????????????????????????");
            }
            delOutboundMap.put(delOutbound1.getOrderNo(), delOutbound1);
        }
        List<String> orderNos = list.stream().map(DelOutbound::getOrderNo).collect(Collectors.toList());
        // ????????????
        LambdaQueryWrapper<DelOutboundAddress> addressLambdaQueryWrapper = Wrappers.lambdaQuery();
        addressLambdaQueryWrapper.in(DelOutboundAddress::getOrderNo, orderNos);
        this.delOutboundAddressService.remove(addressLambdaQueryWrapper);
        // ??????????????????
        this.delOutboundPackingService.deleted(orderNos);
        // ????????????
        for (String orderNo : orderNos) {
            DelOutbound delOutbound1 = delOutboundMap.get(orderNo);
            // ????????????
            String bringVerifyState = delOutbound.getBringVerifyState();
            if (StringUtils.isNotEmpty(bringVerifyState)) {
                // ?????????????????????????????????
                if (BringVerifyEnum.gt(BringVerifyEnum.FREEZE_INVENTORY, BringVerifyEnum.get(bringVerifyState))) {
                    // ??????????????????
                    this.unFreeze(delOutbound1);
                }
                // ???????????????????????????????????????
                if (BringVerifyEnum.gt(BringVerifyEnum.FREEZE_OPERATION, BringVerifyEnum.get(bringVerifyState))) {
                    // ????????????????????????
                    this.unfreezeOperation(orderNo, delOutbound1.getOrderType());
                }
            }
            DelOutboundOperationLogEnum.DELETE.listener(delOutbound1);
        }
        // ????????????
        LambdaQueryWrapper<DelOutboundDetail> detailLambdaQueryWrapper = Wrappers.lambdaQuery();
        detailLambdaQueryWrapper.in(DelOutboundDetail::getOrderNo, orderNos);
        this.delOutboundDetailService.remove(detailLambdaQueryWrapper);
        int i = baseMapper.deleteBatchIds(ids);
        if (i != ids.size()) {
            throw new CommonException("400", "???????????????????????????????????????");
        }
        // ??????????????????
        return i;
    }

    /**
     * ???????????????????????????
     *
     * @param id ???????????????ID
     * @return ??????
     */
    @Transactional
    @Override
    public int deleteDelOutboundById(String id) {
        DelOutbound delOutbound = this.getById(id);
        if (null == delOutbound) {
            throw new CommonException("400", "???????????????");
        }
        // ????????????
        String orderNo = delOutbound.getOrderNo();
        this.deleteAddress(orderNo);
        this.deleteDetail(orderNo);
        return baseMapper.deleteById(id);
    }

    @Transactional
    @Override
    public int shipmentOperationType(ShipmentRequestDto dto) {
        List<String> orderNos = dto.getShipmentList();
        if (CollectionUtils.isEmpty(orderNos)) {
            throw new CommonException("400", "???????????????????????????");
        }
        // ?????????????????????
        if (DelOutboundOperationTypeEnum.CANCELED.getCode().equals(dto.getOperationType())) {
            LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
            if (StringUtils.isNotEmpty(dto.getWarehouseCode())) {
                updateWrapper.eq(DelOutbound::getWarehouseCode, dto.getWarehouseCode());
            }
            updateWrapper.in(DelOutbound::getOrderNo, orderNos);
            updateWrapper.set(DelOutbound::getOperationType, dto.getOperationType());
            updateWrapper.set(DelOutbound::getOperationTime, dto.getOperationTime());
            updateWrapper.set(DelOutbound::getState, DelOutboundStateEnum.WHSE_CANCELLED.getCode());
            // ????????????????????????
            this.baseMapper.update(null, updateWrapper);
            // ????????????????????????????????????????????????????????????
            this.delOutboundCompletedService.add(orderNos, DelOutboundOperationTypeEnum.CANCELED.getCode());
        } else if (DelOutboundOperationTypeEnum.PROCESSING.getCode().equals(dto.getOperationType())) {
            // ???????????????????????????
            this.delOutboundCompletedService.add(orderNos, DelOutboundOperationTypeEnum.PROCESSING.getCode());
        } else if (DelOutboundOperationTypeEnum.SHIPPED.getCode().equals(dto.getOperationType())) {
            LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
            if (StringUtils.isNotEmpty(dto.getWarehouseCode())) {
                updateWrapper.eq(DelOutbound::getWarehouseCode, dto.getWarehouseCode());
            }
            updateWrapper.in(DelOutbound::getOrderNo, orderNos);
            updateWrapper.set(DelOutbound::getOperationType, dto.getOperationType());
            updateWrapper.set(DelOutbound::getOperationTime, dto.getOperationTime());
            updateWrapper.set(DelOutbound::getState, DelOutboundStateEnum.WHSE_COMPLETED.getCode());
            this.baseMapper.update(null, updateWrapper);
            // ????????????????????????????????????????????????????????????
            this.delOutboundCompletedService.add(orderNos, DelOutboundOperationTypeEnum.SHIPPED.getCode());
        }
        return orderNos.size();
    }

    @Transactional
    @Override
    public int shipmentPackingMaterial(ShipmentPackingMaterialRequestDto dto) {
        return this.shipmentPackingMaterial(dto, DelOutboundStateEnum.PROCESSING);
    }

    private int shipmentPackingMaterial(ShipmentPackingMaterialRequestDto dto, DelOutboundStateEnum stateEnum) {
        LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
        if (StringUtils.isNotEmpty(dto.getWarehouseCode())) {
            updateWrapper.eq(DelOutbound::getWarehouseCode, dto.getWarehouseCode());
        }
        updateWrapper.eq(DelOutbound::getOrderNo, dto.getOrderNo());
        if (null != stateEnum) {
            updateWrapper.set(DelOutbound::getState, stateEnum.getCode());
        }
        updateWrapper.set(DelOutbound::getPackingMaterial, dto.getPackingMaterial());
        int i = this.baseMapper.update(null, updateWrapper);
        return i;
    }

    @Transactional
    @Override
    public int shipmentPackingMaterialIgnoreState(ShipmentPackingMaterialRequestDto dto) {
        return this.shipmentPackingMaterial(dto, null);
    }

    @Transactional
    @Override
    public int shipmentPacking(ShipmentPackingMaterialRequestDto dto, String orderType,ShipmentEnum shipmentState) {
        LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
        if (StringUtils.isNotEmpty(dto.getWarehouseCode())) {
            updateWrapper.eq(DelOutbound::getWarehouseCode, dto.getWarehouseCode());
        }
        updateWrapper.eq(DelOutbound::getOrderNo, dto.getOrderNo());
        if (StringUtils.isNotEmpty(dto.getPackingMaterial())) {
            updateWrapper.set(DelOutbound::getPackingMaterial, dto.getPackingMaterial());
        }
        // ??????????????????
        Double length = Utils.defaultValue(dto.getLength());
        Double width = Utils.defaultValue(dto.getWidth());
        Double height = Utils.defaultValue(dto.getHeight());
        Double weight = Utils.defaultValue(dto.getWeight());
        updateWrapper.set(DelOutbound::getLength, length);
        updateWrapper.set(DelOutbound::getWidth, width);
        updateWrapper.set(DelOutbound::getHeight, height);
        updateWrapper.set(DelOutbound::getWeight, weight);
        // ????????????*???*???
        updateWrapper.set(DelOutbound::getSpecifications, length + "*" + width + "*" + height);
        // ????????????????????????
        updateWrapper.set(DelOutbound::getState, DelOutboundStateEnum.PROCESSING.getCode());

        if (shipmentState != null) {
            updateWrapper.set(DelOutbound::getShipmentState, shipmentState);
        }
        updateWrapper.eq(DelOutbound::getOrderNo,dto.getOrderNo());
        return this.baseMapper.update(null, updateWrapper);
    }

    @Transactional
    @Override
    public int shipmentContainers(ShipmentContainersRequestDto dto) {
        // ??????????????????
        List<ContainerInfoDto> containerList = dto.getContainerList();
        this.delOutboundPackingService.save(dto.getOrderNo(), containerList);
        // ??????????????????
        LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.set(DelOutbound::getContainerState, DelOutboundConstant.CONTAINER_STATE_1);
        updateWrapper.eq(DelOutbound::getOrderNo, dto.getOrderNo());
        return this.baseMapper.update(null, updateWrapper);
    }

    @Override
    public DelOutbound selectDelOutboundByOrderId(String orderId) {
        LambdaQueryWrapper<DelOutbound> query = Wrappers.lambdaQuery();
        query.eq(DelOutbound::getOrderNo, orderId);
        DelOutbound delOutbound = baseMapper.selectOne(query);
        if (Objects.isNull(delOutbound)) {
            throw new CommonException("400", "???????????????");
        }
        return delOutbound;
    }

    @Transactional
    @Override
    public void updateState(Long id, DelOutboundStateEnum stateEnum) {
        LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.set(DelOutbound::getState, stateEnum.getCode());
        updateWrapper.eq(DelOutbound::getId, id);
        this.update(updateWrapper);
    }

    @Override
    public List<DelOutboundDetailListVO> getDelOutboundDetailsList(DelOutboundListQueryDto queryDto) {
        QueryWrapper<DelOutboundListQueryDto> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("a.order_type", queryDto.getOrderType());
        queryWrapper.eq("a.warehouse_code", queryDto.getWarehouseCode());
        if (StringUtils.isNotBlank(queryDto.getState())) {
            queryWrapper.eq("a.state", queryDto.getState());
        }
        if (StringUtils.isNotBlank(queryDto.getUpdateTime())) {
            queryWrapper.ge("a.update_time", queryDto.getUpdateTime());
        }
        return baseMapper.getDelOutboundAndDetailsList(queryWrapper);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void bringVerifyFail(Long id, String exceptionMessage) {
        LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.set(DelOutbound::getState, DelOutboundStateEnum.AUDIT_FAILED.getCode());
        updateWrapper.set(DelOutbound::getExceptionState, DelOutboundExceptionStateEnum.ABNORMAL.getCode());
        updateWrapper.set(DelOutbound::getExceptionMessage, StringUtils.substring(exceptionMessage, 0, 255));
        updateWrapper.eq(DelOutbound::getId, id);
        this.update(updateWrapper);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void exceptionMessage(Long id, String exceptionMessage) {
        DelOutbound modifyDelOutbound = new DelOutbound();
        modifyDelOutbound.setId(id);
        modifyDelOutbound.setExceptionState(DelOutboundExceptionStateEnum.ABNORMAL.getCode());
        exceptionMessage = org.apache.commons.lang3.StringUtils.substring(exceptionMessage, 0, 255);
        modifyDelOutbound.setExceptionMessage(exceptionMessage);
        this.updateById(modifyDelOutbound);
    }

    @Transactional
    @Override
    public void exceptionFix(Long id) {
        DelOutbound modifyDelOutbound = new DelOutbound();
        modifyDelOutbound.setId(id);
        modifyDelOutbound.setExceptionState(DelOutboundExceptionStateEnum.NORMAL.getCode());
        modifyDelOutbound.setExceptionMessage("");
        this.updateById(modifyDelOutbound);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void bringVerifyFail(DelOutbound delOutbound) {
        delOutbound.setState(DelOutboundStateEnum.AUDIT_FAILED.getCode());
        delOutbound.setExceptionState(DelOutboundExceptionStateEnum.ABNORMAL.getCode());
        String exceptionMessage = delOutbound.getExceptionMessage();
        if (StringUtils.isNotEmpty(exceptionMessage)) {
            exceptionMessage = org.apache.commons.lang3.StringUtils.substring(exceptionMessage, 0, 255);
            delOutbound.setExceptionMessage(exceptionMessage);
        }
        // ??????????????????
        delOutbound.setBringVerifyTime(new Date());
        this.updateById(delOutbound);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void bringVerifySuccess(DelOutbound delOutbound) {
        delOutbound.setState(DelOutboundStateEnum.DELIVERED.getCode());
        delOutbound.setExceptionState(DelOutboundExceptionStateEnum.NORMAL.getCode());
        // ??????????????????
        delOutbound.setExceptionMessage("");
        // ??????????????????
        delOutbound.setBringVerifyTime(new Date());
        this.updateById(delOutbound);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void shipmentFail(DelOutbound delOutbound) {
        delOutbound.setState(DelOutboundStateEnum.PROCESSING.getCode());
        delOutbound.setExceptionState(DelOutboundExceptionStateEnum.ABNORMAL.getCode());
        this.updateById(delOutbound);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void shipmentSuccess(DelOutbound delOutbound) {
        delOutbound.setState(DelOutboundStateEnum.NOTIFY_WHSE_PROCESSING.getCode());
        delOutbound.setExceptionState(DelOutboundExceptionStateEnum.NORMAL.getCode());
        delOutbound.setExceptionMessage("");
        this.updateById(delOutbound);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void updateByIdTransactional(DelOutbound delOutbound) {
        this.updateById(delOutbound);
    }

    @Override
    public DelOutbound getByOrderNo(String orderNo) {
        LambdaQueryWrapper<DelOutbound> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(DelOutbound::getOrderNo, orderNo);
        return this.getOne(queryWrapper);
    }

    @Transactional
    @Override
    public void completed(Long id) {
        LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.set(DelOutbound::getState, DelOutboundStateEnum.COMPLETED.getCode());
        updateWrapper.set(DelOutbound::getShipmentsTime, new Date());
        updateWrapper.eq(DelOutbound::getId, id);
        this.update(updateWrapper);
    }

    @Transactional
    @Override
    public void updateCompletedState(Long id, String completedState) {
        DelOutbound modifyDelOutbound = new DelOutbound();
        modifyDelOutbound.setId(id);
        modifyDelOutbound.setCompletedState(completedState);
        this.updateById(modifyDelOutbound);
    }

    @Transactional
    @Override
    public void updateCancelledState(Long id, String cancelledState) {
        DelOutbound modifyDelOutbound = new DelOutbound();
        modifyDelOutbound.setId(id);
        modifyDelOutbound.setCancelledState(cancelledState);
        this.updateById(modifyDelOutbound);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int canceled(DelOutboundCanceledDto dto) {
        List<Long> ids = dto.getIds();
        List<String> orderNos1 = dto.getOrderNos();
        if (CollectionUtils.isEmpty(ids) && CollectionUtils.isEmpty(orderNos1)) {
            throw new CommonException("400", "????????????????????????????????????");
        }
        List<DelOutbound> outboundList;
        int cancelSize;
        if (CollectionUtils.isNotEmpty(ids)) {
            // ??????ids????????????
            outboundList = this.listByIds(ids);
            cancelSize = ids.size();
        } else {
            // ???????????????????????????
            LambdaQueryWrapper<DelOutbound> queryWrapper = Wrappers.lambdaQuery();
            queryWrapper.in(DelOutbound::getOrderNo, orderNos1);
            if (null != dto.getOrderType()) {
                queryWrapper.eq(DelOutbound::getOrderType, dto.getOrderType().getCode());
            }
            // ???????????????????????????
            if (null != dto.getSellerCode()) {
                queryWrapper.eq(DelOutbound::getSellerCode, dto.getSellerCode());
            }
            outboundList = this.list(queryWrapper);
            cancelSize = orderNos1.size();
        }
        if (CollectionUtils.isEmpty(outboundList)) {
            throw new CommonException("400", "??????????????????????????????");
        }
        if (cancelSize != outboundList.size()) {
            throw new CommonException("400", "????????????????????????????????????");
        }
        List<String> orderNos = new ArrayList<>();
        String warehouseCode = outboundList.get(0).getWarehouseCode();
        List<String> reviewedList = new ArrayList<>();
        Map<String, DelOutbound> delOutboundMap = new HashMap<>();
        for (DelOutbound outbound : outboundList) {
            if (!warehouseCode.equals(outbound.getWarehouseCode())) {
                throw new CommonException("400", "????????????????????????????????????");
            }
            String orderNo = outbound.getOrderNo();
            delOutboundMap.put(orderNo, outbound);
            // ??????????????????????????????
            if (DelOutboundStateEnum.COMPLETED.getCode().equals(outbound.getState())) {
                // ??????????????????????????????????????????
                // continue;
                throw new CommonException("400", "?????????????????????????????????????????????");
            }

            if (DelOutboundStateEnum.REVIEWED_DOING.getCode().equals(outbound.getState())) {
                // continue;
                throw new CommonException("400", "?????????????????????????????????????????????");
            }

            if (DelOutboundStateEnum.CANCELLED.getCode().equals(outbound.getState())) {
                throw new CommonException("400", "?????????????????????????????????????????????");
            }
            // ?????????????????????????????????
            if (DelOutboundStateEnum.REVIEWED.getCode().equals(outbound.getState())
                    || DelOutboundStateEnum.AUDIT_FAILED.getCode().equals(outbound.getState())) {
                // ??????????????????????????????
                reviewedList.add(orderNo);
                continue;
            }
            // ??????WMS?????????
            orderNos.add(orderNo);
        }
        // ????????????????????????????????????????????????
        if (CollectionUtils.isNotEmpty(reviewedList)) {
            // ????????????????????????
            LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
            updateWrapper.set(DelOutbound::getState, DelOutboundStateEnum.CANCELLED.getCode());
            // ????????????????????????
            updateWrapper.set(DelOutbound::getExceptionState, DelOutboundExceptionStateEnum.NORMAL.getCode());
            updateWrapper.set(DelOutbound::getExceptionMessage, "");
            updateWrapper.in(DelOutbound::getOrderNo, reviewedList);
            this.update(updateWrapper);
            // ?????????????????????
            for (String orderNo : reviewedList) {
                DelOutbound delOutbound = delOutboundMap.get(orderNo);
                // ????????????
                String bringVerifyState = delOutbound.getBringVerifyState();
                if (StringUtils.isNotEmpty(bringVerifyState)) {
                    // ?????????????????????????????????
                    if (BringVerifyEnum.gt(BringVerifyEnum.FREEZE_INVENTORY, BringVerifyEnum.get(bringVerifyState))) {
                        // ??????????????????
                        this.unFreeze(delOutbound);
                    }
                    // ???????????????????????????????????????
                    if (BringVerifyEnum.gt(BringVerifyEnum.FREEZE_OPERATION, BringVerifyEnum.get(bringVerifyState))) {
                        // ????????????????????????
                        this.unfreezeOperation(orderNo, delOutbound.getOrderType());
                    }
                }
                DelOutboundOperationLogEnum.CANCEL.listener(delOutbound);
            }
        }
        // ??????????????????WMS??????
        if (CollectionUtils.isEmpty(orderNos)) {
            return reviewedList.size();
        }

        int count = delOutboundThirdPartyService.count(Wrappers.<DelOutboundThirdParty>query().lambda()
                .eq(DelOutboundThirdParty::getOperationType,"WMS")
                .eq(DelOutboundThirdParty::getState,"SUCCESS")
                .in(DelOutboundThirdParty::getOrderNo,orderNos)
        );

        logger.info("??????WMS?????????????????????{}",count);

        if(count > 0) {

            logger.info("WMS ????????????{}",JSON.toJSONString(orderNos));

            // ??????WMS????????????
            ShipmentCancelRequestDto shipmentCancelRequestDto = new ShipmentCancelRequestDto();
            shipmentCancelRequestDto.setOrderNoList(orderNos);
            shipmentCancelRequestDto.setWarehouseCode(warehouseCode);
            shipmentCancelRequestDto.setRemark("");
            logger.info("??????WMS?????????????????????{}", JSON.toJSONString(shipmentCancelRequestDto));
            ResponseVO responseVO = this.htpOutboundClientService.shipmentDelete(shipmentCancelRequestDto);
            logger.info("??????WMS?????????????????????{}", JSON.toJSONString(responseVO));
            if (null == responseVO || null == responseVO.getSuccess()) {
                throw new CommonException("400", "?????????????????????");
            }
            if (!responseVO.getSuccess()) {

                String msg = responseVO.getMessage().trim();


                if ("????????????????????????".equals(msg)) {

                    this.delOutboundCompletedService.add(orderNos, DelOutboundOperationTypeEnum.CANCELED.getCode());
                    // ???????????????????????????????????????
                    LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
                    updateWrapper.set(DelOutbound::getState, DelOutboundStateEnum.WHSE_CANCELLED.getCode());
                    updateWrapper.in(DelOutbound::getOrderNo, orderNos);
                    return this.baseMapper.update(null, updateWrapper);
                } else {
                    throw new CommonException("400", Utils.defaultValue(msg, "?????????????????????2"));
                }
            }else{

                boolean cacelFlag = this.cancellation(outboundList);

                if(!cacelFlag){
                    logger.info("???????????????????????????:{}",JSON.toJSONString(outboundList));
                    return 0;
                }

                this.delOutboundCompletedService.add(orderNos, DelOutboundOperationTypeEnum.CANCELED.getCode());

                // ?????????????????????????????????
                LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
                updateWrapper.set(DelOutbound::getState, DelOutboundStateEnum.WHSE_CANCELLED.getCode());
                updateWrapper.in(DelOutbound::getOrderNo, orderNos);
                return this.baseMapper.update(null, updateWrapper);
            }

        }else {

            logger.info("WMS ????????????{}",JSON.toJSONString(orderNos));

            boolean cacelFlag = this.cancellation(outboundList);

            if(!cacelFlag){
                logger.info("???????????????????????????:{}",JSON.toJSONString(outboundList));
                return 0;
            }

            this.delOutboundCompletedService.add(orderNos, DelOutboundOperationTypeEnum.CANCELED.getCode());
            // ???????????????????????????????????????
            LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
            updateWrapper.set(DelOutbound::getState, DelOutboundStateEnum.WHSE_CANCELLED.getCode());
            updateWrapper.in(DelOutbound::getOrderNo, orderNos);
            return this.baseMapper.update(null, updateWrapper);
        }
    }

    /**
     * ???????????????????????????
     * @param outboundList
     * @return
     */
    private boolean cancellation(List<DelOutbound> outboundList){

        try {

            for(DelOutbound delOutbound : outboundList) {

                String shipmentOrderNumber = delOutbound.getShipmentOrderNumber();
                String trackingNo = delOutbound.getTrackingNo();
                if (StringUtils.isNotEmpty(shipmentOrderNumber) && StringUtils.isNotEmpty(trackingNo)) {
                    String referenceNumber = delOutbound.getReferenceNumber();
                    this.delOutboundBringVerifyService.cancellation(delOutbound.getWarehouseCode(), referenceNumber, shipmentOrderNumber, trackingNo);
                }
            }

            return true;
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public int handler(DelOutboundHandlerDto dto) {
        List<Long> ids = dto.getIds();
        // ??????ids?????????????????????
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        int result = 0;
        for (Long id : ids) {
            try {
                DelOutbound delOutbound = this.getById(id);
                DelOutboundOperationLogEnum.HANDLER.listener(delOutbound);
                if (DelOutboundStateEnum.WHSE_COMPLETED.getCode().equals(delOutbound.getState())) {
                    // ????????????????????????????????????
                    this.delOutboundAsyncService.completed(delOutbound.getOrderNo(), null);
                    result++;
                } else if (DelOutboundStateEnum.WHSE_CANCELLED.getCode().equals(delOutbound.getState())) {
                    // ????????????????????????????????????
                    this.delOutboundAsyncService.cancelled(delOutbound.getOrderNo());
                    result++;
                } else {
                    result = result + this.delOutboundAsyncService.shipmentPacking(id);
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
        return result;
    }

    @Override
    public int furtherHandler(DelOutboundFurtherHandlerDto dto) {
        DelOutbound delOutbound = this.getByOrderNo(dto.getOrderNo());
        if (null == delOutbound) {
            throw new CommonException("400", "???????????????");
        }
        DelOutboundOperationLogEnum.FURTHER_HANDLER.listener(delOutbound);
        int result;
        if (DelOutboundStateEnum.WHSE_COMPLETED.getCode().equals(delOutbound.getState())) {
            // ????????????????????????????????????
            this.delOutboundAsyncService.completed(delOutbound.getOrderNo(), null);
            result = 1;
        } else if (DelOutboundStateEnum.WHSE_CANCELLED.getCode().equals(delOutbound.getState())) {
            // ????????????????????????????????????
            this.delOutboundAsyncService.cancelled(delOutbound.getOrderNo());
            result = 1;
        } else {
            dto.setExecShipmentShipping(true);
            result = this.delOutboundAsyncService.shipmentPacking(delOutbound.getId(), dto.isShipmentShipping(), dto);
        }
        return result;
    }

    @Override
    public R label(HttpServletResponse response, DelOutboundLabelDto dto) {
        DelOutbound delOutbound = this.getById(dto.getId());
        if (null == delOutbound) {
            R r = R.ok();
            r.setMsg("???????????????");
            return r;
        }

        //String pathname = DelOutboundServiceImplUtil.getPackageTransferLabelFilePath(delOutbound) + "/" + delOutbound.getOrderNo() + ".pdf";
        //File labelFile = new File(pathname);

        //String orderNo = delOutbound.getOrderNo();
//        // ??????????????????
//        DelOutboundAddress delOutboundAddress = this.delOutboundAddressService.getByOrderNo(orderNo);
//        try {
//            // ??????SKU??????
//            List<String> nos = new ArrayList<>();
//            nos.add(orderNo);
//            Map<String, String> skuLabelMap = this.delOutboundDetailService.queryDetailsLabelByNos(nos);
//            String skuLabel = skuLabelMap.get(orderNo);
//            ByteArrayOutputStream byteArrayOutputStream = DelOutboundServiceImplUtil.renderPackageTransfer(delOutbound, delOutboundAddress, skuLabel);
//            byte[] fb = null;
//            FileUtils.writeByteArrayToFile(labelFile, fb = byteArrayOutputStream.toByteArray(), false);
//            ServletOutputStream outputStream = null;
//            InputStream inputStream = null;
//            try {
//                outputStream = response.getOutputStream();
//                //response???HttpServletResponse??????
//                response.setContentType("application/pdf;charset=utf-8");
//                //Loading plan.xls??????????????????????????????????????????????????????????????????????????????
//                response.setHeader("Content-Disposition", "attachment;filename=" + delOutbound.getOrderNo() + ".pdf");
//                IOUtils.copy(new ByteArrayInputStream(fb), outputStream);
//            } catch (IOException e) {
//                logger.error(e.getMessage(), e);
//                R r = R.ok();
//                r.setMsg("????????????????????????");
//                return r;
//            } finally {
//                IoUtil.flush(outputStream);
//                IoUtil.close(outputStream);
//                IoUtil.close(inputStream);
//            }
//            return null;
//
//        } catch (Exception e) {
//            R r = R.ok();
//            r.setMsg("?????????????????????");
//            return r;
//        }

        if("0".equals(dto.getType())){

            String pathname = DelOutboundServiceImplUtil.getPackageTransferLabelFilePath(delOutbound) + "/" + delOutbound.getOrderNo() + ".pdf";
            File labelFile = new File(pathname);
            if (!labelFile.exists()) {
                String orderNo = delOutbound.getOrderNo();
                // ??????????????????
                DelOutboundAddress delOutboundAddress = this.delOutboundAddressService.getByOrderNo(orderNo);

                List<DelOutboundDetail> delOutboundDetailList = this.delOutboundDetailService.list(Wrappers.<DelOutboundDetail>query().lambda().eq(DelOutboundDetail::getOrderNo,orderNo));
                boolean isTh = false;
                for(DelOutboundDetail detail: delOutboundDetailList){
                    String productAttribute = detail.getProductAttribute();
                    if(!"GeneralCargo".equals(productAttribute)){
                        isTh = true;
                    }
                }

                try {
                    // ??????SKU??????
                    List<String> nos = new ArrayList<>();
                    nos.add(orderNo);
                    Map<String, String> skuLabelMap = this.delOutboundDetailService.queryDetailsLabelByNos(nos);
                    String skuLabel = skuLabelMap.get(orderNo);
                    ByteArrayOutputStream byteArrayOutputStream = DelOutboundServiceImplUtil.renderPackageTransfer(isTh,delOutbound, delOutboundAddress, skuLabel);
                    byte[] fb = null;
                    FileUtils.writeByteArrayToFile(labelFile, fb = byteArrayOutputStream.toByteArray(), false);
                    ServletOutputStream outputStream = null;
                    InputStream inputStream = null;
                    try {
                        outputStream = response.getOutputStream();
                        //response???HttpServletResponse??????
                        response.setContentType("application/pdf;charset=utf-8");
                        //Loading plan.xls??????????????????????????????????????????????????????????????????????????????
                        response.setHeader("Content-Disposition", "attachment;filename=" + delOutbound.getOrderNo() + ".pdf");
                        IOUtils.copy(new ByteArrayInputStream(fb), outputStream);
                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                        R r = R.ok();
                        r.setMsg("????????????????????????");
                        return r;
                    } finally {
                        IoUtil.flush(outputStream);
                        IoUtil.close(outputStream);
                        IoUtil.close(inputStream);
                    }
                    return null;

                } catch (Exception e) {
                    R r = R.ok();
                    r.setMsg("?????????????????????");
                    return r;
                }

            }
            ServletOutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                outputStream = response.getOutputStream();
                //response???HttpServletResponse??????
                response.setContentType("application/pdf;charset=utf-8");
                //Loading plan.xls??????????????????????????????????????????????????????????????????????????????
                response.setHeader("Content-Disposition", "attachment;filename=" + delOutbound.getOrderNo() + ".pdf");
                inputStream = new FileInputStream(labelFile);
                IOUtils.copy(inputStream, outputStream);
            } catch (IOException e) {
                R r = R.ok();
                r.setMsg("????????????????????????");
                return r;
            } finally {
                IoUtil.flush(outputStream);
                IoUtil.close(outputStream);
                IoUtil.close(inputStream);
            }
        }else{
            if (StringUtils.isEmpty(delOutbound.getShipmentOrderNumber())) {
                R r = R.ok();
                r.setMsg("????????????????????????");
                return r;
            }
            String pathname = DelOutboundServiceImplUtil.getLabelFilePath(delOutbound) + "/" + delOutbound.getShipmentOrderNumber() + ".pdf";
            File labelFile = new File(pathname);
            if (!labelFile.exists()) {
                R r = R.ok();
                r.setMsg("?????????????????????");
                return r;
            }
            ServletOutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                outputStream = response.getOutputStream();
                //response???HttpServletResponse??????
                response.setContentType("application/pdf;charset=utf-8");
                //Loading plan.xls??????????????????????????????????????????????????????????????????????????????
                response.setHeader("Content-Disposition", "attachment;filename=" + delOutbound.getShipmentOrderNumber() + ".pdf");
                inputStream = new FileInputStream(labelFile);
                IOUtils.copy(inputStream, outputStream);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
                R r = R.ok();
                r.setMsg("????????????????????????");
                return r;
            } finally {
                IoUtil.flush(outputStream);
                IoUtil.close(outputStream);
                IoUtil.close(inputStream);
            }
        }
        return null;
    }

    @Override
    public R labelBatch(HttpServletResponse response, DelOutboundLabelDto dto) {

        List<String> orderNos = dto.getOrderNos();

        if(CollectionUtils.isEmpty(orderNos)){
            return R.failed("????????????????????????");
        }

        if(orderNos.size() > 100){
            return R.failed("?????????????????????100?????????");
        }

        List<DelOutbound> delOutbounds = baseMapper.selectList(Wrappers.<DelOutbound>query().lambda().in(DelOutbound::getOrderNo,orderNos));
        if (CollectionUtils.isEmpty(delOutbounds)) {
            return R.failed("????????????????????????");
        }

        if(delOutbounds.size() == 1){

            DelOutbound delOutbound = delOutbounds.get(0);
            Long id = delOutbound.getId();
            dto.setId(id);

            return label(response,dto);
        }

        Document doc = new Document();

        try {
            response.setContentType("application/pdf;charset=utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=labelBatch.pdf");
            ServletOutputStream outputStream = response.getOutputStream();
            PdfCopy pdfCopy = new PdfCopy(doc, outputStream);
            doc.open();
            for(int i = 0;i<delOutbounds.size();i++) {

                DelOutbound delOutbound = delOutbounds.get(i);

                if("0".equals(dto.getType())) {
                    String pathname = DelOutboundServiceImplUtil.getPackageTransferLabelFilePath(delOutbound) + "/" + delOutbound.getOrderNo() + ".pdf";
                    File labelFile = new File(pathname);
                    if (!labelFile.exists()) {
                        String orderNo = delOutbound.getOrderNo();
                        // ??????????????????
                        DelOutboundAddress delOutboundAddress = this.delOutboundAddressService.getByOrderNo(orderNo);

                        List<DelOutboundDetail> delOutboundDetailList = this.delOutboundDetailService.list(Wrappers.<DelOutboundDetail>query().lambda().eq(DelOutboundDetail::getOrderNo, orderNo));
                        boolean isTh = false;
                        for (DelOutboundDetail detail : delOutboundDetailList) {
                            String productAttribute = detail.getProductAttribute();
                            if (!"GeneralCargo".equals(productAttribute)) {
                                isTh = true;
                            }
                        }

                        // ??????SKU??????
                        List<String> nos = new ArrayList<>();
                        nos.add(orderNo);
                        Map<String, String> skuLabelMap = this.delOutboundDetailService.queryDetailsLabelByNos(nos);
                        String skuLabel = skuLabelMap.get(orderNo);
                        ByteArrayOutputStream byteArrayOutputStream = DelOutboundServiceImplUtil.renderPackageTransfer(isTh,delOutbound, delOutboundAddress, skuLabel);
                        byte[] fb = null;
                        FileUtils.writeByteArrayToFile(labelFile, fb = byteArrayOutputStream.toByteArray(), false);

                        if(fb == null){
                            continue;
                        }

                        PdfImportedPage impage = pdfCopy.getImportedPage(new PdfReader(fb),1);
                        pdfCopy.addPage(impage);
                    }else{

                        byte[] fb = FileUtils.readFileToByteArray(labelFile);

                        PdfImportedPage impage = pdfCopy.getImportedPage(new PdfReader(fb),1);
                        pdfCopy.addPage(impage);

                    }
                }
            }

        } catch (Exception e) {
            //throw new RuntimeException(e);
            logger.error("labelBatch:{}",e.getMessage());
            return R.failed(e.getMessage());
        }finally {
            doc.close();
        }

        return null;
    }


    @Override
    public void labelSelfPick(HttpServletResponse response, DelOutboundLabelDto dto) {
        DelOutbound delOutbound = this.getById(dto.getId());
        if (null == delOutbound) {
            throw new CommonException("400", "???????????????");
        }
        String pathname = DelOutboundServiceImplUtil.getSelfPickLabelFilePath(delOutbound) + "/" + delOutbound.getOrderNo() + ".pdf";
        File labelFile = new File(pathname);
        if (!labelFile.exists()) {
            String orderNo = delOutbound.getOrderNo();
            // ??????????????????
            List<DelOutboundDetail> delOutboundDetailList = this.delOutboundDetailService.listByOrderNo(delOutbound.getOrderNo());
            try {
                ByteArrayOutputStream byteArrayOutputStream = DelOutboundServiceImplUtil.renderSelfPick(delOutbound, delOutboundDetailList, null);
                byte[] fb = null;
                FileUtils.writeByteArrayToFile(labelFile, fb = byteArrayOutputStream.toByteArray(), false);
                ServletOutputStream outputStream = null;
                InputStream inputStream = null;
                try {
                    outputStream = response.getOutputStream();
                    //response???HttpServletResponse??????
                    response.setContentType("application/pdf;charset=utf-8");
                    //Loading plan.xls??????????????????????????????????????????????????????????????????????????????
                    response.setHeader("Content-Disposition", "attachment;filename=" + delOutbound.getOrderNo() + ".pdf");
                    IOUtils.copy(new ByteArrayInputStream(fb), outputStream);
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                    throw new CommonException("500", "??????????????????????????????");
                } finally {
                    IoUtil.flush(outputStream);
                    IoUtil.close(outputStream);
                    IoUtil.close(inputStream);
                }
                return;

            } catch (Exception e) {
                throw new CommonException("400", "???????????????????????????");
            }

        }

        if(response == null){
            return;
        }
        ServletOutputStream outputStream = null;
        InputStream inputStream = null;
        try {
            outputStream = response.getOutputStream();
            //response???HttpServletResponse??????
            response.setContentType("application/pdf;charset=utf-8");
            //Loading plan.xls??????????????????????????????????????????????????????????????????????????????
            response.setHeader("Content-Disposition", "attachment;filename=" + delOutbound.getOrderNo() + ".pdf");
            inputStream = new FileInputStream(labelFile);
            IOUtils.copy(inputStream, outputStream);
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new CommonException("500", "??????????????????????????????");
        } finally {
            IoUtil.flush(outputStream);
            IoUtil.close(outputStream);
            IoUtil.close(inputStream);
        }


    }

    @Override
    public List<DelOutboundTarckOn> selectDelOutboundTarckList(DelOutboundTarckOn delOutboundTarckOn) {
        // ????????????refNo
        List<String> otherQueryNoList = new ArrayList<>();
        if (StringUtils.isNotEmpty(delOutboundTarckOn.getOrderNos())) {
            List<String> nos = splitToArray(delOutboundTarckOn.getOrderNos(), "[\n,]");
            if (CollectionUtils.isNotEmpty(nos)) {
                for (String no : nos) {
                    // CK/RECK????????????????????????
//                    if (no.startsWith("CK") || no.startsWith("RECK")) {
//                        delOutboundNoList.add(no);
//                    } else {
                        otherQueryNoList.add(no);
//                    }
                }


            }
           delOutboundTarckOn.setOrderNosList(otherQueryNoList);

        }


        return delOutboundTarckOnMapper.selectByPrimaryKey(delOutboundTarckOn);
    }

    @Override
    public Integer selectDelOutboundCount(DelOutboundListQueryDto queryDto) {
        QueryWrapper<DelOutboundListQueryDto> queryWrapper = new QueryWrapper<>();
        // ????????????????????????????????????
        if (CollectionUtils.isNotEmpty(queryDto.getSelectIds())) {
            queryWrapper.in("o.id", queryDto.getSelectIds());
            // ????????????????????????
            queryWrapper.orderByDesc("o.create_time");
        } else {
            DelOutboundServiceImplUtil.handlerQueryWrapper(queryWrapper, queryDto);
        }
        return basFileMapper.selectDelOutboundCount(queryWrapper);

    }

    @Override
    public List<DelOutboundTarckError> selectbatchTrackingexport() {
        List<DelOutboundTarckError> list=delOutboundTarckErrorMapper.selectByPrimaryKey();
        delOutboundTarckErrorMapper.deleteByPrimaryKey();
        return list;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void carrierRegister(DelOutbound delOutbound) {
        //???????????????????????????
        try {

            String referenceNumber = delOutbound.getReferenceNumber();

            if(StringUtils.isEmpty(referenceNumber)){
                //log.error("?????????["+delOutbound.getOrderNo()+"] referenceNumber ??????");
                return;
            }

            R<ShipmentOrderResult> r = htpOutboundFeignService.shipmentOrderRealResult(referenceNumber);

            if (r.getCode() != 200) {
                log.error("?????????["+delOutbound.getOrderNo()+"]?????????????????????????????????????????????????????????????????????,{}",r.getMsg());
                throw new RuntimeException(r.getData().getError().getMessage());
            }

            if(!r.getData().getSuccess()){
                throw new RuntimeException(r.getData().getError().getMessage());
            }
            ShipmentOrderResult data = r.getData();
            if (!StringUtils.equals(delOutbound.getTrackingNo(), data.getMainTrackingNumber())) {
                //???????????????????????????????????????
                DelOutbound dataDelOutbound = this.getById(delOutbound.getId());
                dataDelOutbound.setTrackingNo(data.getMainTrackingNumber());
                this.updateById(dataDelOutbound);


                //????????????????????????
                DelOutboundTarckOn delOutboundTarckOn = new DelOutboundTarckOn();
                delOutboundTarckOn.setOrderNo(dataDelOutbound.getOrderNo());
                delOutboundTarckOn.setTrackingNo(delOutbound.getTrackingNo());
                delOutboundTarckOn.setUpdateTime(new Date());
                delOutboundTarckOn.setTrackingNoNew(data.getMainTrackingNumber());
                delOutboundTarckOnMapper.insertSelective(delOutboundTarckOn);


                List<String> orders = new ArrayList<String>();
                orders.add(dataDelOutbound.getOrderNo());
                // ??????ty??????
                manualTrackingYee(orders);


                //?????????
                R<BasSellerInfoVO> info = basSellerFeignService.getInfoBySellerCode(dataDelOutbound.getSellerCode());
                if(info.getData() == null) {
                    throw new RuntimeException("????????????????????????");
                }
                BasSellerInfoVO userInfo = R.getDataAndException(info);

                EmailDto emailDto=new EmailDto();
                emailDto.setModularType(1);
                emailDto.setTo(userInfo.getEmail());
                EmailObjectDto emailDtoDetail=new EmailObjectDto();
                emailDtoDetail.setCustomCode(dataDelOutbound.getSellerCode());
                emailDtoDetail.setOrderNo(dataDelOutbound.getOrderNo());
                emailDtoDetail.setServiceManagerName(userInfo.getServiceManagerName());
                emailDtoDetail.setServiceStaffName(userInfo.getServiceStaffName());
                emailDtoDetail.setNoTrackingNo(delOutbound.getTrackingNo());
                emailDtoDetail.setTrackingNo(data.getMainTrackingNumber());
                emailDto.setList(Arrays.asList(emailDtoDetail));
                emailFeingService.sendEmail(emailDto);


            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean serviceChannelNamePushWMS(DelOutbound delOutbound, DelOutbound updateDelOutbound) {

        String shipmentService = delOutbound.getShipmentService();

        if(StringUtils.isEmpty(shipmentService)){
            return false;
        }

        logger.info("shipmentService??????:{},{}",delOutbound.getOrderNo(),shipmentService);

        BasShipmentRulesDto paramBasShipmentRulesDto = new BasShipmentRulesDto();
        paramBasShipmentRulesDto.setCustomCode(delOutbound.getSellerCode());
        paramBasShipmentRulesDto.setServiceChannelName(shipmentService);
        paramBasShipmentRulesDto.setDelFlag("1");
        paramBasShipmentRulesDto.setTypeName("0");
        List<BasShipmentRules> list = basShipmenRulesService.selectBasShipmentRules(paramBasShipmentRulesDto);
        if(list.isEmpty()){
            return false;
        }

        logger.info("shipmentService selectBasShipmentRules ??????:{},{}",delOutbound.getOrderNo(),JSON.toJSONString(list));

        //??????????????????????????????
        updateDelOutbound.setState(DelOutboundStateEnum.WHSE_COMPLETED.getCode());
        updateDelOutbound.setExceptionState(DelOutboundExceptionStateEnum.NORMAL.getCode());
        // ??????????????????
        updateDelOutbound.setExceptionMessage("");
        // ??????????????????
        updateDelOutbound.setBringVerifyTime(new Date());



        updateDelOutbound.setOperationTime(new Date());
        updateDelOutbound.setOperationType(DelOutboundOperationTypeEnum.SHIPPED.getCode());

        this.updateById(updateDelOutbound);
        // ????????????????????????????????????????????????????????????

        Date pushDate = DateUtils.parseDate(DateUtil.format(new Date(), "yyyy-MM-dd") + " " + list.get(0).getPushDate());
        if(pushDate.getTime() < System.currentTimeMillis()){
            //?????????????????????????????????????????????
            pushDate = DateUtils.parseDate(DateUtil.format(tomorrow(new Date()), "yyyy-MM-dd") + " " + list.get(0).getPushDate());
        }
        this.delOutboundCompletedService.add(delOutbound.getOrderNo(), DelOutboundOperationTypeEnum.SHIPPED.getCode(), pushDate);

        return true;
    }

    @Override
    public void doDirectExpressOrders() {

        LambdaUpdateWrapper<DelOutbound> update = Wrappers.lambdaUpdate();
        update.set(DelOutbound::getThridPartStatus, 0)
                .eq(DelOutbound::getState, DelOutboundStateEnum.DELIVERED.getCode())
                .eq(DelOutbound::getThridPartStatus,1);
        int upda = baseMapper.update(null,update);

        logger.info("doDirectExpressOrders ???????????????{}",upda);

        Integer totalRecord = baseMapper.selectCount(Wrappers.<DelOutbound>query().lambda()
                        .eq(DelOutbound::getState,DelOutboundStateEnum.DELIVERED.getCode())
                .eq(DelOutbound::getThridPartStatus,0)
                .lt(DelOutbound::getThridPartCount,10)
                .in(DelOutbound::getPrcTerminalCarrier,PrcTerminalCarrierEnum.CK1.getCode(),PrcTerminalCarrierEnum.CHOUKOU1.getCode())
        );

        if(totalRecord == 0){
            return ;
        }

        logger.info("????????????:{}",totalRecord);

        Integer pageSize = 5000;

        Integer totalPage = (totalRecord + pageSize -1) / pageSize;

        for(int i= 0;i<totalPage;i++) {

            List<DelOutbound> delOutbounds = baseMapper.selectByState(DelOutboundStateEnum.DELIVERED.getCode(), i, pageSize);
            List<DelOutbound> updateData = new ArrayList<>();

            for (DelOutbound delOutbound : delOutbounds) {

                R<DirectExpressOrderApiDTO> directExpressOrderApiDTOR = htpOutboundFeignService.getDirectExpressOrder(delOutbound.getOrderNo());

                logger.info("?????????DirectExpressOrder1???{},???????????????{}",delOutbound.getOrderNo(),JSON.toJSONString(directExpressOrderApiDTOR));

                baseMapper.updateThridPartcount(delOutbound.getId());

                if (directExpressOrderApiDTOR.getCode() != 200) {
                    continue;
                }
                DirectExpressOrderApiDTO directExpressOrderApiDTO = directExpressOrderApiDTOR.getData();

                String handleStatus = directExpressOrderApiDTO.getHandleStatus();

                if(StringUtils.isEmpty(handleStatus)){
                    continue;
                }

                logger.info("?????????DirectExpressOrder2???{},???????????????{}",delOutbound.getOrderNo(),JSON.toJSONString(directExpressOrderApiDTO));

                if (handleStatus.equals(DelOutboundOperationTypeEnum.SHIPPED.getCode())) {

                    DelOutbound updatedata = new DelOutbound();
                    updatedata.setId(delOutbound.getId());

                    Double length = directExpressOrderApiDTO.getPacking().getLength().doubleValue();
                    Double width = directExpressOrderApiDTO.getPacking().getWidth().doubleValue();
                    Double height = directExpressOrderApiDTO.getPacking().getHeight().doubleValue();

                    //?????????
                    Double weight = null;
                    Integer w = directExpressOrderApiDTO.getWeight();
                    if (w != null) {
                        weight = Double.parseDouble(w.toString());
                    }

                    //?????????
                    Integer chargedWeight = directExpressOrderApiDTO.getChargedWeight();
                    BigDecimal calcWeight = new BigDecimal(chargedWeight);

                    String specifications = length + "*" + width + "*" + height;

                    updatedata.setLength(length);
                    updatedata.setWidth(width);
                    updatedata.setHeight(height);
                    updatedata.setWeight(weight);
                    updatedata.setSpecifications(specifications);
                    updatedata.setThridPartStatus(1);
                    updatedata.setCalcWeight(calcWeight);
                    updatedata.setCalcWeightUnit("G");

                    updateData.add(updatedata);
                }
            }

            if (CollectionUtils.isNotEmpty(updateData)) {

                logger.info("DirectExpressOrder ???????????????{}",JSON.toJSONString(updateData));
                boolean flag = super.updateBatchById(updateData);
                if(flag){
                    logger.info("DirectExpressOrder ???????????????");
                }

                List<Long> orderNoList = updateData.stream().map(DelOutbound::getId).collect(Collectors.toList());

                List<List<Long>> partionOrderNoList = Lists.partition(orderNoList,300);

                for(List<Long> ids : partionOrderNoList){

                    List<DelOutbound> delOutboundList = baseMapper.selectList(Wrappers.<DelOutbound>query().lambda().in(DelOutbound::getId,ids).eq(DelOutbound::getState,DelOutboundStateEnum.DELIVERED.getCode()));

                    for(DelOutbound delOutbound : delOutboundList){
                        Long s = System.currentTimeMillis();
                        this.bringThridPartyAsync(delOutbound);
                        Long e = System.currentTimeMillis();
                        logger.info("bringThridPartyAsync???{},???????????????{}", delOutbound.getOrderNo(), e - s);
                    }
                }
            }

        }

    }

    @Override
    public List<DelOutboundSpecialDto> querySpecialGoods(List<String> orders) {
        if(orders == null || orders.size() == 0){
            return null;
        }
        LambdaQueryWrapper<DelOutboundDetail> detailLambdaQueryWrapper = new LambdaQueryWrapper<>();
        detailLambdaQueryWrapper.in(DelOutboundDetail::getOrderNo, orders);
        List<DelOutboundDetail> list = delOutboundDetailService.list(detailLambdaQueryWrapper);
        List<DelOutboundSpecialDto> dataList = new ArrayList<>();
        for(String orderNo: orders){
            boolean isTh = false;
            for(DelOutboundDetail detail: list){
                if(StringUtils.equals(detail.getOrderNo(), orderNo)){
                    if(!StringUtils.equals(detail.getProductAttribute(), "GeneralCargo")){
                        isTh = true;
                    }
                }
            }
            DelOutboundSpecialDto dto = new DelOutboundSpecialDto();
            dto.setOrderNo(orderNo);
            if(isTh){
                dto.setSpecialGoods("TH");
            }else{
                dto.setSpecialGoods("PH");
            }
            dataList.add(dto);
        }

        return dataList;
    }

    @Override
    public boolean updateWeightDelOutbound(UpdateWeightDelOutboundDto dto) {

        //boolean upd = new OutboundUpdWeightCmd(dto).execute();

        String orderNo = dto.getOrderNo();

        LambdaQueryWrapper<DelOutbound> queryWrapper = new LambdaQueryWrapper<DelOutbound>();
        queryWrapper.eq(DelOutbound::getSellerCode, dto.getCustomCode());
        queryWrapper.eq(DelOutbound::getOrderNo, orderNo);
        DelOutbound data = baseMapper.selectOne(queryWrapper);
        if(data == null){
            throw new CommonException("400", "???????????????????????????");
        }

        logger.info("updateWeightDelOutbound:{}???????????????{}",data.getOrderNo(),data.getState());

        boolean checkState = DelOutboundStateEnum.REVIEWED.getCode().equals(data.getState())
                || DelOutboundStateEnum.DELIVERED.getCode().equals(data.getState())
                || DelOutboundStateEnum.AUDIT_FAILED.getCode().equals(data.getState());

        logger.info("updateWeightDelOutbound:{}???????????????{}",data.getOrderNo(),checkState);

        if (!checkState) {
            throw new CommonException("400", "??????????????????");
        }

        //org.springframework.beans.BeanUtils.copyProperties(dto, data);
        //int upd = baseMapper.updateById(data);

        LambdaUpdateWrapper<DelOutbound> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper.set(DelOutbound::getWeight, dto.getWeight());
        updateWrapper.set(DelOutbound::getLength,dto.getLength());
        updateWrapper.set(DelOutbound::getWidth,dto.getWidth());
        updateWrapper.set(DelOutbound::getHeight,dto.getHeight());
        updateWrapper.set(DelOutbound::getCustomCode,dto.getCustomCode());
        updateWrapper.set(DelOutbound::getPackageWeightDeviation,dto.getPackageWeightDeviation());
        updateWrapper.set(DelOutbound::getPackageConfirm,dto.getPackageConfirm());
        updateWrapper.eq(DelOutbound::getOrderNo, dto.getOrderNo());
        int upd = this.baseMapper.update(null, updateWrapper);

        if(upd > 0){

            log.info("??????DelOutUpdWeightEvent???{}",orderNo);
            DelOutUpdWeightEvent delOutUpdWeightEvent = new DelOutUpdWeightEvent(orderNo);
            EventUtil.publishEvent(delOutUpdWeightEvent);
        }

        return upd > 0;
    }

    @Override
    public void nuclearWeight(DelOutbound delOutbound) {

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        DelOutboundWrapperContext context = this.delOutboundBringVerifyService.initContext(delOutbound);
        stopWatch.stop();
        logger.info(">>>>>[nuclearWeight{}]????????????????????? ??????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskInfo().getTimeMillis());

        NuclearWeightEnum currentState;
        String nuclearWeightState = delOutbound.getNuclearWeightState();
        if (org.apache.commons.lang3.StringUtils.isEmpty(nuclearWeightState)) {
            currentState = NuclearWeightEnum.BEGIN;
        } else {
            currentState = NuclearWeightEnum.get(nuclearWeightState);
            // ??????
            if (null == currentState) {
                currentState = NuclearWeightEnum.BEGIN;
            }
        }
        ApplicationContainer applicationContainer = new ApplicationContainer(context, currentState, NuclearWeightEnum.END, NuclearWeightEnum.BEGIN);
        try {
            applicationContainer.action();
        } catch (CommonException e) {
            // ????????????
            applicationContainer.setEndState(NuclearWeightEnum.BEGIN);
            applicationContainer.rollback();
            // ????????????????????????????????????????????????
            // ????????????????????????????????????????????????
            this.logger.error("(4)nuclearWeight??????????????????????????????" + delOutbound.getOrderNo() + "??????????????????" + e.getMessage(), e);
        } finally {
        }
    }

    @Override
    public List<DelOutboundChargeData> findDelboundCharges(List<String> orderNoList) {

        if(CollectionUtils.isEmpty(orderNoList)){
            return new ArrayList<>();
        }

        List<DelOutbound> delOutbounds = baseMapper.selectList(Wrappers.<DelOutbound>query().lambda().in(DelOutbound::getOrderNo,orderNoList));

        List<DelOutboundCharge> delOutboundCharges = delOutboundChargeService.list(Wrappers.<DelOutboundCharge>query().lambda().in(DelOutboundCharge::getOrderNo,orderNoList).eq(DelOutboundCharge::getDelFlag,0));

        Map<String,List<DelOutboundCharge>> deloutboundChargeMap = delOutboundCharges.stream().collect(Collectors.groupingBy(DelOutboundCharge::getOrderNo));

        List<DelOutboundChargeData> delOutboundChargeData = DelOutboundChargeConvert.INSTANCE.toDelOutboundChargeList(delOutbounds);

        for(DelOutboundChargeData data : delOutboundChargeData){
            String orderNo = data.getOrderNo();
            List<DelOutboundCharge> delOutboundChargesList = deloutboundChargeMap.get(orderNo);
            if(CollectionUtils.isNotEmpty(delOutboundChargesList)){
                data.setDelOutboundCharges(delOutboundChargesList);
            }
        }

        return delOutboundChargeData;
    }



    public void bringThridPartyAsync(DelOutbound delOutbound) {

        String key = applicationName + ":DelOutbound:bringThridPartyAsync:" + delOutbound.getOrderNo();
        RLock lock = this.redissonClient.getLock(key);
        try {
            if (lock.tryLock(0, TimeUnit.SECONDS)) {
                if (DelOutboundStateEnum.DELIVERED.getCode().equals(delOutbound.getState())) {
                    bringThridPartyAsync(delOutbound, AsyncThreadObject.build());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage(), e);
            throw new CommonException("500", "?????????????????????" + e.getMessage());
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void bringThridPartyAsync(DelOutbound delOutbound,AsyncThreadObject asyncThreadObject){

        StopWatch stopWatch = new StopWatch();
        Thread thread = Thread.currentThread();
        // ????????????
        long startTime = System.currentTimeMillis();
        boolean isAsyncThread = !asyncThreadObject.isAsyncThread();
        logger.info("(1)??????????????????????????????????????????{}???????????????ID???{}???????????????????????????{}????????????????????????{}", thread.getName(), thread.getId(), isAsyncThread, JSON.toJSONString(asyncThreadObject));
        if (isAsyncThread) {
            asyncThreadObject.loadTid();
        }
        stopWatch.start();
        DelOutboundWrapperContext context = this.delOutboundBringVerifyService.initContext(delOutbound);
        stopWatch.stop();
        logger.info(">>>>>[bringThridPartyAsync???????????????{}]????????????????????? ??????{}", delOutbound.getOrderNo(), stopWatch.getLastTaskInfo().getTimeMillis());

        ThridPartyEnum currentState;
        String bringVerifyState = delOutbound.getThridPardState();
        if (org.apache.commons.lang3.StringUtils.isEmpty(bringVerifyState)) {
            currentState = ThridPartyEnum.BEGIN;
        } else {
            currentState = ThridPartyEnum.get(bringVerifyState);
            // ??????
            if (null == currentState) {
                currentState = ThridPartyEnum.BEGIN;
            }
        }
        logger.info("(2)bringThridPartyAsync??????????????????????????????????????????{}", delOutbound.getOrderNo());
        ApplicationContainer applicationContainer = new ApplicationContainer(context, currentState, ThridPartyEnum.END, ThridPartyEnum.BEGIN);
        try {
            applicationContainer.action();

            //if (DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
            // ????????????????????????????????????????????????????????????
            this.delOutboundCompletedService.add(delOutbound.getOrderNo(), DelOutboundOperationTypeEnum.SHIPPED.getCode());
            //}

            logger.info("(3)bringThridPartyAsync??????????????????????????????????????????{}", delOutbound.getOrderNo());
        } catch (CommonException e) {
            // ????????????
            applicationContainer.setEndState(ThridPartyEnum.BEGIN);
            applicationContainer.rollback();
            // ????????????????????????????????????????????????
            // ????????????????????????????????????????????????
            this.logger.error("(4)bringThridPartyAsync??????????????????????????????????????????" + delOutbound.getOrderNo() + "??????????????????" + e.getMessage(), e);
        } finally {
            if (isAsyncThread) {
                asyncThreadObject.unloadTid();
            }
        }
        this.logger.info("(5)????????????????????????????????????{}??????????????????{}", delOutbound.getOrderNo(), (System.currentTimeMillis() - startTime));

    }

    public static Date tomorrow(Date today) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + 1);
        return calendar.getTime();
    }

    public static List<String> splitToArray(String text, String split) {
        String[] arr = text.split(split);
        if (arr.length == 0) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (String s : arr) {
            if (StringUtils.isEmpty(s)) {
                continue;
            }
            list.add(s);
        }
        return list;
    }

    @Override
    public List<DelOutboundLabelResponse> labelBase64(DelOutboundLabelDto dto) {
        List<String> orderNos = dto.getOrderNos();
        if (CollectionUtils.isEmpty(orderNos)) {
            return null;
        }
        LambdaQueryWrapper<DelOutbound> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.in(DelOutbound::getOrderNo, orderNos);
        List<DelOutbound> outboundList = this.list(queryWrapper);
        if (CollectionUtils.isEmpty(outboundList)) {
            return null;
        }
        Map<String, DelOutbound> outboundMap = outboundList.stream().collect(Collectors.toMap(DelOutbound::getOrderNo, v -> v, (a, b) -> a));
        List<DelOutboundLabelResponse> responseList = new ArrayList<>();
        for (String orderNo : orderNos) {
            DelOutboundLabelResponse response = new DelOutboundLabelResponse();
            response.setOrderNo(orderNo);
            DelOutbound outbound = outboundMap.get(orderNo);
            if (null == outbound) {
                response.setStatus(false);
                response.setMessage("???????????????");
                responseList.add(response);
                continue;
            }

            if(DelOutboundStateEnum.AUDIT_FAILED.getCode().equals(outbound.getState())){

                String exceptionMessage = outbound.getExceptionMessage();

                if(StringUtils.isNotEmpty(exceptionMessage)){
                    throw new CommonException("400", exceptionMessage);
                }
                throw new CommonException("400", "?????????????????????????????????");
            }

            String pathname = null;
            byte[] fb = null;
            if(outbound.getEndTagState() != null && outbound.getEndTagState().equals(DelOutboundEndTagStateEnum.REVIEWED.getCode())){
                pathname = outbound.getShipmentRetryLabel();

                if(StringUtils.isEmpty(pathname)){
                    response.setStatus(false);
                    response.setMessage("?????????"+outbound.getOrderNo()+",?????????????????????????????????");
                    responseList.add(response);
                    continue;
                }

                File labelFile = new File(pathname);
                if (labelFile.exists()) {
                    FileInputStream fileInputStream = null;
                    try {
                        fileInputStream = new FileInputStream(labelFile);
                        fb = IOUtils.toByteArray(fileInputStream);
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                        response.setStatus(false);
                        response.setMessage("????????????????????????," + e.getMessage());
                        responseList.add(response);
                        continue;
                    } finally {
                        IOUtils.closeQuietly(fileInputStream);
                    }
                }

            }else{

                if (!(DelOutboundStateEnum.DELIVERED.getCode().equals(outbound.getState())
                        || DelOutboundStateEnum.PROCESSING.getCode().equals(outbound.getState())
                        || DelOutboundStateEnum.COMPLETED.getCode().equals(outbound.getState())
                        || DelOutboundStateEnum.WHSE_PROCESSING.getCode().equals(outbound.getState())
                        || DelOutboundStateEnum.WHSE_COMPLETED.getCode().equals(outbound.getState())
                        || DelOutboundStateEnum.NOTIFY_WHSE_PROCESSING.getCode().equals(outbound.getState())
                        || DelOutboundStateEnum.AUDIT_FAILED.getCode().equals(outbound.getState())
                )) {

                    String exceptionMessage = outbound.getExceptionMessage();

                    if(StringUtils.isNotEmpty(exceptionMessage)){
                        throw new CommonException("400", exceptionMessage);
                    }
                    throw new CommonException("400", "?????????????????????????????????");
                }

                pathname = DelOutboundServiceImplUtil.getPackageTransferLabelFilePath(outbound) + "/" + orderNo + ".pdf";
                File labelFile = new File(pathname);

                List<DelOutboundDetail> delOutboundDetailList = this.delOutboundDetailService.list(Wrappers.<DelOutboundDetail>query().lambda().eq(DelOutboundDetail::getOrderNo,orderNo));
                boolean isTh = false;
                for(DelOutboundDetail detail: delOutboundDetailList){

                    String productAttribute = detail.getProductAttribute();

                    if(!"GeneralCargo".equals(productAttribute)){
                        isTh = true;
                    }
                }
//
//                if (labelFile.exists()) {
//                    FileInputStream fileInputStream = null;
//                    try {
//                        fileInputStream = new FileInputStream(labelFile);
//                        fb = IOUtils.toByteArray(fileInputStream);
//                    } catch (Exception e) {
//                        logger.error(e.getMessage(), e);
//                        response.setStatus(false);
//                        response.setMessage("????????????????????????," + e.getMessage());
//                        responseList.add(response);
//                        continue;
//                    } finally {
//                        IOUtils.closeQuietly(fileInputStream);
//                    }
//                } else {
                    // ??????????????????
                    DelOutboundAddress delOutboundAddress = this.delOutboundAddressService.getByOrderNo(orderNo);
                    try {
                        // ??????SKU??????
                        List<String> nos = new ArrayList<>();
                        nos.add(orderNo);
                        Map<String, String> skuLabelMap = this.delOutboundDetailService.queryDetailsLabelByNos(nos);
                        String skuLabel = skuLabelMap.get(orderNo);
                        ByteArrayOutputStream byteArrayOutputStream = DelOutboundServiceImplUtil.renderPackageTransfer(isTh,outbound, delOutboundAddress, skuLabel);
                        FileUtils.writeByteArrayToFile(labelFile, fb = byteArrayOutputStream.toByteArray(), false);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                        response.setStatus(false);
                        response.setMessage("????????????????????????," + e.getMessage());
                        responseList.add(response);
                        continue;
                    }
                //}
            }

//            pathname = DelOutboundServiceImplUtil.getPackageTransferLabelFilePath(outbound) + "/" + orderNo + ".pdf";
//            File labelFile = new File(pathname);
//            // ??????????????????
//            DelOutboundAddress delOutboundAddress = this.delOutboundAddressService.getByOrderNo(orderNo);
//            try {
//                // ??????SKU??????
//                List<String> nos = new ArrayList<>();
//                nos.add(orderNo);
//                Map<String, String> skuLabelMap = this.delOutboundDetailService.queryDetailsLabelByNos(nos);
//                String skuLabel = skuLabelMap.get(orderNo);
//                ByteArrayOutputStream byteArrayOutputStream = DelOutboundServiceImplUtil.renderPackageTransfer(outbound, delOutboundAddress, skuLabel);
//                FileUtils.writeByteArrayToFile(labelFile, fb = byteArrayOutputStream.toByteArray(), false);
//            } catch (Exception e) {
//                log.error(e.getMessage(), e);
//                response.setStatus(false);
//                response.setMessage("????????????????????????," + e.getMessage());
//                responseList.add(response);
//                continue;
//            }

            if(fb == null){
                continue;
            }
            String base64Str = Base64Utils.encodeToString(fb);
            response.setBase64(base64Str);
            response.setFileName(orderNo + ".pdf");
            response.setStatus(true);
            response.setId(outbound.getId());
            responseList.add(response);
        }
        return responseList;
    }

    @Override
    public int uploadBoxLabel(DelOutboundUploadBoxLabelDto dto) {
        // ?????????????????????
        String orderNo = dto.getOrderNo();
        AttachmentTypeEnum attachmentTypeEnum = dto.getAttachmentTypeEnum();
        this.remoteAttachmentService.deleteByBusinessNo(AttachmentDTO.builder().businessNo(orderNo).attachmentTypeEnum(attachmentTypeEnum).build());
        // ????????????
        AttachmentDTO batchLabel = AttachmentDTO.builder().businessNo(orderNo).businessItemNo(null).fileList(dto.getBatchLabels()).attachmentTypeEnum(attachmentTypeEnum).build();
        this.remoteAttachmentService.saveAndUpdate(batchLabel);
        return 1;
    }

    @Deprecated
    @Override
    public List<DelOutboundDetailVO> importDetail(String warehouseCode, String sellerCode, List<DelOutboundDetailImportDto> dtoList) {
        // ??????sku
        List<String> skus = dtoList.stream().map(DelOutboundDetailImportDto::getSku).distinct().collect(Collectors.toList());
        InventoryAvailableQueryDto inventoryAvailableQueryDto = new InventoryAvailableQueryDto();
        inventoryAvailableQueryDto.setWarehouseCode(warehouseCode);
        inventoryAvailableQueryDto.setCusCode(sellerCode);
        inventoryAvailableQueryDto.setSkus(skus);
        // ????????????????????????????????????????????????????????????0???????????????
        inventoryAvailableQueryDto.setQueryType(1);
        List<InventoryAvailableListVO> availableList = this.inventoryFeignClientService.queryAvailableList2(inventoryAvailableQueryDto);
        Map<String, InventoryAvailableListVO> availableMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(availableList)) {
            for (InventoryAvailableListVO vo : availableList) {
                availableMap.put(vo.getSku(), vo);
            }
        }
        List<DelOutboundDetailVO> voList = new ArrayList<>();
        for (DelOutboundDetailImportDto dto : dtoList) {
            InventoryAvailableListVO available = availableMap.get(dto.getSku());
            if (null != available) {
                DelOutboundDetailVO vo = BeanMapperUtil.map(available, DelOutboundDetailVO.class);
                vo.setQty(dto.getQty());
                voList.add(vo);
            }
        }
        return voList;
    }

    @Override
    public List<QueryChargeVO> getDelOutboundCharge(QueryChargeDto queryDto) {
        List<QueryChargeVO> list = baseMapper.selectDelOutboundList(queryDto);
        for (QueryChargeVO queryChargeVO : list) {
            String orderNo = queryChargeVO.getOrderNo();
            queryChargeVO.setCountry(getCountryName(queryChargeVO.getCountry()));
            List<DelOutboundDetail> delOutboundDetails = delOutboundDetailService.selectDelOutboundDetailList(new DelOutboundDetail().setOrderNo(orderNo));
            //???????????? = ??????SKU?????????+?????????1??????
            queryChargeVO.setQty(ListUtils.emptyIfNull(delOutboundDetails).stream().map(value -> StringUtils.isBlank(value.getBindCode())
                    ? value.getQty() : value.getQty() + 1).reduce(Long::sum).orElse(0L));

            List<DelOutboundCharge> delOutboundCharges = delOutboundChargeService.listCharges(orderNo);

            this.setAmount(queryChargeVO, delOutboundCharges);
        }
        return list;
    }

    private String getCountryName(String country) {
        if (StringUtils.isEmpty(country)) return country;
        R<BasRegionSelectListVO> result = basRegionFeignService.queryByCountryCode(country);
        if (result.getCode() == 200 && result.getData() != null) {
            return result.getData().getName();
        }
        return "";
    }

    @Override
    public List<DelOutboundExportListDto> exportList(DelOutboundListQueryDto queryDto) {
        QueryWrapper<DelOutboundListQueryDto> queryWrapper = new QueryWrapper<>();
        // ????????????????????????????????????
        if (CollectionUtils.isNotEmpty(queryDto.getSelectIds())) {
            queryWrapper.in("o.id", queryDto.getSelectIds());
            // ????????????????????????
            queryWrapper.orderByDesc("o.create_time");
        } else {
            DelOutboundServiceImplUtil.handlerQueryWrapper(queryWrapper, queryDto);
        }
        return this.baseMapper.exportList(queryWrapper);
    }

    @Override
    public List<DelOutboundReassignExportListDto> reassignExportList(DelOutboundListQueryDto queryDto) {
        QueryWrapper<DelOutboundListQueryDto> queryWrapper = new QueryWrapper<>();
        // ????????????????????????????????????
        if (CollectionUtils.isNotEmpty(queryDto.getSelectIds())) {
            queryWrapper.in("o.id", queryDto.getSelectIds());
            // ????????????????????????
            queryWrapper.orderByDesc("o.create_time");
        } else {
            DelOutboundServiceImplUtil.handlerQueryWrapper(queryWrapper, queryDto);
        }
        return this.baseMapper.reassignExportList(queryWrapper);
    }

    /**
     * ??????????????????
     *
     * @param inventoryOperateListDto inventoryOperateListDto
     */
    private void unFreeze(InventoryOperateListDto inventoryOperateListDto) {
        // ????????????
        Integer deduction = this.inventoryFeignClientService.unFreeze(inventoryOperateListDto);
        if (null == deduction || deduction < 1) {
            throw new CommonException("500", "????????????????????????");
        }
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????
     *
     * @param queryChargeVO      delOutboundChargeListVO
     * @param delOutboundCharges delOutboundCharges
     */
    private void setAmount(QueryChargeVO queryChargeVO, List<DelOutboundCharge> delOutboundCharges) {
        ListUtils.emptyIfNull(delOutboundCharges).forEach(item -> {
            String chargeNameEn = item.getChargeNameEn();
            if (chargeNameEn != null) {
                switch (chargeNameEn) {
                    case "Base Shipping Fee":
                        queryChargeVO.setBaseShippingFee(item.getAmount());
                        break;
                    case "Remote Area Surcharge":
                        queryChargeVO.setRemoteAreaSurcharge(item.getAmount());
                        break;
                    case "Over-Size Surcharge":
                        queryChargeVO.setOverSizeSurcharge(item.getAmount());
                        break;
                    case "Fuel Charge":
                        queryChargeVO.setFuelCharge(item.getAmount());
                        break;
                    default:
                        break;
                }
            }
        });
    }

    @Override
    public int setPurchaseNo(String purchaseNo, List<String> orderNoList) {
        if (CollectionUtils.isEmpty(orderNoList)) return 1;
        int update = baseMapper.update(new DelOutbound(), Wrappers.<DelOutbound>lambdaUpdate()
                .in(DelOutbound::getOrderNo, orderNoList)
                .set(DelOutbound::getPurchaseNo, purchaseNo)
        );
        logger.info("???????????????{}-????????????{},????????????{}", JSONObject.toJSONString(orderNoList), purchaseNo, update);
        return update;
    }

    @Override
    public int againTrackingNo(DelOutboundAgainTrackingNoDto dto) {
        // 1.?????????????????????????????????
        // ?????????????????????
        // 2.????????????????????????
        // 3.??????????????????
        // 4.??????????????????[call]
        Long id = dto.getId();
        String orderNo = dto.getOrderNo();
        if (null == id && StringUtils.isEmpty(orderNo)) {
            throw new CommonException("400", "ID?????????????????????");
        }
        DelOutbound delOutbound;
        if (null != id) {
            delOutbound = super.getById(id);
        } else {
            delOutbound = this.getByOrderNo(orderNo);
        }
        if (null == delOutbound) {
            throw new CommonException("400", "???????????????");
        }
        // ???????????????????????????
        // ?????????????????????????????????
        // ????????????????????????????????????????????????????????????????????????????????????????????????????????????
        DelOutboundFurtherHandlerDto furtherHandlerDto = new DelOutboundFurtherHandlerDto();

        boolean update = delOutboundExceptionService.againTrackingNo(delOutbound, dto, furtherHandlerDto);
        if (update) {
            furtherHandlerDto.setOrderNo(delOutbound.getOrderNo());
            this.furtherHandler(furtherHandlerDto);
        }
        return update ? 1 : 0;
    }

    @Override
    public List<DelOutboundListExceptionMessageVO> exceptionMessageList(List<String> orderNos) {
        if (CollectionUtils.isEmpty(orderNos)) {
            return Collections.emptyList();
        }
        return super.baseMapper.exceptionMessageList(orderNos);
    }

    @Override
    public List<DelOutboundListExceptionMessageExportVO> exceptionMessageExportList(List<String> orderNos) {
        if (CollectionUtils.isEmpty(orderNos)) {
            return Collections.emptyList();
        }
        return super.baseMapper.exceptionMessageExportList(orderNos);
    }

    @Override
    public List<QueryFinishListVO> queryFinishList(QueryFinishListDTO queryFinishListDTO) {
        return baseMapper.queryFinishList(queryFinishListDTO);
    }

    @Override
    public DelOutboundAddResponse reassign(DelOutboundDto dto) {
        if (!DelOutboundOrderTypeEnum.has(dto.getOrderType())) {
            throw new CommonException("400", "?????????????????????");
        }
        // ???????????????
        dto.setSourceType(DelOutboundConstant.SOURCE_TYPE_ADD);
        dto.setReassignType(DelOutboundConstant.REASSIGN_TYPE_Y);
        return this.createDelOutbound(dto);
    }

    @Override
    public void deleteFlag(DelOutbound delOutbound) {
        DelOutbound updateDelOutbound = new DelOutbound();
        updateDelOutbound.setId(delOutbound.getId());
        updateDelOutbound.setDelFlag("2");
        super.updateById(updateDelOutbound);
    }

    @Override
    public void importBoxLabel(List<DelOutboundBoxLabelDto> list, String sellerCode, String attachmentType) {
        List<String> businessNos = list.stream().map(vo -> vo.getBusinessNo()).collect(Collectors.toList());
        List<String> orders = list.stream().map(vo -> vo.getRemark()).collect(Collectors.toList());

        LambdaQueryWrapper<DelOutbound> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(DelOutbound::getSellerCode, sellerCode);
        queryWrapper.in(DelOutbound::getOrderNo, orders);
        List<DelOutbound> delOutboundList = this.list(queryWrapper);
        if(delOutboundList.size() != orders.size()){
            throw new CommonException("400", "??????????????????????????????");
        }


        BasAttachmentQueryDTO queryDTO = new BasAttachmentQueryDTO();
        queryDTO.setBusinessNoList(businessNos);
        queryDTO.setBusinessCode(attachmentType);
        List<BasAttachment> basAttachmentList = ListUtils.emptyIfNull(remoteAttachmentService.list(queryDTO).getData());
        basAttachmentList =basAttachmentList.stream().filter(distinctByKey(person -> person.getBusinessNo())).collect(Collectors.toList());

        Map<String, BasAttachment> uuidNameMap = basAttachmentList.stream().collect(Collectors.toMap(BasAttachment::getBusinessNo, account -> account));

        List<String> oldOrders = new ArrayList<String>();
        for (DelOutboundBoxLabelDto dto: list){
            BasAttachment data = uuidNameMap.get(dto.getBusinessNo());
            if(data == null){
                throw new CommonException("400", "????????????????????????");
            }
            if(!orders.contains(data.getRemark())){
                oldOrders.add(data.getRemark());
            }
            data.setRemark(dto.getRemark());

        }
        R r = remoteAttachmentService.update(basAttachmentList);
        if(r != null && r.getCode() == 200){
            if(oldOrders.size() > 0){
                LambdaUpdateWrapper<DelOutbound> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
                lambdaUpdateWrapper.set(DelOutbound::getUploadBoxLabel, "N");
                lambdaUpdateWrapper.in(DelOutbound::getOrderNo, oldOrders);
                this.update(lambdaUpdateWrapper);
            }


            LambdaUpdateWrapper<DelOutbound> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
            lambdaUpdateWrapper.set(DelOutbound::getUploadBoxLabel, "Y");
            lambdaUpdateWrapper.in(DelOutbound::getOrderNo, orders);
            this.update(lambdaUpdateWrapper);
        }
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T,?> keyExtractor){
        Map<Object,Boolean> map=new ConcurrentHashMap<>();
        return t -> map.putIfAbsent(keyExtractor.apply(t),Boolean.TRUE)==null;
    }

    @Transactional
    @Override
    public int updateReassignImportedData(List<DelOutboundReassignExportListVO> list) {
        List<LambdaUpdateWrapper<DelOutbound>> delOutboundList = new ArrayList<>();
        List<LambdaUpdateWrapper<DelOutboundAddress>> delOutboundAddressList = new ArrayList<>();
        for (DelOutboundReassignExportListVO vo : list) {
            // ???????????????????????????
            LambdaUpdateWrapper<DelOutbound> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
            lambdaUpdateWrapper.set(DelOutbound::getCodAmount, vo.getCodAmount());
            lambdaUpdateWrapper.set(DelOutbound::getShipmentRule, vo.getShipmentRule());
            lambdaUpdateWrapper.set(DelOutbound::getIoss, vo.getIoss());
            lambdaUpdateWrapper.eq(DelOutbound::getOrderNo, vo.getOrderNo());
            delOutboundList.add(lambdaUpdateWrapper);
            // ??????????????????
            LambdaUpdateWrapper<DelOutboundAddress> addressLambdaUpdateWrapper = Wrappers.lambdaUpdate();
            addressLambdaUpdateWrapper.set(DelOutboundAddress::getCountry, vo.getCountry());
            addressLambdaUpdateWrapper.set(DelOutboundAddress::getCountryCode, vo.getCountryCode());
            addressLambdaUpdateWrapper.set(DelOutboundAddress::getStateOrProvince, vo.getStateOrProvince());
            addressLambdaUpdateWrapper.set(DelOutboundAddress::getCity, vo.getCity());
            addressLambdaUpdateWrapper.set(DelOutboundAddress::getStreet1, vo.getStreet1());
            addressLambdaUpdateWrapper.set(DelOutboundAddress::getStreet2, vo.getStreet2());
            addressLambdaUpdateWrapper.set(DelOutboundAddress::getPostCode, vo.getPostCode());
            addressLambdaUpdateWrapper.set(DelOutboundAddress::getPhoneNo, vo.getPhoneNo());
            addressLambdaUpdateWrapper.set(DelOutboundAddress::getEmail, vo.getEmail());
            addressLambdaUpdateWrapper.eq(DelOutboundAddress::getOrderNo, vo.getOrderNo());
            delOutboundAddressList.add(addressLambdaUpdateWrapper);
        }
        String sqlStatement = sqlStatement(SqlMethod.UPDATE);
        AtomicInteger results = new AtomicInteger(0);
        int size = delOutboundList.size();
        executeBatch(sqlSession -> {
            int i = 1;
            for (LambdaUpdateWrapper<DelOutbound> wrapper : delOutboundList) {
                MapperMethod.ParamMap<LambdaUpdateWrapper<DelOutbound>> param = new MapperMethod.ParamMap<>();
                param.put(Constants.ENTITY, null);
                param.put(Constants.WRAPPER, wrapper);
                sqlSession.update(sqlStatement, param);
                if ((i % 100 == 0) || i == size) {
                    List<BatchResult> batchResults = sqlSession.flushStatements();
                    if (CollectionUtils.isNotEmpty(batchResults)) {
                        for (BatchResult batchResult : batchResults) {
                            int[] updateCounts = batchResult.getUpdateCounts();
                            results.getAndSet(IntStream.of(updateCounts).sum());
                        }
                    }
                }
                i++;
            }
        });
        this.delOutboundAddressService.updateReassignImportedData(delOutboundAddressList);
        return results.get();
    }

    @Override
    public int receiveLabel(DelOutboundReceiveLabelDto dto) {

        LambdaQueryWrapper<DelOutbound> queryWrapper = Wrappers.lambdaQuery();
        if(StringUtils.isNotEmpty(dto.getOrderNo())){
            queryWrapper.eq(DelOutbound::getOrderNo, dto.getOrderNo());

        }else if(StringUtils.isNotEmpty(dto.getOrderNo())){
            queryWrapper.eq(DelOutbound::getRefNo, dto.getRefNo());

        }else if(StringUtils.isNotEmpty(dto.getOrderNo())){
            queryWrapper.eq(DelOutbound::getTrackingNo, dto.getTrackingNo());
        }else{
            throw new CommonException("400", "????????????????????????");
        }
        DelOutbound data = this.getOne(queryWrapper);
        if(data == null){
            throw new CommonException("400", "??????????????????");
        }
        if(StringUtils.isNotEmpty(dto.getTraceId())){
            data.setTraceId(dto.getTraceId());
        }
        if(StringUtils.isNotEmpty(dto.getRemark())){
            data.setRemark(dto.getRemark());

        }

        data.setShipmentOrderLabelUrl(delOutboundBringVerifyService.saveShipmentLabel(dto.getFileStream(), data));
        this.updateById(data);
        return 1;
    }

    @Override
    public int boxStatus(DelOutboundBoxStatusDto dto) {
        LambdaQueryWrapper<DelOutboundDetail> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(DelOutboundDetail::getOrderNo, dto.getOrderNo());
        queryWrapper.eq(DelOutboundDetail::getBoxMark, dto.getBoxNo());
        List<DelOutboundDetail> dataDelOutboundDetailList = delOutboundDetailService.list(queryWrapper);
        if(dataDelOutboundDetailList.size() == 0){
            throw new CommonException("400", "???????????????????????????");
        }
        for (DelOutboundDetail detail: dataDelOutboundDetailList){
            detail.setOperationType(dto.getOperationType());
        }
        delOutboundDetailService.updateBatchById(dataDelOutboundDetailList);

        int i = 0;
        for (DelOutboundDetail detail: dataDelOutboundDetailList){
            if("Completed".equals(detail.getOperationType())){
                i++;
            }
        }
        if(i == dataDelOutboundDetailList.size()){
            //???????????????????????????????????????PRC
            ApplicationContext context = delOutboundBringVerifyService.initContext(this.getByOrderNo(dto.getOrderNo()));
            ApplicationContainer applicationContainer = new ApplicationContainer(context, BringVerifyEnum.PRC_PRICING, BringVerifyEnum.PRODUCT_INFO, BringVerifyEnum.PRC_PRICING);
            applicationContainer.action();

        }
        return 1;
    }


    @Override
    public void manualTrackingYee(List<String> list) {
        list.forEach(x->{
            DelOutboundListQueryDto delOutboundListQueryDto=baseMapper.pageLists(x);
            TraYee(delOutboundListQueryDto);
        });

    }

    public void TraYee(DelOutboundListQueryDto delOutboundListQueryDto){
        boolean success = false;
        String responseBody;
        logger.info("????????????TY{}");

        String trackingNo = delOutboundListQueryDto.getTrackingNo();
        try {
            Map<String, Object> requestBodyMap = new HashMap<>();
            List<Map<String, Object>> shipments = new ArrayList<>();
            Map<String, Object> shipment = new HashMap<>();
            shipment.put("trackingNo", trackingNo);
            shipment.put("carrierCode", delOutboundListQueryDto.getLogisticsProviderCode());
            shipment.put("logisticsServiceProvider", delOutboundListQueryDto.getLogisticsProviderCode());
            shipment.put("logisticsServiceName", delOutboundListQueryDto.getLogisticsProviderCode());
            shipment.put("platformCode", "DM");
            shipment.put("shopName", "");
            Date createTime = delOutboundListQueryDto.getCreateTime();
            if (null != createTime) {
                shipment.put("OrdersOn", DateFormatUtils.format(createTime, "yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
            }
            shipment.put("paymentTime", "");
            shipment.put("shippingOn", "");
            List<String> searchTags = new ArrayList<>();
            searchTags.add("");
            searchTags.add("");
            shipment.put("searchTags", searchTags);
            shipment.put("orderNo", delOutboundListQueryDto.getOrderNo());
            Map<String, Object> senderAddress = new HashMap<>();
            senderAddress.put("country", delOutboundListQueryDto.getCountry());
            senderAddress.put("province", delOutboundListQueryDto.getStateOrProvince());
            senderAddress.put("city", delOutboundListQueryDto.getCity());
            senderAddress.put("postcode", delOutboundListQueryDto.getPostCode());
            senderAddress.put("street1", delOutboundListQueryDto.getStreet1());
            senderAddress.put("street2", delOutboundListQueryDto.getStreet2());
            senderAddress.put("street3", delOutboundListQueryDto.getStreet3());
            shipment.put("senderAddress", senderAddress);
            Map<String, Object> destinationAddress = new HashMap<>();
            destinationAddress.put("country", delOutboundListQueryDto.getCountry());
            destinationAddress.put("province", delOutboundListQueryDto.getStateOrProvince());
            destinationAddress.put("city", delOutboundListQueryDto.getCity());
            destinationAddress.put("postcode", delOutboundListQueryDto.getPostCode());
            destinationAddress.put("street1", delOutboundListQueryDto.getStreet1());
            destinationAddress.put("street2",delOutboundListQueryDto.getStreet2());
            destinationAddress.put("street3", delOutboundListQueryDto.getStreet3());
            shipment.put("destinationAddress", destinationAddress);
            Map<String, Object> recipientInfo = new HashMap<>();
            recipientInfo.put("recipient", delOutboundListQueryDto.getConsignee());
            recipientInfo.put("phoneNumber", delOutboundListQueryDto.getPhoneNo());
            recipientInfo.put("email", "");
            shipment.put("recipientInfo", recipientInfo);
            Map<String, Object> customFieldInfo = new HashMap<>();
            customFieldInfo.put("fieldOne", delOutboundListQueryDto.getOrderNo());
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
                responseBody = "????????????";
            }
        }
        // ?????????????????????????????????
        if (success) {
            try {
                // ?????????????????????????????????????????????
                JSONObject jsonObject = JSON.parseObject(responseBody);
                // ?????????????????????OK
                if ("OK".equals(jsonObject.getString("status"))) {
                    // ????????????????????????????????????
                    JSONObject data = jsonObject.getJSONObject("data");

                    int successNumber = data.getIntValue("successNumber");

                    if (successNumber != 1) {
                        // ???????????????????????????1??????????????????
                        success = false;
                        // ??????????????????
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

                    if(StringUtils.isNotEmpty(trackingNo) && successNumber == 1) {

                        offlineDeliveryImportMapper.update(null, Wrappers.<OfflineDeliveryImport>lambdaUpdate()
                                .set(OfflineDeliveryImport::getDealStatus, OfflineDeliveryStateEnum.PUSH_TY.getCode())
                                .set(OfflineDeliveryImport::getUpdateBy, SecurityUtils.getUsername())
                                .set(OfflineDeliveryImport::getUpdateTime, new Date())
                                .eq(OfflineDeliveryImport::getTrackingNo, trackingNo)
                        );
                    }

                }

            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                if (e instanceof CommonException) {
                    throw e;
                }
                // ??????????????????????????????
                success = false;
            }
        }
        if (!success) {
            throw new CommonException("500", "??????TrackingYee??????");
        }
    }

    public void manualTrackingYees(String orderNo) {

            DelOutboundListQueryDto delOutboundListQueryDto=baseMapper.pageLists(orderNo);
            TraYees(delOutboundListQueryDto);


    }

    public void TraYees(DelOutboundListQueryDto delOutboundListQueryDto){
        boolean success = false;
        String responseBody;
        try {
            Map<String, Object> requestBodyMap = new HashMap<>();
            List<Map<String, Object>> shipments = new ArrayList<>();
            Map<String, Object> shipment = new HashMap<>();
            shipment.put("trackingNo", delOutboundListQueryDto.getTrackingNo());
            shipment.put("carrierCode", delOutboundListQueryDto.getLogisticsProviderCode());
            shipment.put("logisticsServiceProvider", delOutboundListQueryDto.getLogisticsProviderCode());
            shipment.put("logisticsServiceName", delOutboundListQueryDto.getLogisticsProviderCode());
            shipment.put("platformCode", "DM");
            shipment.put("shopName", "");
            Date createTime = delOutboundListQueryDto.getCreateTime();
            if (null != createTime) {
                shipment.put("OrdersOn", DateFormatUtils.format(createTime, "yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
            }
            shipment.put("paymentTime", "");
            shipment.put("shippingOn", "");
            List<String> searchTags = new ArrayList<>();
            searchTags.add("");
            searchTags.add("");
            shipment.put("searchTags", searchTags);
            shipment.put("orderNo", delOutboundListQueryDto.getOrderNo());
            Map<String, Object> senderAddress = new HashMap<>();
            senderAddress.put("country", delOutboundListQueryDto.getCountry());
            senderAddress.put("province", delOutboundListQueryDto.getStateOrProvince());
            senderAddress.put("city", delOutboundListQueryDto.getCity());
            senderAddress.put("postcode", delOutboundListQueryDto.getPostCode());
            senderAddress.put("street1", delOutboundListQueryDto.getStreet1());
            senderAddress.put("street2", delOutboundListQueryDto.getStreet2());
            senderAddress.put("street3", delOutboundListQueryDto.getStreet3());
            shipment.put("senderAddress", senderAddress);
            Map<String, Object> destinationAddress = new HashMap<>();
            destinationAddress.put("country", delOutboundListQueryDto.getCountry());
            destinationAddress.put("province", delOutboundListQueryDto.getStateOrProvince());
            destinationAddress.put("city", delOutboundListQueryDto.getCity());
            destinationAddress.put("postcode", delOutboundListQueryDto.getPostCode());
            destinationAddress.put("street1", delOutboundListQueryDto.getStreet1());
            destinationAddress.put("street2",delOutboundListQueryDto.getStreet2());
            destinationAddress.put("street3", delOutboundListQueryDto.getStreet3());
            shipment.put("destinationAddress", destinationAddress);
            Map<String, Object> recipientInfo = new HashMap<>();
            recipientInfo.put("recipient", delOutboundListQueryDto.getConsignee());
            recipientInfo.put("phoneNumber", delOutboundListQueryDto.getPhoneNo());
            recipientInfo.put("email", "");
            shipment.put("recipientInfo", recipientInfo);
            Map<String, Object> customFieldInfo = new HashMap<>();
            customFieldInfo.put("fieldOne", delOutboundListQueryDto.getOrderNo());
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
                responseBody = "????????????";
            }
        }
        // ?????????????????????????????????
        if (success) {
            try {
                // ?????????????????????????????????????????????
                JSONObject jsonObject = JSON.parseObject(responseBody);
                // ?????????????????????OK
                if ("OK".equals(jsonObject.getString("status"))) {
                    // ????????????????????????????????????
                    JSONObject data = jsonObject.getJSONObject("data");
                    if (1 != data.getIntValue("successNumber")) {
                        // ???????????????????????????1??????????????????
                        success = false;
                        // ??????????????????
                        int failNumber = data.getIntValue("failNumber");
                        if (failNumber > 0) {
                            JSONArray failImportRowResults = data.getJSONArray("failImportRowResults");
                            JSONObject failImportRowResult = failImportRowResults.getJSONObject(0);
                            JSONObject errorInfo = failImportRowResult.getJSONObject("errorInfo");
                            String errorCode = errorInfo.getString("errorCode");
                            String errorMessage = errorInfo.getString("errorMessage");
                            //throw new CommonException("500", "[" + errorCode + "]" + errorMessage);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                if (e instanceof CommonException) {
                    //throw e;
                }
                // ??????????????????????????????
                success = false;
            }
        }
        if (!success) {
            //throw new CommonException("500", "??????TrackingYee??????");
        }
    }
}

