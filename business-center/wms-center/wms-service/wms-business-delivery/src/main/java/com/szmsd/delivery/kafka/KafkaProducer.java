//package com.szmsd.delivery.kafka;
//
//import com.alibaba.fastjson.JSON;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.kafka.support.SendResult;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Component;
//import org.springframework.util.concurrent.ListenableFuture;
//import org.springframework.util.concurrent.ListenableFutureCallback;
//
//import javax.annotation.PostConstruct;
//
///**
// * @author xujie
// * @description kafka生产者
// * @create 2022-12-29 10:51
// **/
//@Component
//@Slf4j
//public class KafkaProducer {
//
//    @Autowired
//    private KafkaTemplate<String, Object> kafkaTemplate;
//    @Value("${kafkaProducer.topic}")
//    private String topic;
//
////    @PostConstruct
////    private void init() {
////        kafkaTemplate.send(topic, "fuck myself");
////        System.out.println("kafka消费成功");
////    }
//
//    @Async
//    public void send(Object obj) {
//        String obj2String = JSON.toJSONString(obj);
//        log.info("准备发送消息为：{}", obj2String);
//        //发送消息
//        ListenableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, obj2String);
//        future.addCallback(new ListenableFutureCallback<SendResult<String, Object>>() {
//            @Override
//            public void onFailure(Throwable throwable) {
//                //发送失败的处理
//                log.error(topic + " - 生产者 发送消息失败：" + throwable.getMessage());
//            }
//
//            @Override
//            public void onSuccess(SendResult<String, Object> stringObjectSendResult) {
//                //成功的处理
//                log.info(topic + " - 生产者 发送消息成功：" + stringObjectSendResult.toString());
//            }
//        });
//
//
//    }
//}
