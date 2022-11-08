package com.szmsd.common.core.interceptor;

/**
 * @author xujie
 * @description 全局trackId
 * @create 2022-11-07 17:25
 **/

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

/**
 * 日志拦截器
 */
@Slf4j
@Component
public class LogInterceptor implements HandlerInterceptor {
    // 会话ID
    private final static String SESSION_ID = "TID";

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o) {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        MDC.put(SESSION_ID, uuid);
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) {
        MDC.remove(SESSION_ID);

    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) {
    }
}
