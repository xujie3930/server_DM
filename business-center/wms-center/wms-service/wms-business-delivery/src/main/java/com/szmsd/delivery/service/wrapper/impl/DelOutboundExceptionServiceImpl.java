package com.szmsd.delivery.service.wrapper.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.utils.MessageUtil;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import com.szmsd.delivery.domain.DelOutbound;
import com.szmsd.delivery.domain.DelOutboundAddress;
import com.szmsd.delivery.dto.DelOutboundAgainTrackingNoDto;
import com.szmsd.delivery.dto.DelOutboundFurtherHandlerDto;
import com.szmsd.delivery.enums.DelOutboundConstant;
import com.szmsd.delivery.enums.DelOutboundTrackingAcquireTypeEnum;
import com.szmsd.delivery.event.DelOutboundOperationLogEnum;
import com.szmsd.delivery.service.IDelOutboundAddressService;
import com.szmsd.delivery.service.IDelOutboundService;
import com.szmsd.delivery.service.wrapper.*;
import com.szmsd.delivery.util.Utils;
import com.szmsd.http.api.service.IHtpCarrierClientService;
import com.szmsd.http.api.service.IHtpOutboundClientService;
import com.szmsd.http.api.service.IHtpPricedProductClientService;
import com.szmsd.http.dto.ChargeWrapper;
import com.szmsd.http.dto.ProblemDetails;
import com.szmsd.http.dto.ResponseObject;
import com.szmsd.http.vo.PricedProductInfo;
import com.szmsd.returnex.api.feign.serivice.IReturnExpressFeignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class DelOutboundExceptionServiceImpl implements IDelOutboundExceptionService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IDelOutboundService delOutboundService;
    @Autowired
    private IDelOutboundAddressService delOutboundAddressService;
    @Autowired
    private IHtpPricedProductClientService htpPricedProductClientService;
    @Autowired
    @Lazy
    private IDelOutboundBringVerifyService delOutboundBringVerifyService;
    @Autowired
    private IHtpOutboundClientService htpOutboundClientService;
    @Autowired
    private IHtpCarrierClientService htpCarrierClientService;
    @SuppressWarnings({"all"})
    @Autowired
    private IReturnExpressFeignService returnExpressFeignService;

    @Transactional
    @Override
    public boolean againTrackingNo(DelOutbound delOutbound, DelOutboundAgainTrackingNoDto dto, DelOutboundFurtherHandlerDto furtherHandlerDto) {
        String orderNo = delOutbound.getOrderNo();
        // ????????????????????????????????????????????????????????????
        LambdaQueryWrapper<DelOutboundAddress> addressLambdaQueryWrapper = Wrappers.lambdaQuery();
        addressLambdaQueryWrapper.eq(DelOutboundAddress::getOrderNo, orderNo);
        this.delOutboundAddressService.remove(addressLambdaQueryWrapper);
        DelOutboundAddress delOutboundAddress = BeanMapperUtil.map(dto.getAddress(), DelOutboundAddress.class);
        if (Objects.nonNull(delOutboundAddress)) {
            delOutboundAddress.setOrderNo(orderNo);
            this.delOutboundAddressService.save(delOutboundAddress);
        }
        furtherHandlerDto.setDelOutboundAddress(delOutboundAddress);
        // ????????????????????????????????????
        String productCode = dto.getShipmentRule();
        PricedProductInfo pricedProductInfo = htpPricedProductClientService.infoAndSubProducts(productCode);
        if (null == pricedProductInfo) {
            // ????????????
            throw new CommonException("400", MessageUtil.to("????????????[" + productCode + "]????????????","Failed to query product ["+productCode+"] information" ));
        }
        // ????????????????????????,??????initContext??????
        furtherHandlerDto.setShipmentRule(productCode);
        furtherHandlerDto.setShipmentService(pricedProductInfo.getLogisticsRouteId());
        furtherHandlerDto.setTrackingAcquireType(DelOutboundTrackingAcquireTypeEnum.WAREHOUSE_SUPPLIER.getCode());


        DelOutboundWrapperContext delOutboundWrapperContext = this.delOutboundBringVerifyService.initContext(delOutbound, furtherHandlerDto);

        /* DelOutbound wrapperContextDelOutbound = delOutboundWrapperContext.getDelOutbound();
       wrapperContextDelOutbound.setShipmentRule(productCode);
        wrapperContextDelOutbound.setShipmentService(pricedProductInfo.getLogisticsRouteId());*/


        // PRC??????????????????
        ResponseObject<ChargeWrapper, ProblemDetails> prcResponseObject = delOutboundBringVerifyService.pricing(delOutboundWrapperContext, PricingEnum.PACKAGE);
        if (null == prcResponseObject) {
            // ??????????????????
            throw new CommonException("400", "????????????????????????[??????????????????]");
        } else {
            // ???????????????
            if (!prcResponseObject.isSuccess()) {
                // ????????????
                String exceptionMessage = Utils.defaultValue(ProblemDetails.getErrorMessageOrNull(prcResponseObject.getError()), "????????????????????????2[??????????????????]");
                throw new CommonException("400", exceptionMessage);
            }
        }

        /*
        // ???????????????????????????
        ShipmentOrderResult shipmentOrderResult = delOutboundBringVerifyService.shipmentOrder(delOutboundWrapperContext);

        // ??????WMS??????
        ShipmentTrackingChangeRequestDto shipmentTrackingChangeRequestDto = new ShipmentTrackingChangeRequestDto();
        shipmentTrackingChangeRequestDto.setWarehouseCode(delOutbound.getWarehouseCode());
        shipmentTrackingChangeRequestDto.setOrderNo(delOutbound.getOrderNo());
        shipmentTrackingChangeRequestDto.setTrackingNo(shipmentOrderResult.getMainTrackingNumber());
        htpOutboundClientService.shipmentTracking(shipmentTrackingChangeRequestDto);

        // ????????????
        CreateShipmentOrderCommand command = new CreateShipmentOrderCommand();
        command.setWarehouseCode(delOutbound.getWarehouseCode());
        command.setOrderNumber(shipmentOrderResult.getOrderNumber());
        ResponseObject<FileStream, ProblemDetails> responseObject = htpCarrierClientService.label(command);
        File labelFile = null;
        if (null != responseObject) {
            if (responseObject.isSuccess()) {
                FileStream fileStream = responseObject.getObject();
                String pathname = DelOutboundServiceImplUtil.getLabelFilePath(delOutbound);
                File file = new File(pathname);
                if (!file.exists()) {
                    try {
                        FileUtils.forceMkdir(file);
                    } catch (IOException e) {
                        throw new CommonException("500", "???????????????[" + file.getPath() + "]?????????Error???" + e.getMessage());
                    }
                }
                byte[] inputStream;
                if (null != fileStream && null != (inputStream = fileStream.getInputStream())) {
                    labelFile = new File(file.getPath() + "/" + shipmentOrderResult.getOrderNumber());
                    try {
                        FileUtils.writeByteArrayToFile(labelFile, inputStream, false);
                    } catch (IOException e) {
                        throw new CommonException("500", "???????????????????????????Error???" + e.getMessage());
                    }
                }
            } else {
                String exceptionMessage = Utils.defaultValue(ProblemDetails.getErrorMessageOrNull(responseObject.getError()), "???????????????????????????2");
                throw new CommonException("500", exceptionMessage);
            }
        } else {
            throw new CommonException("500", "???????????????????????????");
        }

        if (null == labelFile) {
            throw new CommonException("500", "????????????????????????");
        }
        // ????????????
        if (!labelFile.exists()) {
            throw new CommonException("500", "?????????????????????");
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
                throw new CommonException("400", "??????????????????");
            }
            if (!responseVO.getSuccess()) {
                throw new CommonException("400", Utils.defaultValue(responseVO.getMessage(), "??????????????????2"));
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new CommonException("500", "????????????????????????");
        }
        */

        LambdaUpdateWrapper<DelOutbound> lambdaUpdateWrapper = Wrappers.lambdaUpdate();
        // lambdaUpdateWrapper.set(DelOutbound::getTrackingNo, shipmentOrderResult.getMainTrackingNumber());
        // lambdaUpdateWrapper.set(DelOutbound::getShipmentOrderNumber, shipmentOrderResult.getOrderNumber());
        lambdaUpdateWrapper.set(DelOutbound::getShipmentRule, productCode);
        lambdaUpdateWrapper.set(DelOutbound::getShipmentService, pricedProductInfo.getLogisticsRouteId());
        lambdaUpdateWrapper.set(DelOutbound::getTrackingAcquireType, DelOutboundTrackingAcquireTypeEnum.WAREHOUSE_SUPPLIER.getCode());
        // ??????????????????begin???????????????????????????????????????
        lambdaUpdateWrapper.set(DelOutbound::getShipmentState, ShipmentEnum.BEGIN.name());
        lambdaUpdateWrapper.eq(DelOutbound::getId, delOutbound.getId());
        boolean update = delOutboundService.update(null, lambdaUpdateWrapper);
        Object[] params = new Object[]{delOutbound, productCode};
        DelOutboundOperationLogEnum.AGAIN_TRACKING_NO.listener(params);
        return update;
    }

    @Async
    @Override
    public void updateTrackNoByOutBoundNo(String orderNo) {
        DelOutbound delOutbound = this.delOutboundService.getByOrderNo(orderNo);
        // ????????????????????????
        if (DelOutboundConstant.REASSIGN_TYPE_Y.equals(delOutbound.getReassignType())) {
            this.returnExpressFeignService.updateTrackNoByOutBoundNo(orderNo, delOutbound.getTrackingNo());
        }
    }
}
