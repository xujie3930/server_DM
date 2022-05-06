package com.szmsd.open.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.util.StringUtil;
import com.szmsd.common.core.exception.com.AssertUtil;
import com.szmsd.common.core.filter.ContextHttpServletRequestWrapper;
import com.szmsd.common.core.filter.ContextHttpServletResponseWrapper;
import com.szmsd.common.core.filter.ContextServletInputStream;
import com.szmsd.common.core.filter.ContextServletOutputStream;
import com.szmsd.open.config.AuthConfig;
import com.szmsd.open.domain.OpnRequestLog;
import com.szmsd.open.event.EventUtil;
import com.szmsd.open.event.RequestLogEvent;
import com.szmsd.open.interceptor.RequestConstant;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.util.WebUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author zhangyuyuan
 * @date 2021-03-06 17:12
 */
public class RequestLogFilter implements Filter {
    private static final int CONTENT_LENGTH = 40960;
    private static final String METHOD_GET = "GET";
    private final Logger logger = LoggerFactory.getLogger(RequestLogFilter.class);

    private final AuthConfig authConfig;

    public RequestLogFilter(AuthConfig authConfig) {
        this.authConfig = authConfig;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        ContextHttpServletRequestWrapper requestWrapper;
        if (servletRequest instanceof ContextHttpServletRequestWrapper) {
            requestWrapper = (ContextHttpServletRequestWrapper) servletRequest;
        } else {
            requestWrapper = new ContextHttpServletRequestWrapper((HttpServletRequest) servletRequest);
        }
        ContextHttpServletResponseWrapper responseWrapper;
        if (servletResponse instanceof ContextHttpServletResponseWrapper) {
            responseWrapper = (ContextHttpServletResponseWrapper) servletResponse;
        } else {
            responseWrapper = new ContextHttpServletResponseWrapper((HttpServletResponse) servletResponse);
        }

        Date requestTime = new Date();
        String traceId = MDC.get("TID");

        String requestUri = requestWrapper.getRequestURI();
        Set<String> whiteSet = authConfig.getWhiteSet();
        if (CollectionUtils.isNotEmpty(whiteSet)) {
            if (whiteSet.contains(requestUri)) {
                filterChain.doFilter(requestWrapper, responseWrapper);
                return;
            }
        }
        // 获取transactionId
        String transactionId = requestWrapper.getHeader(RequestConstant.TRANSACTION_ID);
        String appId = requestWrapper.getHeader(RequestConstant.APP_ID);
        AssertUtil.isTrue(StringUtils.isNotEmpty(transactionId), "transactionId cannot be null");
        RequestLogFilterContext currentContext = RequestLogFilterContext.getCurrentContext();
        currentContext.setRequestId(traceId);
        currentContext.setTransactionId(transactionId);
        currentContext.setAppId(appId);
        currentContext.setRequestUri(requestUri);
        filterChain.doFilter(requestWrapper, responseWrapper);

        Date responseDate = new Date();
        OpnRequestLog opnRequestLog = new OpnRequestLog();
        opnRequestLog.setTraceId(traceId);
        opnRequestLog.setRequestUri(requestUri);
        opnRequestLog.setRequestMethod(requestWrapper.getMethod());
        opnRequestLog.setRequestHeader(requestHeader(requestWrapper));
        opnRequestLog.setRequestBody(requestParam(requestWrapper).getRequestBody());
        opnRequestLog.setRequestTime(requestTime);
        opnRequestLog.setResponseHeader(responseHeader(responseWrapper));
        opnRequestLog.setResponseBody(responseParam(responseWrapper));
        opnRequestLog.setResponseTime(responseDate);
        EventUtil.publishEvent(new RequestLogEvent(opnRequestLog));
    }

    private String requestHeader(ContextHttpServletRequestWrapper requestWrapper) {
        Map<String, String> headerMap = new HashMap<>();
        Enumeration<String> headerNames = requestWrapper.getHeaderNames();
        if (null != headerNames) {
            while (headerNames.hasMoreElements()) {
                String key = headerNames.nextElement();
                headerMap.put(key, requestWrapper.getHeader(key));
            }
        }
        return JSON.toJSONString(headerMap);
    }

    private RequestBodyObject requestParam(ContextHttpServletRequestWrapper requestWrapper) {
        RequestBodyObject body = new RequestBodyObject();
        String method = requestWrapper.getMethod();
        // 处理GET请求参数
        if (method.equalsIgnoreCase(METHOD_GET)) {
            String requestBody = requestWrapper.getQueryString();
            body.setRequestBody(requestBody);
            body.setTransactionId(requestWrapper.getParameter("transactionId"));
        } else {
            // 处理POST请求参数
            int size = requestWrapper.getContentLength();
            if (size < CONTENT_LENGTH) {
                ContextServletInputStream contextServletInputStream = requestWrapper.getContextServletInputStream();
                if (null == contextServletInputStream) {
                    try {
                        contextServletInputStream = (ContextServletInputStream) requestWrapper.getInputStream();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                // 1.这里无法读取到流的信息，就直接获取request中的参数就行了
                // 2.虽然修改了Filter的顺序，或者是重写HiddenHttpMethodFilter，也无法读取到流
                String requestBody;
                String transactionId;
                if (contextServletInputStream != null) {
                    requestBody = new String(contextServletInputStream.getContent().getBytes(), StandardCharsets.UTF_8);
                    try {
                        JSONObject jsonObject = JSONObject.parseObject(requestBody);
                        if (null != jsonObject) {
                            transactionId = jsonObject.getString("transactionId");
                        } else {
                            transactionId = "";
                        }
                    } catch (ClassCastException e) {
                        logger.error("无法转换的JSON：{}", requestBody);
                        logger.error(e.getMessage(), e);
                        transactionId = "";
                    }
                } else {
                    Map<String, Object> objectMap = WebUtils.getParametersStartingWith(requestWrapper, null);
                    requestBody = JSON.toJSONString(objectMap);
                    transactionId = String.valueOf(objectMap.get("transactionId"));
                }
                body.setRequestBody(requestBody);
                body.setTransactionId(transactionId);
            }
        }
        return body;
    }

    private String responseHeader(ContextHttpServletResponseWrapper responseWrapper) {
        Map<String, String> headerMap = new HashMap<>();
        Collection<String> headerNames = responseWrapper.getHeaderNames();
        if (null != headerNames) {
            for (String headerName : headerNames) {
                headerMap.put(headerName, responseWrapper.getHeader(headerName));
            }
        }
        return JSON.toJSONString(headerMap);
    }

    private String responseParam(ContextHttpServletResponseWrapper responseWrapper) {
        ContextServletOutputStream traceOutputStream = responseWrapper.getContextServletOutputStream();
        if (null != traceOutputStream && StringUtil.isNotEmpty(traceOutputStream.getContent())) {
            return new String(traceOutputStream.getContent().getBytes(), StandardCharsets.UTF_8);
        }

        PrintWriter printWriter = null;
        try {
            printWriter = responseWrapper.getWriter();
        } catch (IOException e) {
            e.printStackTrace();
        }


        return null;
    }
}
