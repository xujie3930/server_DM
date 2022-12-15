package com.dm.copy_data.tesk;


import com.alibaba.datax.core.Engine;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;


/**
 * 定时任务 - 更新第三方接口国家和城市信息
 */
@Component("replaceDataTask")
public class ReplaceDataTask {

    private static boolean isRunning = false;

//    @PostConstruct
//    public void load() {
//        this.ReplaceDataTaskScheduler();
//    }

    //每分钟更新一次
    @Scheduled(cron = "0 */1 * * * ?")
    public void ReplaceDataTaskScheduler() {
        if (!isRunning) {
            try {
                // 开始时间
                isRunning = true;
                String path =  this.getClass().getClassLoader().getResource("").getPath();
                System.out.println("路径:" +path);
                System.setProperty("datax.home", path+ "lib/datax/datax");
                for (String name : getNameList()) {
                    operation(path + name);
                }
//                Thread.sleep(2 * 60 * 1000L);
            } catch (Throwable e) {
                e.printStackTrace();
                System.out.println(e.toString());
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
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(ResourceUtils.CLASSPATH_URL_PREFIX+"/*.json");
            for(Resource resource : resources){
                String fileName = resource.getFilename();
                nameList.add(fileName);
            }

        }catch(Exception e){
            return nameList;
        }
        return nameList;

    }

    public static void main(String[] args) {
//        System.setProperty("datax.home", "D:\\project\\Datax\\target\\datax\\datax");
        try {
            System.setProperty("datax.home", "D:\\project\\Datax\\target\\datax\\datax");
            String[] datxArgs = {"-job", "D:\\project\\DataX\\copy_data\\src\\main\\resources\\daas2daas.json", "-mode", "standalone", "-jobid", "-1"};
            Engine.entry(datxArgs);   //从这里启动
            String[] datxArgs1 = {"-job", "D:\\project\\DataX\\copy_data\\src\\main\\resources\\daas2company_tag.json", "-mode", "standalone", "-jobid", "-1"};
            Engine.entry(datxArgs1);   //从这里启动
            Thread.sleep(2 * 60 * 1000L);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}
