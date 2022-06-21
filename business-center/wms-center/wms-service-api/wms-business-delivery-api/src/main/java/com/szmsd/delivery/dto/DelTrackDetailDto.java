package com.szmsd.delivery.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import com.szmsd.bas.plugin.BasSubCommonPlugin;
import com.szmsd.bas.plugin.BasSubValueCommonParameter;
import com.szmsd.common.core.annotation.Excel;
import com.szmsd.common.core.web.domain.BaseEntity;
import com.szmsd.common.plugin.annotation.AutoFieldValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;


/**
 * <p>
 *
 * </p>
 *
 * @author YM
 * @since 2022-02-10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ApiModel(value = "", description = "DelTrackCommonDto对象")
public class DelTrackDetailDto extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "运输包裹的物流跟踪号")
    @Excel(name = "运输包裹的物流跟踪号")
    private String trackingNo;

    @ApiModelProperty(value = "运输包裹的运输商编码")
    @Excel(name = "运输包裹的运输商编码")
    private String carrierCode;

    @ApiModelProperty(value = "运输包裹的订单号，可以是电商平台的订单号，也可以是用 于自己做唯一标示的号。")
    @Excel(name = "运输包裹的订单号，可以是电商平台的订单号，也可以是用 于自己做唯一标示的号。")
    private String orderNo;

    @AutoFieldValue(supports = BasSubCommonPlugin.SUPPORTS, code = "099", cp = BasSubValueCommonParameter.class)
    @ApiModelProperty(value = "轨迹状态")
    private String trackingStatus;

    @ApiModelProperty(value = "轨迹状态名称")
    @TableField(exist = false)
    private String trackingStatusName;

    @ApiModelProperty(value = " 序号，每单轨迹从 1 开始")
    @Excel(name = " 序号，每单轨迹从 1 开始")
    private Integer no;

    @ApiModelProperty(value = "轨迹信息描述")
    @Excel(name = "轨迹信息描述")
    private String description;

    @ApiModelProperty(value = "轨迹时间")
    @Excel(name = "轨迹时间")
    private Date trackingTime;

    @AutoFieldValue(supports = BasSubCommonPlugin.SUPPORTS, code = "100", cp = BasSubValueCommonParameter.class)
    @ApiModelProperty(value = "动作节点")
    private String actionCode;

    @ApiModelProperty(value = "动作节点名称")
    @TableField(exist = false)
    private String actionCodeName;

    @ApiModelProperty(value = "物流轨迹发生的位置显示描述")
    @Excel(name = "物流轨迹发生的位置显示描述")
    private String display;

    @ApiModelProperty(value = "包裹所处的国家编码")
    @Excel(name = "包裹所处的国家编码")
    private String countryCode;

    @ApiModelProperty(value = "国家或地区名称(英文)")
    @Excel(name = "国家或地区名称(英文)")
    private String countryNameEn;

    @ApiModelProperty(value = "国家或地区名称（中文）")
    @Excel(name = "国家或地区名称（中文）")
    private String countryNameCn;

    @ApiModelProperty(value = "省")
    @Excel(name = "省")
    private String province;

    @ApiModelProperty(value = "市")
    @Excel(name = "市")
    private String city;

    @ApiModelProperty(value = "邮编")
    @Excel(name = "邮编")
    private String postcode;

    @ApiModelProperty(value = "街道1")
    @Excel(name = "街道1")
    private String street1;

    @ApiModelProperty(value = "街道2")
    @Excel(name = "街道2")
    private String street2;

    @ApiModelProperty(value = "街道3")
    @Excel(name = "街道3")
    private String street3;

    @ApiModelProperty(value = "轨迹数据集合")
    private List<DelTrackCommonDto> trackingList;


    @ApiModelProperty(value = "轨迹天数")
    private Long trackDays;
}
