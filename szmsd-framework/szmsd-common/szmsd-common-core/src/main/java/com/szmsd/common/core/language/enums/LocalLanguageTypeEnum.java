package com.szmsd.common.core.language.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LocalLanguageTypeEnum {

    /** 系统语言 **/
    SYSTEM_LANGUAGE,

    /** 入库单状态 **/
    INBOUND_RECEIPT_STATUS,

    /** 入库单日志状态 **/
    INBOUND_RECEIPT_log_STATUS,

    /** 入库单日志备注 **/
    INBOUND_RECEIPT_log_REMARK,

    /** 库存日志类型 **/
    INVENTORY_RECORD_TYPE,

    /** 库存日志 **/
    INVENTORY_RECORD_LOGS,

    /** 入库单是否人工审核 **/
    INBOUND_RECEIPT_REVIEW,

    /** 是否需要 **/
    NEED,

    /** 是否有效 **/
    VALID,

    /** 是否 **/
    YN,

    /** 首页订单类型 **/
    HOME_DOCUMENT_TYPE,

    /** 首页条形统计图 **/
    HOME_BAR_CHART_TYPE,

    /** 地址管理*/
    ADDRESS_MANAGEMENT,

    /** 退件 */
    RETURN_EXPRESS
    ;
}
