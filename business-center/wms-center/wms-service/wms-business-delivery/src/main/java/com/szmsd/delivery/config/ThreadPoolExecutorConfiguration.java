package com.szmsd.delivery.config;

import cn.hutool.core.thread.NamedThreadFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolExecutorConfiguration {

    private static final int availableProcessors;

    static {
        // 获取机器核数
        availableProcessors = Runtime.getRuntime().availableProcessors();
    }

    /**
     * 提审操作后台执行 - 线程池
     */
    public static final String THREADPOOLEXECUTOR_DELOUTBOUND_REVIEWED = "ThreadPoolExecutor-DelOutbound-Reviewed";

    /**
     * 提审操作后台执行 - 重派出库单
     */
    public static final String THREADPOOLEXECUTOR_DELOUTBOUND_REVIEWED2 = "ThreadPoolExecutor-DelOutbound-Reviewed2";

    /**
     * #D2 接收出库包裹信息
     * ShipmentPackingEvent
     */
    public static final String THREADPOOLEXECUTOR_SHIPMENTPACKINGEVENT = "ThreadPoolExecutor-ShipmentPackingEvent";

    /**
     * 重新获取标签文件
     */
    public static final String THREADPOOLEXECUTOR_SHIPMENTENUMLABEL = "ThreadPoolExecutor-ShipmentEnumLabel";
    /**
     * 亚马逊挂号服务
     */
    public static final String THREADPOOLEXECUTOR_THIRDPARTY = "ThreadPoolExecutor-ThirdParty";

    public static final String THREADPOOLEXECUTOR_WMS = "ThreadPoolExecutor-WMS";


    /**
     * 出库单状态修改
     * WMS推送出库单状态为处理中。OMS这边修改状态为：仓库处理中
     */
    public static final String THREADPOOLEXECUTOR_DELOUTBOUND_PROCESSING = "ThreadPoolExecutor-DelOutbound-Processing";

    /**
     * 出库单状态修改
     * WMS推送出库单状态为已完成。
     * OMS修改状态为：
     * 1.接收状态，并修改出库单状态为：仓库已完成，并添加异步任务。
     * 2.异步任务接收，开始执行OMS完成动作。并且修改出库单状态为：已完成
     */
    public static final String THREADPOOLEXECUTOR_DELOUTBOUND_SHIPPED = "ThreadPoolExecutor-DelOutbound-Shipped";

    /**
     * 出库单状态修改
     * WMS推送出库单状态为已取消。
     * OMS修改状态为：
     * 1.接收状态，并修改出库单状态为：仓库已取消，并添加异步任务。
     * 2.异步任务接收，开始执行OMS取消动作。并且修改出库单状态为：已取消
     */
    public static final String THREADPOOLEXECUTOR_DELOUTBOUND_CANCELED = "ThreadPoolExecutor-DelOutbound-Canceled";

    /**
     * CK1保存请求信息
     */
    public static final String THREADPOOLEXECUTOR_CK1_SAVE = "ThreadPoolExecutor-Ck1-Save";

    /**
     * CK1发送请求信息
     */
    public static final String THREADPOOLEXECUTOR_CK1_REQUEST = "ThreadPoolExecutor-Ck1-Request";

    /**
     * TY保存请求信息
     */
    public static final String THREADPOOLEXECUTOR_TY_SAVE = "ThreadPoolExecutor-Ty-Save";

    /**
     * TY发送请求信息
     */
    public static final String THREADPOOLEXECUTOR_TY_REQUEST = "ThreadPoolExecutor-Ty-Request";

    /**
     * SRM保存请求信息
     */
    public static final String THREADPOOLEXECUTOR_SRM_SAVE = "ThreadPoolExecutor-Srm-Save";

    /**
     * SRM发送请求信息
     */
    public static final String THREADPOOLEXECUTOR_SRM_REQUEST = "ThreadPoolExecutor-Srm-Request";

    @Value("${thread.threadpoolexecutor}")
    private int threadpoolexecutor;

    @Bean(THREADPOOLEXECUTOR_DELOUTBOUND_REVIEWED)
    public ThreadPoolExecutor threadPoolExecutorDelOutboundReviewed() {
        // 核心线程数量
        int corePoolSize = threadpoolexecutor;
        int maximumPoolSize = threadpoolexecutor;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2048);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("DelOutbound-Reviewed", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_DELOUTBOUND_REVIEWED2)
    public ThreadPoolExecutor threadPoolExecutorDelOutboundReviewed2() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2048);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("DelOutbound-Reviewed2", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_SHIPMENTPACKINGEVENT)
    public ThreadPoolExecutor threadPoolExecutorShipmentPackingEvent() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2048);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("ShipmentPackingEvent", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_SHIPMENTENUMLABEL)
    public ThreadPoolExecutor threadPoolExecutorShipmentEnumLabel() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2048);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("ShipmentEnumLabel", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_SHIPMENTENUMLABEL)
    public ThreadPoolExecutor threadPoolExecutorThirdParty() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2048);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("ShipmentEnumLabel", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_DELOUTBOUND_PROCESSING)
    public ThreadPoolExecutor threadPoolExecutorDelOutboundProcessing() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2048);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("DelOutbound-Processing", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }
    @Bean(THREADPOOLEXECUTOR_THIRDPARTY)
    public ThreadPoolExecutor threadPoolExecutorTHIRDPARTY() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2048);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("DelOutbound-THIRDPARTY", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }
    @Bean(THREADPOOLEXECUTOR_WMS)
    public ThreadPoolExecutor threadPoolExecutorWMS() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2048);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("DelOutbound-WMS", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }


    @Bean(THREADPOOLEXECUTOR_DELOUTBOUND_SHIPPED)
    public ThreadPoolExecutor threadPoolExecutorDelOutboundShipped() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 2;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(4096);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("DelOutbound-Shipped", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_DELOUTBOUND_CANCELED)
    public ThreadPoolExecutor threadPoolExecutorDelOutboundCanceled() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(2048);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("DelOutbound-Canceled", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_CK1_SAVE)
    public ThreadPoolExecutor threadPoolExecutorCk1Save() {
        // 核心线程数量
        int corePoolSize = availableProcessors;
        int maximumPoolSize = availableProcessors * 2;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(20480);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("Ck1-Save", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_CK1_REQUEST)
    public ThreadPoolExecutor threadPoolExecutorCk1Request() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(20480);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("Ck1-Request", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_TY_SAVE)
    public ThreadPoolExecutor threadPoolExecutorTySave() {
        // 核心线程数量
        int corePoolSize = availableProcessors;
        int maximumPoolSize = availableProcessors * 2;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(20480);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("Ty-Save", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_TY_REQUEST)
    public ThreadPoolExecutor threadPoolExecutorTyRequest() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(20480);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("Ty-Request", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_SRM_SAVE)
    public ThreadPoolExecutor threadPoolExecutorSrmSave() {
        // 核心线程数量
        int corePoolSize = availableProcessors;
        int maximumPoolSize = availableProcessors * 2;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(20480);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("Srm-Save", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    @Bean(THREADPOOLEXECUTOR_SRM_REQUEST)
    public ThreadPoolExecutor threadPoolExecutorSrmRequest() {
        // 核心线程数量
        int corePoolSize = availableProcessors * 4;
        int maximumPoolSize = availableProcessors * 4;
        // 队列
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(20480);
        // 核心和最大一致
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, 10, TimeUnit.SECONDS, queue);
        // 线程池工厂
        NamedThreadFactory threadFactory = new NamedThreadFactory("Srm-Request", false);
        threadPoolExecutor.setThreadFactory(threadFactory);
        // 丢弃任务并抛出RejectedExecutionException异常
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }


}
