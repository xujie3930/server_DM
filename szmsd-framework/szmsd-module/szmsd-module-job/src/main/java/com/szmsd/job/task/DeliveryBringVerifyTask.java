package com.szmsd.job.task;

import com.alibaba.datax.core.Engine;
import com.alibaba.fastjson.JSON;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.delivery.api.feign.DelOutboundFeignService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author lc
 * @Date 2022/8/20 10:49
 * @PackageName:com.szmsd.job.task
 * @ClassName: DeliveryBringVerifyTask
 * @Description:
 * @Version 1.0
 */
@Component("deliveryBringVerifyTask")
@Slf4j
public class DeliveryBringVerifyTask {

    private static boolean isRunning = false;
    @Resource
    private DelOutboundFeignService feignService;

    /**
     * 通知delivery的 bringVerify任务执行
     * 因为生产的多实例，出现任务分配不均的情况
     * 尝试通过外部调用的方法，自动分发均匀执行
     */
    public void notifyBringVerify()
    {
        log.info("[szmsd-module-job]-[DeliveryBringVerifyTask]-[notifyBringVerify] 通知提审 定时任务 开始");
        R<String> notifyBringVerify = feignService.notifyBringVerify();
        log.info("[szmsd-module-job]-[DeliveryBringVerifyTask]-[notifyBringVerify] 通知提审 通知结果:{}", JSON.toJSONString(notifyBringVerify));
        log.info("[szmsd-module-job]-[DeliveryBringVerifyTask]-[notifyBringVerify] 通知提审 定时任务 结束");
    }

    public void notifyAmazonLogisticsRouteId()
    {
        log.info("[szmsd-module-job]-[DeliveryBringVerifyTask]-[notifyBringVerify] 通知获取亚马逊挂号定时任务执行任务 定时任务 开始");
        R<String> notifyBringVerify = feignService.notifyAmazonLogisticsRouteId();
        log.info("[szmsd-module-job]-[DeliveryBringVerifyTask]-[notifyBringVerify] 通知获取亚马逊挂号定时任务执行任务 通知结果:{}", JSON.toJSONString(notifyBringVerify));
        log.info("[szmsd-module-job]-[DeliveryBringVerifyTask]-[notifyBringVerify] 通知获取亚马逊挂号定时任务执行任务 定时任务 结束");
    }

    public void notifyWMS()
    {
        log.info("[szmsd-module-job]-[DeliveryBringVerifyTask]-[notifyBringVerify] 通知订单执行WMS定时任务执行任务 定时任务 开始");
        R<String> notifyBringVerify = feignService.notifyWMS();
        log.info("[szmsd-module-job]-[DeliveryBringVerifyTask]-[notifyBringVerify] 通知订单执行WMS定时任务执行任务 通知结果:{}", JSON.toJSONString(notifyBringVerify));
        log.info("[szmsd-module-job]-[DeliveryBringVerifyTask]-[notifyBringVerify] 通知订单执行WMS定时任务执行任务 定时任务 结束");
    }


    public void notifyDelOutboundTransferTimer()
    {
        R<String> notifyBringVerify = feignService.notifyDelOutboundTransferTimer();
    }


    public void notifyDelOutboundTransferTimer2()
    {
        R<String> notifyBringVerify = feignService.notifyDelOutboundTransferTimer2();
    }

    public void delOutboundCarrierTimer()
    {
        feignService.delOutboundCarrierTimer();
    }

    //每分钟更新一次
    public void replaceDataTaskScheduler() {
        if (!isRunning) {
            try {
                log.info("定时同步数据开始");
                // 开始时间
                isRunning = true;
                String path =  this.getClass().getClassLoader().getResource("").getPath();
                System.setProperty("datax.home", path+ "lib/datax/datax");
                for (String name : getNameList()) {
                    operation(path + "json/" + name);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                log.info("定时同步数据异常：{}",  e.toString());
            }finally {
                isRunning = false;
            }
        }
    }

    private void operation(String absPath) throws Throwable {
        String[] datxArgs1 = {"-job", absPath, "-mode", "standalone", "-jobid", "-1"};
        Engine.entry(datxArgs1);   //从这里启动
    }

    public List<String> getNameList(){
        List<String> nameList = new ArrayList<>();
        try{
            org.springframework.core.io.Resource[] resources = new PathMatchingResourcePatternResolver().getResources(ResourceUtils.CLASSPATH_URL_PREFIX+"/json/*.json");
            for(org.springframework.core.io.Resource resource : resources){
                String fileName = resource.getFilename();
                nameList.add(fileName);
            }

        }catch(Exception e){
            return nameList;
        }
        return nameList;

    }
}
