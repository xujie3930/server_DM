package com.szmsd.delivery.enums;

import org.springframework.http.HttpMethod;

public final class DelCk1RequestLogConstant {

    /**
     * 状态
     */
    public enum State {
        WAIT,
        FAIL_CONTINUE,
        FAIL,
        SUCCESS,
        ;
    }

    /**
     * 类型
     */
    public enum Type {
        create(HttpMethod.POST, "createDelCk1RequestLogCallback"),
        create_status(HttpMethod.GET, "createStatusDelCk1RequestLogCallback"),
        finished(HttpMethod.PUT),
        cancel(HttpMethod.PUT),
        ;

        private final HttpMethod httpMethod;
        private final String callbackService;

        Type(HttpMethod httpMethod) {
            this(httpMethod, "");
        }

        Type(HttpMethod httpMethod, String callbackService) {
            this.httpMethod = httpMethod;
            this.callbackService = callbackService;
        }

        public HttpMethod getHttpMethod() {
            return httpMethod;
        }

        public String getCallbackService() {
            return callbackService;
        }

    }
}
