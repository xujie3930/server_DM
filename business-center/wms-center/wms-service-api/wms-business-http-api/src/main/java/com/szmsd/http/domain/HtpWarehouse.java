package com.szmsd.http.domain;

import com.szmsd.common.core.language.annotation.FieldJsonI18n;
import com.szmsd.common.core.language.constant.RedisLanguageTable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ApiModel(value = "HtpWarehouse", description = "仓库组关联仓库")
public class HtpWarehouse {

    @ApiModelProperty(value = "地址组")
    private String groupId;

    @ApiModelProperty(value = "仓库编码")
    private String warehouseCode;

    @ApiModelProperty(value = "仓库名称")
    @FieldJsonI18n(type = RedisLanguageTable.BAS_WAREHOUSE)
    private String warehouseName;

}
