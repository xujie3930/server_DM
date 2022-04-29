package com.szmsd.delivery.event;

import org.springframework.context.ApplicationEvent;

import java.util.Date;
import java.util.List;

/**
 * @author zhangyuyuan
 * @date 2021-06-22 16:29
 */
public class DelOutboundOperationLogEvent extends ApplicationEvent {

    private final OperationLogEnum operationLogEnum;
    private final String tid;
    private final String userCode;
    private final String userName;
    private final String ip;
    private final Date nowDate;

    public DelOutboundOperationLogEvent(Object source, OperationLogEnum operationLogEnum, String tid, String userCode, String userName, String ip) {
        this(source, operationLogEnum, tid, userCode, userName, ip, null);
    }

    public DelOutboundOperationLogEvent(Object source, OperationLogEnum operationLogEnum, String tid, String userCode, String userName, String ip, Date nowDate) {
        super(source);
        this.operationLogEnum = operationLogEnum;
        this.tid = tid;
        this.userCode = userCode;
        this.userName = userName;
        this.ip = ip;
        this.nowDate = nowDate;
    }

    public OperationLogEnum getOperationLogEnum() {
        return operationLogEnum;
    }

    public String getTid() {
        return tid;
    }

    public String getUserCode() {
        return userCode;
    }

    public String getUserName() {
        return userName;
    }

    public String getIp() {
        return ip;
    }

    public Date getNowDate() {
        return nowDate;
    }

    public static class BatchObject {
        private List<String> orderNos;
        private Object params;

        public BatchObject(List<String> orderNos, Object params) {
            this.orderNos = orderNos;
            this.params = params;
        }

        public List<String> getOrderNos() {
            return orderNos;
        }

        public void setOrderNos(List<String> orderNos) {
            this.orderNos = orderNos;
        }

        public Object getParams() {
            return params;
        }

        public void setParams(Object params) {
            this.params = params;
        }
    }
}
