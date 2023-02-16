package com.szmsd.bas.api.feign;


import com.szmsd.bas.api.BusinessBasInterface;
import com.szmsd.bas.api.factory.EmailFeingFallback;
import com.szmsd.bas.dto.EmailDto;
import com.szmsd.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(contextId = "FeignClient.EmailFeingService", name = BusinessBasInterface.SERVICE_NAME, fallbackFactory = EmailFeingFallback.class)
public interface EmailFeingService {

    @PostMapping("/bas/email/sendEmail")
    R sendEmail(@RequestBody EmailDto emailDto);

    @PostMapping("/bas/email/sendEmailError")
    R sendEmailError(@RequestBody EmailDto emailDto);
}
