package com.szmsd.delivery.api.feign.factory;

import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.exception.web.BaseException;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.delivery.api.feign.DelOutboundFeignService;
import com.szmsd.delivery.domain.DelOutbound;
import com.szmsd.delivery.domain.DelOutboundPacking;
import com.szmsd.delivery.dto.*;
import com.szmsd.delivery.vo.*;
import com.szmsd.finance.dto.QueryChargeDto;
import com.szmsd.finance.vo.QueryChargeVO;
import com.szmsd.http.vo.PricedProduct;
import com.szmsd.inventory.domain.dto.QueryFinishListDTO;
import com.szmsd.inventory.domain.vo.QueryFinishListVO;
import feign.hystrix.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author zhangyuyuan
 * @date 2021-03-06 14:33
 */
@Component
public class DelOutboundFeignFallback implements FallbackFactory<DelOutboundFeignService> {
    @Override
    public DelOutboundFeignService create(Throwable throwable) {
        return new DelOutboundFeignService() {
            @Override
            public R<Integer> shipment(ShipmentRequestDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Integer> shipmentPacking(ShipmentPackingMaterialRequestDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Integer> shipmentPackingMaterial(ShipmentPackingMaterialRequestDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Integer> shipmentPackingMeasure(ShipmentPackingMaterialRequestDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Integer> shipmentContainers(ShipmentContainersRequestDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<DelOutbound> details(String orderId) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundDetailListVO>> getDelOutboundDetailsList(DelOutboundListQueryDto queryDto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<DelOutboundThirdPartyVO> getInfoForThirdParty(DelOutboundVO vo) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<TableDataInfo<QueryChargeVO>> getDelOutboundCharge(QueryChargeDto queryDto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundDetailVO>> createPurchaseOrderListByIdList(List<String> idList) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundDetailVO>> getTransshipmentProductData(List<String> idList) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R setPurchaseNo(String purchaseNo, List<String> orderNoList) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Integer> furtherHandler(DelOutboundFurtherHandlerDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public TableDataInfo<DelOutboundListVO> page(DelOutboundListQueryDto queryDto) {
                throw new BaseException("查询出库单异常:", throwable.getMessage());
            }

            @Override
            public R<Integer> canceled(DelOutboundCanceledDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<PricedProduct>> inService(DelOutboundOtherInServiceDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundAddResponse>> add(List<DelOutboundDto> dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<DelOutboundAddResponse> addShipment(DelOutboundDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<DelOutboundAddResponse> addShipmentPackageCollection(DelOutboundDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundLabelResponse>> labelBase64(DelOutboundLabelDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Integer> uploadBoxLabel(DelOutboundUploadBoxLabelDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundPacking>> queryList(DelOutboundPacking request) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundPackingVO>> listByOrderNo(DelOutboundPacking delOutboundPacking) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Integer> againTrackingNo(DelOutboundAgainTrackingNoDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundListExceptionMessageVO>> exceptionMessageList(List<String> orderNos) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundListExceptionMessageExportVO>> exceptionMessageExportList(List<String> orderNos) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundBringVerifyVO>> bringVerify(DelOutboundBringVerifyDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R updateShipmentLabel(List<String> idList) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<DelOutboundVO> getInfoByOrderNo(String orderNo) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<DelOutboundVO> getStatusByOrderNo(String orderNo) {
                return R.convertResultJson(throwable);
            }

            @Override
            public TableDataInfo<QueryFinishListVO> queryFinishList(QueryFinishListDTO queryFinishListDTO) {
                throw new RuntimeException(throwable.getMessage());
            }

            @Override
            public R<DelOutboundAddResponse> reassign(DelOutboundDto dto) {
                throw new RuntimeException(throwable.getMessage());
            }

            @Override
            public R<DelOutboundAddResponse> addShopify(DelOutboundDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<DelTrackMainDocCommonDto> commonTrackList(List<String> orders) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Boolean> updateInStockList(List<Long> idList) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Integer> updateWeightDelOutbound(UpdateWeightDelOutboundDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Integer> receiveLabel(DelOutboundReceiveLabelDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Integer> boxStatus(DelOutboundBoxStatusDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<DelOutboundBringVerifyVO>> bringVerifyByOrderNo(DelOutboundBringVerifyNoDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<String> notifyBringVerify() {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<String> notifyAmazonLogisticsRouteId() {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<String> notifyWMS() {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<String> notifyDelOutboundTransferTimer() {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<String> notifyDelOutboundTransferTimer2() {
                return R.convertResultJson(throwable);
            }
        };
    }
}
