package com.szmsd.delivery;

import com.szmsd.common.security.annotation.EnableCustomConfig;
import com.szmsd.common.security.annotation.EnableRyFeignClients;
import com.szmsd.common.swagger.annotation.EnableCustomSwagger2;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.client.SpringCloudApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCustomConfig
@EnableCustomSwagger2
@EnableRyFeignClients
@SpringCloudApplication
@EnableScheduling
public class BusinessDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessDeliveryApplication.class, args);
    }

}
