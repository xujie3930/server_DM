package com.dm.copy_data;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CopyDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(CopyDataApplication.class, args);
    }

}
