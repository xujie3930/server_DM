package com.szmsd.delivery.enums;

import java.util.Objects;

/**
 * @author zhangyuyuan
 * @date 2021-03-05 16:48
 */
public enum DelOutboundOrderTypeEnum {

    NORMAL("Normal", "普通出库单"),
    DESTROY("Destroy", "销毁出库单"),
    SELF_PICK("SelfPick", "自提出库单"),
    PACKAGE_TRANSFER("PackageTransfer", "转运出库单"),
    COLLECTION("Collection", "集运出库单"),
    NEW_SKU("NewSku", "组合SKU上架出库单"),
    SPLIT_SKU("SplitSku", "拆分SKU上架出库单"),
    BATCH("Batch", "批量出库单"),
    SALES("Sales", "普通销售订单"),
    MULTIPLE_PIECES("MultiplePieces","一票多件出库单"),
    BULK_ORDER("BulkOrder","大货订单"),

    ;

    private final String code;
    private final String name;

    DelOutboundOrderTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static DelOutboundOrderTypeEnum get(String code) {
        if (null == code) {
            return null;
        }
        for (DelOutboundOrderTypeEnum anEnum : DelOutboundOrderTypeEnum.values()) {
            if (anEnum.getCode().equals(code)) {
                return anEnum;
            }
        }
        return null;
    }

    public static String getCode(String name) {
        if (null == name) {
            return null;
        }
        for (DelOutboundOrderTypeEnum anEnum : DelOutboundOrderTypeEnum.values()) {
            if (anEnum.getName().equals(name)) {
                return anEnum.getCode();
            }
        }
        return null;
    }

    public static String getOriginName(String code) {
        DelOutboundOrderTypeEnum anEnum = get(code);
        if (null != anEnum) {
            return anEnum.getCode();
        }
        return "";
    }

    public static boolean has(String code) {
        return Objects.nonNull(get(code));
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
