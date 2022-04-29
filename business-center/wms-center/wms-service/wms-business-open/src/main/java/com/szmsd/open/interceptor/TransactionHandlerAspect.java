package com.szmsd.open.interceptor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.common.core.filter.ContextServletInputStream;
import com.szmsd.open.filter.RequestLogFilterContext;
import com.szmsd.open.service.IOpnTransactionService;
import com.szmsd.open.vo.ResponseVO;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * @author zhangyuyuan
 * @date 2021-03-26 19:01
 */
@Aspect
@Component
public class TransactionHandlerAspect {
    private final Logger logger = LoggerFactory.getLogger(TransactionHandlerAspect.class);

    @Value("${spring.application.name}")
    private String applicationName;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private IOpnTransactionService opnTransactionService;

    /**
     * 拦截所有的Controller
     */
    @Pointcut("execution(* com.szmsd.open.controller..*.*(..)) && !execution(* com.szmsd.open.controller.BaseController.*(..)) && !@annotation(com.szmsd.open.interceptor.NoTransactionHandler)")
    public void transactionHandler() {
    }

    /**
     * 环绕处理
     *
     * @param point point
     * @return Object
     * @throws Throwable Throwable
     */
    @Around("transactionHandler()")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object result;
        RequestLogFilterContext currentContext = RequestLogFilterContext.getCurrentContext();
        if (StringUtils.isNotEmpty(currentContext.getRequestUri()) && StringUtils.isNotEmpty(currentContext.getTransactionId())) {
            String key = applicationName + ":transaction:" + builderOnlyKey(currentContext);
            RLock lock = redissonClient.getLock(key);
            long time = 5;
            TimeUnit timeUnit = TimeUnit.SECONDS;
            try {
                // 获取锁
                if (lock.tryLock(time, timeUnit)) {
                    // 验证有没有REP记录
                    if (this.opnTransactionService.hasRep(currentContext.getRequestUri(), currentContext.getTransactionId(), currentContext.getAppId())) {
                        result = ResponseVO.ok("重复请求");
                    } else {
                        // 新增记录
                        this.opnTransactionService.add(currentContext.getRequestId(), currentContext.getRequestUri(), currentContext.getTransactionId(), currentContext.getAppId());
                        try {
                            // 执行业务操作
                            result = proceed(point);
                            // 执行成功
                            this.opnTransactionService.onRep(currentContext.getRequestId(), currentContext.getAppId());
                        } catch (CommonException e) {
                            logger.error(e.getMessage(), e);
                            result = ResponseVO.failed(e.getMessage());
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            result = ResponseVO.failed("执行失败，请重新再试，" + e.getMessage());
                        }
                    }
                } else {
                    result = ResponseVO.failed("执行失败，请重新再试");
                }
            } catch (InterruptedException e) {
                logger.error(e.getMessage(), e);
                result = ResponseVO.failed("执行失败，请重新再试，" + e.getMessage());
            } finally {
                lock.unlock();
            }
        } else {
            // 不走切面
            result = point.proceed();
        }
        return result;
    }

    /**
     * 执行业务操作
     *
     * @param point
     * @return
     * @throws Throwable
     */
    public Object proceed(ProceedingJoinPoint point) throws Throwable {
        String requestBody = getRequestBody();
        Object[] args = point.getArgs();
        if (StringUtils.isEmpty(requestBody) && (args == null || args.length == 0)) {
            return point.proceed();
        }
        Object parse = JSON.parse(requestBody);
        args[0] = JSON.toJavaObject((JSONObject) parse, args[0].getClass());
        return point.proceed(args);
    }

    public String getRequestBody() throws IOException {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        byte[] bytes = ((ContextServletInputStream) request.getInputStream()).getContent().getBytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String builderOnlyKey(RequestLogFilterContext currentContext) {
        return Base64.getEncoder().encodeToString(currentContext.getRequestUri().getBytes(StandardCharsets.UTF_8)) + "_" + currentContext.getAppId() + "_" + currentContext.getTransactionId();
    }
}
