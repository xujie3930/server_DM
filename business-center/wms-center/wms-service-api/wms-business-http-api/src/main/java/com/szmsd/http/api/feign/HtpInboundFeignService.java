package com.szmsd.http.api.feign;

import com.szmsd.common.core.domain.R;
import com.szmsd.http.api.BusinessHttpInterface;
import com.szmsd.http.api.feign.fallback.HtpInboundFeignFallback;
import com.szmsd.http.dto.CancelReceiptRequest;
import com.szmsd.http.dto.CreatePackageReceiptRequest;
import com.szmsd.http.dto.CreateReceiptRequest;
import com.szmsd.http.dto.CreateTrackRequest;
import com.szmsd.http.vo.CreateReceiptResponse;
import com.szmsd.http.vo.ResponseVO;
import io.swagger.annotations.ApiOperation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(contextId = "FeignClient.HtpInboundFeignService", name = BusinessHttpInterface.SERVICE_NAME, fallbackFactory = HtpInboundFeignFallback.class)
public interface HtpInboundFeignService {

    @PostMapping("/api/inbound/http/receipt")
    R<CreateReceiptResponse> create(@RequestBody CreateReceiptRequest createReceiptRequestDTO);

    @DeleteMapping("/api/inbound/http/receipt")
    R<ResponseVO> cancel(@RequestBody CancelReceiptRequest cancelReceiptRequestDTO);

    @PostMapping("/api/inbound/http/package")
    @ApiOperation(value = "B3 创建转运入库单")
    R<ResponseVO> createPackage(@RequestBody CreatePackageReceiptRequest createPackageReceiptRequest);

    @PostMapping("/api/inbound/http/tracking")
    @ApiOperation(value = "B4 创建入库单物流信息列表")
    R<ResponseVO> createTracking(@RequestBody CreateTrackRequest createTrackRequest);

}
