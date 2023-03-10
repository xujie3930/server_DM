package com.szmsd.bas.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.szmsd.common.core.utils.StringToolkit;
import com.szmsd.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import com.szmsd.common.core.annotation.Excel;

import java.util.Date;
import java.util.List;
import java.util.Optional;


/**
 * <p>
 *
 * </p>
 *
 * @author l
 * @since 2021-03-09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ApiModel(value = "BasSeller", description = "BasSeller对象")
public class BasSeller extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "id")
    @TableId(value = "id", type = IdType.AUTO)
//    @Excel(name = "id")
    private Long id;

    @ApiModelProperty(value = "创建人")
//    @Excel(name = "创建人")
    private String createBy;

    @ApiModelProperty(value = "修改人")
//    @Excel(name = "修改人")
    private String updateBy;

    @ApiModelProperty(value = "删除标识：0未删除 1已删除")
//    @Excel(name = "删除标识：0未删除 1已删除")
    private String delFlag;

    @ApiModelProperty(value = "客户代码")
    @Excel(name = "客户代码")
    private String sellerCode;

    @ApiModelProperty(value = "初始注册邮箱")
    @Excel(name = "注册邮箱")
    private String initEmail;

    @ApiModelProperty(value = "用户名")
    @Excel(name = "用户名")
    private String userName;

    @ApiModelProperty(value = "公司")
    @Excel(name = "公司")
    private String company;



    public void setSellerCode(String sellerCode) {
        this.sellerCode = sellerCode;
        Optional.ofNullable(sellerCode).ifPresent(x -> this.sellerCodeList = StringToolkit.getCodeByArray(sellerCode));
    }

    @TableField(exist = false)
    @ApiModelProperty(value = "客户代码")
//    @Excel(name = "客户代码")
    private List<String> sellerCodeList;

    @ApiModelProperty(value = "认证状态 ")
//    @Excel(name = "认证状态 ")
    private Boolean state;

    @ApiModelProperty(value = "用户状态 生效 失效")
//    @Excel(name = "用户状态 生效 失效")
    private Boolean isActive;

    @ApiModelProperty(value = "业务经理-id")
//    @Excel(name = "业务经理")
    private String serviceManager;

    @ApiModelProperty(value = "业务经理-编码")
//    @Excel(name = "业务经理-编码")
    private String serviceManagerName;

    @ApiModelProperty(value = "业务经理-名称")
    @Excel(name = "客户经理")
    private String serviceManagerNickName;

    @ApiModelProperty(value = "国家")
//    @Excel(name = "国家")
    private String countryName;

    @ApiModelProperty(value = "国家")
//    @Excel(name = "国家")
    private String countryCode;

    @ApiModelProperty(value = "客服-id")
//    @Excel(name = "客服")
    private String serviceStaff;

    @ApiModelProperty(value = "客服姓名-编码")
//    @Excel(name = "客服姓名")
    private String serviceStaffName;

    @ApiModelProperty(value = "客服姓名")
    @Excel(name = "客服人员")
    private String serviceStaffNickName;

    @ApiModelProperty(value = "姓名")
//    @Excel(name = "姓名")
    private String nickName;

    @ApiModelProperty(value = "邮箱")
//    @Excel(name = "邮箱")
    private String email;

    @ApiModelProperty(value = "住址")
//    @Excel(name = "住址")
    private String address;

    @ApiModelProperty(value = "联系电话")
//    @Excel(name = "联系电话")
    private String phoneNumber;

    @ApiModelProperty(value = "身份证号码")
//    @Excel(name = "身份证号码")
    private String idCard;

    @ApiModelProperty(value = "出口易账号")
//    @Excel(name = "出口易账号")
    private String ck1Account;

    @ApiModelProperty(value = "中文名")
//    @Excel(name = "中文名")
    private String nameCn;

    @ApiModelProperty(value = "英文名")
//    @Excel(name = "英文名")
    private String nameEn;



    @ApiModelProperty(value = "性别 F 女 M 男")
//    @Excel(name = "性别 F 女 M 男")
    private String sex;

    @ApiModelProperty(value = "是否验重量尺寸")
//    @Excel(name = "是否验重量尺寸")
    private String inspectionRequirement;


    @ApiModelProperty(value = "是否验属性")
//    @Excel(name = "是否验属性")
    private String inspectionAttribute;

    @ApiModelProperty(value = "真实姓名")
//    @Excel(name = "真实姓名")
    private String realName;

    @ApiModelProperty(value = "证件号码")
//    @Excel(name = "证件号码")
    private String identificationNumber;

    @ApiModelProperty(value = "用户类型")
//    @Excel(name = "用户类型")
    private String type;

    @ApiModelProperty(value = "实名状态")
//    @Excel(name = "实名状态")
    private String realState;

    @ApiModelProperty(value = "折扣用户类型-编码", notes = "主子类别")
//    @Excel(name = "折扣用户类型-编码")
    private String discountUserType;

    /**----------------CK1 INFO----------------*/
    /**
     * 把push set到 thirdPartSystemInfo中
     */
    @ApiModelProperty(value = "是否需要推送CK1")
    private Boolean pushFlag;

    @ApiModelProperty(value = "授权码")
    private String authorizationCode;

    @ApiModelProperty(value = "子母状态 0未关联 1母 2子")
    private String childParentStatus;

    @ApiModelProperty(value = "关联账号信息")
    @TableField(exist = false)
    List<BasChildParentChild> childList;


    @ApiModelProperty(value = "申请时间")
    @TableField(exist = false)
    private Date applyTime;
    @ApiModelProperty(value = "申请人")
    @TableField(exist = false)
    private String applyName;

    @ApiModelProperty(value = "申请人编码")
    @TableField(exist = false)
    private String applyCode;

    @ApiModelProperty(value = "是否自动生成入库单 0或null:不生成 1:生成")
    private String generateInboundReceipt;



    @ApiModelProperty(value = "推荐人名字")
    @Excel(name = "推荐人")
    private String secondSalesStaffName;

    @ApiModelProperty(value = "推荐人名字code")
    private String secondSalesStaffCode;

    @ApiModelProperty(value = "0表示金额，1表示账期")
    @TableField(exist = false)
//    @Excel(name = "授信方式")
    private String creditType;

    @ApiModelProperty(value = "客户basic认证秘钥")
    @TableField(exist = false)
    private String sellerKey;



    @ApiModelProperty(value = "自定义尺寸")
    private String rulerCustomized;


    @ApiModelProperty(value = "勾选id集合")
    @TableField(exist = false)
    private List<String> ids;




}
