package com.szmsd.http.api.feign;

import com.szmsd.common.core.domain.R;
import com.szmsd.http.api.BusinessHttpInterface;
import com.szmsd.http.api.feign.fallback.HtpBasFeignFallback;
import com.szmsd.http.dto.*;
import com.szmsd.http.vo.BaseOperationResponse;
import com.szmsd.http.vo.ResponseVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(contextId = "FeignClient.HtpBasFeignService", name = BusinessHttpInterface.SERVICE_NAME, fallbackFactory = HtpBasFeignFallback.class)
public interface HtpBasFeignService {
    @PostMapping("/api/bas/http/createPacking")
    R<ResponseVO> createPacking(@RequestBody PackingRequest packingRequest);

    @PostMapping("/api/bas/http/createProduct")
    R<ResponseVO> createProduct(@RequestBody ProductRequest productRequest);

    @PostMapping("/api/bas/http/createMaterial")
    R<ResponseVO> createMaterial(@RequestBody MaterialRequest materialRequest);

    @PostMapping("/api/bas/http/createSeller")
    R<ResponseVO> createSeller(@RequestBody SellerRequest sellerRequest);

    @PostMapping("/api/bas/http/specialOperation/type")
    R<ResponseVO> specialOperationType(@RequestBody SpecialOperationRequest specialOperationRequest);

    @PostMapping("/api/bas/http/specialOperation/result")
    R<ResponseVO> specialOperationResult(@RequestBody SpecialOperationResultRequest specialOperationResultRequest);

    @PostMapping("/api/bas/http/shipmentRule")
    R<BaseOperationResponse> shipmentRule(@RequestBody AddShipmentRuleRequest addShipmentRuleRequest);

    @PostMapping("/api/bas/http/inspection")
    R<ResponseVO> inspection(@RequestBody AddSkuInspectionRequest request);
}
