package com.szmsd.putinstorage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.putinstorage.domain.InboundReceiptRecord;
import com.szmsd.putinstorage.domain.vo.InboundReceiptDetailVO;
import com.szmsd.putinstorage.domain.vo.InboundReceiptInfoVO;
import com.szmsd.putinstorage.enums.InboundReceiptEnum;
import com.szmsd.putinstorage.enums.InboundReceiptRecordEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InboundReceiptRecordAsyncService {

    @Resource
    private IInboundReceiptService iInboundReceiptService;

    @Resource
    private IInboundReceiptRecordService iInboundReceiptRecordService;

    @Async
    public void saveRecord(InboundReceiptRecord inboundReceiptRecord) {
        String type = inboundReceiptRecord.getType();
        if (InboundReceiptRecordEnum.CREATE.getType().equals(type)) {
            LambdaQueryWrapper<InboundReceiptRecord> queryWrapper = Wrappers.lambdaQuery();
            queryWrapper.eq(InboundReceiptRecord::getType, type).eq(InboundReceiptRecord::getWarehouseNo, inboundReceiptRecord.getWarehouseNo());
            InboundReceiptRecord one = iInboundReceiptRecordService.getOne(queryWrapper);
            if (one != null) {
                inboundReceiptRecord.setType("修改");
                String remark = inboundReceiptRecord.getRemark();
                if (StringUtils.isNotEmpty(remark)) {
                    inboundReceiptRecord.setRemark(remark.replaceAll(InboundReceiptRecordEnum.CREATE.getType(), "修改"));
                }
            }
        }
        String warehouseNo = inboundReceiptRecord.getWarehouseNo();
        InboundReceiptInfoVO inboundReceiptInfoVO = iInboundReceiptService.queryInfo(warehouseNo);
        if (inboundReceiptInfoVO == null) {
            log.info("入库单[{}]不存在： {}", warehouseNo, inboundReceiptRecord);
        } else {
            inboundReceiptRecord.setWarehouseCode(inboundReceiptInfoVO.getWarehouseCode());
            if (!InboundReceiptRecordEnum.PUT.getType().equals(type)) {
                String sku = ListUtils.emptyIfNull(inboundReceiptInfoVO.getInboundReceiptDetails()).stream().map(InboundReceiptDetailVO::getSku).collect(Collectors.joining(","));
                inboundReceiptRecord.setSku(sku);
            }
        }
        log.info("保存入库单日志: {}", inboundReceiptRecord);
        iInboundReceiptRecordService.save(inboundReceiptRecord);

        if (inboundReceiptInfoVO == null) {
            return;
        }
        if (InboundReceiptRecordEnum.CREATE.getType().equals(type) || "修改".equals(type)) {
            boolean isReviewPassed = InboundReceiptEnum.InboundReceiptStatus.REVIEW_PASSED.getValue().equals(inboundReceiptInfoVO.getStatus());
            if (isReviewPassed) {
                iInboundReceiptRecordService.save(inboundReceiptRecord.setType(InboundReceiptRecordEnum.REVIEW.getType()).setRemark("仓库自动审核"));
            }

        }
    }

}

