package com.szmsd.bas.api.factory;

import com.szmsd.bas.api.feign.BaseProductFeignService;
import com.szmsd.bas.domain.BaseProduct;
import com.szmsd.bas.dto.*;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.web.page.TableDataInfo;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Slf4j
@Component
public class BaseProductFeignFallback implements FallbackFactory<BaseProductFeignService> {

    @Override
    public BaseProductFeignService create(Throwable throwable) {
        return new BaseProductFeignService() {
            @Override
            public TableDataInfo list(BaseProductQueryDto queryDto) {
                return null;
            }

            @Override
            public R add(BaseProductDto baseProductDto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R rePushBaseProduct(String sku) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<Boolean> checkSkuValidToDelivery(@RequestBody BaseProduct baseProduct) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<BaseProduct>> listSku(@RequestBody BaseProduct baseProduct) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<BaseProduct> getSku(@RequestBody BaseProduct baseProduct) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<BaseProductMeasureDto>> batchSKU(@RequestBody BaseProductBatchQueryDto dto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R measuringProduct(@RequestBody MeasuringProductRequest measuringProductRequest) {
                log.info("更新sku仓库测量值失败: {}", throwable.getMessage());
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<String>> listProductAttribute(BaseProductConditionQueryDto conditionQueryDto) {
                return R.convertResultJson(throwable);
            }

            @Override
            public R<List<BaseProduct>> queryProductList(BaseProductConditionQueryDto conditionQueryDto) {
                return R.convertResultJson(throwable);
            }
        };
    }
}
