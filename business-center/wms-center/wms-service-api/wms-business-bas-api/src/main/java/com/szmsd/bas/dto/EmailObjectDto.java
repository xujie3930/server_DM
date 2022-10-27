package com.szmsd.bas.dto;


import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(value = "", description = "EmailObjectDto对象")
public class EmailObjectDto {
    @ColumnWidth(30)
    @ExcelProperty(index = 0, value = "客户代码")
    private String customCode;
    @ColumnWidth(30)
    @ExcelProperty(index = 1, value = "CBD")
    private String serviceManagerName;
    @ColumnWidth(30)
    @ExcelProperty(index = 2, value = "CS")
    private String serviceStaffName;
    @ColumnWidth(30)
    @ExcelProperty(index = 3, value = "orderNo")
    private String orderNo;

    @ColumnWidth(30)
    @ExcelProperty(index = 4, value = "trackingNo")
    private String noTrackingNo;

    @ColumnWidth(30)
    @ExcelProperty(index = 5, value = "New TrackingNo")
    private String trackingNo;






}