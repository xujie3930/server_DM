package com.szmsd.pack.listeners;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.szmsd.common.core.domain.R;
import com.szmsd.pack.domain.PackageCollection;
import com.szmsd.pack.domain.PackageCollectionDetail;
import com.szmsd.pack.events.PackageCollectionContextEvent;
import com.szmsd.pack.service.IPackageCollectionDetailService;
import com.szmsd.pack.service.IPackageCollectionService;
import com.szmsd.pack.service.impl.PackageCollectionContext;
import com.szmsd.putinstorage.api.feign.InboundReceiptFeignService;
import com.szmsd.putinstorage.domain.vo.InboundReceiptInfoVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class PackageCollectionContextListener {
    private final Logger logger = LoggerFactory.getLogger(PackageCollectionContextListener.class);

    @Autowired
    private IPackageCollectionService packageCollectionService;
    @Autowired
    private IPackageCollectionDetailService packageCollectionDetailService;
    @SuppressWarnings({"all"})
    @Autowired
    private InboundReceiptFeignService inboundReceiptFeignService;

    @Async
    @EventListener
    public void onApplicationEvent(PackageCollectionContextEvent event) {
        Object source = event.getSource();
        if (source instanceof PackageCollectionContext) {
            PackageCollectionContext packageCollectionContext = (PackageCollectionContext) source;
            PackageCollection packageCollection = packageCollectionContext.getPackageCollection();
            if (PackageCollectionContext.Type.CANCEL.equals(packageCollectionContext.getType())) {
                // 处理单据取消的逻辑
                this.packageCollectionService.notRecordCancel(packageCollection);
            } else if (PackageCollectionContext.Type.CREATE_RECEIVER.equals(packageCollectionContext.getType())) {
                if (!packageCollectionContext.isHasDetail()) {
                    LambdaQueryWrapper<PackageCollectionDetail> packageCollectionDetailLambdaQueryWrapper = Wrappers.lambdaQuery();
                    packageCollectionDetailLambdaQueryWrapper.eq(PackageCollectionDetail::getCollectionId, packageCollection.getId());
                    packageCollectionDetailLambdaQueryWrapper.orderByAsc(PackageCollectionDetail::getSort);
                    packageCollection.setDetailList(this.packageCollectionDetailService.list(packageCollectionDetailLambdaQueryWrapper));
                }
                // 创建入库单
                R<InboundReceiptInfoVO> r = inboundReceiptFeignService.collectAndInbound(packageCollection);
                try {
                    InboundReceiptInfoVO receiptInfoVO = R.getDataAndException(r);
                    String warehouseNo = receiptInfoVO.getWarehouseNo();
                    // 把入库单号更新到揽收单上
                    LambdaUpdateWrapper<PackageCollection> updateWrapper = Wrappers.lambdaUpdate();
                    updateWrapper.eq(PackageCollection::getId, packageCollection.getId());
                    updateWrapper.set(PackageCollection::getReceiptNo, warehouseNo);
                    this.packageCollectionService.update(updateWrapper);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }
}
