package com.szmsd.bas.quartz;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * 文件名: QuartzConfig.java
 *
 * @author: jiangjun
 * 创建时间: 2022/10/20
 * 描述:
 */

@Configuration
public class BasQuartzConfig {

    @Bean
    public JobDetail EmailJob() {
        return JobBuilder.newJob(EmailJob.class).withIdentity("EmailJob").storeDurably().build();
    }




    @Bean
    public JobDetail InitRedisCacheJob() {
        return JobBuilder.newJob(InitRedisCacheJob.class).withIdentity("InitRedisCacheJob").storeDurably().build();
    }





    @Bean
    public Trigger EmailJobTrigger() {
        //0/3 * * * * ? 3秒测试 "0 0 12 * * ?"
        return TriggerBuilder.newTrigger().forJob(EmailJob())
                .withIdentity("EmailJob")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 12 * * ?"))
                .build();
    }


    @Bean
    public Trigger InitRedisCacheJobTrigger() {
        //0/3 * * * * ? 3秒测试 "0 0 12 * * ?"，"0 */50 * * * ?" 五十分钟一次
        return TriggerBuilder.newTrigger().forJob(InitRedisCacheJob())
                .withIdentity("InitRedisCacheJob")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 */10 * * * ?"))
                .build();
    }







}