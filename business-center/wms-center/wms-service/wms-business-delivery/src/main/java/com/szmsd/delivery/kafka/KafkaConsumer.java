//package com.szmsd.delivery.kafka;
//
//import com.alibaba.fastjson.JSON;
//import com.szmsd.delivery.config.ThreadPoolExecutorConfiguration;
//import com.szmsd.http.api.service.IHtpCarrierClientService;
//import com.szmsd.http.dto.CreateShipmentOrderCommand;
//import com.szmsd.http.dto.ProblemDetails;
//import com.szmsd.http.dto.ResponseObject;
//import com.szmsd.http.dto.ShipmentOrderResult;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.kafka.clients.consumer.ConsumerRecord;
//import org.redisson.api.RedissonClient;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.kafka.support.Acknowledgment;
//import org.springframework.kafka.support.KafkaHeaders;
//import org.springframework.messaging.handler.annotation.Header;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.PostConstruct;
//import javax.annotation.Resource;
//import java.util.Optional;
//import java.util.concurrent.ThreadPoolExecutor;
//import java.util.concurrent.locks.LockSupport;
//
///**
// * @author xujie
// * @description kafka消费者
// * @create 2022-03-02 16:57
// **/
//@Component
//@Slf4j
//public class KafkaConsumer {
//
//    @Autowired
//    private RedisTemplate redisTemplate;
//    @Autowired
//    private IHtpCarrierClientService htpCarrierClientService;
//
//
//    @KafkaListener(groupId = "test", topics = "#{'${kafkaConsumer.topic}'}")
//    public void topic_test(ConsumerRecord<?, ?> record, Acknowledgment ack, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
//        Optional message = Optional.ofNullable(record.value());
////        System.out.println("获取Topic:" + topic +"的消息:"+ message +"进行消费");
//        log.info("获取Topic:{}的消息:{}进行消费", topic, message);
//        Object msg = message.get();
//        CreateShipmentOrderCommand createShipmentOrderCommand = JSON.parseObject(String.valueOf(msg), CreateShipmentOrderCommand.class);
//        try {
//            ResponseObject<ShipmentOrderResult, ProblemDetails> responseObjectWrapper = htpCarrierClientService.shipmentOrder(createShipmentOrderCommand);
//            log.info("调用成功");
//            redisTemplate.opsForValue().set(createShipmentOrderCommand.getCurrentThread().getId(), responseObjectWrapper);
//            log.info("redis设置值成功:{}", responseObjectWrapper);
//        } catch (Exception e) {
//            log.error("消费异常:{},Topic:{},Message:{}", e, topic, record.value());
//        } finally {
//            log.info("唤醒线程:{}", createShipmentOrderCommand.getCurrentThread());
//            LockSupport.unpark(createShipmentOrderCommand.getCurrentThread());
//            ack.acknowledge();
//        }
//    }
//
//}
