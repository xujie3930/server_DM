package com.szmsd.delivery.enums;

public final class DelTyRequestLogConstant {

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
        shipments,
        ;

    }
}
