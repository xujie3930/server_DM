package com.szmsd.bas.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.szmsd.common.core.annotation.Excel;
import com.szmsd.common.core.web.domain.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;


/**
 * <p>
 * 客户shopify授权信息
 * </p>
 *
 * @author asd
 * @since 2022-05-11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ApiModel(value = "BasSellerShopifyPermission", description = "BasSellerShopifyPermission对象")
public class BasSellerShopifyPermission extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "id不能为空")
    @ApiModelProperty(value = "id")
    @TableId(value = "id", type = IdType.AUTO)
    @Excel(name = "id")
    private Long id;

    @ApiModelProperty(value = "客户id")
    @Excel(name = "客户id")
    private Long sellerId;

    @ApiModelProperty(value = "客户代码")
    @Excel(name = "客户代码")
    private String sellerCode;

    @ApiModelProperty(value = "客户名称")
    @Excel(name = "客户名称")
    private String sellerName;

    @ApiModelProperty(value = "访问权限，逗号分割")
    @Excel(name = "访问权限，逗号分割")
    private String scope;

    @ApiModelProperty(value = "永久令牌")
    @Excel(name = "永久令牌")
    private String accessToken;

    @ApiModelProperty(value = "最终访问权限，逗号分割")
    @Excel(name = "最终访问权限，逗号分割")
    private String oauthScope;

    @ApiModelProperty(value = "店铺名称")
    @Excel(name = "店铺名称")
    private String shop;

    @ApiModelProperty(value = "状态，1有效，2无效")
    @Excel(name = "状态，1有效，2无效")
    private String state;

    @ApiModelProperty(value = "locations")
    private String locations;

    @NotNull(message = "默认发货仓库不能为空")
    @ApiModelProperty(value = "默认仓库id")
    private Long defaultLocationId;

    @NotEmpty(message = "默认发货仓库不能为空")
    @ApiModelProperty(value = "默认仓库地址")
    private String defaultLocation;

    @ApiModelProperty("当前页，从1开始，默认为1")
    @TableField(exist = false)
    private int pageNum = 1;

    @ApiModelProperty("每页的数量，默认为10")
    @TableField(exist = false)
    private int pageSize = 10;
}
