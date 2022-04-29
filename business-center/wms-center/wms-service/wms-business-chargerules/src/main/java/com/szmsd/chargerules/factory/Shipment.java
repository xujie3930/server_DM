package com.szmsd.chargerules.factory;

import com.szmsd.chargerules.service.IChargeLogService;
import com.szmsd.chargerules.service.IPayService;
import com.szmsd.common.core.domain.R;
import com.szmsd.delivery.api.feign.DelOutboundFeignService;
import com.szmsd.delivery.domain.DelOutbound;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 出库
 */
@Slf4j
@Component
public class Shipment extends OrderType {

    @Resource
    private IPayService payService;

    @Resource
    private IChargeLogService chargeLogService;

    @Resource
    private DelOutboundFeignService delOutboundFeignService;

    @Override
    public String findOrderById(String orderNo) {
        R<DelOutbound> info = delOutboundFeignService.details(orderNo);
        if(info.getCode() == 200 && info.getData() != null) {
            return info.getData().getCustomCode();
        }
        log.error("findOrderById error: {}",info.getData());
        return null;
    }
}
